package com.zxcmc.chorusfix.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.chorusfix.ChorusFaceMask;
import com.zxcmc.chorusfix.ChorusfixConfig;
import com.zxcmc.chorusfix.PaperChorusPlantUpdates;
import com.zxcmc.chorusfix.diagnostics.ProviderDiagnosticProviderReport;
import com.zxcmc.chorusfix.diagnostics.ProviderDiagnosticWarning;
import com.zxcmc.chorusfix.diagnostics.ProviderDiagnosticsReport;
import com.zxcmc.chorusfix.provider.ProviderHookStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChorusfixMetricsTest {
  @Test
  void customBlockProvidersReportsAvailableEnabledHooksOnly() {
    Map<String, Integer> values =
        ChorusfixMetrics.customBlockProvidersChartValues(
            List.of(
                new ProviderHookStatus("Nexo", true, true, "ok"),
                new ProviderHookStatus("ItemsAdder", true, false, "missing api"),
                new ProviderHookStatus("Oraxen", false, false, "disabled"),
                new ProviderHookStatus("Exort", true, true, "ok")));

    assertEquals(Map.of("nexo", 1, "exort", 1), values);
  }

  @Test
  void customBlockProvidersReportsNoneDetectedWhenNoHookIsAvailable() {
    assertEquals(
        Map.of("none_detected", 1),
        ChorusfixMetrics.customBlockProvidersChartValues(
            List.of(new ProviderHookStatus("Nexo", true, false, "plugin not installed"))));
  }

  @Test
  void runtimeStateUsesBoundedCategories() {
    assertEquals(
        "active", ChorusfixMetrics.runtimeStateChartValue(config(true, true), runtimeState(true)));
    assertEquals(
        "disabled_by_config",
        ChorusfixMetrics.runtimeStateChartValue(config(false, true), runtimeState(true)));
    assertEquals(
        "inactive_paper_updates_enabled",
        ChorusfixMetrics.runtimeStateChartValue(config(true, true), runtimeState(false)));
    assertEquals(
        "paper_state_unknown",
        ChorusfixMetrics.runtimeStateChartValue(
            config(true, true), PaperChorusPlantUpdates.RuntimeState.unknown("test")));
    assertEquals(
        "active",
        ChorusfixMetrics.runtimeStateChartValue(config(true, false), runtimeState(false)));
  }

  @Test
  void unsafeProviderConfigsReportsSingleMultipleNoneAndDisabled() {
    assertEquals(
        "diagnostics_disabled",
        ChorusfixMetrics.unsafeProviderConfigsChartValue(ProviderDiagnosticsReport.disabled()));
    assertEquals(
        "none",
        ChorusfixMetrics.unsafeProviderConfigsChartValue(
            new ProviderDiagnosticsReport(
                true,
                List.of(
                    new ProviderDiagnosticProviderReport(
                        "Nexo", true, true, 1, List.of(), "ok")))));
    assertEquals(
        "itemsadder",
        ChorusfixMetrics.unsafeProviderConfigsChartValue(
            new ProviderDiagnosticsReport(
                true,
                List.of(
                    providerWithWarning("ItemsAdder"),
                    new ProviderDiagnosticProviderReport(
                        "Oraxen", true, true, 1, List.of(), "ok")))));
    assertEquals(
        "multiple",
        ChorusfixMetrics.unsafeProviderConfigsChartValue(
            new ProviderDiagnosticsReport(
                true, List.of(providerWithWarning("Nexo"), providerWithWarning("Oraxen")))));
  }

  private static ProviderDiagnosticProviderReport providerWithWarning(String provider) {
    return new ProviderDiagnosticProviderReport(
        provider,
        true,
        true,
        1,
        List.of(new ProviderDiagnosticWarning(provider, "file.yml", "item", "unsafe")),
        "warning");
  }

  private static PaperChorusPlantUpdates.RuntimeState runtimeState(boolean disabled) {
    return PaperChorusPlantUpdates.RuntimeState.known(disabled);
  }

  private static ChorusfixConfig config(boolean enabled, boolean onlyWhenPaperDisabled) {
    return new ChorusfixConfig(
        enabled,
        onlyWhenPaperDisabled,
        256,
        512,
        4096,
        Set.of(ChorusFaceMask.ALL),
        new ChorusfixConfig.ProviderHookSettings(true, true, true, true),
        new ChorusfixConfig.ProviderConfigDiagnosticsSettings(true, true, true, true),
        false);
  }
}
