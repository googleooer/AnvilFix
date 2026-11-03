package com.googlerooeric.anvilfix.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Version-specific client calls for the in-game test. */
final class TestClient {

    private TestClient() {
    }

    /** The open screen; it lives on Gui from 26.2. */
    static net.minecraft.client.gui.screens.Screen screen(Minecraft minecraft) {
        //? if >=26.2 {
        /*return minecraft.gui.screen();
        *///?} else {
        return minecraft.screen;
        //?}
    }

    /** The loading overlay, null once resources are loaded. */
    static Object overlay(Minecraft minecraft) {
        //? if >=26.2 {
        /*return minecraft.gui.overlay();
        *///?} else {
        return minecraft.getOverlay();
        //?}
    }

    static void connect(Minecraft minecraft, String host, int port) {
        ServerAddress address = new ServerAddress(host, port);
        //? if >=1.20.5 {
        /*ServerData data = new ServerData("AnvilFix self test", host + ":" + port, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(TestClient.screen(minecraft), minecraft, address, data, false, null);
        *///?} elif >=1.20.2 {
        /*ServerData data = new ServerData("AnvilFix self test", host + ":" + port, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(TestClient.screen(minecraft), minecraft, address, data, false);
        *///?} elif >=1.20 {
        /*ServerData data = new ServerData("AnvilFix self test", host + ":" + port, false);
        ConnectScreen.startConnecting(TestClient.screen(minecraft), minecraft, address, data, false);
        *///?} else {
        ServerData data = new ServerData("AnvilFix self test", host + ":" + port, false);
        ConnectScreen.startConnecting(TestClient.screen(minecraft), minecraft, address, data);
        //?}
    }
}
