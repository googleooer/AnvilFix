package com.googlerooeric.anvilfix.neoforge;

import com.googlerooeric.anvilfix.AnvilFix;
import net.neoforged.fml.common.Mod;
//? if >=1.21.11 {
/*import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.gamerules.GameRule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?} else {
import com.googlerooeric.anvilfix.mixin.BooleanValueInvoker;
import net.minecraft.world.level.GameRules;
//?}

@Mod(AnvilFix.MOD_ID)
public class AnvilFixNeoForge {
    //? if >=1.21.11 {
    /*private static final DeferredRegister<GameRule<?>> GAME_RULES = DeferredRegister.create(Registries.GAME_RULE, AnvilFix.MOD_ID);

    public AnvilFixNeoForge(IEventBus modBus) {
        GAME_RULES.register(AnvilFix.MENDING_RULE_PATH, () -> AnvilFix.MENDING_WORKS_WITH_UNBREAKING = AnvilFix.createMendingRule());
        GAME_RULES.register(modBus);
    }
    *///?} else {
    public AnvilFixNeoForge() {
        AnvilFix.MENDING_WORKS_WITH_UNBREAKING = GameRules.register(AnvilFix.MENDING_RULE_NAME, GameRules.Category.MISC, BooleanValueInvoker.anvilfix$create(false));
    }
    //?}
}
