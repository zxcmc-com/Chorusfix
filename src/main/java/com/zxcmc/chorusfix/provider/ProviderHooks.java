package com.zxcmc.chorusfix.provider;

import com.zxcmc.chorusfix.ChorusFaceMask;
import com.zxcmc.chorusfix.ChorusfixConfig.ProviderHookSettings;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class ProviderHooks {
  private ProviderHooks() {}

  public static CustomChorusBlockDetector create(
      ProviderHookSettings settings, Logger logger, boolean debug) {
    List<CustomChorusBlockDetector> detectors = new ArrayList<>();
    detectors.add(
        ReflectiveHook.booleanHook(
            "Nexo",
            settings.nexo(),
            "com.nexomc.nexo.api.NexoBlocks",
            "isNexoChorusBlock",
            logger,
            debug));
    detectors.add(
        ReflectiveHook.nonNullHook(
            "ItemsAdder",
            settings.itemsAdder(),
            "dev.lone.itemsadder.api.CustomBlock",
            "byAlreadyPlaced",
            logger,
            debug));
    detectors.add(
        ReflectiveHook.booleanHook(
            "Oraxen",
            settings.oraxen(),
            "io.th0rgal.oraxen.api.OraxenBlocks",
            "isOraxenChorusBlock",
            logger,
            debug));
    detectors.add(ExortApiHook.create(settings.exort(), logger, debug));
    return new CompositeCustomChorusBlockDetector(detectors);
  }

  private static final class ReflectiveHook implements CustomChorusBlockDetector {
    private final String pluginName;
    private final Method method;
    private final DetectionMode mode;
    private final ProviderHookStatus status;
    private final Logger logger;
    private final boolean debug;

    private ReflectiveHook(
        String pluginName,
        Method method,
        DetectionMode mode,
        ProviderHookStatus status,
        Logger logger,
        boolean debug) {
      this.pluginName = pluginName;
      this.method = method;
      this.mode = mode;
      this.status = status;
      this.logger = logger;
      this.debug = debug;
    }

    static ReflectiveHook booleanHook(
        String pluginName,
        boolean enabled,
        String className,
        String methodName,
        Logger logger,
        boolean debug) {
      return create(
          pluginName, enabled, className, methodName, DetectionMode.BOOLEAN_TRUE, logger, debug);
    }

    static ReflectiveHook nonNullHook(
        String pluginName,
        boolean enabled,
        String className,
        String methodName,
        Logger logger,
        boolean debug) {
      return create(
          pluginName, enabled, className, methodName, DetectionMode.NON_NULL, logger, debug);
    }

    private static ReflectiveHook create(
        String pluginName,
        boolean enabled,
        String className,
        String methodName,
        DetectionMode mode,
        Logger logger,
        boolean debug) {
      if (!enabled) {
        return unavailable(pluginName, false, "disabled by config", logger, debug);
      }
      Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
      if (plugin == null) {
        return unavailable(pluginName, true, "plugin not installed", logger, debug);
      }
      try {
        Class<?> apiClass = Class.forName(className);
        Method method = apiClass.getMethod(methodName, Block.class);
        method.setAccessible(true);
        return new ReflectiveHook(
            pluginName,
            method,
            mode,
            new ProviderHookStatus(pluginName, true, true, className + "." + methodName),
            logger,
            debug);
      } catch (ReflectiveOperationException | LinkageError | SecurityException e) {
        return unavailable(
            pluginName, true, e.getClass().getSimpleName() + ": " + e.getMessage(), logger, debug);
      }
    }

    private static ReflectiveHook unavailable(
        String pluginName, boolean enabled, String detail, Logger logger, boolean debug) {
      return new ReflectiveHook(
          pluginName,
          null,
          DetectionMode.UNAVAILABLE,
          new ProviderHookStatus(pluginName, enabled, false, detail),
          logger,
          debug);
    }

    @Override
    public boolean isCustom(Block block, ChorusFaceMask mask) {
      if (method == null) {
        return false;
      }
      try {
        Object result = method.invoke(null, block);
        return switch (mode) {
          case BOOLEAN_TRUE -> Boolean.TRUE.equals(result);
          case NON_NULL -> result != null;
          case UNAVAILABLE -> false;
        };
      } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
        if (debug) {
          logger.log(
              Level.WARNING, pluginName + " chorus hook failed for " + block.getLocation(), e);
        }
        return false;
      }
    }

    @Override
    public List<ProviderHookStatus> statuses() {
      return List.of(status);
    }
  }

  private enum DetectionMode {
    BOOLEAN_TRUE,
    NON_NULL,
    UNAVAILABLE
  }
}
