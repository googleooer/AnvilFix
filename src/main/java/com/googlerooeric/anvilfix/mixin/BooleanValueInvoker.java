package com.googlerooeric.anvilfix.mixin;

import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.11 {
/*import net.minecraft.world.level.gamerules.GameRules;

/^* Nothing to invoke from 1.21.11: game rules are registry entries there. Kept so the mixin config is the same. ^/
@Mixin(GameRules.class)
public interface BooleanValueInvoker {
}
*///?} else {
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.gen.Invoker;

/** BooleanValue.create is package-private; Fabric has its own API for this, Forge and NeoForge go through here. */
@Mixin(GameRules.BooleanValue.class)
public interface BooleanValueInvoker {
    @Invoker("create")
    static GameRules.Type<GameRules.BooleanValue> anvilfix$create(boolean defaultValue) {
        throw new AssertionError();
    }
}
//?}
