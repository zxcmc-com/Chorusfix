package com.zxcmc.chorusfix.diagnostics;

public record ProviderDiagnosticWarning(
    String provider, String location, String itemId, String detail) {}
