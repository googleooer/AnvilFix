package com.googlerooeric.anvilfix.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Client half of the in-game test (-Danvilfix.ingame=true, the client auto-connects to the {@link IngameHost} server).
 * Uses the anvil the way a player would: slot clicks and the rename box, all going through the network. Then checks
 * what the client ends up seeing: the synced cost, the cost label, the output, its levels and the leftover items.
 */
public final class IngameClient {

    /** The cost the anvil label last asked to show, recorded by a client mixin on TranslatableContents. */
    public static volatile Object lastCostLabel;

    private static final int TIMEOUT_TICKS = 20 * 60 * 3;
    private static final int STEP_DELAY = 10;

    // AnvilMenu slots: 0 and 1 are the inputs, 2 the output, 3-29 the main inventory, 30-38 the hotbar
    private static final int LEFT = 0, RIGHT = 1, OUTPUT = 2;

    private static int hotbar(int index) {
        return 30 + index;
    }

    private static int mainInventory(int index) {
        return 3 + index;
    }

    private interface Step {
        void run(Minecraft minecraft);
    }

    private static final List<Step> steps = new ArrayList<>();
    private static final List<String> failures = new ArrayList<>();
    private static int passed;
    private static int tick;
    private static int nextStepAt = -1;
    private static int stepIndex;
    private static boolean finished;

