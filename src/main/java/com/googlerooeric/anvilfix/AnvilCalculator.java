package com.googlerooeric.anvilfix;

import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * AnvilFix's anvil rules, kept free of menu state and loader code so they can be tested and shared.
 * Everything that differs between Minecraft versions goes through {@link Compat}.
 */
public final class AnvilCalculator {

    public static final int DEFAULT_ENCHANTMENT_COST = 2;
    public static final int REPAIR_COST_PER_MATERIAL = 2;
    public static final int RENAME_COST = 2;

    /** Base cost per level, by enchantment id. Anything not listed (including modded enchantments) costs {@link #DEFAULT_ENCHANTMENT_COST}. */
    private static final Map<String, Integer> ENCHANTMENT_COSTS = Map.ofEntries(
            Map.entry("minecraft:sharpness", 2),
            Map.entry("minecraft:efficiency", 2),
            Map.entry("minecraft:mending", 15),
            Map.entry("minecraft:unbreaking", 3),
            Map.entry("minecraft:fortune", 3),
            Map.entry("minecraft:fire_aspect", 4),
            Map.entry("minecraft:sweeping", 2),
            Map.entry("minecraft:sweeping_edge", 2),
            Map.entry("minecraft:infinity", 8),
            Map.entry("minecraft:silk_touch", 4),
            Map.entry("minecraft:thorns", 2),
            Map.entry("minecraft:flame", 4),
            Map.entry("minecraft:aqua_affinity", 6),
            Map.entry("minecraft:luck_of_the_sea", 3),
            Map.entry("minecraft:punch", 3),
            Map.entry("minecraft:respiration", 3),
            Map.entry("minecraft:knockback", 3),
            Map.entry("minecraft:looting", 3),
            Map.entry("minecraft:lure", 3)
    );

    /**
     * @param output        the result stack, or {@link ItemStack#EMPTY}
     * @param levelCost     levels charged for taking the output
     * @param materialUsage how many items to take from the second slot; 0 means the whole slot is used up
     */
    public record Result(ItemStack output, int levelCost, int materialUsage) {
        public static final Result EMPTY = new Result(ItemStack.EMPTY, 0, 0);
    }

    private AnvilCalculator() {
    }

    public static int baseCost(String enchantmentId) {
        return ENCHANTMENT_COSTS.getOrDefault(enchantmentId, DEFAULT_ENCHANTMENT_COST);
    }

    /**
     * Vanilla's exclusivity rules (protection types, Silk Touch/Fortune, Infinity/Mending, ...) plus AnvilFix's
     * Mending/Unbreaking rule. An enchantment is never incompatible with itself, since that's an upgrade.
     */
    public static <E> boolean areIncompatible(Compat.Enchantments<E> enchantments, E e1, E e2, boolean mendingWorksWithUnbreaking) {
        if (enchantments.same(e1, e2)) {
            return false;
        }
        if (!mendingWorksWithUnbreaking) {
            String id1 = enchantments.id(e1), id2 = enchantments.id(e2);
            if (id1.equals("minecraft:mending") && id2.equals("minecraft:unbreaking") || id1.equals("minecraft:unbreaking") && id2.equals("minecraft:mending")) {
                return true;
            }
        }
        return !enchantments.compatible(e1, e2);
    }

    /**
     * Two equal levels make the next one (up to the max level), otherwise the higher level wins.
     * Levels above the max, e.g. from commands, are kept rather than capped.
     */
    public static int combinedLevel(int maxLevel, int currentLevel, int newLevel) {
        if (currentLevel == newLevel && newLevel < maxLevel) {
            return newLevel + 1;
        }
        return Math.max(currentLevel, newLevel);
    }

