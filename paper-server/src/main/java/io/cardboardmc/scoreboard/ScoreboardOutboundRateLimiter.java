package io.cardboardmc.scoreboard;

import com.mojang.logging.LogUtils;
import io.papermc.paper.configuration.GlobalConfiguration;
import net.minecraft.network.protocol.Packet;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ScoreboardOutboundRateLimiter {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int HARD_MAX_QUEUE_SIZE = 10_000;

    private final Deque<Packet<?>> queue = new ArrayDeque<>();

    private Packet<?> lastQueued;

    private long lastFlushTick = -1;
    private long lastDebugLogMs;

    private long queued;
    private long deduped;
    private long overflowDropped;
    private long flushed;

    public boolean enqueue(final Packet<?> packet) {
        final GlobalConfiguration.Scoreboards.OutboundRateLimiter config = config();
        if (config == null || !config.enabled) {
            return false;
        }

        synchronized (this.queue) {
            if (config.deduplicateConsecutivePackets && this.lastQueued != null && this.lastQueued.equals(packet)) {
                this.deduped++;
                return true;
            }

            if (this.queue.size() >= HARD_MAX_QUEUE_SIZE) {
                this.overflowDropped++;
                return true;
            }

            this.queue.addLast(packet);
            this.lastQueued = packet;
            this.queued++;
            return true;
        }
    }

    public void tickAndFlush(final long currentTick, final PacketSender sender) {
        final GlobalConfiguration.Scoreboards.OutboundRateLimiter config = config();
        if (config == null || !config.enabled) {
            this.clear();
            return;
        }

        final int flushInterval = Math.max(1, config.flushIntervalTicks);
        if (this.lastFlushTick != -1 && (currentTick - this.lastFlushTick) < flushInterval) {
            this.maybeLog(config);
            return;
        }
        this.lastFlushTick = currentTick;

        final int budget = Math.max(1, config.maxPacketsPerTick);
        int sent = 0;
        while (sent < budget) {
            final Packet<?> packet;
            synchronized (this.queue) {
                packet = this.queue.pollFirst();
            }
            if (packet == null) {
                break;
            }
            sender.send(packet);
            sent++;
            this.flushed++;
        }

        this.maybeLog(config);
    }

    private void maybeLog(final GlobalConfiguration.Scoreboards.OutboundRateLimiter config) {
        if (!config.debugLogging) {
            return;
        }

        final long intervalMs = Math.max(1, config.debugLoggingIntervalSeconds) * 1000L;
        final long now = System.currentTimeMillis();
        if (now - this.lastDebugLogMs < intervalMs) {
            return;
        }
        this.lastDebugLogMs = now;

        final int depth;
        synchronized (this.queue) {
            depth = this.queue.size();
        }

        LOGGER.info("[CardboardMC] Scoreboard outbound limiter stats: queued={}, flushed={}, deduped={}, overflowDropped={}, queueDepth={}",
            this.queued, this.flushed, this.deduped, this.overflowDropped, depth);
    }

    private void clear() {
        synchronized (this.queue) {
            this.queue.clear();
        }
        this.lastQueued = null;
    }

    private static GlobalConfiguration.Scoreboards.OutboundRateLimiter config() {
        final GlobalConfiguration config = GlobalConfiguration.get();
        if (config == null || config.scoreboards == null) {
            return null;
        }
        return config.scoreboards.outboundRateLimiter;
    }

    @FunctionalInterface
    public interface PacketSender {
        void send(Packet<?> packet);
    }
}
