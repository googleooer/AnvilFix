package com.googlerooeric.anvilfix.forge;

import com.googlerooeric.anvilfix.AnvilFix;
import net.minecraftforge.fml.common.Mod;
//? if >=1.21.11 {
/*import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
*///?} else {
import com.googlerooeric.anvilfix.mixin.BooleanValueInvoker;
import net.minecraft.world.level.GameRules;
//?}

@Mod(AnvilFix.MOD_ID)
public class AnvilFixForge {
    //? if >=1.21.11 {
    /*private static final DeferredRegister<GameRule<?>> GAME_RULES = DeferredRegister.create(Registries.GAME_RULE, AnvilFix.MOD_ID);

    public AnvilFixForge(FMLJavaModLoadingContext context) {
        GAME_RULES.register(AnvilFix.MENDING_RULE_PATH, () -> AnvilFix.MENDING_WORKS_WITH_UNBREAKING = AnvilFix.createMendingRule());
        GAME_RULES.register(context.getModBusGroup());
    }
    *///?} else {
    public AnvilFixForge() {
        AnvilFix.MENDING_WORKS_WITH_UNBREAKING = GameRules.register(AnvilFix.MENDING_RULE_NAME, GameRules.Category.MISC, BooleanValueInvoker.anvilfix$create(false));
    }
    //?}
}
