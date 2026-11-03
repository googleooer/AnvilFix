package com.googlerooeric.anvilfix.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * With -Danvilfix.smoke=true, waits for the title screen, loads the anvil screen class so its mixins are applied
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
        if (done || !(minecraft.screen instanceof TitleScreen)) {
            return;
        }
        done = true;
        try {
            Class.forName("net.minecraft.client.gui.screens.inventory.AnvilScreen");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        SelfTest.LOGGER.info("ANVILFIX_SMOKE_OK client reached the title screen and anvil mixins applied");
        minecraft.stop();
    }
}
