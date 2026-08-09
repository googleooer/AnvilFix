package com.googlerooeric.anvilfix;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnvilFix implements ModInitializer {

    public static final GameRules.Key<GameRules.BooleanRule> MENDING_WORKS_WITH_UNBREAKING =
            GameRuleRegistry.register("mendingWorksWithUnbreaking", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));

    public static Logger LOGGER = LoggerFactory.getLogger("AnvilFix");;

    @Override
    public void onInitialize() {
    }
}
