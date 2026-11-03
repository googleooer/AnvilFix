package com.googlerooeric.anvilfix.gametest;

import com.googlerooeric.anvilfix.AnvilFix;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Drives a real AnvilScreenHandler on the game test server and checks the rules from the README.
 * Each test sets the slots, which runs updateResult() the same way a player's clicks would.
 */
public class AnvilFixGameTests implements FabricGameTest {

    private static final class Anvil {
        final TestContext ctx;
        final PlayerEntity player;
        final AnvilScreenHandler handler;

        Anvil(TestContext ctx) {
            this.ctx = ctx;
            BlockPos pos = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
            ctx.getWorld().setBlockState(pos, Blocks.ANVIL.getDefaultState());
            // createMockPlayer() reports isCreative() but its abilities are survival, which is what the anvil checks
            this.player = ctx.createMockPlayer();
            this.player.experienceLevel = 1000;
            this.handler = new AnvilScreenHandler(0, player.getInventory(), ScreenHandlerContext.create(ctx.getWorld(), pos));
        }

        Anvil put(ItemStack left, ItemStack right) {
            handler.getSlot(0).setStack(left);
            handler.getSlot(1).setStack(right);
            return this;
        }

        Anvil rename(String name) {
            handler.setNewItemName(name);
            return this;
        }

        ItemStack output() {
            return handler.getSlot(2).getStack();
        }

        int cost() {
            return handler.getLevelCost();
        }

        ItemStack take() {
            ItemStack out = output();
            check(handler.getSlot(2).canTakeItems(player), "output can't be taken (cost " + cost() + ")");
            handler.getSlot(2).onTakeItem(player, out);
            return out;
        }

        void expectCost(int expected) {
            check(!output().isEmpty(), "expected an output costing " + expected + " but output is empty (cost " + cost() + ")");
            check(cost() == expected, "expected cost " + expected + " but was " + cost());
        }

        void expectNoOutput() {
            check(output().isEmpty(), "expected no output but got " + output() + " " + EnchantmentHelper.get(output()) + " for " + cost());
        }

        void expectLevel(Enchantment enchantment, int level) {
            int actual = EnchantmentHelper.get(output()).getOrDefault(enchantment, 0);
            check(actual == level, "expected " + enchantment.getName(level).getString() + " but level was " + actual);
        }

        void check(boolean condition, String message) {
            if (!condition) {
                ctx.throwGameTestException(message);
            }
        }
    }

    private static ItemStack book(Enchantment enchantment, int level) {
        return EnchantedBookItem.forEnchantment(new EnchantmentLevelEntry(enchantment, level));
    }

