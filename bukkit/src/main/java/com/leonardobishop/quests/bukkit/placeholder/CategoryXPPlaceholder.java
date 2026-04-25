package com.leonardobishop.quests.bukkit.placeholder;


import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.common.quest.Category;
import com.leonardobishop.quests.common.quest.CategoryXP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class CategoryXPPlaceholder {
    /**
     * Devuelve una barra de progreso de experiencia para la categoría del jugador.
     * Ejemplo de uso: %quests_categoryxp:categoria_bar%
     */
    public static String getCategoryXPBar(Player player, Category category) {
        FileConfiguration config = BukkitQuestsPlugin.getPlugin(BukkitQuestsPlugin.class).getConfig();
        if (category == null) return config.getString("messages.categoryxp-bar-category-not-found", "Categoría no encontrada");
        CategoryXP xp = category.getCategoryXP();
        int level, exp, maxLevel, initialLevel, expToNext;
        int[] expPerLevel;
        if (xp == null) {
            // Si la categoría no tiene XP configurado, usar valores por defecto
            level = 1;
            exp = 0;
            maxLevel = 1;
            initialLevel = 1;
            expPerLevel = new int[]{100};
        } else {
            String playerId = player.getUniqueId().toString();
            level = xp.getLevel(playerId);
            exp = xp.getExp(playerId);
            expPerLevel = xp.getExpPerLevel();
            maxLevel = xp.getMaxLevel();
            initialLevel = xp.getInitialLevel();
        }
        int relLevel = level - initialLevel;
        expToNext = (relLevel < expPerLevel.length && relLevel >= 0) ? expPerLevel[relLevel] : 1;
        double percent = Math.min(1.0, expToNext > 0 ? (double) exp / expToNext : 1.0);
        String levelText = config.getString("messages.categoryxp-bar-level", "Lvl");
        String levelColor = config.getString("messages.categoryxp-bar-level-color", "§e");
        String sep = config.getString("messages.categoryxp-bar-separator", "/");
        String template = config.getString("messages.categoryxp-bar-template", "{levelcolor}{level} {leveltext} {bar} {exp}{sep}{maxexp}");
        String filledChar = config.getString("messages.categoryxp-bar-bar-filled", "§a|");
        String emptyChar = config.getString("messages.categoryxp-bar-bar-empty", "§7|");
        int barLength = config.getInt("messages.categoryxp-bar-bar-length", 20);
        int filled = (int) Math.round(barLength * percent);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < filled; i++) bar.append(filledChar);
        for (int i = filled; i < barLength; i++) bar.append(emptyChar);
        return template
            .replace("{levelcolor}", levelColor)
            .replace("{level}", String.valueOf(level))
            .replace("{leveltext}", levelText)
            .replace("{bar}", bar.toString())
            .replace("{exp}", String.valueOf(exp))
            .replace("{sep}", sep)
            .replace("{maxexp}", String.valueOf(expToNext));
    }
}
