package com.leonardobishop.quests.common.quest;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestor de sistemas de experiencia por categoría.
 */
public class CategoryXPManager {
    private final Map<String, CategoryXP> categoryXPMap = new HashMap<>();

    public void addCategoryXP(CategoryXP categoryXP) {
        categoryXPMap.put(categoryXP.getCategoryId(), categoryXP);
    }

    public CategoryXP getCategoryXP(String categoryId) {
        return categoryXPMap.get(categoryId);
    }

    public void addExp(String categoryId, String playerId, int amount) {
        CategoryXP xp = getCategoryXP(categoryId);
        if (xp != null) {
            xp.addExp(playerId, amount);
        }
    }

    public void addLevel(String categoryId, String playerId, int amount) {
        CategoryXP xp = getCategoryXP(categoryId);
        if (xp != null) {
            xp.addLevel(playerId, amount);
        }
    }
}
