package com.zxcmc.chorusfix.diagnostics;

import com.zxcmc.chorusfix.ChorusFaceMask;
import com.zxcmc.chorusfix.ChorusfixConfig.ProviderConfigDiagnosticsSettings;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProviderConfigDiagnostics {
  private static final int MIN_PROVIDER_VARIATION = 1;
  private static final int MAX_PROVIDER_VARIATION = 63;
  private static final int MIN_SAFE_PROVIDER_VARIATION = 49;
  private static final String SAFE_RANGE = "49..63";

  private final Path pluginsDirectory;
  private final Logger logger;
  private final ItemsAdderRegistryFacade itemsAdderRegistry;

  public ProviderConfigDiagnostics(JavaPlugin plugin, Logger logger) {
    this(plugin.getDataFolder().toPath().getParent(), logger, new ReflectiveItemsAdderRegistry());
  }

  ProviderConfigDiagnostics(
      Path pluginsDirectory, Logger logger, ItemsAdderRegistryFacade itemsAdderRegistry) {
    this.pluginsDirectory = pluginsDirectory;
    this.logger = logger;
    this.itemsAdderRegistry = itemsAdderRegistry;
  }

  public ProviderDiagnosticsReport run(ProviderConfigDiagnosticsSettings settings) {
    if (!settings.enabled()) {
      return ProviderDiagnosticsReport.disabled();
    }

    List<ProviderDiagnosticProviderReport> reports = new ArrayList<>();
    reports.add(
        settings.nexo()
            ? scanYamlProvider("Nexo", pluginsDirectory.resolve("Nexo/items"), "custom_block", true)
            : ProviderDiagnosticProviderReport.disabled("Nexo"));
    reports.add(
        settings.oraxen()
            ? scanYamlProvider(
                "Oraxen", pluginsDirectory.resolve("Oraxen/items"), "chorusblock", false)
            : ProviderDiagnosticProviderReport.disabled("Oraxen"));
    reports.add(
        settings.itemsAdder()
            ? scanItemsAdderRegistry()
            : ProviderDiagnosticProviderReport.disabled("ItemsAdder"));

    ProviderDiagnosticsReport report = new ProviderDiagnosticsReport(true, List.copyOf(reports));
    for (ProviderDiagnosticProviderReport provider : report.providers()) {
      for (ProviderDiagnosticWarning warning : provider.warnings()) {
        logger.warning(formatWarning(warning));
      }
    }
    return report;
  }

  private ProviderDiagnosticProviderReport scanYamlProvider(
      String provider, Path itemsDirectory, String mechanicKey, boolean requireChorusType) {
    if (!Files.isDirectory(itemsDirectory)) {
      return new ProviderDiagnosticProviderReport(
          provider, true, false, 0, List.of(), "items directory not found: " + itemsDirectory);
    }

    List<ProviderDiagnosticWarning> warnings = new ArrayList<>();
    int scanned = 0;
    Map<Integer, String> firstItemByVariation = new HashMap<>();
    try {
      List<Path> yamlFiles;
      try (var stream = Files.walk(itemsDirectory)) {
        yamlFiles =
            stream
                .filter(Files::isRegularFile)
                .filter(ProviderConfigDiagnostics::isYamlFile)
                .sorted()
                .toList();
      }

      for (Path yamlFile : yamlFiles) {
        scanned +=
            scanYamlFile(
                provider,
                itemsDirectory,
                yamlFile,
                mechanicKey,
                requireChorusType,
                firstItemByVariation,
                warnings);
      }
      return new ProviderDiagnosticProviderReport(
          provider,
          true,
          true,
          scanned,
          List.copyOf(warnings),
          "scanned " + yamlFiles.size() + " files");
    } catch (IOException e) {
      return new ProviderDiagnosticProviderReport(
          provider,
          true,
          false,
          scanned,
          List.copyOf(warnings),
          "I/O error while scanning " + itemsDirectory + ": " + e.getMessage());
    }
  }

  private int scanYamlFile(
      String provider,
      Path itemsDirectory,
      Path yamlFile,
      String mechanicKey,
      boolean requireChorusType,
      Map<Integer, String> firstItemByVariation,
      List<ProviderDiagnosticWarning> warnings) {
    YamlConfiguration yaml = new YamlConfiguration();
    String location = relativeLocation(itemsDirectory, yamlFile);
    try {
      yaml.load(yamlFile.toFile());
    } catch (IOException | InvalidConfigurationException e) {
      warnings.add(
          new ProviderDiagnosticWarning(
              provider, location, "<file>", "could not parse YAML: " + e.getMessage()));
      return 0;
    }

    int scanned = 0;
    for (String itemId : yaml.getKeys(false)) {
      ConfigurationSection item = yaml.getConfigurationSection(itemId);
      if (item == null) {
        continue;
      }
      ConfigurationSection mechanic = item.getConfigurationSection("Mechanics." + mechanicKey);
      if (mechanic == null) {
        continue;
      }
      if (requireChorusType && !mechanic.getString("type", "").equalsIgnoreCase("CHORUSBLOCK")) {
        continue;
      }
      scanned++;
      String itemName = item.getString("itemname", item.getString("item_name", itemId));
      validateProviderVariation(
          provider, location, itemId, itemName, mechanic, firstItemByVariation, warnings);
    }
    return scanned;
  }

  private void validateProviderVariation(
      String provider,
      String location,
      String itemId,
      String itemName,
      ConfigurationSection mechanic,
      Map<Integer, String> firstItemByVariation,
      List<ProviderDiagnosticWarning> warnings) {
    String label = itemId + " (" + itemName + ")";
    if (mechanic.contains("custom_variaton")) {
      warnings.add(
          new ProviderDiagnosticWarning(
              provider,
              location,
              itemId,
              label
                  + " uses misspelled key custom_variaton; use custom_variation with a value in "
                  + SAFE_RANGE
                  + "."));
    }

    if (!mechanic.contains("custom_variation")) {
      warnings.add(
          new ProviderDiagnosticWarning(
              provider,
              location,
              itemId,
              label + " is missing custom_variation; set it to a value in " + SAFE_RANGE + "."));
      return;
    }

    Integer variation = parseInteger(mechanic.get("custom_variation"));
    if (variation == null) {
      warnings.add(
          new ProviderDiagnosticWarning(
              provider,
              location,
              itemId,
              label
                  + " has non-numeric custom_variation="
                  + mechanic.get("custom_variation")
                  + "; use a value in "
                  + SAFE_RANGE
                  + "."));
      return;
    }

    if (variation < MIN_PROVIDER_VARIATION || variation > MAX_PROVIDER_VARIATION) {
      warnings.add(
          new ProviderDiagnosticWarning(
              provider,
              location,
              itemId,
              label
                  + " uses custom_variation "
                  + variation
                  + ", outside the provider-valid range 1..63; use "
                  + SAFE_RANGE
                  + " for Chorusfix safety."));
      return;
    }

    String previous = firstItemByVariation.putIfAbsent(variation, label + " in " + location);
    if (previous != null) {
      warnings.add(
          new ProviderDiagnosticWarning(
              provider,
              location,
              itemId,
              label
                  + " reuses custom_variation "
                  + variation
                  + " already used by "
                  + previous
                  + "."));
    }

    ChorusFaceMask mask = providerVariationMask(variation);
    if (!mask.isImpossibleCustomCarrier()) {
      warnings.add(
          new ProviderDiagnosticWarning(
              provider,
              location,
              itemId,
              label
                  + " uses custom_variation "
                  + variation
                  + " (mask "
                  + mask.asConfigToken()
                  + "), which can overlap vanilla chorus states; use "
                  + MIN_SAFE_PROVIDER_VARIATION
                  + " or higher ("
                  + SAFE_RANGE
                  + ")."));
    }
  }

  private ProviderDiagnosticProviderReport scanItemsAdderRegistry() {
    List<ProviderDiagnosticWarning> warnings = new ArrayList<>();
    try {
      List<ItemsAdderCustomBlockState> blocks = itemsAdderRegistry.loadBlocks();
      int scanned = 0;
      for (ItemsAdderCustomBlockState block : blocks) {
        if (block.material() != Material.CHORUS_PLANT) {
          continue;
        }
        scanned++;
        if (block.mask() == null || !block.mask().isImpossibleCustomCarrier()) {
          String mask = block.mask() == null ? "unknown" : block.mask().asConfigToken();
          warnings.add(
              new ProviderDiagnosticWarning(
                  "ItemsAdder",
                  "runtime registry",
                  block.namespacedId(),
                  "ItemsAdder block "
                      + block.namespacedId()
                      + " uses "
                      + block.blockDataDescription()
                      + " (mask "
                      + mask
                      + "), which can overlap vanilla chorus states; reassign/regenerate its "
                      + "REAL_TRANSPARENT slot or add this exact state to ignored-states if the "
                      + "risk is intentional."));
        }
      }
      return new ProviderDiagnosticProviderReport(
          "ItemsAdder", true, true, scanned, List.copyOf(warnings), "runtime registry scanned");
    } catch (ItemsAdderRegistryUnavailableException e) {
      return new ProviderDiagnosticProviderReport(
          "ItemsAdder", true, false, 0, List.of(), e.getMessage());
    }
  }

  private static String formatWarning(ProviderDiagnosticWarning warning) {
    return "[provider diagnostics] "
        + warning.provider()
        + " "
        + warning.location()
        + " "
        + warning.itemId()
        + ": "
        + warning.detail();
  }

  private static Integer parseInteger(Object rawValue) {
    if (rawValue instanceof Number number) {
      return number.intValue();
    }
    if (rawValue instanceof String string) {
      try {
        return Integer.valueOf(string.trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private static ChorusFaceMask providerVariationMask(int variation) {
    return new ChorusFaceMask(
        (variation & 1) != 0,
        (variation & 4) != 0,
        (variation & 2) != 0,
        (variation & 8) != 0,
        (variation & 16) != 0,
        (variation & 32) != 0);
  }

  private static boolean isYamlFile(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".yml") || name.endsWith(".yaml");
  }

  private static String relativeLocation(Path root, Path file) {
    return root.relativize(file).toString();
  }

  record ItemsAdderCustomBlockState(
      String namespacedId, Material material, ChorusFaceMask mask, String blockDataDescription) {}

  interface ItemsAdderRegistryFacade {
    List<ItemsAdderCustomBlockState> loadBlocks() throws ItemsAdderRegistryUnavailableException;
  }

  static final class ItemsAdderRegistryUnavailableException extends Exception {
    ItemsAdderRegistryUnavailableException(String message) {
      super(message);
    }

    ItemsAdderRegistryUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static final class ReflectiveItemsAdderRegistry implements ItemsAdderRegistryFacade {
    @Override
    public List<ItemsAdderCustomBlockState> loadBlocks()
        throws ItemsAdderRegistryUnavailableException {
      try {
        Class<?> customBlockClass = Class.forName("dev.lone.itemsadder.api.CustomBlock");
        Method idsMethod = customBlockClass.getMethod("getNamespacedIdsInRegistry");
        Method staticBaseDataMethod = customBlockClass.getMethod("getBaseBlockData", String.class);
        Method getInstanceMethod = customBlockClass.getMethod("getInstance", String.class);
        Method instanceBaseDataMethod = customBlockClass.getMethod("getBaseBlockData");

        Object rawIds = idsMethod.invoke(null);
        if (!(rawIds instanceof Iterable<?> ids)) {
          throw new ItemsAdderRegistryUnavailableException(
              "CustomBlock.getNamespacedIdsInRegistry() did not return an iterable registry");
        }

        List<ItemsAdderCustomBlockState> blocks = new ArrayList<>();
        for (Object rawId : ids) {
          if (!(rawId instanceof String namespacedId)) {
            continue;
          }
          BlockData blockData =
              loadBlockData(
                  customBlockClass,
                  staticBaseDataMethod,
                  getInstanceMethod,
                  instanceBaseDataMethod,
                  namespacedId);
          if (blockData == null) {
            continue;
          }
          blocks.add(
              new ItemsAdderCustomBlockState(
                  namespacedId,
                  blockData.getMaterial(),
                  ChorusFaceMask.fromBlockData(blockData).orElse(null),
                  blockData.getAsString()));
        }
        return blocks;
      } catch (ClassNotFoundException e) {
        throw new ItemsAdderRegistryUnavailableException("ItemsAdder API class not found", e);
      } catch (NoSuchMethodException
          | IllegalAccessException
          | LinkageError
          | SecurityException e) {
        throw new ItemsAdderRegistryUnavailableException(
            "ItemsAdder registry API unavailable: " + e.getClass().getSimpleName(), e);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw new ItemsAdderRegistryUnavailableException(
            "ItemsAdder registry API failed: " + cause.getClass().getSimpleName(), cause);
      }
    }

    private static BlockData loadBlockData(
        Class<?> customBlockClass,
        Method staticBaseDataMethod,
        Method getInstanceMethod,
        Method instanceBaseDataMethod,
        String namespacedId)
        throws InvocationTargetException, IllegalAccessException {
      Object staticBlockData = staticBaseDataMethod.invoke(null, namespacedId);
      if (staticBlockData instanceof BlockData blockData) {
        return blockData;
      }

      Object instance = getInstanceMethod.invoke(null, namespacedId);
      if (instance == null || !customBlockClass.isInstance(instance)) {
        return null;
      }
      Object instanceBlockData = instanceBaseDataMethod.invoke(instance);
      return instanceBlockData instanceof BlockData blockData ? blockData : null;
    }
  }
}
