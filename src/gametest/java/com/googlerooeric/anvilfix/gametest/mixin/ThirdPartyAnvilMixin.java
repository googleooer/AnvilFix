package com.googlerooeric.anvilfix.gametest.mixin;

import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stands in for another mod injecting into the vanilla updateResult body, like AdventureZ does
 * (AnvilFix-issues#1). If AnvilFix replaces the method instead of injecting, this fails to apply and the server crashes.
 */
@Mixin(AnvilScreenHandler.class)
public abstract class ThirdPartyAnvilMixin {
    @Inject(method = "updateResult", require = 1,
            at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/enchantment/EnchantmentHelper;get(Lnet/minecraft/item/ItemStack;)Ljava/util/Map;"))
    private void anvilfixGametest$afterGetEnchantments(CallbackInfo ci) {
    }
}
