package com.googlerooeric.anvilfix;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * AnvilFix's anvil rules, kept free of screen handler state so they can be tested and shared between loaders.
 */
public final class AnvilCalculator {

    public static final int DEFAULT_ENCHANTMENT_COST = 2;
    public static final int REPAIR_COST_PER_MATERIAL = 2;
    public static final int RENAME_COST = 2;

    private static final Map<Enchantment, Integer> ENCHANTMENT_COST_MAP = new HashMap<>();

    static {
        // Populate the map with enchantments and their base costs
        ENCHANTMENT_COST_MAP.put(Enchantments.SHARPNESS, 2);
        ENCHANTMENT_COST_MAP.put(Enchantments.EFFICIENCY, 2);
        ENCHANTMENT_COST_MAP.put(Enchantments.MENDING, 15);
        ENCHANTMENT_COST_MAP.put(Enchantments.UNBREAKING, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.FORTUNE, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.FIRE_ASPECT, 4);
        ENCHANTMENT_COST_MAP.put(Enchantments.SWEEPING, 2);
        ENCHANTMENT_COST_MAP.put(Enchantments.INFINITY, 8);
        ENCHANTMENT_COST_MAP.put(Enchantments.SILK_TOUCH, 4);
        ENCHANTMENT_COST_MAP.put(Enchantments.THORNS, 2);
        ENCHANTMENT_COST_MAP.put(Enchantments.FLAME, 4);
        ENCHANTMENT_COST_MAP.put(Enchantments.AQUA_AFFINITY, 6);
        ENCHANTMENT_COST_MAP.put(Enchantments.LUCK_OF_THE_SEA, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.PUNCH, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.RESPIRATION, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.KNOCKBACK, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.LOOTING, 3);
        ENCHANTMENT_COST_MAP.put(Enchantments.LURE, 3);
    }

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

    public static int baseCost(Enchantment enchantment) {
        return ENCHANTMENT_COST_MAP.getOrDefault(enchantment, DEFAULT_ENCHANTMENT_COST);
    }

    /**
     * Vanilla's exclusivity rules (protection types, Silk Touch/Fortune, Infinity/Mending, ...) plus AnvilFix's
     * Mending/Unbreaking rule. An enchantment is never incompatible with itself, since that's an upgrade.
     */
    public static boolean areIncompatible(Enchantment e1, Enchantment e2, boolean mendingWorksWithUnbreaking) {
        if (e1 == e2) {
            return false;
        }
        if (!mendingWorksWithUnbreaking
                && (e1 == Enchantments.MENDING && e2 == Enchantments.UNBREAKING || e1 == Enchantments.UNBREAKING && e2 == Enchantments.MENDING)) {
            return true;
        }
        return !e1.canCombine(e2);
    }

