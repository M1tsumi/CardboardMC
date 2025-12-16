package io.cardboardmc.internal;

import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginLoadOrder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class InternalCardboardPluginMeta implements PluginMeta {

    private final String name;
    private final String version;

    public InternalCardboardPluginMeta(final String name, final String version) {
        this.name = name;
        this.version = version;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getMainClass() {
        return InternalCardboardPlugin.class.getName();
    }

    @Override
    public PluginLoadOrder getLoadOrder() {
        return PluginLoadOrder.STARTUP;
    }

    @Override
    public String getVersion() {
        return this.version;
    }

    @Override
    public @Nullable String getLoggerPrefix() {
        return this.name;
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of();
    }

    @Override
    public List<String> getPluginSoftDependencies() {
        return List.of();
    }

    @Override
    public List<String> getLoadBeforePlugins() {
        return List.of();
    }

    @Override
    public List<String> getProvidedPlugins() {
        return List.of();
    }

    @Override
    public List<String> getAuthors() {
        return List.of();
    }

    @Override
    public List<String> getContributors() {
        return List.of();
    }

    @Override
    public @Nullable String getDescription() {
        return null;
    }

    @Override
    public @Nullable String getWebsite() {
        return null;
    }

    @Override
    public List<Permission> getPermissions() {
        return List.of();
    }

    @Override
    public PermissionDefault getPermissionDefault() {
        return PermissionDefault.OP;
    }

    @Override
    public @Nullable String getAPIVersion() {
        return null;
    }
}
