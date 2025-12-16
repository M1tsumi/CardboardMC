package io.cardboardmc.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

public interface CardboardMinigameService {

    enum CleanupMode {
        ALL,
        ITEMS,
        PROJECTILES,
        STANDS,
        DISPLAY,
        XP
    }

    @NotNull
    UUID queueTeleport(
        @NotNull Collection<? extends Player> players,
        @NotNull Location destination,
        @NotNull PlayerTeleportEvent.TeleportCause cause,
        int batchSize,
        int intervalTicks
    );

    boolean cancelQueuedTeleport(@NotNull UUID batchId);

    int getQueuedTeleportQueueSize(@NotNull UUID batchId);

    int getTeleportQueueActiveBatches();

    int getTeleportQueueTotalQueuedPlayers();

    int getTeleportQueueLastTickTeleported();

    int unloadChunks(@NotNull World world, int radius, boolean safe);

    int cleanupEntities(@NotNull World world, @NotNull CleanupMode mode);

    @Nullable
    static CardboardMinigameService get() {
        final RegisteredServiceProvider<CardboardMinigameService> reg = org.bukkit.Bukkit.getServicesManager().getRegistration(CardboardMinigameService.class);
        return reg == null ? null : reg.getProvider();
    }
}
