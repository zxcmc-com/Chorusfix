package com.zxcmc.chorusfix;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public final class PaperChorusPlantUpdates {
  public static final String SETTING_PATH = "block-updates.disable-chorus-plant-updates";

  private PaperChorusPlantUpdates() {}

  public static RuntimeState runtimeState() {
    try {
      Class<?> globalConfiguration =
          Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
      Method get = globalConfiguration.getMethod("get");
      Object global = get.invoke(null);
      if (global == null) {
        return RuntimeState.unknown("GlobalConfiguration.get() returned null");
      }
      Object blockUpdates = publicField(global, "blockUpdates");
      if (blockUpdates == null) {
        return RuntimeState.unknown("GlobalConfiguration.blockUpdates is null");
      }
      Object value = publicField(blockUpdates, "disableChorusPlantUpdates");
      if (value instanceof Boolean disabled) {
        return RuntimeState.known(disabled);
      }
      return RuntimeState.unknown("disableChorusPlantUpdates is not boolean");
    } catch (ReflectiveOperationException
        | LinkageError
        | SecurityException
        | IllegalArgumentException e) {
      return RuntimeState.unknown(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private static Object publicField(Object instance, String name)
      throws ReflectiveOperationException {
    Field field = instance.getClass().getField(name);
    return field.get(instance);
  }

  public record RuntimeState(Optional<Boolean> disabled, String detail) {
    public static RuntimeState known(boolean disabled) {
      return new RuntimeState(Optional.of(disabled), Boolean.toString(disabled));
    }

    public static RuntimeState unknown(String detail) {
      return new RuntimeState(Optional.empty(), detail);
    }

    public boolean disabledTrue() {
      return disabled.orElse(false);
    }

    public String displayValue() {
      return disabled.map(Object::toString).orElse("unknown");
    }
  }
}
