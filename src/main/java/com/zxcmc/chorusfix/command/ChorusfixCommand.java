package com.zxcmc.chorusfix.command;

import com.zxcmc.chorusfix.ChorusUpdateService;
import com.zxcmc.chorusfix.ChorusfixConfig;
import com.zxcmc.chorusfix.ChorusfixPlugin;
import com.zxcmc.chorusfix.PaperChorusPlantUpdates;
import com.zxcmc.chorusfix.diagnostics.ProviderDiagnosticProviderReport;
import com.zxcmc.chorusfix.diagnostics.ProviderDiagnosticsReport;
import com.zxcmc.chorusfix.provider.ProviderHookStatus;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public final class ChorusfixCommand extends Command {
  private static final String PERMISSION = "chorusfix.admin";
  private final ChorusfixPlugin plugin;

  public ChorusfixCommand(ChorusfixPlugin plugin) {
    super("chorusfix", "Chorusfix administration.", "/chorusfix status|reload", List.of("cf"));
    this.plugin = plugin;
    setPermission(PERMISSION);
  }

  @Override
  public boolean execute(CommandSender sender, String commandLabel, String[] args) {
    if (!sender.hasPermission(PERMISSION)) {
      sender.sendMessage(
          Component.text("You do not have permission to use this command.", NamedTextColor.RED));
      return true;
    }
    String subcommand = args.length == 0 ? "status" : args[0].toLowerCase();
    switch (subcommand) {
      case "status" -> sendStatus(sender);
      case "reload" -> {
        plugin.reloadPlugin();
        sender.sendMessage(
            Component.text("Chorusfix configuration reloaded.", NamedTextColor.GREEN));
        sendStatus(sender);
      }
      default ->
          sender.sendMessage(
              Component.text("Usage: /chorusfix status|reload", NamedTextColor.YELLOW));
    }
    return true;
  }

  @Override
  public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
    if (!sender.hasPermission(PERMISSION) || args.length != 1) {
      return List.of();
    }
    String prefix = args[0].toLowerCase();
    List<String> completions = new ArrayList<>();
    for (String option : List.of("status", "reload")) {
      if (option.startsWith(prefix)) {
        completions.add(option);
      }
    }
    return completions;
  }

  private void sendStatus(CommandSender sender) {
    ChorusfixConfig config = plugin.settings();
    PaperChorusPlantUpdates.RuntimeState paper = plugin.paperState();
    ChorusUpdateService.Status status = plugin.updateService().status();
    sender.sendMessage(
        Component.text("Chorusfix " + plugin.getPluginMeta().getVersion(), NamedTextColor.GOLD));
    sender.sendMessage(
        Component.text(
            "enabled="
                + config.enabled()
                + ", active="
                + status.active()
                + ", "
                + PaperChorusPlantUpdates.SETTING_PATH
                + "="
                + paper.displayValue(),
            NamedTextColor.GRAY));
    sender.sendMessage(
        Component.text(
            "queue="
                + status.queued()
                + ", processed="
                + status.processedTotal()
                + ", broken="
                + status.brokenTotal()
                + ", corrected="
                + status.correctedTotal()
                + ", skipped="
                + status.skippedTotal(),
            NamedTextColor.GRAY));
    for (ProviderHookStatus hook : plugin.providerStatuses()) {
      sender.sendMessage(
          Component.text(
              "hook "
                  + hook.name()
                  + ": enabled="
                  + hook.enabled()
                  + ", available="
                  + hook.available()
                  + ", "
                  + hook.detail(),
              NamedTextColor.GRAY));
    }
    ProviderDiagnosticsReport diagnostics = plugin.diagnosticsReport();
    sender.sendMessage(
        Component.text(
            "diagnostics enabled="
                + diagnostics.enabled()
                + ", scanned="
                + diagnostics.scannedCount()
                + ", warnings="
                + diagnostics.warningCount(),
            NamedTextColor.GRAY));
    for (ProviderDiagnosticProviderReport provider : diagnostics.providers()) {
      sender.sendMessage(
          Component.text(
              "diagnostic "
                  + provider.name()
                  + ": enabled="
                  + provider.enabled()
                  + ", available="
                  + provider.available()
                  + ", scanned="
                  + provider.scanned()
                  + ", warnings="
                  + provider.warningCount()
                  + ", "
                  + provider.detail(),
              NamedTextColor.GRAY));
    }
  }
}
