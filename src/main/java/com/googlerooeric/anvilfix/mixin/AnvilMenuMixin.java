package com.googlerooeric.anvilfix.mixin;

import com.googlerooeric.anvilfix.AnvilCalculator;
import com.googlerooeric.anvilfix.AnvilFix;
import com.googlerooeric.anvilfix.Compat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    @Shadow @Final private DataSlot cost;

    @Shadow private int repairItemCountCost;

    @Shadow private String itemName;

    //? if >=1.21.2 {
    /*public AnvilMenuMixin(@Nullable MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access, net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition slots) {
        super(type, containerId, inventory, access, slots);
    }
    *///?} else {
    public AnvilMenuMixin(@Nullable MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(type, containerId, inventory, access);
    }
    //?}

    // Inject and cancel instead of @Overwrite so other mods can still inject into the vanilla body (AnvilFix-issues#1)
    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void anvilfix$createResult(CallbackInfo ci) {
        ci.cancel();

        // The server is authoritative: the client can't read server gamerules, so a client-side guess could
        // disagree with the server. The output slot and level cost reach the client through normal menu sync.
        Level level = Compat.level(this.player);
        if (level.isClientSide()) {
            return;
        }

        // onTake empties slot 0 (which lands here) before it reads repairItemCountCost, so leave that alone
        if (this.inputSlots.getItem(0).isEmpty()) {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.cost.set(0);
            return;
        }

        // Not client-side, so this is the server's level (only ServerLevel has game rules from 1.21.2)
        boolean mendingWorksWithUnbreaking = AnvilFix.mendingWorksWithUnbreaking((ServerLevel) level);
        AnvilCalculator.Result result = AnvilCalculator.calculate(Compat.enchantments(level),
                this.inputSlots.getItem(0), this.inputSlots.getItem(1), this.itemName, mendingWorksWithUnbreaking);

        this.repairItemCountCost = result.materialUsage();
        this.cost.set(result.levelCost());
        this.resultSlots.setItem(0, result.output());

        // Data slots are only sent when the server's value changes. A client without AnvilFix runs the vanilla
        // calculation on its own (e.g. on every keystroke while renaming) and would keep showing that number,
        // so forget the last sent cost to make broadcastChanges() send it again.
        AbstractContainerMenuAccessor self = (AbstractContainerMenuAccessor) this;
        int index = self.anvilfix$getDataSlots().indexOf(this.cost);
        if (index >= 0) {
            self.anvilfix$getRemoteDataSlots().set(index, ~result.levelCost());
        }
        this.broadcastChanges();
    }

    // Make it so repairing doesn't increase cost.
    @Inject(method = "calculateIncreasedRepairCost", at = @At("HEAD"), cancellable = true)
    private static void anvilfix$calculateIncreasedRepairCost(int cost, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cost); // Return the cost as it is
    }
}