    private IngameClient() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("anvilfix.ingame");
    }

    /** @return true once the test is over and the client may quit */
    public static boolean tick(Minecraft minecraft) {
        if (finished) {
            return true;
        }
        tick++;
        if (tick > TIMEOUT_TICKS) {
            fail("timeout", "test didn't finish in time (step " + stepIndex + ", screen " + minecraft.screen + ")");
            return finish();
        }
        if (nextStepAt < 0) {
            if (minecraft.player == null || minecraft.level == null) {
                return false;
            }
            if (minecraft.screen instanceof AnvilScreen) {
                buildSteps();
                nextStepAt = tick + STEP_DELAY;
            } else if (minecraft.screen == null && tick % 20 == 0) {
                // In the world with nothing open: right-click the anvil the server put next to us
                BlockPos anvil = findAnvil(minecraft);
                if (anvil != null) {
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(anvil), Direction.UP, anvil, false));
                }
                if (tick % 200 == 0) {
                    SelfTest.LOGGER.info("ANVILFIX_INGAME waiting: at {}, anvil {}, screen {}", minecraft.player.blockPosition(), anvil, minecraft.screen);
                }
            }
            return false;
        }
        if (tick < nextStepAt) {
            return false;
        }
        if (stepIndex >= steps.size()) {
            return finish();
        }
        try {
            steps.get(stepIndex++).run(minecraft);
        } catch (Throwable t) {
            fail("step " + stepIndex, t.toString());
            SelfTest.LOGGER.error("in-game step failed", t);
            return finish();
        }
        nextStepAt = tick + STEP_DELAY;
        return false;
    }

    private static BlockPos findAnvil(Minecraft minecraft) {
        BlockPos origin = minecraft.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -3, -4), origin.offset(4, 3, 4))) {
            if (minecraft.level.getBlockState(pos).is(Blocks.ANVIL)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static boolean finish() {
        finished = true;
        SelfTest.LOGGER.info("ANVILFIX_INGAME_RESULT {} passed, {} failed", passed, failures.size());
        return true;
    }

    public static boolean failed() {
        return !failures.isEmpty();
    }

    private static void fail(String name, String message) {
        failures.add(name);
        SelfTest.LOGGER.error("ANVILFIX_INGAME FAIL {}: {}", name, message);
    }

    private static void check(String name, boolean condition, String message) {
        if (condition) {
            passed++;
            SelfTest.LOGGER.info("ANVILFIX_INGAME PASS {}", name);
        } else {
            fail(name, message);
        }
    }

    private static AnvilMenu menu(Minecraft minecraft) {
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (!(menu instanceof AnvilMenu anvil)) {
            throw new IllegalStateException("anvil menu closed, have " + menu);
        }
        return anvil;
    }

    private static void click(Minecraft minecraft, int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu(minecraft).containerId, slot, 0, ClickType.PICKUP, minecraft.player);
    }

    /** Moves a stack between two empty-cursor states: pick it up from one slot and put it in another. */
    private static void move(Minecraft minecraft, int from, int to) {
        click(minecraft, from);
        click(minecraft, to);
    }

    /** Types into the anvil's rename box, which updates the menu and sends the rename packet like a keystroke would. */
    private static void typeName(Minecraft minecraft, String name) {
        for (Field field : AnvilScreen.class.getDeclaredFields()) {
            if (field.getType() == EditBox.class) {
                try {
                    field.setAccessible(true);
                    ((EditBox) field.get(minecraft.screen)).setValue(name);
                    return;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalStateException("no rename box on " + minecraft.screen);
    }

    private static ItemStack output(Minecraft minecraft) {
        return menu(minecraft).getSlot(OUTPUT).getItem();
    }

    private static void buildSteps() {
        int[] levels = {IngameHost.START_LEVELS};

        // 1. Rename: 2 levels, applied to the item we take out
        steps.add(mc -> move(mc, hotbar(IngameHost.RENAME_SWORD), LEFT));
        steps.add(mc -> {
            lastCostLabel = null;
            typeName(mc, "Blade");
        });
        steps.add(mc -> {
            check("renameCost", menu(mc).getCost() == 2, "cost shown is " + menu(mc).getCost());
            check("renameOutput", output(mc).getHoverName().getString().equals("Blade"), "output is " + output(mc).getHoverName().getString());
            check("renameLabel", Integer.valueOf(2).equals(lastCostLabel), "label asked to show " + lastCostLabel);
            move(mc, OUTPUT, hotbar(7));
        });
        steps.add(mc -> {
            levels[0] -= 2;
            check("renameLevelsSpent", mc.player.experienceLevel == levels[0], "levels are " + mc.player.experienceLevel + ", expected " + levels[0]);
            check("renameTaken", menu(mc).getSlot(hotbar(7)).getItem().getHoverName().getString().equals("Blade"), "hotbar holds " + menu(mc).getSlot(hotbar(7)).getItem());
        });

        // 2. 42 levels: past vanilla's 40, the label must show the cost instead of "Too Expensive!"
        steps.add(mc -> move(mc, hotbar(IngameHost.EXPENSIVE_SWORD), LEFT));
        steps.add(mc -> {
            lastCostLabel = null;
            move(mc, hotbar(IngameHost.EXPENSIVE_BOOK), RIGHT);
        });
        steps.add(mc -> {
            check("expensiveCost", menu(mc).getCost() == 42, "cost shown is " + menu(mc).getCost());
            check("expensiveLabelNotCapped", Integer.valueOf(42).equals(lastCostLabel), "label asked to show " + lastCostLabel + " (null means \"Too Expensive!\")");
            check("expensiveOutput", TestItems.level(output(mc), "minecraft:sharpness") == 5, "output is " + output(mc) + " " + TestItems.describe(output(mc)));
            move(mc, OUTPUT, hotbar(8));
        });
        steps.add(mc -> {
            levels[0] -= 42;
            check("expensiveLevelsSpent", mc.player.experienceLevel == levels[0], "levels are " + mc.player.experienceLevel + ", expected " + levels[0]);
            check("expensiveBookUsed", menu(mc).getSlot(RIGHT).getItem().isEmpty(), "book slot holds " + menu(mc).getSlot(RIGHT).getItem());
        });

        // 3. Mending + Unbreaking is refused by default, and the client shows no output
        steps.add(mc -> move(mc, hotbar(IngameHost.MENDING_SWORD), LEFT));
        steps.add(mc -> move(mc, hotbar(IngameHost.UNBREAKING_BOOK), RIGHT));
        steps.add(mc -> {
            check("mendingRuleNoOutput", output(mc).isEmpty(), "output is " + output(mc) + " " + TestItems.describe(output(mc)));
            check("mendingRuleNoCost", menu(mc).getCost() == 0, "cost shown is " + menu(mc).getCost());
            move(mc, LEFT, hotbar(IngameHost.MENDING_SWORD));
        });
        steps.add(mc -> move(mc, RIGHT, hotbar(IngameHost.UNBREAKING_BOOK)));

        // 4. Repair: 4 diamonds for a nearly broken sword, 2 levels each, the rest stays in the anvil
        steps.add(mc -> move(mc, hotbar(IngameHost.DAMAGED_SWORD), LEFT));
        steps.add(mc -> move(mc, hotbar(IngameHost.DIAMONDS), RIGHT));
        steps.add(mc -> {
            check("repairCost", menu(mc).getCost() == 8, "cost shown is " + menu(mc).getCost());
            move(mc, OUTPUT, mainInventory(0));
        });
        steps.add(mc -> {
            levels[0] -= 8;
            check("repairLevelsSpent", mc.player.experienceLevel == levels[0], "levels are " + mc.player.experienceLevel + ", expected " + levels[0]);
            check("repairDiamondsLeft", menu(mc).getSlot(RIGHT).getItem().getCount() == 60, "diamond slot holds " + menu(mc).getSlot(RIGHT).getItem());
            check("repairDone", menu(mc).getSlot(mainInventory(0)).getItem().getDamageValue() == 0, "taken sword is " + menu(mc).getSlot(mainInventory(0)).getItem());
        });
    }
}
