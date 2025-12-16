package io.cardboardmc.internal;

import io.papermc.paper.plugin.lifecycle.event.PaperLifecycleEventManager;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginBase;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

public final class InternalCardboardPlugin extends PluginBase {

    private final Server server;
    private final InternalCardboardPluginMeta meta;
    private final File dataFolder;
    private final Logger logger;
    private final FileConfiguration config = new YamlConfiguration();
    private final PaperLifecycleEventManager<org.bukkit.plugin.Plugin> lifecycleManager;

    public InternalCardboardPlugin(final Server server, final String name, final String version) {
        this.server = server;
        this.meta = new InternalCardboardPluginMeta(name, version);
        this.dataFolder = new File(server.getPluginsFolder(), name);
        this.logger = Logger.getLogger(name);
        this.lifecycleManager = new PaperLifecycleEventManager<>(this, () -> true);
    }

    @Override
    public @NotNull File getDataFolder() {
        return this.dataFolder;
    }

    @Override
    public @NotNull PluginDescriptionFile getDescription() {
        return new PluginDescriptionFile(this.meta.getName(), this.meta.getVersion(), this.meta.getMainClass());
    }

    @Override
    public @NotNull io.papermc.paper.plugin.configuration.PluginMeta getPluginMeta() {
        return this.meta;
    }

    @Override
    public @NotNull FileConfiguration getConfig() {
        return this.config;
    }

    @Override
    public @Nullable InputStream getResource(@NotNull String filename) {
        return null;
    }

    @Override
    public void saveConfig() {
    }

    @Override
    public void saveDefaultConfig() {
    }

    @Override
    public void saveResource(@NotNull String resourcePath, boolean replace) {
    }

    @Override
    public void reloadConfig() {
    }

    @Override
    public @NotNull PluginLoader getPluginLoader() {
        return new io.papermc.paper.plugin.manager.DummyBukkitPluginLoader();
    }

    @Override
    public @NotNull Server getServer() {
        return this.server;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onLoad() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public boolean isNaggable() {
        return false;
    }

    @Override
    public void setNaggable(boolean canNag) {
    }

    @Override
    public @Nullable org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return null;
    }

    @Override
    public @Nullable org.bukkit.generator.BiomeProvider getDefaultBiomeProvider(@NotNull String worldName, @Nullable String id) {
        return null;
    }

    @Override
    public @NotNull Logger getLogger() {
        return this.logger;
    }

    @Override
    public @NotNull io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager<org.bukkit.plugin.Plugin> getLifecycleManager() {
        return this.lifecycleManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String label, @NotNull String[] args) {
        return false;
    }

    @Override
    public @NotNull java.util.List<String> onTabComplete(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String alias, @NotNull String[] args) {
        return java.util.List.of();
    }
}
