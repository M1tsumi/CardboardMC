package io.cardboardmc.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CardboardMinigameServiceImpl implements CardboardMinigameService {

    private record TeleportBatch(
        UUID id,
        Deque<UUID> players,
        Location destination,
        PlayerTeleportEvent.TeleportCause cause,
        int batchSize,
        int intervalTicks,
        boolean prewarm,
        Deque<Long> prewarmChunks
    ) {
    }

    private final Map<UUID, TeleportBatch> batches = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> tickCountdown = new ConcurrentHashMap<>();

    private volatile int lastTickTeleported;

    public void tick() {
        final int currentTick = MinecraftServer.currentTick;
        int teleportedThisTick = 0;
        for (final TeleportBatch batch : this.batches.values()) {
            if (batch.prewarm()) {
                int prewarmed = 0;
                while (prewarmed < 4) {
                    final Long packed;
                    synchronized (batch.prewarmChunks()) {
                        packed = batch.prewarmChunks().pollFirst();
                    }
                    if (packed == null) {
                        break;
                    }

                    final int cx = (int) (packed >> 32);
                    final int cz = (int) (packed.longValue());
                    final World bworld = batch.destination().getWorld();
                    if (bworld != null) {
                        bworld.getChunkAt(cx, cz);
                    }
                    prewarmed++;
                }

                synchronized (batch.prewarmChunks()) {
                    if (!batch.prewarmChunks().isEmpty()) {
                        continue;
                    }
                }
            }

            final int remaining = this.tickCountdown.getOrDefault(batch.id(), 0);
            if (remaining > 0) {
                this.tickCountdown.put(batch.id(), remaining - 1);
                continue;
            }

            int sent = 0;
            while (sent < batch.batchSize()) {
                final UUID playerId;
                synchronized (batch.players()) {
                    playerId = batch.players().pollFirst();
                }
                if (playerId == null) {
                    this.batches.remove(batch.id());
                    this.tickCountdown.remove(batch.id());
                    break;
                }

                final Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.teleport(batch.destination(), batch.cause());
                    teleportedThisTick++;
                }
                sent++;
            }

            if (this.batches.containsKey(batch.id())) {
                this.tickCountdown.put(batch.id(), Math.max(1, batch.intervalTicks()) - 1);
            }
        }

        this.lastTickTeleported = teleportedThisTick;
    }

    @Override
    public @NotNull UUID queueTeleport(@NotNull Collection<? extends Player> players, @NotNull Location destination, @NotNull PlayerTeleportEvent.TeleportCause cause, int batchSize, int intervalTicks) {
        final UUID id = UUID.randomUUID();
        final Deque<UUID> queue = new ArrayDeque<>();
        for (final Player p : players) {
            queue.addLast(p.getUniqueId());
        }

        final int safeBatchSize = Math.max(1, batchSize);
        final int safeIntervalTicks = Math.max(1, intervalTicks);

        final Location destClone = destination.clone();

        final boolean prewarm = true;
        final Deque<Long> prewarmChunks = new ArrayDeque<>();
        if (prewarm) {
            final World world = destClone.getWorld();
            if (world != null) {
                final int baseX = destClone.getBlockX() >> 4;
                final int baseZ = destClone.getBlockZ() >> 4;
                final int radius = 1;
                final Set<Long> seen = new HashSet<>();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        final int cx = baseX + dx;
                        final int cz = baseZ + dz;
                        final long packed = (((long) cx) << 32) | (cz & 0xffffffffL);
                        if (seen.add(packed)) {
                            prewarmChunks.addLast(packed);
                        }
                    }
                }
            }
        }

        this.batches.put(id, new TeleportBatch(id, queue, destClone, cause, safeBatchSize, safeIntervalTicks, prewarm, prewarmChunks));
        this.tickCountdown.put(id, 0);
        return id;
    }

    @Override
    public boolean cancelQueuedTeleport(@NotNull UUID batchId) {
        return this.batches.remove(batchId) != null;
    }

    @Override
    public int getQueuedTeleportQueueSize(@NotNull UUID batchId) {
        final TeleportBatch batch = this.batches.get(batchId);
        if (batch == null) {
            return 0;
        }
        synchronized (batch.players()) {
            return batch.players().size();
        }
    }

    @Override
    public int getTeleportQueueActiveBatches() {
        return this.batches.size();
    }

    @Override
    public int getTeleportQueueTotalQueuedPlayers() {
        int total = 0;
        for (final TeleportBatch batch : this.batches.values()) {
            synchronized (batch.players()) {
                total += batch.players().size();
            }
        }
        return total;
    }

    @Override
    public int getTeleportQueueLastTickTeleported() {
        return this.lastTickTeleported;
    }

    @Override
    public int unloadChunks(@NotNull World world, int radius, boolean safe) {
        final CraftWorld craftWorld = (CraftWorld) world;
        final ServerLevel level = craftWorld.getHandle();

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

        int requested = 0;
        for (final Chunk chunk : loaded) {
            if (radius >= 0) {
                final int dx = Math.abs(chunk.getX() - centerX);
                final int dz = Math.abs(chunk.getZ() - centerZ);
                if (dx <= radius && dz <= radius) {
                    continue;
                }
            }

            if (!world.isChunkLoaded(chunk.getX(), chunk.getZ())) {
                continue;
            }

            if (!safe) {
                final LevelChunk nmsChunk = level.getChunk(chunk.getX(), chunk.getZ());
                nmsChunk.tryMarkSaved();
            }

            level.getChunkSource().removeTicketWithRadius(TicketType.PLUGIN, new ChunkPos(chunk.getX(), chunk.getZ()), 1);
            requested++;
        }

        level.getChunkSource().purgeUnload();
        return requested;
    }

    @Override
    public int cleanupEntities(@NotNull World world, @NotNull CleanupMode mode) {
        int removed = 0;
        for (final Entity entity : world.getEntities()) {
            final boolean match = switch (mode) {
                case ITEMS -> entity instanceof Item;
                case PROJECTILES -> entity instanceof Projectile;
                case STANDS -> entity instanceof ArmorStand;
                case DISPLAY -> entity instanceof Display;
                case XP -> entity instanceof ExperienceOrb;
                case ALL -> (entity instanceof Item)
                    || (entity instanceof Projectile)
                    || (entity instanceof ArmorStand)
                    || (entity instanceof Display)
                    || (entity instanceof ExperienceOrb);
            };

            if (!match) {
                continue;
            }

            entity.remove();
            removed++;
        }
        return removed;
    }
}
