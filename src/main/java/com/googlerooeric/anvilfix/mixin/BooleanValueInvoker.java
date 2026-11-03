package com.googlerooeric.anvilfix.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** BooleanValue.create is package-private; Fabric has its own API for this, Forge and NeoForge go through here. */
@Mixin(GameRules.BooleanValue.class)
public interface BooleanValueInvoker {
    @Invoker("create")
    static GameRules.Type<GameRules.BooleanValue> anvilfix$create(boolean defaultValue) {
        throw new AssertionError();
    }
}
