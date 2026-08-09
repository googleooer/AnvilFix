package com.googlerooeric.anvilfix.mixin.screen;

import com.googlerooeric.anvilfix.AnvilFix;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.*;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler {
    @Shadow @Final private Property levelCost;
    @Shadow private int repairItemUsage;
    @Shadow private String newItemName;

    public AnvilScreenHandlerMixin(@Nullable ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(type, syncId, playerInventory, context);
    }

    @Unique
    private static final Map<Enchantment, Integer> ENCHANTMENT_COST_MAP = new HashMap<>();

    @Unique
    private static final Set<Set<Enchantment>> ENCHANTMENT_INCOMPATIBILITY_SETS = new HashSet<>();

    static {
        // Populate the map with enchantments and their base costs
        // Example: ENCHANTMENT_COST_MAP.put(Enchantments.SHARPNESS, 2);
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

        // Populate the incompatibility sets with groups of incompatible enchantments
        // Note: Does not include trident enchants since channeling + loyalty is valid
        //       Does not include mending + unbreaking, since it depends on gamerule
        ENCHANTMENT_INCOMPATIBILITY_SETS.add(Set.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS));
        ENCHANTMENT_INCOMPATIBILITY_SETS.add(Set.of(Enchantments.PROTECTION, Enchantments.PROJECTILE_PROTECTION, Enchantments.BLAST_PROTECTION, Enchantments.FIRE_PROTECTION));
        ENCHANTMENT_INCOMPATIBILITY_SETS.add(Set.of(Enchantments.FORTUNE, Enchantments.SILK_TOUCH));
        ENCHANTMENT_INCOMPATIBILITY_SETS.add(Set.of(Enchantments.FROST_WALKER, Enchantments.DEPTH_STRIDER));
        ENCHANTMENT_INCOMPATIBILITY_SETS.add(Set.of(Enchantments.INFINITY, Enchantments.MENDING));
        ENCHANTMENT_INCOMPATIBILITY_SETS.add(Set.of(Enchantments.MULTISHOT, Enchantments.PIERCING));
    }

    /**
     * @author googler_ooeric
     * @reason Make it so repairing doesn't increase cost.
     */
    @Overwrite
    public static int getNextCost(int cost) {
        return cost; // Return the cost as it is
    }

    /**
     * @author DavidMacDonald11
     * @reason Change how anvils work to make more sense
     */
    @Overwrite
    public void updateResult() {
        var item = this.input.getStack(0);
        var totalCost = 0;

        if(item.isEmpty()) {
            this.output.setStack(0, ItemStack.EMPTY);
            this.levelCost.set(0);
            return;
        }

        this.repairItemUsage = 0;
        var modifier = this.input.getStack(1);
        var result = item.copy();

        if(!modifier.isEmpty()) {
            totalCost += repairAndEnchantItem(item, modifier, result);
        }

        totalCost += renameItem(item, result, modifier.isEmpty(), totalCost);
        setFinalResult(totalCost, result);
    }

    @Unique
    private int repairAndEnchantItem(ItemStack item, ItemStack modifier, ItemStack result) {
        if(canItemBeRepairedByModifier(item, modifier)) {
            return repairItem(item, modifier, result);
        }

        var itemIsBook = isItemAnEnchantedBook(item);
        var modifierIsBook = isItemAnEnchantedBook(modifier);

        if(!itemIsBook && !modifierIsBook) {
            return mergeItems(item, modifier, result);
        }

        if(modifierIsBook) {
            return addEnchantsToItem(item, modifier, result);
        }

        return 0;
    }

    @Unique
    private int renameItem(ItemStack item, ItemStack result, boolean modifierIsEmpty, int cost) {
        if(!modifierIsEmpty && cost == 0) { return 0; }

        if(StringUtils.isBlank(this.newItemName) || this.newItemName == null) {
            if(item.hasCustomName()) {
                result.removeCustomName();
                return 2;
            }

            return 0;
        }

        if(this.newItemName.equals(item.getName().getString())) { return 0; }

        result.setCustomName(Text.literal(this.newItemName));
        return 2;
    }

    @Unique
    private void setFinalResult(int totalCost,  ItemStack result) {
        if(totalCost == 0) {
            result = ItemStack.EMPTY;
        }

        this.levelCost.set(totalCost);
        this.output.setStack(0, result);
        this.sendContentUpdates();
    }

    @Unique
    private static boolean canItemBeRepairedByModifier(ItemStack first, ItemStack second) {
        return first.isDamageable() && first.getItem().canRepair(first, second);
    }

    @Unique
    private static boolean isItemAnEnchantedBook(ItemStack item) {
        return item.isOf(Items.ENCHANTED_BOOK) && !EnchantedBookItem.getEnchantmentNbt(item).isEmpty();
    }

    @Unique
    private int repairItem(ItemStack item, ItemStack modifier, ItemStack result) {
        var damage = item.getDamage();
        var damageReductionPerRepair = item.getMaxDamage() / 4;

        var desiredRepairs = (int)Math.ceil((double)damage / damageReductionPerRepair);
        var possibleRepairs = modifier.getCount();
        var repairs = Math.min(desiredRepairs, possibleRepairs);

        var maxDamageReduction = repairs * damageReductionPerRepair;
        var damageReduction = Math.min(damage, maxDamageReduction);

        result.setDamage(damage - damageReduction);
        this.repairItemUsage = repairs;

        return 2 * repairs;
    }

    @Unique
    private int mergeItems(ItemStack item, ItemStack modifier, ItemStack result) {
        if(!ItemStack.areItemsEqualIgnoreDamage(item, modifier)) { return 0; }
        var totalCost = addEnchantsToItem(item, modifier, result);

        var maxHealth = item.getMaxDamage();
        var itemHealth = maxHealth - item.getDamage();
        var modifierHealth = maxHealth - modifier.getDamage();

        if(itemHealth != maxHealth) {
            var combinedHealth = (int)(itemHealth + modifierHealth + maxHealth * .12);
            var newHealth = Math.min(maxHealth, combinedHealth);

            result.setDamage(maxHealth - newHealth);
            totalCost += 2;
        }

        return totalCost;
    }

    @Unique
    private int addEnchantsToItem(ItemStack item, ItemStack modifier, ItemStack result) {
        var enchants = new HashMap<>(EnchantmentHelper.get(item));

        var itemIsBook = isItemAnEnchantedBook(item);
        var newEnchants = EnchantmentHelper.get(modifier);
        var totalCost = 0;

        for(var newEnchant : newEnchants.keySet()) {
            if(!itemIsBook && !newEnchant.isAcceptableItem(item)) {
                continue;
            }

            int newLevel = newEnchants.get(newEnchant);

            if(enchants.containsKey(newEnchant)) {
                totalCost += mergeEnchantmentLevels(enchants, newEnchant, newLevel);
            } else {
                totalCost += addNewEnchantment(enchants, newEnchant, newLevel);
            }
        }

        EnchantmentHelper.set(enchants, result);
        return totalCost;
    }

    @Unique
    private int mergeEnchantmentLevels(Map<Enchantment, Integer> enchants, Enchantment newEnchant, int newLevel) {
        int level = enchants.get(newEnchant);
        if(level == newLevel && level < newEnchant.getMaxLevel()) { newLevel++; }

        if(level < newLevel) {
            enchants.put(newEnchant, newLevel);
            return ENCHANTMENT_COST_MAP.getOrDefault(newEnchant, 2) * newLevel;
        }

        return 0;
    }

    @Unique
    private int addNewEnchantment(Map<Enchantment, Integer> enchants, Enchantment newEnchant, int newLevel) {
        var incompatibilitySet = getIncompatibiltySet(newEnchant);
        var enchantKeys = enchants.keySet();

        if(Collections.disjoint(enchantKeys, incompatibilitySet)) {
            enchants.put(newEnchant, newLevel);
            return ENCHANTMENT_COST_MAP.getOrDefault(newEnchant, 2) * newLevel;
        }

        return 0;
    }

    @Unique
    private Set<Enchantment> getIncompatibiltySet(Enchantment enchant) {
        var specialTridentEnchants = Set.of(Enchantments.CHANNELING, Enchantments.LOYALTY);

        if(specialTridentEnchants.contains(enchant)) { return Set.of(Enchantments.RIPTIDE); }
        if(enchant.equals(Enchantments.RIPTIDE)) { return specialTridentEnchants; }

        if(!doesMendingWorkWithUnbreaking()) {
            if(enchant.equals(Enchantments.MENDING)) { return Set.of(Enchantments.UNBREAKING); }
            if(enchant.equals(Enchantments.UNBREAKING)) { return Set.of(Enchantments.MENDING); }
        }

        for(var set : ENCHANTMENT_INCOMPATIBILITY_SETS) {
            if(set.contains(enchant)) { return set; }
        }

        return Set.of();
    }

    @Unique
    private boolean doesMendingWorkWithUnbreaking() {
        var result = new AtomicBoolean(false);
        this.context.run((world, pos) -> result.set(world.getGameRules().getBoolean(AnvilFix.MENDING_WORKS_WITH_UNBREAKING)));

        return result.get();
    }
}