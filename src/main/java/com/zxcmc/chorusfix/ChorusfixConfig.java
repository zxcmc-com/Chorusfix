package com.zxcmc.chorusfix;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record ChorusfixConfig(
    boolean enabled,
    boolean onlyWhenPaperDisabled,
    int maxPerEvent,
    int maxPerTick,
    int maxChainDepth,
    Set<ChorusFaceMask> ignoredMasks,
    ProviderHookSettings providerHooks,
    ProviderConfigDiagnosticsSettings providerConfigDiagnostics,
    boolean debug) {
  public static ChorusfixConfig from(FileConfiguration config, Logger logger) {
    ConfigurationSection queue = config.getConfigurationSection("queue");
    Set<ChorusFaceMask> ignored = new LinkedHashSet<>();
    List<String> rawMasks = config.getStringList("ignored-states");
    for (String rawMask : rawMasks) {
      try {
        ignored.add(ChorusFaceMask.parse(rawMask));
      } catch (IllegalArgumentException e) {
        logger.warning("Ignoring invalid chorus mask in ignored-states: " + rawMask);
      }
    }
    return new ChorusfixConfig(
        config.getBoolean("enabled", true),
        config.getBoolean("only-when-paper-disabled", true),
        positiveInt(queue, "max-per-event", 256),
        positiveInt(queue, "max-per-tick", 512),
        positiveInt(queue, "max-chain-depth", 4096),
        Collections.unmodifiableSet(ignored),
        ProviderHookSettings.from(config.getConfigurationSection("provider-hooks")),
        ProviderConfigDiagnosticsSettings.from(
            config.getConfigurationSection("provider-config-diagnostics")),
        config.getBoolean("debug", false));
  }

  private static int positiveInt(ConfigurationSection section, String key, int fallback) {
    if (section == null) {
      return fallback;
    }
    return Math.max(0, section.getInt(key, fallback));
  }

  public record ProviderHookSettings(
      boolean nexo, boolean itemsAdder, boolean oraxen, boolean exort) {
    static ProviderHookSettings from(ConfigurationSection section) {
      if (section == null) {
        return new ProviderHookSettings(true, true, true, true);
      }
      return new ProviderHookSettings(
          section.getBoolean("nexo", true),
          section.getBoolean("itemsadder", true),
          section.getBoolean("oraxen", true),
          section.getBoolean("exort", true));
    }
  }

  public record ProviderConfigDiagnosticsSettings(
      boolean enabled, boolean nexo, boolean itemsAdder, boolean oraxen) {
    static ProviderConfigDiagnosticsSettings from(ConfigurationSection section) {
      if (section == null) {
        return new ProviderConfigDiagnosticsSettings(true, true, true, true);
      }
      return new ProviderConfigDiagnosticsSettings(
          section.getBoolean("enabled", true),
          section.getBoolean("nexo", true),
          section.getBoolean("itemsadder", true),
          section.getBoolean("oraxen", true));
    }
  }
}
