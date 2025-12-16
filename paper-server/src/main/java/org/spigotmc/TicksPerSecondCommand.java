package org.spigotmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.DecimalFormat;

import static net.kyori.adventure.text.Component.text;

public class TicksPerSecondCommand extends Command {

    private boolean hasShownMemoryWarning; // Paper
    private final Map<String, Long> lastGcCollectionCounts = new HashMap<>();
    private final Map<String, Long> lastGcCollectionTimesMs = new HashMap<>();
    private long lastGcQueryTimeMs;
    private static final ThreadLocal<DecimalFormat> ONE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("########0.0");
    });

    public TicksPerSecondCommand(String name) {
        super(name);
        this.description = "Gets the current ticks per second for the server";
        this.usageMessage = "/tps [mem|entities|chunks|gc]";
        this.setPermission("bukkit.command.tps");
    }

    // Paper start
    private static final Component WARN_MSG = text()
        .append(text("Warning: ", NamedTextColor.RED))
        .append(text("Memory usage on modern garbage collectors is not a stable value and it is perfectly normal to see it reach max. Please do not pay it much attention.", NamedTextColor.GOLD))
        .build();
    // Paper end

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        // Paper start - Further improve tick handling
        double[] tps = org.bukkit.Bukkit.getTPS();
        Component[] tpsAvg = new Component[tps.length];

        for (int i = 0; i < tps.length; i++) {
            tpsAvg[i] = TicksPerSecondCommand.format(tps[i]);
        }

        TextComponent.Builder builder = text();
        builder.append(text("TPS from last 1m, 5m, 15m: ", NamedTextColor.GOLD));
        builder.append(Component.join(JoinConfiguration.commas(true), tpsAvg));
        sender.sendMessage(builder.asComponent());

        if (args.length > 0) {
            final String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "mem" -> {
                    if (!sender.hasPermission("bukkit.command.tpsmemory")) {
                        return true;
                    }
                    sender.sendMessage(text()
                        .append(text("Current Memory Usage: ", NamedTextColor.GOLD))
                        .append(text(((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)) + "/" + (Runtime.getRuntime().totalMemory() / (1024 * 1024)) + " mb (Max: " + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " mb)", NamedTextColor.GREEN))
                    );
                    if (!this.hasShownMemoryWarning) {
                        sender.sendMessage(WARN_MSG);
                        this.hasShownMemoryWarning = true;
                    }
                }
                case "entities" -> {
                    if (!sender.hasPermission("bukkit.command.tpsentities")) {
                        return true;
                    }
                    this.sendEntities(sender);
                }
                case "chunks" -> {
                    if (!sender.hasPermission("bukkit.command.tpschunks")) {
                        return true;
                    }
                    this.sendChunks(sender);
                }
                case "gc" -> {
                    if (!sender.hasPermission("bukkit.command.tpsgc")) {
                        return true;
                    }
                    this.sendGc(sender);
                }
                default -> {
                }
            }
        }
        // Paper end

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        if (args.length != 1) {
            return Collections.emptyList();
        }

        final String prefix = args[0].toLowerCase(Locale.ROOT);
        final List<String> completions = new ArrayList<>(4);

        if ("mem".startsWith(prefix) && sender.hasPermission("bukkit.command.tpsmemory")) {
            completions.add("mem");
        }
        if ("entities".startsWith(prefix) && sender.hasPermission("bukkit.command.tpsentities")) {
            completions.add("entities");
        }
        if ("chunks".startsWith(prefix) && sender.hasPermission("bukkit.command.tpschunks")) {
            completions.add("chunks");
        }
        if ("gc".startsWith(prefix) && sender.hasPermission("bukkit.command.tpsgc")) {
            completions.add("gc");
        }

        Collections.sort(completions);
        return completions;
    }

    private void sendEntities(final CommandSender sender) {
        int totalEntities = 0;
        int totalTileEntities = 0;
        int totalTickableTileEntities = 0;
        int totalPlayers = 0;

        for (final World world : Bukkit.getWorlds()) {
            totalEntities += world.getEntityCount();
            totalTileEntities += world.getTileEntityCount();
            totalTickableTileEntities += world.getTickableTileEntityCount();
            totalPlayers += world.getPlayerCount();
        }

        sender.sendMessage(text()
            .append(text("Entities: ", NamedTextColor.GOLD))
            .append(text(String.valueOf(totalEntities), NamedTextColor.GREEN))
            .append(text(" | Block Entities: ", NamedTextColor.GOLD))
            .append(text(String.valueOf(totalTileEntities), NamedTextColor.GREEN))
            .append(text(" | Tickable Block Entities: ", NamedTextColor.GOLD))
            .append(text(String.valueOf(totalTickableTileEntities), NamedTextColor.GREEN))
            .append(text(" | Players: ", NamedTextColor.GOLD))
            .append(text(String.valueOf(totalPlayers), NamedTextColor.GREEN))
        );

        for (final World world : Bukkit.getWorlds()) {
            sender.sendMessage(text()
                .append(text("- ", NamedTextColor.DARK_GRAY))
                .append(text(world.getName(), NamedTextColor.YELLOW))
                .append(text(": ", NamedTextColor.DARK_GRAY))
                .append(text(String.valueOf(world.getEntityCount()), NamedTextColor.GREEN))
                .append(text(" entities", NamedTextColor.GRAY))
                .append(text(", ", NamedTextColor.DARK_GRAY))
                .append(text(String.valueOf(world.getTileEntityCount()), NamedTextColor.GREEN))
                .append(text(" block entities", NamedTextColor.GRAY))
                .append(text(", ", NamedTextColor.DARK_GRAY))
                .append(text(String.valueOf(world.getTickableTileEntityCount()), NamedTextColor.GREEN))
                .append(text(" tickable", NamedTextColor.GRAY))
                .append(text(", ", NamedTextColor.DARK_GRAY))
                .append(text(String.valueOf(world.getPlayerCount()), NamedTextColor.GREEN))
                .append(text(" players", NamedTextColor.GRAY))
            );
        }
    }

    private void sendChunks(final CommandSender sender) {
        int totalChunks = 0;
        for (final World world : Bukkit.getWorlds()) {
            totalChunks += world.getChunkCount();
        }

        sender.sendMessage(text()
            .append(text("Loaded Chunks: ", NamedTextColor.GOLD))
            .append(text(String.valueOf(totalChunks), NamedTextColor.GREEN))
        );

        for (final World world : Bukkit.getWorlds()) {
            sender.sendMessage(text()
                .append(text("- ", NamedTextColor.DARK_GRAY))
                .append(text(world.getName(), NamedTextColor.YELLOW))
                .append(text(": ", NamedTextColor.DARK_GRAY))
                .append(text(String.valueOf(world.getChunkCount()), NamedTextColor.GREEN))
                .append(text(" chunks", NamedTextColor.GRAY))
            );
        }
    }

    private void sendGc(final CommandSender sender) {
        long totalCollections = 0L;
        long totalCollectionTimeMs = 0L;

        long deltaCollections = 0L;
        long deltaCollectionTimeMs = 0L;

        for (final GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            final String name = bean.getName();

            final long collectionCount = bean.getCollectionCount();
            final long collectionTime = bean.getCollectionTime();

            if (collectionCount >= 0L) {
                totalCollections += collectionCount;
            }
            if (collectionTime >= 0L) {
                totalCollectionTimeMs += collectionTime;
            }

            final long lastCount = this.lastGcCollectionCounts.getOrDefault(name, collectionCount);
            final long lastTime = this.lastGcCollectionTimesMs.getOrDefault(name, collectionTime);
            if (collectionCount >= 0L && lastCount >= 0L) {
                deltaCollections += Math.max(0L, collectionCount - lastCount);
            }
            if (collectionTime >= 0L && lastTime >= 0L) {
                deltaCollectionTimeMs += Math.max(0L, collectionTime - lastTime);
            }

            this.lastGcCollectionCounts.put(name, collectionCount);
            this.lastGcCollectionTimesMs.put(name, collectionTime);
        }

        final long nowMs = System.currentTimeMillis();
        final long deltaWindowMs = this.lastGcQueryTimeMs == 0L ? 0L : Math.max(0L, nowMs - this.lastGcQueryTimeMs);
        this.lastGcQueryTimeMs = nowMs;

        sender.sendMessage(text()
            .append(text("GC: ", NamedTextColor.GOLD))
            .append(text(totalCollections + " collections", NamedTextColor.GREEN))
            .append(text(" (", NamedTextColor.DARK_GRAY))
            .append(text(totalCollectionTimeMs + "ms", NamedTextColor.GREEN))
            .append(text(")", NamedTextColor.DARK_GRAY))
        );

        sender.sendMessage(text()
            .append(text("Since last /tps gc", NamedTextColor.GOLD))
            .append(text(deltaWindowMs > 0L ? " (" + deltaWindowMs + "ms)" : "", NamedTextColor.DARK_GRAY))
            .append(text(": ", NamedTextColor.DARK_GRAY))
            .append(text("+" + deltaCollections, NamedTextColor.GREEN))
            .append(text(" collections", NamedTextColor.GRAY))
            .append(text(", ", NamedTextColor.DARK_GRAY))
            .append(text("+" + deltaCollectionTimeMs, NamedTextColor.GREEN))
            .append(text("ms", NamedTextColor.GRAY))
        );
    }

    private static Component format(double tps) { // Paper - Made static
        // Paper start
        TextColor color = ((tps > 18.0) ? NamedTextColor.GREEN : (tps > 16.0) ? NamedTextColor.YELLOW : NamedTextColor.RED);
        String amount = ONE_DECIMAL_PLACES.get().format(tps); // Paper - only print * at 21, we commonly peak to 20.02 as the tick sleep is not accurate enough, stop the noise
        return text(amount, color);
        // Paper end
    }
}
