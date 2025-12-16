package io.cardboardmc.command;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.kyori.adventure.text.Component.text;

public final class CardboardCommand extends Command {

    private static final String BASE_PERMISSION = "cardboard.command";
    private static final String UNLOAD_CHUNKS_PERMISSION = "cardboard.command.unloadchunks";
    private static final String CLEANUP_ENTITIES_PERMISSION = "cardboard.command.cleanupentities";
    private static final String BATCH_TELEPORT_PERMISSION = "cardboard.command.batchteleport";

    private static final AtomicBoolean BATCH_TELEPORT_RUNNING = new AtomicBoolean(false);

    public CardboardCommand(final String name) {
        super(name);
        this.description = "CardboardMC related commands";
        this.usageMessage = "/cardboard unloadchunks <world> [radius] [safe] | cleanupentities <world> [all|items|projectiles|stands|display|xp] | batchteleport <world> <x> <y> <z> [batchSize] [intervalTicks] | batchteleportcancel";
        this.setPermission(String.join(";", BASE_PERMISSION, UNLOAD_CHUNKS_PERMISSION, CLEANUP_ENTITIES_PERMISSION, BATCH_TELEPORT_PERMISSION));

        final var pluginManager = Bukkit.getServer().getPluginManager();
        pluginManager.addPermission(new Permission(BASE_PERMISSION, PermissionDefault.OP));
        pluginManager.addPermission(new Permission(UNLOAD_CHUNKS_PERMISSION, PermissionDefault.OP));
        pluginManager.addPermission(new Permission(CLEANUP_ENTITIES_PERMISSION, PermissionDefault.OP));
        pluginManager.addPermission(new Permission(BATCH_TELEPORT_PERMISSION, PermissionDefault.OP));
    }

    @Override
    public boolean execute(final CommandSender sender, final String commandLabel, final String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(text("Usage: " + this.usageMessage, NamedTextColor.RED));
            return false;
        }

