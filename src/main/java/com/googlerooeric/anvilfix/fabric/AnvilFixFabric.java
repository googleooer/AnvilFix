package com.googlerooeric.anvilfix.fabric;

import com.googlerooeric.anvilfix.AnvilFix;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;

public class AnvilFixFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AnvilFix.MENDING_WORKS_WITH_UNBREAKING = GameRuleRegistry.register(AnvilFix.MENDING_RULE_NAME, GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));
    }
}
