package com.googlerooeric.anvilfix.selftest;

import com.googlerooeric.anvilfix.AnvilFix;
import com.googlerooeric.anvilfix.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The README rules, checked against a real AnvilMenu on the server. Setting a slot runs createResult() the same way
 * a player's click does. Enchantments are referred to by id so the scenarios read the same on every version.
 */
public final class AnvilScenarios {

    public record Scenario(String name, Consumer<Context> body) {
    }

    public record Context(MinecraftServer server) {
        ServerLevel level() {
            return server.overworld();
        }
    }

    private AnvilScenarios() {
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Anvil {
        final Player player;
        final AnvilMenu menu;
        final BlockPos pos;

        Anvil(Context ctx) {
            this.pos = ctx.level().getSharedSpawnPos().above(3);
            this.player = TestItems.survivalPlayer(ctx.level());
            this.player.experienceLevel = 1000;
            this.menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.create(ctx.level(), pos));
        }

        Anvil put(ItemStack left, ItemStack right) {
            menu.getSlot(0).set(left);
            menu.getSlot(1).set(right);
            return this;
        }

        Anvil rename(String name) {
            menu.setItemName(name);
            return this;
        }

        ItemStack output() {
            return menu.getSlot(2).getItem();
        }

        int cost() {
            return menu.getCost();
        }

        ItemStack take() {
            ItemStack out = output();
            check(menu.getSlot(2).mayPickup(player), "output can't be taken (cost " + cost() + ")");
            menu.getSlot(2).onTake(player, out);
            return out;
        }

        Anvil expectCost(int expected) {
            check(!output().isEmpty(), "expected an output costing " + expected + " but output is empty (cost " + cost() + ")");
            check(cost() == expected, "expected cost " + expected + " but was " + cost());
            return this;
        }

        Anvil expectNoOutput() {
            check(output().isEmpty(), "expected no output but got " + output() + " " + TestItems.describe(output()) + " for " + cost());
            return this;
        }

        Anvil expectLevel(String enchantment, int level) {
            int actual = TestItems.level(output(), enchantment);
            check(actual == level, "expected " + enchantment + " " + level + " but level was " + actual);
            return this;
        }
    }

    private static final String SHARPNESS = "minecraft:sharpness";
    private static final String SMITE = "minecraft:smite";
    private static final String UNBREAKING = "minecraft:unbreaking";
    private static final String MENDING = "minecraft:mending";

