package com.googlerooeric.anvilfix;

//? if >=1.19.3 {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?} else {
import net.minecraft.core.Registry;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Minecraft calls AnvilFix needs that changed between versions, behind one stable surface.
 */
public final class Compat {

    private Compat() {
    }

    /** How enchantments are read and written; {@code E} is the version's enchantment handle. */
    public interface Enchantments<E> {
        /** Enchantments on an item, or stored on an enchanted book, in order. */
        Map<E, Integer> get(ItemStack stack);

        void set(ItemStack stack, Map<E, Integer> enchantments);

        String id(E enchantment);

        boolean same(E a, E b);

        boolean compatible(E a, E b);

        boolean canEnchant(E enchantment, ItemStack stack);

        int maxLevel(E enchantment);
    }

    public static Enchantments<?> enchantments(Level level) {
        return LegacyEnchantments.INSTANCE;
    }

    private enum LegacyEnchantments implements Enchantments<Enchantment> {
        INSTANCE;

        @Override
        public Map<Enchantment, Integer> get(ItemStack stack) {
            return new LinkedHashMap<>(EnchantmentHelper.getEnchantments(stack));
        }

        @Override
        public void set(ItemStack stack, Map<Enchantment, Integer> enchantments) {
            EnchantmentHelper.setEnchantments(enchantments, stack);
        }

        @Override
        public String id(Enchantment enchantment) {
            //? if >=1.19.3 {
            /*return String.valueOf(BuiltInRegistries.ENCHANTMENT.getKey(enchantment));
            *///?} else {
            return String.valueOf(Registry.ENCHANTMENT.getKey(enchantment));
            //?}
        }

        @Override
        public boolean same(Enchantment a, Enchantment b) {
            return a == b;
        }

        @Override
        public boolean compatible(Enchantment a, Enchantment b) {
            return a.isCompatibleWith(b);
        }

        @Override
        public boolean canEnchant(Enchantment enchantment, ItemStack stack) {
            return enchantment.canEnchant(stack);
        }

        @Override
        public int maxLevel(Enchantment enchantment) {
            return enchantment.getMaxLevel();
        }
    }

    public static boolean isEnchantedBook(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) && !EnchantedBookItem.getEnchantments(stack).isEmpty();
    }

    public static boolean isValidRepairItem(ItemStack stack, ItemStack material) {
        return stack.getItem().isValidRepairItem(stack, material);
    }

    public static boolean hasCustomName(ItemStack stack) {
        return stack.hasCustomHoverName();
    }

    public static void setCustomName(ItemStack stack, String name) {
        stack.setHoverName(Component.literal(name));
    }

    public static void clearCustomName(ItemStack stack) {
        stack.resetHoverName();
    }

    public static int getRepairCost(ItemStack stack) {
        return stack.getBaseRepairCost();
    }

    public static void setRepairCost(ItemStack stack, int cost) {
        stack.setRepairCost(cost);
    }

    public static Level level(Player player) {
        //? if >=1.20 {
        /*return player.level();
        *///?} else {
        return player.level;
        //?}
    }
}
