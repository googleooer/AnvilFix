package com.googlerooeric.anvilfix;

import net.minecraft.server.level.ServerLevel;
//? if >=1.21.11 {
/*import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
*///?} else {
import net.minecraft.world.level.GameRules;
//?}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent state. Each loader's entry point registers the gamerule and stores it here.
 */
public final class AnvilFix {

    public static final String MOD_ID = "anvilfix";
    /** Gamerule name up to 1.21.10. */
    public static final String MENDING_RULE_NAME = "mendingWorksWithUnbreaking";
    /** Gamerule id path from 1.21.11, where game rules became registry entries with snake_case ids. */
    public static final String MENDING_RULE_PATH = "mending_works_with_unbreaking";
    public static final Logger LOGGER = LoggerFactory.getLogger("AnvilFix");

    /** Off by default: Mending and Unbreaking can't be on the same item. */
    //? if >=1.21.11 {
    /*public static GameRule<Boolean> MENDING_WORKS_WITH_UNBREAKING;
    *///?} else {
    public static GameRules.Key<GameRules.BooleanValue> MENDING_WORKS_WITH_UNBREAKING;
    //?}

    private AnvilFix() {
    }

    //? if >=1.21.11 {
    /*/^* The rule as vanilla builds its own boolean rules, for loaders that register it through their registry events. ^/
    public static GameRule<Boolean> createMendingRule() {
        return new GameRule<>(GameRuleCategory.MISC, GameRuleType.BOOL, BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean,
                Codec.BOOL, value -> value ? 1 : 0, false, FeatureFlagSet.of());
    }
    *///?}

    public static boolean mendingWorksWithUnbreaking(ServerLevel level) {
        //? if >=1.21.11 {
        /*return level.getGameRules().get(MENDING_WORKS_WITH_UNBREAKING);
        *///?} else {
        return level.getGameRules().getBoolean(MENDING_WORKS_WITH_UNBREAKING);
        //?}
    }

    public static void setMendingWorksWithUnbreaking(ServerLevel level, boolean value) {
        //? if >=1.21.11 {
        /*level.getGameRules().set(MENDING_WORKS_WITH_UNBREAKING, value, level.getServer());
        *///?} else {
        level.getGameRules().getRule(MENDING_WORKS_WITH_UNBREAKING).set(value, level.getServer());
        //?}
    }
}
