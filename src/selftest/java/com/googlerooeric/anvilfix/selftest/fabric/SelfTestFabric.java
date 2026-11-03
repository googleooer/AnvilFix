package com.googlerooeric.anvilfix.selftest.fabric;

import com.googlerooeric.anvilfix.selftest.ClientSmoke;
import com.googlerooeric.anvilfix.selftest.SelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class SelfTestFabric implements ModInitializer, ClientModInitializer {
    @Override
    public void onInitialize() {
        if (SelfTest.enabled()) {
            ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::runAndExit);
        }
    }

    @Override
    public void onInitializeClient() {
        if (ClientSmoke.enabled()) {
            ClientTickEvents.END_CLIENT_TICK.register(ClientSmoke::onClientTick);
        }
    }
}
