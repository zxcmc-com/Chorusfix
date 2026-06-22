package com.zxcmc.chorusfix.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.chorusfix.ChorusFaceMask;
import com.zxcmc.exort.api.ExortApi;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

final class ExortApiHookTest {
  private static final String API_CLASS = "com.zxcmc.exort.api.ExortApi";
  private static final String METHOD = "isExortChorusCarrier";

  @Test
  void apiClaimIsCustomAndHardCustom() {
    AtomicBoolean claimed = new AtomicBoolean(true);
    ExortApiHook hook = hook(true, exortPlugin(claimed));

    assertTrue(hook.isCustom(null, ChorusFaceMask.ALL));
    assertTrue(hook.isHardCustom(null, ChorusFaceMask.ALL));

    ProviderHookStatus status = onlyStatus(hook);
    assertEquals("Exort", status.name());
    assertTrue(status.enabled());
    assertTrue(status.available());
    assertEquals(API_CLASS + "." + METHOD, status.detail());
  }

  @Test
  void apiFalseDoesNotClaimImpossibleMasks() {
    ExortApiHook hook = hook(true, exortPlugin(new AtomicBoolean(false)));

    assertFalse(hook.isCustom(null, ChorusFaceMask.ALL));
    assertFalse(hook.isHardCustom(null, ChorusFaceMask.ALL));
  }

  @Test
  void disabledHookIsUnavailableAndFailOpen() {
    ExortApiHook hook = hook(false, exortPlugin(new AtomicBoolean(true)));

    assertFalse(hook.isCustom(null, ChorusFaceMask.ALL));
    assertFalse(hook.isHardCustom(null, ChorusFaceMask.ALL));

    ProviderHookStatus status = onlyStatus(hook);
    assertFalse(status.enabled());
    assertFalse(status.available());
    assertEquals("disabled by config", status.detail());
  }

  @Test
  void missingPluginIsUnavailableAndFailOpen() {
    ExortApiHook hook = hook(true, null);

    assertFalse(hook.isCustom(null, ChorusFaceMask.ALL));

    ProviderHookStatus status = onlyStatus(hook);
    assertTrue(status.enabled());
    assertFalse(status.available());
    assertEquals("plugin not installed", status.detail());
  }

  @Test
  void missingApiIsUnavailableAndFailOpen() {
    ExortApiHook hook =
        ExortApiHook.fromPlugin(
            true,
            exortPlugin(new AtomicBoolean(true)),
            "com.zxcmc.exort.api.MissingApi",
            METHOD,
            Logger.getLogger("chorusfix-test"),
            false);

    assertFalse(hook.isCustom(null, ChorusFaceMask.ALL));
    assertFalse(onlyStatus(hook).available());
  }

  @Test
  void nonImplementingPluginIsUnavailableAndFailOpen() {
    ExortApiHook hook = hook(true, plainPlugin());

    assertFalse(hook.isCustom(null, ChorusFaceMask.ALL));

    ProviderHookStatus status = onlyStatus(hook);
    assertTrue(status.enabled());
    assertFalse(status.available());
    assertEquals("plugin does not implement " + API_CLASS, status.detail());
  }

  private static ExortApiHook hook(boolean enabled, Plugin plugin) {
    return ExortApiHook.fromPlugin(
        enabled, plugin, API_CLASS, METHOD, Logger.getLogger("chorusfix-test"), false);
  }

  private static Plugin exortPlugin(AtomicBoolean claimed) {
    return proxy(
        new Class<?>[] {Plugin.class, ExortApi.class},
        (proxy, method, args) -> {
          if (method.getDeclaringClass() == Object.class) {
            return objectMethod(proxy, method, args);
          }
          if (method.getName().equals(METHOD)) {
            return claimed.get();
          }
          return defaultValue(method);
        });
  }

  private static Plugin plainPlugin() {
    return proxy(
        new Class<?>[] {Plugin.class},
        (proxy, method, args) -> {
          if (method.getDeclaringClass() == Object.class) {
            return objectMethod(proxy, method, args);
          }
          return defaultValue(method);
        });
  }

  private static ProviderHookStatus onlyStatus(ExortApiHook hook) {
    List<ProviderHookStatus> statuses = hook.statuses();
    assertEquals(1, statuses.size());
    return statuses.getFirst();
  }

  private static Plugin proxy(Class<?>[] interfaces, InvocationHandler handler) {
    return (Plugin) Proxy.newProxyInstance(ExortApi.class.getClassLoader(), interfaces, handler);
  }

  private static Object objectMethod(Object proxy, Method method, Object[] args) {
    return switch (method.getName()) {
      case "toString" -> "proxy";
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> proxy == args[0];
      default -> null;
    };
  }

  private static Object defaultValue(Method method) {
    Class<?> returnType = method.getReturnType();
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == int.class) {
      return 0;
    }
    if (returnType == long.class) {
      return 0L;
    }
    if (returnType == double.class) {
      return 0.0D;
    }
    if (returnType == float.class) {
      return 0.0F;
    }
    return null;
  }
}
