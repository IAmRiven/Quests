package com.leonardobishop.quests.bukkit.command;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.util.CommandUtils;
import com.leonardobishop.quests.common.player.QPlayer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AdminReloadCommandHandler implements CommandHandler {

    private final BukkitQuestsPlugin plugin;

    public AdminReloadCommandHandler(BukkitQuestsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.GRAY + "Please note that some options, such as storage, require a full restart for chances to take effect.");

        for (final QPlayer qPlayer : plugin.getPlayerManager().getQPlayers()) {
            try {
                plugin.getPlayerManager().savePlayerSync(qPlayer.getPlayerUUID());
            } catch (final Exception e) {
                plugin.getLogger().warning("Failed to save player data for " + qPlayer.getPlayerUUID() + " before reload.");
            }
        }

        plugin.reloadQuests();
        if (!plugin.getConfigProblems().isEmpty()) CommandUtils.showProblems(sender, plugin.getConfigProblems());
        sender.sendMessage(ChatColor.GREEN + "Quests successfully reloaded.");
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