    /**
     * @param newItemName the name typed into the anvil, or null if the player never typed one
     */
    public static Result calculate(ItemStack inputItem, ItemStack enchantingItem, @Nullable String newItemName, boolean mendingWorksWithUnbreaking) {
        int totalCost = 0;
        int repairItemUsage = 0;

        // If the input item is empty, there is no output
        if (inputItem.isEmpty()) {
            return Result.EMPTY;
        }

        // Create a copy of the input item to work on
        ItemStack resultItem = inputItem.copy();

        // Get the current enchantments on the input item
        Map<Enchantment, Integer> currentEnchants = EnchantmentHelper.get(resultItem);

        // If the enchanting item isn't empty, handle enchanting/repairing
        if (!enchantingItem.isEmpty()) {

            // Check if the enchanting item and input item are enchanted books
            boolean isEnchantedBook = enchantingItem.isOf(Items.ENCHANTED_BOOK) && !EnchantedBookItem.getEnchantmentNbt(enchantingItem).isEmpty();
            boolean isInputItemBook = inputItem.isOf(Items.ENCHANTED_BOOK) && !EnchantedBookItem.getEnchantmentNbt(inputItem).isEmpty();

            // If the input item is damageable and can be repaired by the second slot item, handle repairing
            if (resultItem.isDamageable() && resultItem.getItem().canRepair(inputItem, enchantingItem)) {
                int repairCount = 0;
                // Calculate the amount to repair, capped at a quarter of the maximum durability
                int repairAmount = Math.min(resultItem.getDamage(), resultItem.getMaxDamage() / 4);

                // While there's still damage to repair and there are more repairing items available
                while (repairAmount > 0 && repairCount < enchantingItem.getCount()) {
                    resultItem.setDamage(resultItem.getDamage() - repairAmount);
                    totalCost += REPAIR_COST_PER_MATERIAL;
                    repairAmount = Math.min(resultItem.getDamage(), resultItem.getMaxDamage() / 4);
                    repairCount++;
                }
                repairItemUsage = repairCount;
            } else {
                // If the input item isn't a book, and the enchanting item isn't an enchanted book or the input item is not damageable, there is no output
                if (!(isEnchantedBook || resultItem.getItem() == enchantingItem.getItem() && resultItem.isDamageable()) && !isInputItemBook) {
                    return Result.EMPTY;
                }

                // Get the enchantments on the enchanting item
                Map<Enchantment, Integer> newEnchants = EnchantmentHelper.get(enchantingItem);

                if (isInputItemBook && isEnchantedBook) // If both the input item and the enchanting item are enchanted books, handle merging
                {
                    Map<Enchantment, Integer> book2Enchants = new HashMap<>(newEnchants);
                    for (Enchantment e : currentEnchants.keySet()) {
                        if (book2Enchants.containsKey(e)) {
                            int currentLevel = currentEnchants.get(e);
                            int book2Level = book2Enchants.get(e);
                            if (currentLevel <= book2Level && currentLevel + 1 <= e.getMaxLevel()) {
                                currentEnchants.put(e, currentLevel + 1);
                                totalCost += baseCost(e) * (currentLevel + 1);
                            }

                            book2Enchants.remove(e);
                        }
                    }

                    for (Map.Entry<Enchantment, Integer> entry : book2Enchants.entrySet()) {
                        Enchantment enchantment = entry.getKey();
                        Integer level = entry.getValue();
                        boolean compatible = currentEnchants.keySet().stream()
                                .noneMatch(existing -> areIncompatible(existing, enchantment, mendingWorksWithUnbreaking));
                        if (compatible) {
                            currentEnchants.put(enchantment, level);
                            totalCost += baseCost(enchantment) * level;
                        }
                    }
                }
                else // If the input item is not an enchanted book, handle regular enchanting
                {
                    for (Enchantment enchantment : newEnchants.keySet()) {
                        if (enchantment == null) continue;

                        int currentLevel = currentEnchants.getOrDefault(enchantment, 0);
                        int newLevel = newEnchants.getOrDefault(enchantment, 0);
                        int finalLevel;

                        // If the current level and new level are the same, increment the final level
                        if (currentLevel <= newLevel && currentLevel < enchantment.getMaxLevel()) {
                            finalLevel = currentLevel + 1;
                        } else {
                            finalLevel = Math.max(newLevel, currentLevel);
                        }

                        // If enchantment is acceptable on item
                        if (enchantment.isAcceptableItem(inputItem)) {

                            // Check for incompatible enchantments
                            boolean hasIncompatibleEnchantments = currentEnchants.keySet().stream()
                                    .anyMatch(enchant -> areIncompatible(enchantment, enchant, mendingWorksWithUnbreaking));

                            // If there are no incompatible enchantments, add the enchantment
                            if (!hasIncompatibleEnchantments) {
                                if (currentEnchants.containsKey(enchantment) && finalLevel > currentEnchants.get(enchantment)) {
                                    totalCost += baseCost(enchantment) * finalLevel;
                                } else if (!currentEnchants.containsKey(enchantment)) {
                                    totalCost += baseCost(enchantment) * Math.max(finalLevel, newLevel);
                                }
                                currentEnchants.put(enchantment, Math.max(finalLevel, newLevel));
                            }
                        }
                    }
                }
            }

            // The second item has to do something, or taking the output (say, to rename) would consume it for nothing
            if (totalCost <= 0) {
                return Result.EMPTY;
            }
        }

        // If the enchanting item is empty and the new name is null or the same as the input item's name, there is no output
        if (enchantingItem.isEmpty() && (newItemName == null || newItemName.equals(inputItem.getName().getString()))) {
            return Result.EMPTY;
        }

        // If the new name is blank and the input item has a custom name, remove the custom name and add the cost
        if (StringUtils.isBlank(newItemName)) {
            if (inputItem.hasCustomName()) {
                totalCost += RENAME_COST;
                resultItem.removeCustomName();
            }
        }
        // If the new name is different from the input item's name, set the custom name and add the cost
        else if (!newItemName.equals(inputItem.getName().getString())) {
            totalCost += RENAME_COST;
            resultItem.setCustomName(Text.literal(newItemName));
        }

        if (totalCost <= 0) {
            return Result.EMPTY;
        }

        // Carry the prior work penalty over unchanged; AnvilFix never grows it
        int repairCost = Math.max(resultItem.getRepairCost(), enchantingItem.isEmpty() ? 0 : enchantingItem.getRepairCost());
        resultItem.setRepairCost(repairCost);
        EnchantmentHelper.set(currentEnchants, resultItem);

        return new Result(resultItem, totalCost, repairItemUsage);
    }
}
