package com.googlerooeric.anvilfix.fabric;

import com.googlerooeric.anvilfix.AnvilFix;
import net.fabricmc.api.ModInitializer;
//? if >=1.21.11 {
/*import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRuleCategory;
*///?} else {
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;
//?}

public class AnvilFixFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        //? if >=1.21.11 {
        /*AnvilFix.MENDING_WORKS_WITH_UNBREAKING = GameRuleBuilder.forBoolean(false).category(GameRuleCategory.MISC)
                .buildAndRegister(Identifier.fromNamespaceAndPath(AnvilFix.MOD_ID, AnvilFix.MENDING_RULE_PATH));
        *///?} else {
        AnvilFix.MENDING_WORKS_WITH_UNBREAKING = GameRuleRegistry.register(AnvilFix.MENDING_RULE_NAME, GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));
        //?}
    }
}
