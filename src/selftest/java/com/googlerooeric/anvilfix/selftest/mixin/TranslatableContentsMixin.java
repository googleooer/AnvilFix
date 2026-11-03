package com.googlerooeric.anvilfix.selftest.mixin;

import com.googlerooeric.anvilfix.selftest.IngameClient;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The anvil screen builds its "Enchantment Cost: N" label as a new translatable component every frame, and shows its
 * cached "Too Expensive!" text instead when it caps the cost. Recording the cost here tells the in-game test which
 * label the player actually sees, on every version, without depending on the GUI rendering API. (Component itself is
 * an interface, which stock Mixin on Forge won't inject into.)
 */
@Mixin(TranslatableContents.class)
public abstract class TranslatableContentsMixin {
    @Shadow @Final private String key;

    @Shadow @Final private Object[] args;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void anvilfixSelftest$recordCostLabel(CallbackInfo ci) {
        if ("container.repair.cost".equals(this.key) && this.args.length == 1) {
            IngameClient.lastCostLabel = this.args[0];
        }
    }
}
