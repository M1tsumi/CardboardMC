package io.cardboardmc.command;

import io.cardboardmc.api.CardboardMinigameService;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static net.kyori.adventure.text.Component.text;

public final class CardboardCommand extends Command {

    private static final String BASE_PERMISSION = "cardboard.command";
    private static final String UNLOAD_CHUNKS_PERMISSION = "cardboard.command.unloadchunks";
    private static final String CLEANUP_ENTITIES_PERMISSION = "cardboard.command.cleanupentities";
    private static final String BATCH_TELEPORT_PERMISSION = "cardboard.command.batchteleport";
    private static final String TELEPORT_QUEUE_PERMISSION = "cardboard.command.teleportqueue";

    private static final AtomicBoolean BATCH_TELEPORT_RUNNING = new AtomicBoolean(false);
    private static final AtomicReference<UUID> LAST_BATCH_TELEPORT_ID = new AtomicReference<>(null);

    public CardboardCommand(final String name) {
        super(name);
        this.description = "CardboardMC related commands";
        this.usageMessage = "/cardboard unloadchunks <world> [radius] [safe] | cleanupentities <world> [all|items|projectiles|stands|display|xp] | batchteleport <world> <x> <y> <z> [batchSize] [intervalTicks] | batchteleportcancel | teleportqueue";
        this.setPermission(String.join(";", BASE_PERMISSION, UNLOAD_CHUNKS_PERMISSION, CLEANUP_ENTITIES_PERMISSION, BATCH_TELEPORT_PERMISSION, TELEPORT_QUEUE_PERMISSION));

        final var pluginManager = Bukkit.getServer().getPluginManager();
        pluginManager.addPermission(new Permission(BASE_PERMISSION, PermissionDefault.OP));
        pluginManager.addPermission(new Permission(UNLOAD_CHUNKS_PERMISSION, PermissionDefault.OP));
        pluginManager.addPermission(new Permission(CLEANUP_ENTITIES_PERMISSION, PermissionDefault.OP));
        pluginManager.addPermission(new Permission(BATCH_TELEPORT_PERMISSION, PermissionDefault.OP));
        pluginManager.addPermission(new Permission(TELEPORT_QUEUE_PERMISSION, PermissionDefault.OP));
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
        if (sub.equals("teleportqueue")) {
            return this.executeTeleportQueue(sender);
        }

        sender.sendMessage(text("Usage: " + this.usageMessage, NamedTextColor.RED));
        return false;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String alias, final String[] args, final Location location) throws IllegalArgumentException {
        if (args.length == 1) {
            return io.papermc.paper.command.CommandUtil.getListMatchingLast(sender, args, List.of("unloadchunks", "cleanupentities", "batchteleport", "batchteleportcancel", "teleportqueue"));
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

        final CardboardMinigameService service = CardboardMinigameService.get();
        if (service == null) {
            sender.sendMessage(text("[CardboardMC] Minigame service unavailable.", NamedTextColor.RED));
            return true;
        }

        final int requested = service.unloadChunks(world, radius, safe);

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

        final String modeStr = (args.length >= 2 ? args[1] : "all").toLowerCase(Locale.ROOT);
        final CardboardMinigameService.CleanupMode mode = switch (modeStr) {
            case "items" -> CardboardMinigameService.CleanupMode.ITEMS;
            case "projectiles" -> CardboardMinigameService.CleanupMode.PROJECTILES;
            case "stands" -> CardboardMinigameService.CleanupMode.STANDS;
            case "display" -> CardboardMinigameService.CleanupMode.DISPLAY;
            case "xp" -> CardboardMinigameService.CleanupMode.XP;
            case "all" -> CardboardMinigameService.CleanupMode.ALL;
            default -> null;
        };
        if (mode == null) {
            sender.sendMessage(text("Unknown cleanup mode: " + modeStr, NamedTextColor.RED));
            return true;
        }

        final CardboardMinigameService service = CardboardMinigameService.get();
        if (service == null) {
            sender.sendMessage(text("[CardboardMC] Minigame service unavailable.", NamedTextColor.RED));
            return true;
        }

        final int removed = service.cleanupEntities(world, mode);

        sender.sendMessage(text()
            .append(text("[CardboardMC] ", NamedTextColor.GOLD))
            .append(text("Removed ", NamedTextColor.GRAY))
            .append(text(String.valueOf(removed), NamedTextColor.YELLOW))
            .append(text(" entities (mode=", NamedTextColor.GRAY))
            .append(text(modeStr, NamedTextColor.YELLOW))
            .append(text(") in world ", NamedTextColor.GRAY))
            .append(text(world.getName(), NamedTextColor.YELLOW))
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

        final UUID batchId = LAST_BATCH_TELEPORT_ID.getAndSet(null);
        final CardboardMinigameService service = CardboardMinigameService.get();
        if (batchId != null && service != null && service.cancelQueuedTeleport(batchId)) {
            BATCH_TELEPORT_RUNNING.set(false);
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

        final CardboardMinigameService service = CardboardMinigameService.get();
        if (service == null) {
            BATCH_TELEPORT_RUNNING.set(false);
            sender.sendMessage(text().append(text("[CardboardMC] ", NamedTextColor.GOLD)).append(text("Minigame service unavailable.", NamedTextColor.RED)).build());
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

        final UUID batchId = service.queueTeleport(players, new Location(world, x, y, z), PlayerTeleportEvent.TeleportCause.COMMAND, batchSize, intervalTicks);
        LAST_BATCH_TELEPORT_ID.set(batchId);

        return true;
    }

    private boolean executeTeleportQueue(final CommandSender sender) {
        if (!sender.hasPermission(TELEPORT_QUEUE_PERMISSION)) {
            sender.sendMessage(Bukkit.permissionMessage());
            return true;
        }

        final CardboardMinigameService service = CardboardMinigameService.get();
        if (service == null) {
            sender.sendMessage(text().append(text("[CardboardMC] ", NamedTextColor.GOLD)).append(text("Minigame service unavailable.", NamedTextColor.RED)).build());
            return true;
        }

        sender.sendMessage(text()
            .append(text("[CardboardMC] ", NamedTextColor.GOLD))
            .append(text("Teleport queue: ", NamedTextColor.GRAY))
            .append(text("batches=", NamedTextColor.GRAY)).append(text(String.valueOf(service.getTeleportQueueActiveBatches()), NamedTextColor.YELLOW))
            .append(text(", queuedPlayers=", NamedTextColor.GRAY)).append(text(String.valueOf(service.getTeleportQueueTotalQueuedPlayers()), NamedTextColor.YELLOW))
            .append(text(", lastTickTeleported=", NamedTextColor.GRAY)).append(text(String.valueOf(service.getTeleportQueueLastTickTeleported()), NamedTextColor.YELLOW))
            .build()
        );
        return true;
    }
}
