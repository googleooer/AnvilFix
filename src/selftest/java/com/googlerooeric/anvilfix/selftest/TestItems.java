package com.googlerooeric.anvilfix.selftest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.GameRules;

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
        return new Player(level, BlockPos.ZERO, 0.0f, profile, null) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
    }

    static GameRules freshGameRules() {
        return new GameRules();
    }

    private static Enchantment enchantment(String id) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(new ResourceLocation(id));
        AnvilScenarios.check(enchantment != null, "unknown enchantment " + id);
        return enchantment;
    }

    static ItemStack book(AnvilScenarios.Context ctx, String id, int level) {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment(id), level));
    }

    static ItemStack book(AnvilScenarios.Context ctx, Map<String, Integer> enchantments) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        Map<Enchantment, Integer> map = new LinkedHashMap<>();
        enchantments.forEach((id, level) -> map.put(enchantment(id), level));
        EnchantmentHelper.setEnchantments(map, book);
        return book;
    }

    static ItemStack enchanted(AnvilScenarios.Context ctx, ItemStack stack, String id, int level) {
        stack.enchant(enchantment(id), level);
        return stack;
    }

    /** Level of an enchantment on an item or stored on a book, 0 if absent. */
    static int level(ItemStack stack, String id) {
        return EnchantmentHelper.getEnchantments(stack).getOrDefault(enchantment(id), 0);
    }

    static String describe(ItemStack stack) {
        Map<String, Integer> named = new LinkedHashMap<>();
        EnchantmentHelper.getEnchantments(stack).forEach((e, l) -> named.put(String.valueOf(Registry.ENCHANTMENT.getKey(e)), l));
        return named.toString();
    }
}
