package com.googlerooeric.anvilfix.mixin.screen;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ScreenHandler.class)
public interface ScreenHandlerAccessor {
    @Accessor("properties")
    List<Property> anvilfix$getProperties();

    @Accessor("trackedPropertyValues")
    IntList anvilfix$getTrackedPropertyValues();
}
