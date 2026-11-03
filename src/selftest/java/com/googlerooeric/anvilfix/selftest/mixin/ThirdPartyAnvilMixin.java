package com.googlerooeric.anvilfix.selftest.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stands in for another mod injecting into the vanilla createResult body, like AdventureZ does (AnvilFix-issues#1).
 * If AnvilFix replaced the method instead of injecting, this would fail to apply and crash the server.
 */
@Mixin(AnvilMenu.class)
public abstract class ThirdPartyAnvilMixin {
    @Inject(method = "createResult", require = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;copy()Lnet/minecraft/world/item/ItemStack;"))
    private void anvilfixSelftest$beforeCopy(CallbackInfo ci) {
    }
}