        final String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("unloadchunks")) {
            return this.executeUnloadChunks(sender, commandLabel, Arrays.copyOfRange(args, 1, args.length));
        }
        if (sub.equals("cleanupentities")) {
            return this.executeCleanupEntities(sender, commandLabel, Arrays.copyOfRange(args, 1, args.length));
        }
        if (sub.equals("batchteleport")) {
            return this.executeBatchTeleport(sender, commandLabel, Arrays.copyOfRange(args, 1, args.length));
        }
        if (sub.equals("batchteleportcancel")) {
            return this.executeBatchTeleportCancel(sender);
        }

        sender.sendMessage(text("Usage: " + this.usageMessage, NamedTextColor.RED));
        return false;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String alias, final String[] args, final Location location) throws IllegalArgumentException {
        if (args.length == 1) {
            return io.papermc.paper.command.CommandUtil.getListMatchingLast(sender, args, List.of("unloadchunks", "cleanupentities", "batchteleport", "batchteleportcancel"));
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("unloadchunks")) {
            if (args.length == 2) {
                final List<String> worlds = new ArrayList<>();
                for (final World world : Bukkit.getWorlds()) {
                    worlds.add(world.getName());
                }
                return io.papermc.paper.command.CommandUtil.getListMatchingLast(sender, args, worlds);
            }
            if (args.length == 4) {
                return io.papermc.paper.command.CommandUtil.getListMatchingLast(sender, args, List.of("true", "false"));
            }
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("cleanupentities")) {
            if (args.length == 2) {
                final List<String> worlds = new ArrayList<>();
                for (final World world : Bukkit.getWorlds()) {
                    worlds.add(world.getName());
                }
                return io.papermc.paper.command.CommandUtil.getListMatchingLast(sender, args, worlds);
            }
            if (args.length == 3) {
                return io.papermc.paper.command.CommandUtil.getListMatchingLast(sender, args, List.of("all", "items", "projectiles", "stands", "display", "xp"));
            }
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("batchteleport")) {
            if (args.length == 2) {
                final List<String> worlds = new ArrayList<>();
                for (final World world : Bukkit.getWorlds()) {
                    worlds.add(world.getName());
                }
                return io.papermc.paper.command.CommandUtil.getListMatchingLast(sender, args, worlds);
            }
        }

        return Collections.emptyList();
    }

    private boolean executeUnloadChunks(final CommandSender sender, final String commandLabel, final String[] args) {
        if (!sender.hasPermission(UNLOAD_CHUNKS_PERMISSION)) {
            sender.sendMessage(Bukkit.permissionMessage());
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(text("Usage: /" + commandLabel + " unloadchunks <world> [radius] [safe]", NamedTextColor.RED));
            return false;
        }

        final World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            sender.sendMessage(text("Unknown world: " + args[0], NamedTextColor.RED));
            return true;
        }

        final CraftWorld craftWorld = (CraftWorld) world;
        final ServerLevel level = craftWorld.getHandle();

        final int radius;
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (final NumberFormatException ex) {
                sender.sendMessage(text("Invalid radius: " + args[1], NamedTextColor.RED));
                return true;
            }
        } else {
            radius = -1;
        }

        final boolean safe;
        if (args.length >= 3) {
            safe = Boolean.parseBoolean(args[2]);
        } else {
            safe = true;
        }

        // Best practice: operate only on already-loaded chunks to avoid chunk loads (and extra memory churn).
        final Chunk[] loaded = world.getLoadedChunks();

        final int centerX;
        final int centerZ;
        if (radius >= 0) {
            final Location center = world.getSpawnLocation();
            centerX = center.getBlockX() >> 4;
            centerZ = center.getBlockZ() >> 4;
        } else {
            centerX = 0;
            centerZ = 0;
        }

        final Deque<Chunk> queue = new ArrayDeque<>(loaded.length);
        int skipped = 0;
        for (final Chunk chunk : loaded) {
            if (radius >= 0) {
                final int dx = Math.abs(chunk.getX() - centerX);
                final int dz = Math.abs(chunk.getZ() - centerZ);
                if (dx <= radius && dz <= radius) {
                    skipped++;
                    continue;
                }
            }
            queue.add(chunk);
        }

        final int totalToUnload = queue.size();
        sender.sendMessage(text()
            .append(text("[CardboardMC] ", NamedTextColor.GOLD))
            .append(text("Queued ", NamedTextColor.GRAY))
            .append(text(String.valueOf(totalToUnload), NamedTextColor.YELLOW))
            .append(text(" chunks for unloading in world ", NamedTextColor.GRAY))
            .append(text(world.getName(), NamedTextColor.YELLOW))
            .append(text(safe ? " (safe save)" : " (no save)", NamedTextColor.GRAY))
            .append(text(radius >= 0 ? ", keeping " + radius + "-chunk radius around spawn" : "", NamedTextColor.GRAY))
            .append(text(skipped > 0 ? " (skipped " + skipped + ")" : "", NamedTextColor.GRAY))
            .build()
        );

        // Best method available here: queue unloads (remove PLUGIN ticket) and then purge unloads once.
        // This avoids forcing chunk loads and avoids per-chunk purge costs.
        int requested = 0;
        while (!queue.isEmpty()) {
            final Chunk chunk = queue.poll();
            if (chunk == null) {
                break;
            }

            // Avoid forcing loads; only operate on still-loaded chunks.
            if (!world.isChunkLoaded(chunk.getX(), chunk.getZ())) {
                continue;
            }

            if (!safe) {
                final LevelChunk nmsChunk = level.getChunk(chunk.getX(), chunk.getZ());
                nmsChunk.tryMarkSaved();
            }

            // Same behavior as CraftWorld#unloadChunkRequest but avoids extra Bukkit lookups.
            level.getChunkSource().removeTicketWithRadius(TicketType.PLUGIN, new ChunkPos(chunk.getX(), chunk.getZ()), 1);
            requested++;
        }

        level.getChunkSource().purgeUnload();

        sender.sendMessage(text()
            .append(text("[CardboardMC] ", NamedTextColor.GOLD))
            .append(text("Requested unload for ", NamedTextColor.GRAY))
            .append(text(String.valueOf(requested), NamedTextColor.YELLOW))
            .append(text(" chunks in world ", NamedTextColor.GRAY))
            .append(text(world.getName(), NamedTextColor.YELLOW))
            .append(text(".", NamedTextColor.GRAY))
            .build()
        );

        return true;
    }

    private boolean executeCleanupEntities(final CommandSender sender, final String commandLabel, final String[] args) {
        if (!sender.hasPermission(CLEANUP_ENTITIES_PERMISSION)) {
            sender.sendMessage(Bukkit.permissionMessage());
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(text("Usage: /" + commandLabel + " cleanupentities <world> [all|items|projectiles|stands|display|xp]", NamedTextColor.RED));
            return false;
        }

        final World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            sender.sendMessage(text("Unknown world: " + args[0], NamedTextColor.RED));
            return true;
        }

        final String mode = (args.length >= 2 ? args[1] : "all").toLowerCase(Locale.ROOT);

        int removed = 0;
        int scanned = 0;

        for (final Entity entity : world.getEntities()) {
            scanned++;

            final boolean match = switch (mode) {
                case "items" -> entity instanceof Item;
                case "projectiles" -> entity instanceof Projectile;
                case "stands" -> entity instanceof ArmorStand;
                case "display" -> entity instanceof Display;
                case "xp" -> entity instanceof ExperienceOrb;
                case "all" -> (entity instanceof Item)
                    || (entity instanceof Projectile)
                    || (entity instanceof ArmorStand)
                    || (entity instanceof Display)
                    || (entity instanceof ExperienceOrb);
                default -> false;
            };

            if (!match) {
                continue;
            }

            entity.remove();
            removed++;
        }

        if (!mode.equals("all") && !mode.equals("items") && !mode.equals("projectiles") && !mode.equals("stands") && !mode.equals("display") && !mode.equals("xp")) {
            sender.sendMessage(text("Unknown cleanup mode: " + mode, NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(text()
            .append(text("[CardboardMC] ", NamedTextColor.GOLD))
            .append(text("Removed ", NamedTextColor.GRAY))
            .append(text(String.valueOf(removed), NamedTextColor.YELLOW))
            .append(text(" entities (mode=", NamedTextColor.GRAY))
            .append(text(mode, NamedTextColor.YELLOW))
            .append(text(") in world ", NamedTextColor.GRAY))
            .append(text(world.getName(), NamedTextColor.YELLOW))
            .append(text(". Scanned ", NamedTextColor.GRAY))
            .append(text(String.valueOf(scanned), NamedTextColor.YELLOW))
            .append(text(".", NamedTextColor.GRAY))
            .build()
        );

        return true;
    }

    private boolean executeBatchTeleportCancel(final CommandSender sender) {
        if (!sender.hasPermission(BATCH_TELEPORT_PERMISSION)) {
            sender.sendMessage(Bukkit.permissionMessage());
            return true;
        }

        if (BATCH_TELEPORT_RUNNING.compareAndSet(true, false)) {
            sender.sendMessage(text().append(text("[CardboardMC] ", NamedTextColor.GOLD)).append(text("Teleport batching cancelled.", NamedTextColor.GRAY)).build());
        } else {
            sender.sendMessage(text().append(text("[CardboardMC] ", NamedTextColor.GOLD)).append(text("No active teleport batching.", NamedTextColor.GRAY)).build());
        }
        return true;
    }

    private boolean executeBatchTeleport(final CommandSender sender, final String commandLabel, final String[] args) {
        if (!sender.hasPermission(BATCH_TELEPORT_PERMISSION)) {
            sender.sendMessage(Bukkit.permissionMessage());
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(text("Usage: /" + commandLabel + " batchteleport <world> <x> <y> <z> [batchSize] [intervalTicks]", NamedTextColor.RED));
            return false;
        }

        final World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            sender.sendMessage(text("Unknown world: " + args[0], NamedTextColor.RED));
            return true;
        }

        final double x;
        final double y;
        final double z;
        try {
            x = Double.parseDouble(args[1]);
            y = Double.parseDouble(args[2]);
            z = Double.parseDouble(args[3]);
        } catch (final NumberFormatException ex) {
            sender.sendMessage(text("Invalid coordinates.", NamedTextColor.RED));
            return true;
        }

        final int batchSize;
        if (args.length >= 5) {
            try {
                batchSize = Math.max(1, Integer.parseInt(args[4]));
            } catch (final NumberFormatException ex) {
                sender.sendMessage(text("Invalid batchSize: " + args[4], NamedTextColor.RED));
                return true;
            }
        } else {
            batchSize = 5;
        }

        final int intervalTicks;
        if (args.length >= 6) {
            try {
                intervalTicks = Math.max(1, Integer.parseInt(args[5]));
            } catch (final NumberFormatException ex) {
                sender.sendMessage(text("Invalid intervalTicks: " + args[5], NamedTextColor.RED));
                return true;
            }
        } else {
            intervalTicks = 1;
        }

        final List<Player> players = new ArrayList<>(world.getPlayers());
        if (players.isEmpty()) {
            sender.sendMessage(text().append(text("[CardboardMC] ", NamedTextColor.GOLD)).append(text("No players in world ", NamedTextColor.GRAY)).append(text(world.getName(), NamedTextColor.YELLOW)).append(text(".", NamedTextColor.GRAY)).build());
            return true;
        }

        if (!BATCH_TELEPORT_RUNNING.compareAndSet(false, true)) {
            sender.sendMessage(text().append(text("[CardboardMC] ", NamedTextColor.GOLD)).append(text("A teleport batch is already running. Use /" + commandLabel + " batchteleportcancel", NamedTextColor.RED)).build());
            return true;
        }

        sender.sendMessage(text()
            .append(text("[CardboardMC] ", NamedTextColor.GOLD))
            .append(text("Batch teleporting ", NamedTextColor.GRAY))
            .append(text(String.valueOf(players.size()), NamedTextColor.YELLOW))
            .append(text(" players in ", NamedTextColor.GRAY))
            .append(text(world.getName(), NamedTextColor.YELLOW))
            .append(text(" to ", NamedTextColor.GRAY))
            .append(text(x + "," + y + "," + z, NamedTextColor.YELLOW))
            .append(text(" (batchSize=", NamedTextColor.GRAY))
            .append(text(String.valueOf(batchSize), NamedTextColor.YELLOW))
            .append(text(", intervalTicks=", NamedTextColor.GRAY))
            .append(text(String.valueOf(intervalTicks), NamedTextColor.YELLOW))
            .append(text(")", NamedTextColor.GRAY))
            .build()
        );

        final MinecraftServer server = MinecraftServer.getServer();
        final long intervalMs = intervalTicks * 50L;

        final Thread worker = new Thread(() -> {
            int index = 0;
            try {
                while (BATCH_TELEPORT_RUNNING.get() && index < players.size()) {
                    final int start = index;
                    final int end = Math.min(players.size(), index + batchSize);
                    index = end;

                    server.execute(() -> {
                        for (int i = start; i < end; i++) {
                            final Player player = players.get(i);
                            if (!player.isOnline()) {
                                continue;
                            }
                            if (player.getWorld() != world) {
                                continue;
                            }
                            player.teleport(new Location(world, x, y, z));
                        }
                    });

                    if (index < players.size()) {
                        Thread.sleep(intervalMs);
                    }
                }
            } catch (final InterruptedException ignored) {
            } finally {
                BATCH_TELEPORT_RUNNING.set(false);
            }
        }, "Cardboard-BatchTeleport");
        worker.setDaemon(true);
        worker.start();

        return true;
    }
}
