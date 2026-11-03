package com.googlerooeric.anvilfix.forge;

import com.googlerooeric.anvilfix.AnvilFix;
import com.googlerooeric.anvilfix.mixin.BooleanValueInvoker;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.fml.common.Mod;

@Mod(AnvilFix.MOD_ID)
public class AnvilFixForge {
    public AnvilFixForge() {
        AnvilFix.MENDING_WORKS_WITH_UNBREAKING = GameRules.register(AnvilFix.MENDING_RULE_NAME, GameRules.Category.MISC, BooleanValueInvoker.anvilfix$create(false));
    }
}
