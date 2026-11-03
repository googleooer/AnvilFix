package com.googlerooeric.anvilfix.selftest;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@link AnvilScenarios} on a started dedicated server, writes a JUnit report and exits with 0 (all passed) or 1.
 * Each loader calls {@link #runAndExit} from its server-started event when -Danvilfix.selftest=true.
 */
public final class SelfTest {
    public static final Logger LOGGER = LoggerFactory.getLogger("AnvilFix self test");

    private record Outcome(String name, long millis, String failure) {
    }

    private SelfTest() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("anvilfix.selftest");
    }

    public static void runAndExit(MinecraftServer server) {
        List<Outcome> outcomes = new ArrayList<>();
        for (AnvilScenarios.Scenario scenario : AnvilScenarios.all()) {
            long start = System.nanoTime();
            String failure = null;
            try {
                scenario.body().accept(new AnvilScenarios.Context(server));
            } catch (Throwable t) {
                failure = t.getClass() == AssertionError.class ? t.getMessage() : t.toString();
                LOGGER.error("FAIL {}: {}", scenario.name(), failure, t);
            }
            outcomes.add(new Outcome(scenario.name(), (System.nanoTime() - start) / 1_000_000, failure));
        }

        long failed = outcomes.stream().filter(o -> o.failure() != null).count();
        for (Outcome o : outcomes) {
            LOGGER.info("{} {}", o.failure() == null ? "PASS" : "FAIL", o.name());
        }
        LOGGER.info("ANVILFIX_SELFTEST_RESULT {} passed, {} failed", outcomes.size() - failed, failed);
        writeReport(outcomes, failed);

        int code = failed == 0 ? 0 : 1;
        if (IngameHost.enabled() && code == 0) {
            IngameHost.hostThenExit(server, code);
            return;
        }
        // Exit from another thread: the shutdown hook waits for the server thread, which we're on
        new Thread(() -> System.exit(code), "AnvilFix self test exit").start();
    }

    private static void writeReport(List<Outcome> outcomes, long failed) {
        String path = System.getProperty("anvilfix.selftest.report");
        if (path == null) {
            return;
        }
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"anvilfix\" tests=\"").append(outcomes.size()).append("\" failures=\"").append(failed).append("\">\n");
        for (Outcome o : outcomes) {
            xml.append("  <testcase classname=\"AnvilScenarios\" name=\"").append(o.name()).append("\" time=\"").append(o.millis() / 1000.0).append('"');
            if (o.failure() == null) {
                xml.append("/>\n");
            } else {
                xml.append(">\n    <failure message=\"").append(escape(o.failure())).append("\"/>\n  </testcase>\n");
            }
        }
        xml.append("</testsuite>\n");
        try {
            Path file = Path.of(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, xml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Couldn't write {}", path, e);
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