    private static ItemStack sword() {
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    private static ItemStack damaged(ItemStack stack, int damage) {
        stack.setDamageValue(damage);
        return stack;
    }

    private static void withMendingRule(Context ctx, boolean value, Runnable body) {
        GameRules.BooleanValue rule = ctx.level().getGameRules().getRule(AnvilFix.MENDING_WORKS_WITH_UNBREAKING);
        boolean old = rule.get();
        rule.set(value, ctx.server());
        try {
            body.run();
        } finally {
            rule.set(old, ctx.server());
        }
    }

    public static List<Scenario> all() {
        List<Scenario> s = new ArrayList<>();

        // --- Enchanting: base cost x level ---

        s.add(new Scenario("sharpnessCostsTwoPerLevel", ctx -> {
            int[][] cases = {{1, 2}, {2, 4}, {5, 10}};
            for (int[] c : cases) {
                new Anvil(ctx).put(sword(), TestItems.book(ctx, SHARPNESS, c[0])).expectCost(c[1]).expectLevel(SHARPNESS, c[0]);
            }
        }));
        s.add(new Scenario("equalLevelsCombineToNextLevel", ctx ->
                new Anvil(ctx).put(TestItems.enchanted(ctx, sword(), SHARPNESS, 1), TestItems.book(ctx, SHARPNESS, 1)).expectLevel(SHARPNESS, 2).expectCost(4)));
        s.add(new Scenario("upgradeCostsResultingLevel", ctx ->
                // Sharpness I sword + Sharpness III book gives Sharpness III, which costs 2 x 3
                new Anvil(ctx).put(TestItems.enchanted(ctx, sword(), SHARPNESS, 1), TestItems.book(ctx, SHARPNESS, 3)).expectLevel(SHARPNESS, 3).expectCost(6)));
        s.add(new Scenario("lowerLevelBookDoesNothing", ctx ->
                new Anvil(ctx).put(TestItems.enchanted(ctx, sword(), SHARPNESS, 4), TestItems.book(ctx, SHARPNESS, 2)).expectNoOutput()));
        s.add(new Scenario("bookMergeKeepsHigherLevel", ctx -> {
            new Anvil(ctx).put(TestItems.book(ctx, SHARPNESS, 1), TestItems.book(ctx, SHARPNESS, 3)).expectLevel(SHARPNESS, 3).expectCost(6);
            new Anvil(ctx).put(TestItems.book(ctx, SHARPNESS, 2), TestItems.book(ctx, SHARPNESS, 2)).expectLevel(SHARPNESS, 3).expectCost(6);
        }));
        s.add(new Scenario("bookMergeAddsNewEnchantment", ctx ->
                new Anvil(ctx).put(TestItems.book(ctx, SHARPNESS, 1), TestItems.book(ctx, UNBREAKING, 2))
                        .expectLevel(SHARPNESS, 1).expectLevel(UNBREAKING, 2).expectCost(6)));
        s.add(new Scenario("enchantedItemOnBookDoesNothing", ctx ->
                new Anvil(ctx).put(TestItems.book(ctx, UNBREAKING, 1), TestItems.enchanted(ctx, sword(), SHARPNESS, 5)).expectNoOutput()));
        s.add(new Scenario("bookNotAcceptedByItemDoesNothing", ctx ->
                new Anvil(ctx).put(new ItemStack(Items.DIAMOND_PICKAXE), TestItems.book(ctx, SHARPNESS, 1)).expectNoOutput()));

        // --- Incompatible enchantments ---

        s.add(new Scenario("sharpnessAndSmiteAreIncompatible", ctx ->
                new Anvil(ctx).put(TestItems.enchanted(ctx, sword(), SHARPNESS, 1), TestItems.book(ctx, SMITE, 1)).expectNoOutput()));
        s.add(new Scenario("vanillaIncompatibilitiesAreKept", ctx -> {
            // AnvilFix-issues#3: Protection + Fire Protection
            new Anvil(ctx).put(TestItems.enchanted(ctx, new ItemStack(Items.DIAMOND_CHESTPLATE), "minecraft:protection", 4),
                    TestItems.book(ctx, "minecraft:fire_protection", 4)).expectNoOutput();
            new Anvil(ctx).put(TestItems.enchanted(ctx, new ItemStack(Items.DIAMOND_CHESTPLATE), "minecraft:protection", 4),
                    TestItems.enchanted(ctx, new ItemStack(Items.DIAMOND_CHESTPLATE), "minecraft:fire_protection", 4)).expectNoOutput();
            new Anvil(ctx).put(TestItems.enchanted(ctx, new ItemStack(Items.DIAMOND_PICKAXE), "minecraft:silk_touch", 1),
                    TestItems.book(ctx, "minecraft:fortune", 3)).expectNoOutput();
            new Anvil(ctx).put(TestItems.enchanted(ctx, new ItemStack(Items.BOW), "minecraft:infinity", 1),
                    TestItems.book(ctx, MENDING, 1)).expectNoOutput();
            new Anvil(ctx).put(TestItems.book(ctx, "minecraft:protection", 1), TestItems.book(ctx, "minecraft:blast_protection", 1)).expectNoOutput();
        }));
        s.add(new Scenario("mendingAndUnbreakingIncompatibleByDefault", ctx -> withMendingRule(ctx, false, () -> {
            new Anvil(ctx).put(TestItems.enchanted(ctx, sword(), MENDING, 1), TestItems.book(ctx, UNBREAKING, 3)).expectNoOutput();
            new Anvil(ctx).put(TestItems.enchanted(ctx, sword(), UNBREAKING, 3), TestItems.book(ctx, MENDING, 1)).expectNoOutput();
            new Anvil(ctx).put(TestItems.book(ctx, MENDING, 1), TestItems.book(ctx, UNBREAKING, 3)).expectNoOutput();
        })));
        s.add(new Scenario("mendingWorksWithUnbreakingWhenRuleEnabled", ctx -> withMendingRule(ctx, true, () ->
                new Anvil(ctx).put(TestItems.enchanted(ctx, sword(), MENDING, 1), TestItems.book(ctx, UNBREAKING, 3)).expectLevel(UNBREAKING, 3).expectCost(9))));
        s.add(new Scenario("mendingRuleDefaultsToFalse", ctx ->
                // A fresh GameRules instance holds the registered default
                check(!TestItems.freshGameRules().getBoolean(AnvilFix.MENDING_WORKS_WITH_UNBREAKING), "mendingWorksWithUnbreaking should default to false")));

        // --- Repairing ---

        s.add(new Scenario("repairCostsTwoPerMaterial", ctx -> {
            ItemStack sword = sword();
            Anvil anvil = new Anvil(ctx).put(damaged(sword, sword.getMaxDamage() - 1), new ItemStack(Items.DIAMOND, 64));
            // each diamond repairs a quarter of max durability, so a nearly broken sword needs 4
            anvil.expectCost(8);
            ItemStack out = anvil.take();
            check(out.getDamageValue() == 0, "sword should be fully repaired but damage is " + out.getDamageValue());
            int left = anvil.menu.getSlot(1).getItem().getCount();
            check(left == 60, "expected 60 diamonds left, got " + left);

            new Anvil(ctx).put(damaged(sword(), 10), new ItemStack(Items.DIAMOND, 64)).expectCost(2);
        }));
        s.add(new Scenario("undamagedItemWithMaterialDoesNothing", ctx ->
                // Renaming here would otherwise eat the whole diamond stack
                new Anvil(ctx).put(sword(), new ItemStack(Items.DIAMOND, 64)).rename("Blade").expectNoOutput()));
        s.add(new Scenario("unusableSecondItemIsNotConsumed", ctx -> {
            new Anvil(ctx).put(TestItems.book(ctx, SHARPNESS, 1), new ItemStack(Items.DIRT, 64)).rename("Tome").expectNoOutput();
            new Anvil(ctx).put(TestItems.enchanted(ctx, sword(), SHARPNESS, 1), TestItems.book(ctx, SMITE, 1)).rename("Blade").expectNoOutput();
        }));
        s.add(new Scenario("combiningTwoDamagedItemsRepairs", ctx -> {
            int max = sword().getMaxDamage();
            Anvil anvil = new Anvil(ctx).put(damaged(sword(), max / 2), damaged(sword(), max / 2)).expectCost(2);
            check(anvil.output().getDamageValue() == 0, "combined sword should be fully repaired, damage " + anvil.output().getDamageValue());
        }));

        // --- Renaming ---

        s.add(new Scenario("renameCostsTwo", ctx -> {
            Anvil anvil = new Anvil(ctx).put(sword(), ItemStack.EMPTY).rename("Blade").expectCost(2);
            check(anvil.output().getHoverName().getString().equals("Blade"), "output not renamed");
        }));
        s.add(new Scenario("renameToSameNameDoesNothing", ctx -> {
            ItemStack sword = sword();
            new Anvil(ctx).put(sword, ItemStack.EMPTY).rename(sword.getHoverName().getString()).expectNoOutput();
        }));
        s.add(new Scenario("clearingCustomNameCostsTwo", ctx -> {
            ItemStack sword = sword();
            Compat.setCustomName(sword, "Blade");
            Anvil anvil = new Anvil(ctx).put(sword, ItemStack.EMPTY).rename("").expectCost(2);
            check(!Compat.hasCustomName(anvil.output()), "custom name should be removed");
        }));

        // --- Independence, accumulation and the 40 level cap ---

        s.add(new Scenario("costsAreIndependentAndAdd", ctx -> {
            // Sharpness I (2) + rename (2)
            new Anvil(ctx).put(sword(), TestItems.book(ctx, SHARPNESS, 1)).rename("Blade").expectCost(4);
            // repair 1 diamond (2) + rename (2)
            new Anvil(ctx).put(damaged(sword(), 10), new ItemStack(Items.DIAMOND, 64)).rename("Blade").expectCost(4);
        }));
        s.add(new Scenario("priorWorkDoesNotAccumulate", ctx -> {
            ItemStack sword = sword();
            Compat.setRepairCost(sword, 63);
            Anvil anvil = new Anvil(ctx).put(sword, TestItems.book(ctx, SHARPNESS, 1)).expectCost(2);
            check(Compat.getRepairCost(anvil.output()) <= 63, "repair cost grew to " + Compat.getRepairCost(anvil.output()));

            // the output fed back in costs the same as the first time
            ItemStack once = anvil.take();
            new Anvil(ctx).put(once, TestItems.book(ctx, UNBREAKING, 1)).expectCost(3);
            check(AnvilMenu.calculateIncreasedRepairCost(5) == 5, "calculateIncreasedRepairCost should not grow");
        }));
        s.add(new Scenario("noTooExpensiveCap", ctx -> {
            Map<String, Integer> enchants = new LinkedHashMap<>();
            enchants.put(SHARPNESS, 5);                 // 10
            enchants.put("minecraft:looting", 3);       // 9
            enchants.put("minecraft:fire_aspect", 2);   // 8
            enchants.put(UNBREAKING, 3);                // 9
            enchants.put("minecraft:knockback", 2);     // 6
            Anvil anvil = new Anvil(ctx).put(sword(), TestItems.book(ctx, enchants)).expectCost(42);
            anvil.player.experienceLevel = 42;
            anvil.take();
            check(anvil.player.experienceLevel == 0, "expected 42 levels spent, " + anvil.player.experienceLevel + " left");
        }));
        s.add(new Scenario("notEnoughLevelsBlocksTaking", ctx -> {
            Anvil anvil = new Anvil(ctx).put(sword(), TestItems.book(ctx, SHARPNESS, 5));
            anvil.player.experienceLevel = 9;
            check(!anvil.menu.getSlot(2).mayPickup(anvil.player), "10 level result should not be takeable with 9 levels");
        }));

        // --- Multiplayer sync ---

        s.add(new Scenario("levelCostIsResentOnEveryUpdate", ctx -> {
            Anvil anvil = new Anvil(ctx);
            List<Integer> sentCosts = new ArrayList<>();
            anvil.menu.setSynchronizer(new ContainerSynchronizer() {
                @Override
                public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items, ItemStack carried, int[] data) {
                }

                @Override
                public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
                }

                @Override
                public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {
                }

                @Override
                public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
                    sentCosts.add(value);
                }
            });
            anvil.put(sword(), ItemStack.EMPTY);
            sentCosts.clear();
            // A vanilla client predicts its own cost for each keystroke, so the server has to resend 2 every time
            anvil.rename("B");
            anvil.rename("Bl");
            check(sentCosts.equals(List.of(2, 2)), "expected the cost to be sent twice, sent " + sentCosts);
        }));

        // --- Edge cases ---

        s.add(new Scenario("creativeTakesWithoutLevels", ctx -> {
            Anvil anvil = new Anvil(ctx);
            TestItems.setCreative(anvil.player, true);
            anvil.player.experienceLevel = 0;
            anvil.put(sword(), TestItems.book(ctx, SHARPNESS, 5)).expectCost(10);
            anvil.take();
            check(anvil.player.experienceLevel == 0, "creative player should not pay levels, has " + anvil.player.experienceLevel);
        }));
        s.add(new Scenario("cursesCombineLikeOtherEnchantments", ctx -> {
            // Curse of Binding can go on armor; Curse of Vanishing has a single level so V+V stays at I
            new Anvil(ctx).put(new ItemStack(Items.DIAMOND_CHESTPLATE), TestItems.book(ctx, "minecraft:binding_curse", 1))
                    .expectLevel("minecraft:binding_curse", 1).expectCost(2);
            new Anvil(ctx).put(TestItems.book(ctx, "minecraft:vanishing_curse", 1), TestItems.book(ctx, "minecraft:vanishing_curse", 1)).expectNoOutput();
        }));
        s.add(new Scenario("aboveMaxLevelsAreKept", ctx -> {
            // Command-made books above the max level still apply, and cost base x level
            new Anvil(ctx).put(sword(), TestItems.book(ctx, SHARPNESS, 6)).expectLevel(SHARPNESS, 6).expectCost(12);
            new Anvil(ctx).put(TestItems.enchanted(ctx, sword(), SHARPNESS, 5), TestItems.book(ctx, SHARPNESS, 6)).expectLevel(SHARPNESS, 6).expectCost(12);
        }));
        s.add(new Scenario("maxLevelPlusSameLevelDoesNothing", ctx ->
                new Anvil(ctx).put(TestItems.enchanted(ctx, sword(), SHARPNESS, 5), TestItems.book(ctx, SHARPNESS, 5)).expectNoOutput()));
        s.add(new Scenario("partlyIncompatibleBookAppliesTheRest", ctx -> {
            Map<String, Integer> enchants = new LinkedHashMap<>();
            enchants.put(SMITE, 2);         // conflicts with the sword's Sharpness
            enchants.put(UNBREAKING, 2);    // 3 x 2 = 6
            new Anvil(ctx).put(TestItems.enchanted(ctx, sword(), SHARPNESS, 1), TestItems.book(ctx, enchants))
                    .expectLevel(SMITE, 0).expectLevel(UNBREAKING, 2).expectLevel(SHARPNESS, 1).expectCost(6);
        }));
        s.add(new Scenario("plainBookCantBeEnchanted", ctx ->
                new Anvil(ctx).put(new ItemStack(Items.BOOK), TestItems.book(ctx, SHARPNESS, 1)).expectNoOutput()));
        s.add(new Scenario("renamingAStackKeepsTheStack", ctx -> {
            Anvil anvil = new Anvil(ctx).put(new ItemStack(Items.STICK, 16), ItemStack.EMPTY).rename("Wand").expectCost(2);
            check(anvil.output().getCount() == 16, "expected 16 renamed sticks, got " + anvil.output().getCount());
        }));
        s.add(new Scenario("blankNameWithoutCustomNameDoesNothing", ctx ->
                new Anvil(ctx).put(sword(), ItemStack.EMPTY).rename("").expectNoOutput()));
        s.add(new Scenario("anvilStillWearsOut", ctx -> {
            // Vanilla has a 12% chance per use to damage the anvil; 200 uses without a single hit is ~1e-11
            Anvil anvil = new Anvil(ctx);
            BlockPos pos = anvil.pos;
            ctx.level().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState());
            for (int i = 0; i < 200 && ctx.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.ANVIL); i++) {
                anvil.put(sword(), ItemStack.EMPTY).rename("Blade " + i);
                anvil.take();
            }
            check(!ctx.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.ANVIL), "anvil never got damaged in 200 uses");
            ctx.level().removeBlock(pos, false);
        }));

        s.add(new Scenario("emptyAnvilHasNoOutput", ctx -> {
            new Anvil(ctx).put(ItemStack.EMPTY, TestItems.book(ctx, SHARPNESS, 1)).expectNoOutput();
            new Anvil(ctx).put(sword(), ItemStack.EMPTY).expectNoOutput();
        }));

        return s;
    }
}
