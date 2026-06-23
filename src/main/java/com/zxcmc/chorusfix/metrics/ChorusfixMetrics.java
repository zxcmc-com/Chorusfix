package com.zxcmc.chorusfix.metrics;

import com.zxcmc.chorusfix.ChorusfixConfig;
import com.zxcmc.chorusfix.ChorusfixPlugin;
import com.zxcmc.chorusfix.PaperChorusPlantUpdates;
import com.zxcmc.chorusfix.diagnostics.ProviderDiagnosticProviderReport;
import com.zxcmc.chorusfix.diagnostics.ProviderDiagnosticsReport;
import com.zxcmc.chorusfix.provider.ProviderHookStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;

public final class ChorusfixMetrics {
  private static final int BSTATS_PLUGIN_ID = 32157;

  private ChorusfixMetrics() {}

  public static Metrics create(ChorusfixPlugin plugin) {
    Metrics metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
    metrics.addCustomChart(
        new AdvancedPie(
            "custom_block_providers",
            () -> customBlockProvidersChartValues(plugin.providerStatuses())));
    metrics.addCustomChart(
        new SimplePie(
            "runtime_state", () -> runtimeStateChartValue(plugin.settings(), plugin.paperState())));
    metrics.addCustomChart(
        new SimplePie(
            "unsafe_provider_configs",
            () -> unsafeProviderConfigsChartValue(plugin.diagnosticsReport())));
    return metrics;
  }

  static Map<String, Integer> customBlockProvidersChartValues(List<ProviderHookStatus> statuses) {
    Map<String, Integer> values = new LinkedHashMap<>();
    for (ProviderHookStatus status : statuses) {
      if (!status.enabled() || !status.available()) {
        continue;
      }
      String category = providerCategory(status.name());
      if (category != null) {
        values.put(category, 1);
      }
    }
    if (values.isEmpty()) {
      values.put("none_detected", 1);
    }
    return values;
  }

  static String runtimeStateChartValue(
      ChorusfixConfig config, PaperChorusPlantUpdates.RuntimeState paperState) {
    if (!config.enabled()) {
      return "disabled_by_config";
    }
    if (!config.onlyWhenPaperDisabled()) {
      return "active";
    }
    if (paperState.disabled().isEmpty()) {
      return "paper_state_unknown";
    }
    return paperState.disabledTrue() ? "active" : "inactive_paper_updates_enabled";
  }

  static String unsafeProviderConfigsChartValue(ProviderDiagnosticsReport report) {
    if (!report.enabled()) {
      return "diagnostics_disabled";
    }
    String unsafeProvider = null;
    int unsafeProviders = 0;
    for (ProviderDiagnosticProviderReport provider : report.providers()) {
      if (provider.warningCount() == 0) {
        continue;
      }
      String category = providerCategory(provider.name());
      if (category == null) {
        continue;
      }
      unsafeProvider = category;
      unsafeProviders++;
    }
    if (unsafeProviders == 0) {
      return "none";
    }
    return unsafeProviders == 1 ? unsafeProvider : "multiple";
  }

  private static String providerCategory(String providerName) {
    return switch (providerName.toLowerCase(Locale.ROOT)) {
      case "nexo" -> "nexo";
      case "itemsadder" -> "itemsadder";
      case "oraxen" -> "oraxen";
      case "exort" -> "exort";
      default -> null;
    };
  }
}
