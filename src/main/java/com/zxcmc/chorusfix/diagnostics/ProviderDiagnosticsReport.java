package com.zxcmc.chorusfix.diagnostics;

import java.util.List;

public record ProviderDiagnosticsReport(
    boolean enabled, List<ProviderDiagnosticProviderReport> providers) {
  public static ProviderDiagnosticsReport disabled() {
    return new ProviderDiagnosticsReport(false, List.of());
  }

  public int warningCount() {
    int warnings = 0;
    for (ProviderDiagnosticProviderReport provider : providers) {
      warnings += provider.warningCount();
    }
    return warnings;
  }

  public int scannedCount() {
    int scanned = 0;
    for (ProviderDiagnosticProviderReport provider : providers) {
      scanned += provider.scanned();
    }
    return scanned;
  }
}
