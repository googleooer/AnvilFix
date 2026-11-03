package com.googlerooeric.anvilfix.mixin;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {
    @Accessor("dataSlots")
    List<DataSlot> anvilfix$getDataSlots();

    @Accessor("remoteDataSlots")
    IntList anvilfix$getRemoteDataSlots();
}
