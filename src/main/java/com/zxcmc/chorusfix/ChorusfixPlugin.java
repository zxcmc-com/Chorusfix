package com.zxcmc.chorusfix;

import com.zxcmc.chorusfix.command.ChorusfixCommand;
import com.zxcmc.chorusfix.diagnostics.ProviderConfigDiagnostics;
import com.zxcmc.chorusfix.diagnostics.ProviderDiagnosticsReport;
import com.zxcmc.chorusfix.provider.CustomChorusBlockDetector;
import com.zxcmc.chorusfix.provider.ProviderHookStatus;
import com.zxcmc.chorusfix.provider.ProviderHooks;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChorusfixPlugin extends JavaPlugin {
  private ChorusfixConfig settings;
  private PaperChorusPlantUpdates.RuntimeState paperState;
  private CustomChorusBlockDetector detector;
  private ProviderConfigDiagnostics diagnostics;
  private ProviderDiagnosticsReport diagnosticsReport = ProviderDiagnosticsReport.disabled();
  private ChorusUpdateService updates;
  private ChorusfixCommand command;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    diagnostics = new ProviderConfigDiagnostics(this, getLogger());
    reloadPlugin();
    updates = new ChorusUpdateService(this, settings, detector, paperState);
    Bukkit.getPluginManager().registerEvents(new ChorusUpdateListener(updates), this);
    registerCommand();
    getLogger()
        .info(
            "Loaded. active="
                + updates.active()
                + ", "
                + PaperChorusPlantUpdates.SETTING_PATH
                + "="
                + paperState.displayValue());
  }

  @Override
  public void onDisable() {
    if (updates != null) {
      updates.shutdown();
    }
    if (command != null) {
      command.unregister(Bukkit.getCommandMap());
      command = null;
    }
  }

  public void reloadPlugin() {
    reloadConfig();
    settings = ChorusfixConfig.from(getConfig(), getLogger());
    paperState = PaperChorusPlantUpdates.runtimeState();
    detector = ProviderHooks.create(settings.providerHooks(), getLogger(), settings.debug());
    diagnosticsReport = diagnostics.run(settings.providerConfigDiagnostics());
    if (updates != null) {
      updates.update(settings, detector, paperState);
    }
  }

  public ChorusfixConfig settings() {
    return settings;
  }

  public PaperChorusPlantUpdates.RuntimeState paperState() {
    return paperState;
  }

  public ChorusUpdateService updateService() {
    return updates;
  }

  public List<ProviderHookStatus> providerStatuses() {
    return detector.statuses();
  }

  public ProviderDiagnosticsReport diagnosticsReport() {
    return diagnosticsReport;
  }

  private void registerCommand() {
    CommandMap commandMap = Bukkit.getCommandMap();
    command = new ChorusfixCommand(this);
    commandMap.register("chorusfix", command);
  }
}
