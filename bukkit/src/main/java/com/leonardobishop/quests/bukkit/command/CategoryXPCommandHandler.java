package com.leonardobishop.quests.bukkit.command;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.quest.Category;
import com.leonardobishop.quests.common.quest.CategoryXP;
import com.leonardobishop.quests.common.quest.CategoryXPManager;
import com.leonardobishop.quests.bukkit.util.Messages;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class CategoryXPCommandHandler implements CommandHandler {
    private final BukkitQuestsPlugin plugin;

    public CategoryXPCommandHandler(BukkitQuestsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Messages.COMMAND_CATEGORYXP_USAGE.send(sender);
            return;
        }
        String categoryId = args[1];
        String type = args[2];
        String amountStr = args[3];
        int amount;
        try {
            amount = Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            Messages.COMMAND_CATEGORYXP_INVALID_AMOUNT.send(sender, "{amount}", amountStr);
            return;
        }
        String targetName = args.length >= 5 ? args[4] : (sender instanceof Player ? ((Player) sender).getName() : null);
        if (targetName == null) {
            Messages.COMMAND_CATEGORYXP_USAGE.send(sender);
            return;
        }
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado: " + targetName);
            return;
        }
        Category category = plugin.getQuestManager().getCategoryById(categoryId);
        if (category == null) {
            Messages.COMMAND_CATEGORYXP_CATEGORY_NOT_FOUND.send(sender, "{category}", categoryId);
            return;
        }
        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(target.getUniqueId());
        if (qPlayer == null) {
            Messages.COMMAND_CATEGORYXP_DATA_NOT_LOADED.send(sender);
            return;
        }
        CategoryXP xp = category.getCategoryXP();
        if (xp == null) {
            Messages.COMMAND_CATEGORYXP_NO_XP_SYSTEM.send(sender);
            return;
        }
        String playerId = target.getUniqueId().toString();
        if (type.equalsIgnoreCase("exp")) {
            xp.addExp(playerId, amount);
            Messages.COMMAND_CATEGORYXP_GIVEN_EXP.send(sender, "{amount}", String.valueOf(amount), "{category}", categoryId);
        } else if (type.equalsIgnoreCase("level")) {
            xp.addLevel(playerId, amount);
            Messages.COMMAND_CATEGORYXP_GIVEN_LEVEL.send(sender, "{amount}", String.valueOf(amount), "{category}", categoryId);
        } else {
            Messages.COMMAND_CATEGORYXP_INVALID_TYPE.send(sender, "{type}", type);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // Opcional: autocompletado de categorías y tipo
        return Collections.emptyList();
    }

    @Override
    public @Nullable String getPermission() {
        return "quests.command.categoryxp";
    }
}
