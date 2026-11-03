package com.googlerooeric.anvilfix.selftest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server half of the in-game test (-Danvilfix.host=true). After the scenarios pass, the server stays up for the
 * client from {@link IngameClient}: it gives the joining player a known inventory and puts an anvil above them,
 * then shuts down once they leave. The client opens and uses the anvil over the network and checks what it sees.
 */
public final class IngameHost {

    /** Hotbar layout the client expects. */
    static final int RENAME_SWORD = 0, EXPENSIVE_SWORD = 1, EXPENSIVE_BOOK = 2, MENDING_SWORD = 3, UNBREAKING_BOOK = 4, DAMAGED_SWORD = 5, DIAMONDS = 6;
    static final int START_LEVELS = 200;
    static final int PORT = Integer.getInteger("anvilfix.port", 25599);

    private static final long TIMEOUT_MS = 10 * 60 * 1000;

    private final MinecraftServer server;
    private final int exitCode;
    private ServerPlayer player;
    private boolean done;

    private IngameHost(MinecraftServer server, int exitCode) {
        this.server = server;
        this.exitCode = exitCode;
    }

    public static boolean enabled() {
        return Boolean.getBoolean("anvilfix.host");
    }

    /** Keeps the server running until the test client has joined and left (or the timeout passes), then exits. */
    static void hostThenExit(MinecraftServer server, int exitCode) {
        IngameHost host = new IngameHost(server, exitCode);
        Thread watcher = new Thread(() -> {
            long start = System.currentTimeMillis();
            while (true) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    return;
                }
                if (System.currentTimeMillis() - start > TIMEOUT_MS) {
                    SelfTest.LOGGER.error("ANVILFIX_HOST no test client connected within the timeout");
                    System.exit(1);
                }
                server.execute(host::poll);
            }
        }, "AnvilFix in-game test host");
        watcher.setDaemon(true);
        watcher.start();
        SelfTest.LOGGER.info("ANVILFIX_HOST_READY waiting for the test client on port {}", server.getPort());
    }

    private void poll() {
        if (done) {
            return;
        }
        if (player == null) {
            if (!server.getPlayerList().getPlayers().isEmpty()) {
                player = server.getPlayerList().getPlayers().get(0);
                setUp(player);
            }
            return;
        }
        if (player.hasDisconnected()) {
            done = true;
            SelfTest.LOGGER.info("ANVILFIX_HOST test client left, shutting down");
            new Thread(() -> System.exit(exitCode), "AnvilFix self test exit").start();
        }
    }

    private void setUp(ServerPlayer player) {
        AnvilScenarios.Context ctx = new AnvilScenarios.Context(server);
        player.setGameMode(GameType.SURVIVAL);
        player.setExperienceLevels(START_LEVELS);
        Inventory inventory = player.getInventory();
        inventory.clearContent();

        inventory.setItem(RENAME_SWORD, new ItemStack(Items.DIAMOND_SWORD));
        inventory.setItem(EXPENSIVE_SWORD, new ItemStack(Items.DIAMOND_SWORD));
        inventory.setItem(EXPENSIVE_BOOK, TestItems.book(ctx, IngameHost.expensiveBook()));
        inventory.setItem(MENDING_SWORD, TestItems.enchanted(ctx, new ItemStack(Items.DIAMOND_SWORD), "minecraft:mending", 1));
        inventory.setItem(UNBREAKING_BOOK, TestItems.book(ctx, "minecraft:unbreaking", 3));
        ItemStack damaged = new ItemStack(Items.DIAMOND_SWORD);
        damaged.setDamageValue(damaged.getMaxDamage() - 1);
        inventory.setItem(DAMAGED_SWORD, damaged);
        inventory.setItem(DIAMONDS, new ItemStack(Items.DIAMOND, 64));

        // Within reach next to the player, for the client to open like a player would. Anvils fall, so give it a floor.
        BlockPos pos = player.blockPosition().east(2);
        TestItems.serverLevel(player).setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        TestItems.serverLevel(player).setBlockAndUpdate(pos, Blocks.ANVIL.defaultBlockState());
        SelfTest.LOGGER.info("ANVILFIX_HOST set up {} with an anvil at {}", player.getGameProfile().getName(), pos);
    }

    /** Sharpness V + Looting III + Fire Aspect II + Unbreaking III + Knockback II = 42 levels, past vanilla's 40 cap. */
    static Map<String, Integer> expensiveBook() {
        Map<String, Integer> enchants = new LinkedHashMap<>();
        enchants.put("minecraft:sharpness", 5);
        enchants.put("minecraft:looting", 3);
        enchants.put("minecraft:fire_aspect", 2);
        enchants.put("minecraft:unbreaking", 3);
        enchants.put("minecraft:knockback", 2);
        return enchants;
    }

}
