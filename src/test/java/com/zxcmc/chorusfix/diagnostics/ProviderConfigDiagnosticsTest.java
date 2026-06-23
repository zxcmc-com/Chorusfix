package com.zxcmc.chorusfix.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.zxcmc.chorusfix.ChorusFaceMask;
import com.zxcmc.chorusfix.ChorusfixConfig.ProviderConfigDiagnosticsSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProviderConfigDiagnosticsTest {
  private static final ProviderConfigDiagnosticsSettings ALL_ENABLED =
      new ProviderConfigDiagnosticsSettings(true, true, true, true);
  private static final ProviderConfigDiagnosticsSettings YAML_ONLY =
      new ProviderConfigDiagnosticsSettings(true, true, false, true);
  private static final ProviderConfigDiagnosticsSettings NEXO_ONLY =
      new ProviderConfigDiagnosticsSettings(true, true, false, false);
  private static final ProviderConfigDiagnosticsSettings ORAXEN_ONLY =
      new ProviderConfigDiagnosticsSettings(true, false, false, true);
  private static final ProviderConfigDiagnosticsSettings ITEMS_ADDER_ONLY =
      new ProviderConfigDiagnosticsSettings(true, false, true, false);

  @TempDir Path tempDir;

  @Test
  void nexoSafeVariationDoesNotWarn() throws IOException {
    write(
        "Nexo/items/safe.yml",
        """
        safe_block:
          itemname: "Safe Block"
          Mechanics:
            custom_block:
              type: CHORUSBLOCK
              custom_variation: 49
              model: safe_block
        """);

    ProviderDiagnosticsReport report = diagnostics(List.of()).run(YAML_ONLY);

    ProviderDiagnosticProviderReport nexo = provider(report, "Nexo");
    assertEquals(1, nexo.scanned());
    assertEquals(0, nexo.warningCount());
  }

  @Test
  void nexoWarnsForUnsafeMissingTypoNonNumericAndDuplicateVariations() throws IOException {
    write(
        "Nexo/items/problem.yml",
        """
        unsafe_block:
          itemname: "Unsafe Block"
          Mechanics:
            custom_block:
              type: CHORUSBLOCK
              custom_variation: 3
              model: unsafe_block
        missing_block:
          Mechanics:
            custom_block:
              type: CHORUSBLOCK
              model: missing_block
        typo_block:
          Mechanics:
            custom_block:
              type: CHORUSBLOCK
              custom_variaton: 55
              model: typo_block
        text_block:
          Mechanics:
            custom_block:
              type: CHORUSBLOCK
              custom_variation: nope
              model: text_block
        duplicate_a:
          Mechanics:
            custom_block:
              type: CHORUSBLOCK
              custom_variation: 55
              model: duplicate_a
        duplicate_b:
          Mechanics:
            custom_block:
              type: CHORUSBLOCK
              custom_variation: 55
              model: duplicate_b
        out_of_range:
          Mechanics:
            custom_block:
              type: CHORUSBLOCK
              custom_variation: 64
              model: out_of_range
        not_chorus:
          Mechanics:
            custom_block:
              type: NOTEBLOCK
              custom_variation: 1
              model: not_chorus
        """);

    ProviderDiagnosticsReport report = diagnostics(List.of()).run(YAML_ONLY);

    ProviderDiagnosticProviderReport nexo = provider(report, "Nexo");
    assertEquals(7, nexo.scanned());
    assertWarningContains(nexo, "unsafe_block", "49..63");
    assertWarningContains(nexo, "missing_block", "missing custom_variation");
    assertWarningContains(nexo, "typo_block", "custom_variaton");
    assertWarningContains(nexo, "text_block", "non-numeric");
    assertWarningContains(nexo, "duplicate_b", "reuses custom_variation 55");
    assertWarningContains(nexo, "out_of_range", "outside the provider-valid range 1..63");
  }

  @Test
  void nexoConfigIsNotScannedWhenPluginIsNotEnabled() throws IOException {
    write(
        "Nexo/items/stale.yml",
        """
        stale_block:
          itemname: "Stale Block"
          Mechanics:
            custom_block:
              type: CHORUSBLOCK
              custom_variation: 3
              model: stale_block
        """);

    ProviderDiagnosticsReport report =
        diagnostics(List.of(), provider -> !provider.equals("Nexo")).run(NEXO_ONLY);

    ProviderDiagnosticProviderReport nexo = provider(report, "Nexo");
    assertTrue(nexo.enabled());
    assertFalse(nexo.available());
    assertEquals(0, nexo.scanned());
    assertEquals(0, nexo.warningCount());
    assertTrue(nexo.detail().contains("plugin not loaded/enabled"));
  }

  @Test
  void oraxenUsesChorusblockMechanicPath() throws IOException {
    write(
        "Oraxen/items/oraxen.yml",
        """
        unsafe_oraxen:
          displayname: "<gray>Unsafe"
          Mechanics:
            chorusblock:
              custom_variation: 2
              model: unsafe_oraxen
        safe_oraxen:
          displayname: "<gray>Safe"
          Mechanics:
            chorusblock:
              custom_variation: 61
              model: safe_oraxen
        """);

    ProviderDiagnosticsReport report = diagnostics(List.of()).run(YAML_ONLY);

    ProviderDiagnosticProviderReport oraxen = provider(report, "Oraxen");
    assertEquals(2, oraxen.scanned());
    assertEquals(1, oraxen.warningCount());
    assertWarningContains(oraxen, "unsafe_oraxen", "49..63");
  }

  @Test
  void oraxenConfigIsNotScannedWhenPluginIsNotEnabled() throws IOException {
    write(
        "Oraxen/items/stale.yml",
        """
        stale_oraxen:
          displayname: "<gray>Stale"
          Mechanics:
            chorusblock:
              custom_variation: 2
              model: stale_oraxen
        """);

    ProviderDiagnosticsReport report =
        diagnostics(List.of(), provider -> !provider.equals("Oraxen")).run(ORAXEN_ONLY);

    ProviderDiagnosticProviderReport oraxen = provider(report, "Oraxen");
    assertTrue(oraxen.enabled());
    assertFalse(oraxen.available());
    assertEquals(0, oraxen.scanned());
    assertEquals(0, oraxen.warningCount());
    assertTrue(oraxen.detail().contains("plugin not loaded/enabled"));
  }

  @Test
  void itemsAdderWarnsForVanillaPossibleChorusRegistryStates() {
    ProviderDiagnosticsReport report =
        diagnostics(
                List.of(
                    new ProviderConfigDiagnostics.ItemsAdderCustomBlockState(
                        "example:bad",
                        Material.CHORUS_PLANT,
                        ChorusFaceMask.parse("down"),
                        "minecraft:chorus_plant[down=true]"),
                    new ProviderConfigDiagnostics.ItemsAdderCustomBlockState(
                        "example:safe",
                        Material.CHORUS_PLANT,
                        ChorusFaceMask.parse("north,up,down"),
                        "minecraft:chorus_plant[north=true,up=true,down=true]"),
                    new ProviderConfigDiagnostics.ItemsAdderCustomBlockState(
                        "example:stone", Material.STONE, null, "minecraft:stone")))
            .run(ALL_ENABLED);

    ProviderDiagnosticProviderReport itemsAdder = provider(report, "ItemsAdder");
    assertEquals(2, itemsAdder.scanned());
    assertEquals(1, itemsAdder.warningCount());
    assertWarningContains(itemsAdder, "example:bad", "REAL_TRANSPARENT slot");
    assertFalse(report.providers().isEmpty());
  }

  @Test
  void itemsAdderRegistryIsNotLoadedWhenPluginIsNotEnabled() {
    ProviderConfigDiagnostics diagnostics =
        new ProviderConfigDiagnostics(
            tempDir.resolve("plugins"),
            logger(),
            () -> {
              fail("ItemsAdder registry should not be loaded when the plugin is disabled");
              return List.of();
            },
            provider -> !provider.equals("ItemsAdder"));

    ProviderDiagnosticsReport report = diagnostics.run(ITEMS_ADDER_ONLY);

    ProviderDiagnosticProviderReport itemsAdder = provider(report, "ItemsAdder");
    assertTrue(itemsAdder.enabled());
    assertFalse(itemsAdder.available());
    assertEquals(0, itemsAdder.scanned());
    assertEquals(0, itemsAdder.warningCount());
    assertTrue(itemsAdder.detail().contains("plugin not loaded/enabled"));
  }

  @Test
  void itemsAdderUnavailableDoesNotFailDiagnostics() {
    ProviderConfigDiagnostics diagnostics =
        new ProviderConfigDiagnostics(
            tempDir.resolve("plugins"),
            logger(),
            () -> {
              throw new ProviderConfigDiagnostics.ItemsAdderRegistryUnavailableException(
                  "ItemsAdder API class not found");
            });

    ProviderDiagnosticsReport report = diagnostics.run(ALL_ENABLED);

    ProviderDiagnosticProviderReport itemsAdder = provider(report, "ItemsAdder");
    assertFalse(itemsAdder.available());
    assertEquals(0, itemsAdder.warningCount());
    assertTrue(itemsAdder.detail().contains("ItemsAdder API class not found"));
  }

  private ProviderConfigDiagnostics diagnostics(
      List<ProviderConfigDiagnostics.ItemsAdderCustomBlockState> itemsAdderBlocks) {
    return diagnostics(itemsAdderBlocks, provider -> true);
  }

  private ProviderConfigDiagnostics diagnostics(
      List<ProviderConfigDiagnostics.ItemsAdderCustomBlockState> itemsAdderBlocks,
      Predicate<String> providerEnabled) {
    return new ProviderConfigDiagnostics(
        tempDir.resolve("plugins"), logger(), () -> itemsAdderBlocks, providerEnabled);
  }

  private void write(String relativePath, String content) throws IOException {
    Path file = tempDir.resolve("plugins").resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private static ProviderDiagnosticProviderReport provider(
      ProviderDiagnosticsReport report, String name) {
    return report.providers().stream()
        .filter(provider -> provider.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static void assertWarningContains(
      ProviderDiagnosticProviderReport provider, String itemId, String expectedText) {
    assertTrue(
        provider.warnings().stream()
            .anyMatch(
                warning ->
                    warning.itemId().equals(itemId) && warning.detail().contains(expectedText)),
        "Expected warning for " + itemId + " containing " + expectedText);
  }

  private static Logger logger() {
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    return logger;
  }
}
