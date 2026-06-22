package com.zxcmc.chorusfix.provider;

import com.zxcmc.chorusfix.ChorusFaceMask;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

final class ExortApiHook implements CustomChorusBlockDetector {
  private static final String PLUGIN_NAME = "Exort";
  private static final String API_CLASS_NAME = "com.zxcmc.exort.api.ExortApi";
  private static final String CHORUS_CARRIER_METHOD = "isExortChorusCarrier";

  private final Object api;
  private final Method chorusCarrierMethod;
  private final ProviderHookStatus status;
  private final Logger logger;
  private final boolean debug;

  private ExortApiHook(
      Object api,
      Method chorusCarrierMethod,
      ProviderHookStatus status,
      Logger logger,
      boolean debug) {
    this.api = api;
    this.chorusCarrierMethod = chorusCarrierMethod;
    this.status = status;
    this.logger = logger;
    this.debug = debug;
  }

  static ExortApiHook create(boolean enabled, Logger logger, boolean debug) {
    Plugin plugin = enabled ? Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) : null;
    return fromPlugin(enabled, plugin, API_CLASS_NAME, CHORUS_CARRIER_METHOD, logger, debug);
  }

  static ExortApiHook fromPlugin(
      boolean enabled,
      Plugin plugin,
      String apiClassName,
      String methodName,
      Logger logger,
      boolean debug) {
    if (!enabled) {
      return unavailable(false, "disabled by config", logger, debug);
    }
    if (plugin == null) {
      return unavailable(true, "plugin not installed", logger, debug);
    }
    try {
      Class<?> apiClass = Class.forName(apiClassName, false, plugin.getClass().getClassLoader());
      if (!apiClass.isInstance(plugin)) {
        return unavailable(true, "plugin does not implement " + apiClassName, logger, debug);
      }
      Method method = apiClass.getMethod(methodName, Block.class);
      method.setAccessible(true);
      return new ExortApiHook(
          plugin,
          method,
          new ProviderHookStatus(PLUGIN_NAME, true, true, apiClassName + "." + methodName),
          logger,
          debug);
    } catch (ReflectiveOperationException | LinkageError | SecurityException e) {
      return unavailable(true, e.getClass().getSimpleName() + ": " + e.getMessage(), logger, debug);
    }
  }

  private static ExortApiHook unavailable(
      boolean enabled, String detail, Logger logger, boolean debug) {
    return new ExortApiHook(
        null, null, new ProviderHookStatus(PLUGIN_NAME, enabled, false, detail), logger, debug);
  }

  @Override
  public boolean isCustom(Block block, ChorusFaceMask mask) {
    if (chorusCarrierMethod == null) {
      return false;
    }
    try {
      return Boolean.TRUE.equals(chorusCarrierMethod.invoke(api, block));
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      if (debug) {
        logger.log(Level.WARNING, "Exort chorus API hook failed for " + location(block), e);
      }
      return false;
    }
  }

  @Override
  public boolean isHardCustom(Block block, ChorusFaceMask mask) {
    return isCustom(block, mask);
  }

  @Override
  public List<ProviderHookStatus> statuses() {
    return List.of(status);
  }

  private static String location(Block block) {
    return block == null ? "<null>" : String.valueOf(block.getLocation());
  }
}
