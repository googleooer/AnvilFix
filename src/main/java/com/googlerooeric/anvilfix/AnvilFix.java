package com.googlerooeric.anvilfix;

import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent state. Each loader's entry point registers the gamerule and stores its key here.
 */
public final class AnvilFix {

    public static final String MOD_ID = "anvilfix";
    public static final String MENDING_RULE_NAME = "mendingWorksWithUnbreaking";
    public static final Logger LOGGER = LoggerFactory.getLogger("AnvilFix");

    /** Off by default: Mending and Unbreaking can't be on the same item. */
    public static GameRules.Key<GameRules.BooleanValue> MENDING_WORKS_WITH_UNBREAKING;

    private AnvilFix() {
    }
}
