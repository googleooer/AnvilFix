package com.googlerooeric.anvilfix;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;

import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Anvilfix implements ModInitializer {

    public static final GameRules.Key<GameRules.BooleanRule> MENDING_WORKS_WITH_UNBREAKING =
            GameRuleRegistry.register("mendingWorksWithUnbreaking", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));

    public static Logger LOGGER;

    @Override
    public void onInitialize() {

        LOGGER = LogManager.getLogManager().getLogger("AnvilFix");

    }
}
