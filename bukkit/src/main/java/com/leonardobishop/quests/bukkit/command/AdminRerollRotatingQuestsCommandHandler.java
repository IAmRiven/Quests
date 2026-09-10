package com.leonardobishop.quests.bukkit.command;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AdminRerollRotatingQuestsCommandHandler implements CommandHandler {

    private final BukkitQuestsPlugin plugin;

    public AdminRerollRotatingQuestsCommandHandler(BukkitQuestsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        plugin.getDailyQuestManager().forceRegenerateAll();
        sender.sendMessage(ChatColor.GREEN + "Misiones diarias y semanales regeneradas correctamente.");
        sender.sendMessage(ChatColor.GRAY + "Se ha limpiado el progreso y la misión seguida de las rotativas activas.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public @Nullable String getPermission() {
        return "quests.admin";
    }
}