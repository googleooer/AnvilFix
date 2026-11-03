package com.googlerooeric.anvilfix.mixin.client;

import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {

    // The screen labels any cost of 40+ "Too Expensive!" on its own, even though the server now allows it
    @ModifyConstant(method = "renderLabels", constant = @Constant(intValue = 40))
    private int anvilfix$removeTooExpensiveLabel(int tooExpensiveCost) {
        return Integer.MAX_VALUE;
    }
}
