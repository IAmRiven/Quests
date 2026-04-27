package com.leonardobishop.quests.common.quest;

import java.util.HashMap;
import java.util.Map;

/**
 * Sistema de experiencia y niveles para categorías.
 */
public class CategoryXP {
    private final String categoryId;
    private final int maxLevel;
    private final int initialLevel;
    private final int[] expPerLevel;
    private final Map<String, Integer> playerLevels = new HashMap<>();
    private final Map<String, Integer> playerExp = new HashMap<>();

    public CategoryXP(String categoryId, int maxLevel, int initialLevel, int[] expPerLevel) {
        this.categoryId = categoryId;
        this.maxLevel = maxLevel;
        this.initialLevel = initialLevel;
        this.expPerLevel = expPerLevel;
    }

    public int getLevel(String playerId) {
        return playerLevels.getOrDefault(playerId, initialLevel);
    }

    public int getExp(String playerId) {
        return playerExp.getOrDefault(playerId, 0);
    }

    public void addExp(String playerId, int amount) {
        int exp = getExp(playerId) + amount;
        int level = getLevel(playerId);
        while (level < maxLevel && exp >= expPerLevel[level - initialLevel]) {
            exp -= expPerLevel[level - initialLevel];
            level++;
        }
        playerLevels.put(playerId, level);
        playerExp.put(playerId, exp);
    }

    public void addLevel(String playerId, int amount) {
        int level = Math.min(getLevel(playerId) + amount, maxLevel);
        playerLevels.put(playerId, level);
        // Opcional: resetear exp al subir de nivel manualmente
        // playerExp.put(playerId, 0);
    }

    /**
     * Establece el nivel absoluto de un jugador para esta categoría.
     * @param playerId id del jugador (UUID como String)
     * @param level nivel a asignar
     */
    public void setLevel(String playerId, int level) {
        if (level < initialLevel) level = initialLevel;
        if (level > maxLevel) level = maxLevel;
        playerLevels.put(playerId, level);
    }

    /**
     * Establece la experiencia absoluta de un jugador para esta categoría.
     * @param playerId id del jugador (UUID como String)
     * @param exp experiencia a asignar (no negativa)
     */
    public void setExp(String playerId, int exp) {
        if (exp < 0) exp = 0;
        playerExp.put(playerId, exp);
    }

    public String getCategoryId() {
        return categoryId;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getInitialLevel() {
        return initialLevel;
    }

    public int[] getExpPerLevel() {
        return expPerLevel;
    }
}
