package com.googlerooeric.anvilfix.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Version-specific client calls for the in-game test. */
final class TestClient {

    private TestClient() {
    }

    static void connect(Minecraft minecraft, String host, int port) {
        ServerAddress address = new ServerAddress(host, port);
        //? if >=1.20.5 {
        /*ServerData data = new ServerData("AnvilFix self test", host + ":" + port, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(minecraft.screen, minecraft, address, data, false, null);
        *///?} elif >=1.20 {
        /*ServerData data = new ServerData("AnvilFix self test", host + ":" + port, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(minecraft.screen, minecraft, address, data, false);
        *///?} else {
        ServerData data = new ServerData("AnvilFix self test", host + ":" + port, false);
        ConnectScreen.startConnecting(minecraft.screen, minecraft, address, data);
        //?}
    }
}