    private static ItemStack book(Map<Enchantment, Integer> enchantments) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.set(enchantments, book);
        return book;
    }

    private static ItemStack enchanted(ItemStack stack, Enchantment enchantment, int level) {
        stack.addEnchantment(enchantment, level);
        return stack;
    }

    private static ItemStack damaged(ItemStack stack, int damage) {
        stack.setDamage(damage);
        return stack;
    }

    private static void withMendingRule(TestContext ctx, boolean value, Runnable body) {
        var rule = ctx.getWorld().getGameRules().get(AnvilFix.MENDING_WORKS_WITH_UNBREAKING);
        boolean old = rule.get();
        rule.set(value, ctx.getWorld().getServer());
        try {
            body.run();
        } finally {
            rule.set(old, ctx.getWorld().getServer());
        }
    }

    // --- Enchanting: base cost x level ---

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sharpnessCostsTwoPerLevel(TestContext ctx) {
        int[][] cases = {{1, 2}, {2, 4}, {5, 10}};
        for (int[] c : cases) {
            Anvil anvil = new Anvil(ctx).put(new ItemStack(Items.DIAMOND_SWORD), book(Enchantments.SHARPNESS, c[0]));
            anvil.expectCost(c[1]);
            anvil.expectLevel(Enchantments.SHARPNESS, c[0]);
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void equalLevelsCombineToNextLevel(TestContext ctx) {
        Anvil anvil = new Anvil(ctx).put(enchanted(new ItemStack(Items.DIAMOND_SWORD), Enchantments.SHARPNESS, 1), book(Enchantments.SHARPNESS, 1));
        anvil.expectLevel(Enchantments.SHARPNESS, 2);
        anvil.expectCost(4);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void upgradeCostsResultingLevel(TestContext ctx) {
        // Sharpness I sword + Sharpness III book gives Sharpness III, which costs 2 x 3
        Anvil anvil = new Anvil(ctx).put(enchanted(new ItemStack(Items.DIAMOND_SWORD), Enchantments.SHARPNESS, 1), book(Enchantments.SHARPNESS, 3));
        anvil.expectLevel(Enchantments.SHARPNESS, 3);
        anvil.expectCost(6);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void lowerLevelBookDoesNothing(TestContext ctx) {
        new Anvil(ctx).put(enchanted(new ItemStack(Items.DIAMOND_SWORD), Enchantments.SHARPNESS, 4), book(Enchantments.SHARPNESS, 2)).expectNoOutput();
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bookMergeKeepsHigherLevel(TestContext ctx) {
        Anvil anvil = new Anvil(ctx).put(book(Enchantments.SHARPNESS, 1), book(Enchantments.SHARPNESS, 3));
        anvil.expectLevel(Enchantments.SHARPNESS, 3);
        anvil.expectCost(6);

        anvil = new Anvil(ctx).put(book(Enchantments.SHARPNESS, 2), book(Enchantments.SHARPNESS, 2));
        anvil.expectLevel(Enchantments.SHARPNESS, 3);
        anvil.expectCost(6);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bookMergeAddsNewEnchantment(TestContext ctx) {
        Anvil anvil = new Anvil(ctx).put(book(Enchantments.SHARPNESS, 1), book(Enchantments.UNBREAKING, 2));
        anvil.expectLevel(Enchantments.SHARPNESS, 1);
        anvil.expectLevel(Enchantments.UNBREAKING, 2);
        anvil.expectCost(6);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void enchantedItemOnBookDoesNothing(TestContext ctx) {
        new Anvil(ctx).put(book(Enchantments.UNBREAKING, 1), enchanted(new ItemStack(Items.DIAMOND_SWORD), Enchantments.SHARPNESS, 5)).expectNoOutput();
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bookNotAcceptedByItemDoesNothing(TestContext ctx) {
        new Anvil(ctx).put(new ItemStack(Items.DIAMOND_PICKAXE), book(Enchantments.SHARPNESS, 1)).expectNoOutput();
        ctx.complete();
    }

    // --- Incompatible enchantments ---

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sharpnessAndSmiteAreIncompatible(TestContext ctx) {
        new Anvil(ctx).put(enchanted(new ItemStack(Items.DIAMOND_SWORD), Enchantments.SHARPNESS, 1), book(Enchantments.SMITE, 1)).expectNoOutput();
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void vanillaIncompatibilitiesAreKept(TestContext ctx) {
        // AnvilFix-issues#3: Protection + Fire Protection
        new Anvil(ctx).put(enchanted(new ItemStack(Items.DIAMOND_CHESTPLATE), Enchantments.PROTECTION, 4), book(Enchantments.FIRE_PROTECTION, 4)).expectNoOutput();
        new Anvil(ctx).put(
                enchanted(new ItemStack(Items.DIAMOND_CHESTPLATE), Enchantments.PROTECTION, 4),
                enchanted(new ItemStack(Items.DIAMOND_CHESTPLATE), Enchantments.FIRE_PROTECTION, 4)).expectNoOutput();
        new Anvil(ctx).put(enchanted(new ItemStack(Items.DIAMOND_PICKAXE), Enchantments.SILK_TOUCH, 1), book(Enchantments.FORTUNE, 3)).expectNoOutput();
        new Anvil(ctx).put(enchanted(new ItemStack(Items.BOW), Enchantments.INFINITY, 1), book(Enchantments.MENDING, 1)).expectNoOutput();
        new Anvil(ctx).put(book(Enchantments.PROTECTION, 1), book(Enchantments.BLAST_PROTECTION, 1)).expectNoOutput();
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mendingAndUnbreakingIncompatibleByDefault(TestContext ctx) {
        withMendingRule(ctx, false, () -> {
            new Anvil(ctx).put(enchanted(new ItemStack(Items.DIAMOND_SWORD), Enchantments.MENDING, 1), book(Enchantments.UNBREAKING, 3)).expectNoOutput();
            new Anvil(ctx).put(enchanted(new ItemStack(Items.DIAMOND_SWORD), Enchantments.UNBREAKING, 3), book(Enchantments.MENDING, 1)).expectNoOutput();
            new Anvil(ctx).put(book(Enchantments.MENDING, 1), book(Enchantments.UNBREAKING, 3)).expectNoOutput();
        });
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mendingWorksWithUnbreakingWhenRuleEnabled(TestContext ctx) {
        withMendingRule(ctx, true, () -> {
            Anvil anvil = new Anvil(ctx).put(enchanted(new ItemStack(Items.DIAMOND_SWORD), Enchantments.MENDING, 1), book(Enchantments.UNBREAKING, 3));
            anvil.expectLevel(Enchantments.UNBREAKING, 3);
            anvil.expectCost(9);
        });
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mendingRuleDefaultsToFalse(TestContext ctx) {
        // A fresh GameRules instance holds the registered default
        boolean value = new net.minecraft.world.GameRules().getBoolean(AnvilFix.MENDING_WORKS_WITH_UNBREAKING);
        if (value) {
            ctx.throwGameTestException("mendingWorksWithUnbreaking should default to false");
        }
        ctx.complete();
    }

    // --- Repairing ---

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void repairCostsTwoPerMaterial(TestContext ctx) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        int max = sword.getMaxDamage();
        Anvil anvil = new Anvil(ctx).put(damaged(sword, max - 1), new ItemStack(Items.DIAMOND, 64));
        // each diamond repairs a quarter of max durability, so a nearly broken sword needs 4
        anvil.expectCost(8);
        ItemStack out = anvil.take();
        anvil.check(out.getDamage() == 0, "sword should be fully repaired but damage is " + out.getDamage());
        anvil.check(anvil.handler.getSlot(1).getStack().getCount() == 60, "expected 60 diamonds left, got " + anvil.handler.getSlot(1).getStack().getCount());

        anvil = new Anvil(ctx).put(damaged(new ItemStack(Items.DIAMOND_SWORD), 10), new ItemStack(Items.DIAMOND, 64));
        anvil.expectCost(2);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void undamagedItemWithMaterialDoesNothing(TestContext ctx) {
        // Renaming here would otherwise eat the whole diamond stack
        Anvil anvil = new Anvil(ctx).put(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND, 64)).rename("Blade");
        anvil.expectNoOutput();
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unusableSecondItemIsNotConsumed(TestContext ctx) {
        new Anvil(ctx).put(book(Enchantments.SHARPNESS, 1), new ItemStack(Items.DIRT, 64)).rename("Tome").expectNoOutput();
        new Anvil(ctx).put(enchanted(new ItemStack(Items.DIAMOND_SWORD), Enchantments.SHARPNESS, 1), book(Enchantments.SMITE, 1)).rename("Blade").expectNoOutput();
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void combiningTwoDamagedItemsRepairs(TestContext ctx) {
        int max = new ItemStack(Items.DIAMOND_SWORD).getMaxDamage();
        Anvil anvil = new Anvil(ctx).put(damaged(new ItemStack(Items.DIAMOND_SWORD), max / 2), damaged(new ItemStack(Items.DIAMOND_SWORD), max / 2));
        anvil.expectCost(2);
        anvil.check(anvil.output().getDamage() == 0, "combined sword should be fully repaired, damage " + anvil.output().getDamage());
        ctx.complete();
    }

    // --- Renaming ---

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void renameCostsTwo(TestContext ctx) {
        Anvil anvil = new Anvil(ctx).put(new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY).rename("Blade");
        anvil.expectCost(2);
        anvil.check(anvil.output().getName().getString().equals("Blade"), "output not renamed");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void renameToSameNameDoesNothing(TestContext ctx) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        new Anvil(ctx).put(sword, ItemStack.EMPTY).rename(sword.getName().getString()).expectNoOutput();
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void clearingCustomNameCostsTwo(TestContext ctx) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD).setCustomName(Text.literal("Blade"));
        Anvil anvil = new Anvil(ctx).put(sword, ItemStack.EMPTY).rename("");
        anvil.expectCost(2);
        anvil.check(!anvil.output().hasCustomName(), "custom name should be removed");
        ctx.complete();
    }

    // --- Independence, accumulation and the 40 level cap ---

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void costsAreIndependentAndAdd(TestContext ctx) {
        // Sharpness I (2) + rename (2)
        new Anvil(ctx).put(new ItemStack(Items.DIAMOND_SWORD), book(Enchantments.SHARPNESS, 1)).rename("Blade").expectCost(4);
        // repair 1 diamond (2) + rename (2)
        new Anvil(ctx).put(damaged(new ItemStack(Items.DIAMOND_SWORD), 10), new ItemStack(Items.DIAMOND, 64)).rename("Blade").expectCost(4);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void priorWorkDoesNotAccumulate(TestContext ctx) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setRepairCost(63);
        Anvil anvil = new Anvil(ctx).put(sword, book(Enchantments.SHARPNESS, 1));
        anvil.expectCost(2);
        anvil.check(anvil.output().getRepairCost() <= 63, "repair cost grew to " + anvil.output().getRepairCost());

        // the output fed back in costs the same as the first time
        ItemStack once = anvil.take();
        anvil = new Anvil(ctx).put(once, book(Enchantments.UNBREAKING, 1));
        anvil.expectCost(3);
        anvil.check(AnvilScreenHandler.getNextCost(5) == 5, "getNextCost should not grow");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void noTooExpensiveCap(TestContext ctx) {
        Map<Enchantment, Integer> enchants = new java.util.LinkedHashMap<>();
        enchants.put(Enchantments.SHARPNESS, 5);     // 10
        enchants.put(Enchantments.LOOTING, 3);       // 9
        enchants.put(Enchantments.FIRE_ASPECT, 2);   // 8
        enchants.put(Enchantments.UNBREAKING, 3);    // 9
        enchants.put(Enchantments.KNOCKBACK, 2);     // 6
        enchants.put(Enchantments.SWEEPING, 3);      // 6
        Anvil anvil = new Anvil(ctx).put(new ItemStack(Items.DIAMOND_SWORD), book(enchants));
        anvil.expectCost(48);
        anvil.player.experienceLevel = 48;
        anvil.take();
        anvil.check(anvil.player.experienceLevel == 0, "expected 48 levels spent, " + anvil.player.experienceLevel + " left");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void notEnoughLevelsBlocksTaking(TestContext ctx) {
        Anvil anvil = new Anvil(ctx).put(new ItemStack(Items.DIAMOND_SWORD), book(Enchantments.SHARPNESS, 5));
        anvil.player.experienceLevel = 9;
        anvil.check(!anvil.handler.getSlot(2).canTakeItems(anvil.player), "10 level result should not be takeable with 9 levels");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void emptyAnvilHasNoOutput(TestContext ctx) {
        new Anvil(ctx).put(ItemStack.EMPTY, book(Enchantments.SHARPNESS, 1)).expectNoOutput();
        new Anvil(ctx).put(new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY).expectNoOutput();
        ctx.complete();
    }
}
