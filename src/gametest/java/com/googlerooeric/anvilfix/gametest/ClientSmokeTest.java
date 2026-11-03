package com.googlerooeric.anvilfix.gametest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * With -Danvilfix.smoke=true, waits for the client to finish starting, loads the anvil screen class so its mixins
 * are applied (a failed injection crashes here instead of when someone first opens an anvil), then quits.
 */
public class ClientSmokeTest implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AnvilFix smoke test");

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("anvilfix.smoke")) {
            return;
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                Class.forName("net.minecraft.client.gui.screen.ingame.AnvilScreen");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            LOGGER.info("ANVILFIX_SMOKE_OK client started and anvil mixins applied");
            client.scheduleStop();
        });
    }
}
