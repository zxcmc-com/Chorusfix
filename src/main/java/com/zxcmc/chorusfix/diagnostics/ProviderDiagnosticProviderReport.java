package com.zxcmc.chorusfix.diagnostics;

import java.util.List;

public record ProviderDiagnosticProviderReport(
    String name,
    boolean enabled,
    boolean available,
    int scanned,
    List<ProviderDiagnosticWarning> warnings,
    String detail) {
  public int warningCount() {
    return warnings.size();
  }

  public static ProviderDiagnosticProviderReport disabled(String name) {
    return new ProviderDiagnosticProviderReport(
        name, false, false, 0, List.of(), "disabled by config");
  }
}
