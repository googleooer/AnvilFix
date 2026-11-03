package com.googlerooeric.anvilfix.selftest.neoforge;

import com.googlerooeric.anvilfix.selftest.ClientSmoke;
import com.googlerooeric.anvilfix.selftest.SelfTest;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
//? if >=1.20.5 {
/*import net.neoforged.neoforge.client.event.ClientTickEvent;
*///?} else {
import net.neoforged.neoforge.event.TickEvent;
//?}

@Mod("anvilfix_selftest")
public class SelfTestNeoForge {
    public SelfTestNeoForge() {
        if (SelfTest.enabled()) {
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> SelfTest.runAndExit(event.getServer()));
        }
        if (ClientSmoke.enabled()) {
            //? if >=1.20.5 {
            /*NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientSmoke.onClientTick(Minecraft.getInstance()));
            *///?} else {
            NeoForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) {
                    ClientSmoke.onClientTick(Minecraft.getInstance());
                }
            });
            //?}
        }
    }
}