    /**
     * @param newItemName the name typed into the anvil, or null if the player never typed one
     */
    public static <E> Result calculate(Compat.Enchantments<E> enchantments, ItemStack inputItem, ItemStack enchantingItem, @Nullable String newItemName, boolean mendingWorksWithUnbreaking) {
        int totalCost = 0;
        int repairItemUsage = 0;

        // If the input item is empty, there is no output
        if (inputItem.isEmpty()) {
            return Result.EMPTY;
        }

        // Create a copy of the input item to work on
        ItemStack resultItem = inputItem.copy();

        // Get the current enchantments on the input item
        Map<E, Integer> currentEnchants = enchantments.get(resultItem);

        // If the enchanting item isn't empty, handle enchanting/repairing
        if (!enchantingItem.isEmpty()) {

            // Check if the enchanting item and input item are enchanted books
            boolean isEnchantedBook = Compat.isEnchantedBook(enchantingItem);
            boolean isInputItemBook = Compat.isEnchantedBook(inputItem);

            // If the input item is damageable and can be repaired by the second slot item, handle repairing
            if (resultItem.isDamageableItem() && Compat.isValidRepairItem(inputItem, enchantingItem)) {
                int repairCount = 0;
                // Calculate the amount to repair, capped at a quarter of the maximum durability
                int repairAmount = Math.min(resultItem.getDamageValue(), resultItem.getMaxDamage() / 4);

                // While there's still damage to repair and there are more repairing items available
                while (repairAmount > 0 && repairCount < enchantingItem.getCount()) {
                    resultItem.setDamageValue(resultItem.getDamageValue() - repairAmount);
                    totalCost += REPAIR_COST_PER_MATERIAL;
                    repairAmount = Math.min(resultItem.getDamageValue(), resultItem.getMaxDamage() / 4);
                    repairCount++;
                }
                repairItemUsage = repairCount;
            } else {
                // The second item must be an enchanted book or a second copy of a damageable item, otherwise there is no output
                if (!(isEnchantedBook || resultItem.getItem() == enchantingItem.getItem() && resultItem.isDamageableItem())) {
                    return Result.EMPTY;
                }

                // Two copies of a damageable item: add their durability plus vanilla's 12% bonus, at the cost of one material
                if (resultItem.isDamageableItem() && !isEnchantedBook) {
                    int remaining = inputItem.getMaxDamage() - inputItem.getDamageValue();
                    int added = enchantingItem.getMaxDamage() - enchantingItem.getDamageValue() + resultItem.getMaxDamage() * 12 / 100;
                    int newDamage = Math.max(resultItem.getMaxDamage() - (remaining + added), 0);
                    if (newDamage < resultItem.getDamageValue()) {
                        resultItem.setDamageValue(newDamage);
                        totalCost += REPAIR_COST_PER_MATERIAL;
                    }
                }

                // Get the enchantments on the enchanting item
                Map<E, Integer> newEnchants = enchantments.get(enchantingItem);

                for (Map.Entry<E, Integer> entry : newEnchants.entrySet()) {
                    E enchantment = entry.getKey();
                    int currentLevel = levelOf(enchantments, currentEnchants, enchantment);
                    int resultLevel = combinedLevel(enchantments.maxLevel(enchantment), currentLevel, entry.getValue());

                    // Skip enchantments the item can't take, that conflict with what it has, or that wouldn't go up.
                    // An enchanted book takes anything, so two books merge by the same rules as book + item.
                    if (!(isInputItemBook || enchantments.canEnchant(enchantment, inputItem)) || resultLevel <= currentLevel) continue;
                    boolean hasIncompatibleEnchantments = currentEnchants.keySet().stream()
                            .anyMatch(enchant -> areIncompatible(enchantments, enchantment, enchant, mendingWorksWithUnbreaking));
                    if (hasIncompatibleEnchantments) continue;

                    // Charge for the level the item ends up with: base cost x level
                    putLevel(enchantments, currentEnchants, enchantment, resultLevel);
                    totalCost += baseCost(enchantments.id(enchantment)) * resultLevel;
                }
            }

            // The second item has to do something, or taking the output (say, to rename) would consume it for nothing
            if (totalCost <= 0) {
                return Result.EMPTY;
            }
        }

        // If the enchanting item is empty and the new name is null or the same as the input item's name, there is no output
        if (enchantingItem.isEmpty() && (newItemName == null || newItemName.equals(inputItem.getHoverName().getString()))) {
            return Result.EMPTY;
        }

        // If the new name is blank and the input item has a custom name, remove the custom name and add the cost
        if (StringUtils.isBlank(newItemName)) {
            if (Compat.hasCustomName(inputItem)) {
                totalCost += RENAME_COST;
                Compat.clearCustomName(resultItem);
            }
        }
        // If the new name is different from the input item's name, set the custom name and add the cost
        else if (!newItemName.equals(inputItem.getHoverName().getString())) {
            totalCost += RENAME_COST;
            Compat.setCustomName(resultItem, newItemName);
        }

        if (totalCost <= 0) {
            return Result.EMPTY;
        }

        // Carry the prior work penalty over unchanged; AnvilFix never grows it
        int repairCost = Math.max(Compat.getRepairCost(resultItem), enchantingItem.isEmpty() ? 0 : Compat.getRepairCost(enchantingItem));
        Compat.setRepairCost(resultItem, repairCost);
        enchantments.set(resultItem, currentEnchants);

        return new Result(resultItem, totalCost, repairItemUsage);
    }

    // Enchantment keys may not have identity equality (e.g. holders), so look them up through the access
    private static <E> int levelOf(Compat.Enchantments<E> enchantments, Map<E, Integer> map, E enchantment) {
        for (Map.Entry<E, Integer> entry : map.entrySet()) {
            if (enchantments.same(entry.getKey(), enchantment)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private static <E> void putLevel(Compat.Enchantments<E> enchantments, Map<E, Integer> map, E enchantment, int level) {
        for (Map.Entry<E, Integer> entry : map.entrySet()) {
            if (enchantments.same(entry.getKey(), enchantment)) {
                entry.setValue(level);
                return;
            }
        }
        map.put(enchantment, level);
    }
}
