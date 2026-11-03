package com.googlerooeric.anvilfix;

//? if >=1.19.3 {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?} else {
import net.minecraft.core.Registry;
//?}
//? if >=1.20.5 {
/*import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
*///?} else {
import net.minecraft.world.item.EnchantedBookItem;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
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
        //? if >=1.20.5 {
        /*return ComponentEnchantments.INSTANCE;
        *///?} else {
        return LegacyEnchantments.INSTANCE;
        //?}
    }

    //? if >=1.20.5 {
    /*/^* 1.20.5+: enchantments are data components keyed by registry holders (data-driven from 1.21). ^/
    private enum ComponentEnchantments implements Enchantments<Holder<Enchantment>> {
        INSTANCE;

        @Override
        public Map<Holder<Enchantment>, Integer> get(ItemStack stack) {
            Map<Holder<Enchantment>, Integer> map = new LinkedHashMap<>();
            for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
                map.put(entry.getKey(), entry.getIntValue());
            }
            return map;
        }

        @Override
        public void set(ItemStack stack, Map<Holder<Enchantment>, Integer> enchantments) {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            //? if >=1.21 {
            /^enchantments.forEach(mutable::set);
            ^///?} else {
            enchantments.forEach((holder, level) -> mutable.set(holder.value(), level));
            //?}
            EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
        }

        @Override
        public String id(Holder<Enchantment> enchantment) {
            //? if >=1.21.11 {
            /^return enchantment.unwrapKey().map(key -> key.identifier().toString()).orElse("");
            ^///?} else {
            return enchantment.unwrapKey().map(key -> key.location().toString()).orElse("");
            //?}
        }

        @Override
        public boolean same(Holder<Enchantment> a, Holder<Enchantment> b) {
            return a.value() == b.value();
        }

        @Override
        public boolean compatible(Holder<Enchantment> a, Holder<Enchantment> b) {
            //? if >=1.21 {
            /^return Enchantment.areCompatible(a, b);
            ^///?} else {
            return a.value().isCompatibleWith(b.value());
            //?}
        }

        @Override
        public boolean canEnchant(Holder<Enchantment> enchantment, ItemStack stack) {
            return enchantment.value().canEnchant(stack);
        }

        @Override
        public int maxLevel(Holder<Enchantment> enchantment) {
            return enchantment.value().getMaxLevel();
        }
    }
    *///?} else {
    /** Up to 1.20.4: enchantments live in NBT, keyed by the Enchantment objects of the built-in registry. */
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
    //?}

    public static boolean isEnchantedBook(ItemStack stack) {
        //? if >=1.20.5 {
        /*return stack.is(Items.ENCHANTED_BOOK) && !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
        *///?} else {
        return stack.is(Items.ENCHANTED_BOOK) && !EnchantedBookItem.getEnchantments(stack).isEmpty();
        //?}
    }

    public static boolean isValidRepairItem(ItemStack stack, ItemStack material) {
        //? if >=1.21.2 {
        /*return stack.isValidRepairItem(material);
        *///?} else {
        return stack.getItem().isValidRepairItem(stack, material);
        //?}
    }

    public static boolean hasCustomName(ItemStack stack) {
        //? if >=1.20.5 {
        /*return stack.has(DataComponents.CUSTOM_NAME);
        *///?} else {
        return stack.hasCustomHoverName();
        //?}
    }

    public static void setCustomName(ItemStack stack, String name) {
        //? if >=1.20.5 {
        /*stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        *///?} else {
        stack.setHoverName(Component.literal(name));
        //?}
    }

    public static void clearCustomName(ItemStack stack) {
        //? if >=1.20.5 {
        /*stack.remove(DataComponents.CUSTOM_NAME);
        *///?} else {
        stack.resetHoverName();
        //?}
    }

    public static int getRepairCost(ItemStack stack) {
        //? if >=1.20.5 {
        /*return stack.getOrDefault(DataComponents.REPAIR_COST, 0);
        *///?} else {
        return stack.getBaseRepairCost();
        //?}
    }

    public static void setRepairCost(ItemStack stack, int cost) {
        //? if >=1.20.5 {
        /*stack.set(DataComponents.REPAIR_COST, cost);
        *///?} else {
        stack.setRepairCost(cost);
        //?}
    }

    public static Level level(Player player) {
        //? if >=1.20 {
        /*return player.level();
        *///?} else {
        return player.level;
        //?}
    }
}
