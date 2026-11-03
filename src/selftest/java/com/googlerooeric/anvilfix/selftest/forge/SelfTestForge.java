package com.googlerooeric.anvilfix.selftest.forge;

import com.googlerooeric.anvilfix.selftest.ClientSmoke;
import com.googlerooeric.anvilfix.selftest.SelfTest;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
//? if <1.21.6 {
import net.minecraftforge.common.MinecraftForge;
//?}

@Mod("anvilfix_selftest")
public class SelfTestForge {
    public SelfTestForge() {
        //? if >=1.21.6 {
        /*// EventBus 7: every event type has its own bus
        if (SelfTest.enabled()) {
            ServerStartedEvent.BUS.addListener(event -> SelfTest.runAndExit(event.getServer()));
        }
        if (ClientSmoke.enabled()) {
            TickEvent.ClientTickEvent.Post.BUS.addListener(event -> ClientSmoke.onClientTick(Minecraft.getInstance()));
        }
        *///?} else {
        if (SelfTest.enabled()) {
            MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> SelfTest.runAndExit(event.getServer()));
        }
        if (ClientSmoke.enabled()) {
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) {
                    ClientSmoke.onClientTick(Minecraft.getInstance());
                }
            });
        }
        //?}
    }
}
