package com.leonardobishop.quests.bukkit.menu;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.menu.element.MenuElement;
import com.leonardobishop.quests.bukkit.menu.element.QuestMenuElement;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.quest.Quest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class DailyQMenu extends QMenu {

    private final String title;

    public DailyQMenu(BukkitQuestsPlugin plugin, QPlayer owner, List<Quest> dailyQuests, List<Quest> weeklyQuests) {
        super(owner);
        this.title = "§8Misiones diarias y semanales";

        int[] dailySlots = {2, 4, 6};
        for (int i = 0; i < Math.min(dailySlots.length, dailyQuests.size()); i++) {
            menuElements.put(dailySlots[i], new QuestMenuElement(plugin, dailyQuests.get(i), this));
        }

        int[] weeklySlots = {10, 11, 13, 15, 16};
        for (int i = 0; i < Math.min(weeklySlots.length, weeklyQuests.size()); i++) {
            menuElements.put(weeklySlots[i], new QuestMenuElement(plugin, weeklyQuests.get(i), this));
        }
    }

    @Override
    Inventory draw() {
        Inventory inventory = Bukkit.createInventory(null, 18, title);
        ItemStack filler = new ItemStack(Material.AIR);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            MenuElement element = menuElements.get(slot);
            if (element != null) {
                inventory.setItem(slot, element.asItemStack());
            }
        }

        return inventory;
    }
}