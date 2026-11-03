package com.googlerooeric.anvilfix.mixin.screen;

import com.googlerooeric.anvilfix.AnvilCalculator;
import com.googlerooeric.anvilfix.AnvilFix;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin
        extends ForgingScreenHandler {

    @Shadow @Final private Property levelCost;

    @Shadow private int repairItemUsage;

    @Shadow private String newItemName;

    public AnvilScreenHandlerMixin(@Nullable ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(type, syncId, playerInventory, context);
    }

    // Inject and cancel instead of @Overwrite so other mods can still inject into the vanilla body (AnvilFix-issues#1)
    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void anvilfix$updateResult(CallbackInfo ci) {
        ci.cancel();

        // The server is authoritative: the client can't read server gamerules, so a client-side guess could
        // disagree with the server. The output slot and level cost reach the client through normal handler sync.
        if (this.player.world.isClient) {
            return;
        }

        // onTakeOutput empties slot 0 (which lands here) before it reads repairItemUsage, so leave that alone
        if (this.input.getStack(0).isEmpty()) {
            this.output.setStack(0, ItemStack.EMPTY);
            this.levelCost.set(0);
            return;
        }

        boolean mendingWorksWithUnbreaking = this.player.world.getGameRules().getBoolean(AnvilFix.MENDING_WORKS_WITH_UNBREAKING);
        AnvilCalculator.Result result = AnvilCalculator.calculate(this.input.getStack(0), this.input.getStack(1), this.newItemName, mendingWorksWithUnbreaking);

        this.repairItemUsage = result.materialUsage();
        this.levelCost.set(result.levelCost());
        this.output.setStack(0, result.output());

        // Properties are only sent when the server's value changes. A client without AnvilFix runs the vanilla
        // calculation on its own (e.g. on every keystroke while renaming) and would keep showing that number,
        // so forget the last sent cost to make sendContentUpdates() send it again.
        ScreenHandlerAccessor self = (ScreenHandlerAccessor) this;
        int index = self.anvilfix$getProperties().indexOf(this.levelCost);
        if (index >= 0) {
            self.anvilfix$getTrackedPropertyValues().set(index, ~result.levelCost());
        }
        this.sendContentUpdates();
    }

    // Make it so repairing doesn't increase cost.
    @Inject(method = "getNextCost", at = @At("HEAD"), cancellable = true)
    private static void anvilfix$getNextCost(int cost, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cost); // Return the cost as it is
    }
}
