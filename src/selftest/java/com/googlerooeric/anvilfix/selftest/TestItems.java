package com.googlerooeric.anvilfix.selftest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import com.googlerooeric.anvilfix.AnvilFix;
import com.googlerooeric.anvilfix.Compat;
//? if >=1.19.3 {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?} else {
import net.minecraft.core.Registry;
//?}
//? if >=1.20.5 {
/*import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
*///?}
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
//? if <1.21.11 {
import net.minecraft.world.level.GameRules;
//?}
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Version-specific ways to build the stacks and player the scenarios need. */
final class TestItems {

    private TestItems() {
    }

    /** A plain survival player. The anvil checks its abilities (instabuild), not a game mode. */
    static Player survivalPlayer(ServerLevel level) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "anvilfix-test");
        //? if >=1.21.6 {
        /*return new Player(level, profile) {
            @Override
            public net.minecraft.world.level.GameType gameMode() {
                return net.minecraft.world.level.GameType.SURVIVAL;
            }
        };
        *///?} elif >=1.21.5 {
        /*return new Player(level, BlockPos.ZERO, 0.0f, profile) {
            @Override
            public net.minecraft.world.level.GameType gameMode() {
                return net.minecraft.world.level.GameType.SURVIVAL;
            }
        };
        *///?} else {
        //? if >=1.19.3 {
        /*return new Player(level, BlockPos.ZERO, 0.0f, profile) {
        *///?} else {
        return new Player(level, BlockPos.ZERO, 0.0f, profile, null) {
        //?}
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        //?}
    }

    static net.minecraft.server.level.ServerLevel serverLevel(net.minecraft.server.level.ServerPlayer player) {
        //? if >=1.21.6 {
        /*return player.level();
        *///?} elif >=1.20 {
        /*return player.serverLevel();
        *///?} else {
        return player.getLevel();
        //?}
    }

    static void setCreative(Player player, boolean creative) {
        player.getAbilities().instabuild = creative;
    }

    /** The mending rule's value in a fresh set of game rules, i.e. its registered default. */
    static boolean defaultMendingRule(AnvilScenarios.Context ctx) {
        //? if >=1.21.11 {
        /*return new net.minecraft.world.level.gamerules.GameRules(ctx.level().enabledFeatures()).get(AnvilFix.MENDING_WORKS_WITH_UNBREAKING);
        *///?} elif >=1.21.2 {
        /*return new GameRules(ctx.level().enabledFeatures()).getBoolean(AnvilFix.MENDING_WORKS_WITH_UNBREAKING);
        *///?} else {
        return new GameRules().getBoolean(AnvilFix.MENDING_WORKS_WITH_UNBREAKING);
        //?}
    }

    /** The version's enchantment handle (Enchantment, or a registry Holder from 1.20.5) for an id. */
    private static Object handle(Level level, String id) {
        //? if >=1.21.11 {
        /*ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id));
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        *///?} elif >=1.21.2 {
        /*ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(id));
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        *///?} elif >=1.21 {
        /*ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(id));
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
        *///?} elif >=1.20.5 {
        /*ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, new ResourceLocation(id));
        return BuiltInRegistries.ENCHANTMENT.getHolderOrThrow(key);
        *///?} elif >=1.19.3 {
        /*Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(new ResourceLocation(id));
        AnvilScenarios.check(enchantment != null, "unknown enchantment " + id);
        return enchantment;
        *///?} else {
        Enchantment enchantment = Registry.ENCHANTMENT.get(new ResourceLocation(id));
        AnvilScenarios.check(enchantment != null, "unknown enchantment " + id);
        return enchantment;
        //?}
    }

    @SuppressWarnings("unchecked")
    private static Compat.Enchantments<Object> enchantments(Level level) {
        return (Compat.Enchantments<Object>) Compat.enchantments(level);
    }

    static ItemStack book(AnvilScenarios.Context ctx, String id, int level) {
        return book(ctx, Map.of(id, level));
    }

    static ItemStack book(AnvilScenarios.Context ctx, Map<String, Integer> enchantments) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        Map<Object, Integer> map = new LinkedHashMap<>();
        enchantments.forEach((id, level) -> map.put(handle(ctx.level(), id), level));
        enchantments(ctx.level()).set(book, map);
        return book;
    }

    static ItemStack enchanted(AnvilScenarios.Context ctx, ItemStack stack, String id, int level) {
        Compat.Enchantments<Object> access = enchantments(ctx.level());
        Map<Object, Integer> map = access.get(stack);
        map.put(handle(ctx.level(), id), level);
        access.set(stack, map);
        return stack;
    }

    /** Enchantments on an item or stored on a book, by id. Works on the client too. */
    static Map<String, Integer> byId(Level level, ItemStack stack) {
        Compat.Enchantments<Object> access = enchantments(level);
        Map<String, Integer> named = new LinkedHashMap<>();
        access.get(stack).forEach((e, l) -> named.put(access.id(e), l));
        return named;
    }

    /** Level of an enchantment on an item or stored on a book, 0 if absent. */
    static int level(Level level, ItemStack stack, String id) {
        return byId(level, stack).getOrDefault(id, 0);
    }

    static String describe(Level level, ItemStack stack) {
        return byId(level, stack).toString();
    }
}
