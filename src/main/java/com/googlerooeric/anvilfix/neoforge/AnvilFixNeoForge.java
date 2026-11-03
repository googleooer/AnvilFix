package com.googlerooeric.anvilfix.neoforge;

import com.googlerooeric.anvilfix.AnvilFix;
import com.googlerooeric.anvilfix.mixin.BooleanValueInvoker;
import net.minecraft.world.level.GameRules;
import net.neoforged.fml.common.Mod;

@Mod(AnvilFix.MOD_ID)
public class AnvilFixNeoForge {
    public AnvilFixNeoForge() {
        AnvilFix.MENDING_WORKS_WITH_UNBREAKING = GameRules.register(AnvilFix.MENDING_RULE_NAME, GameRules.Category.MISC, BooleanValueInvoker.anvilfix$create(false));
    }
}
