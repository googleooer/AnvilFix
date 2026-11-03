package com.googlerooeric.anvilfix.selftest;

import net.minecraft.client.Minecraft;

/**
 * With -Danvilfix.smoke=true, waits until resource loading is done and a menu screen is up, loads the anvil screen class so its mixins are applied
 * (a failed injection crashes here instead of when someone first opens an anvil), then quits.
 */
public final class ClientSmoke {
    private static boolean done;

    private ClientSmoke() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("anvilfix.smoke");
    }

    public static void onClientTick(Minecraft minecraft) {
        // The first menu isn't always the title screen (e.g. loader warnings), so accept any screen once loading is over
        if (done || minecraft.getOverlay() != null || minecraft.screen == null) {
            return;
        }
        done = true;
        // Forge and NeoForge show their mod loading errors and warnings on this screen instead of crashing
        if (minecraft.screen.getClass().getSimpleName().equals("LoadingErrorScreen")) {
            SelfTest.LOGGER.error("ANVILFIX_SMOKE_FAILED mod loading reported problems: {}", describeFields(minecraft.screen));
            System.exit(1);
        }
        try {
            Class.forName("net.minecraft.client.gui.screens.inventory.AnvilScreen");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        SelfTest.LOGGER.info("ANVILFIX_SMOKE_OK client finished loading ({}) and anvil mixins applied", minecraft.screen.getClass().getName());
        minecraft.stop();
    }

    private static String describeFields(Object screen) {
        StringBuilder out = new StringBuilder();
        for (java.lang.reflect.Field field : screen.getClass().getDeclaredFields()) {
            if (java.util.Collection.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    out.append(field.getName()).append("=[");
                    for (Object entry : (java.util.Collection<?>) field.get(screen)) {
                        out.append(format(entry)).append("; ");
                    }
                    out.append("] ");
                } catch (ReflectiveOperationException | RuntimeException e) {
                    out.append(field.getName()).append("=<").append(e).append("> ");
                }
            }
        }
        return out.toString();
    }

    // ModLoadingWarning/ModLoadingException only have a readable message through formatToString()
    private static String format(Object entry) {
        try {
            return String.valueOf(entry.getClass().getMethod("formatToString").invoke(entry));
        } catch (ReflectiveOperationException e) {
            return String.valueOf(entry);
        }
    }
}
