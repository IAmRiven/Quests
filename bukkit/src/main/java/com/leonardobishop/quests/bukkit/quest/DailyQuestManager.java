package com.leonardobishop.quests.bukkit.quest;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.menu.itemstack.QItemStack;
import com.leonardobishop.quests.bukkit.scheduler.WrappedTask;
import com.leonardobishop.quests.bukkit.util.chat.Chat;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.QuestProgressFile;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DailyQuestManager {

    private static final String DAILY_PREFIX = "daily_auto_";
    private static final String WEEKLY_PREFIX = "weekly_auto_";
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final WeekFields WEEK_FIELDS = WeekFields.ISO;
    private static final Map<String, String> DISPLAY_NAMES = createDisplayNames();

    private final BukkitQuestsPlugin plugin;
    private final List<Quest> activeDailyQuests = new ArrayList<>();
    private final List<Quest> activeWeeklyQuests = new ArrayList<>();
    private YamlConfiguration rotatingRewardsConfig = new YamlConfiguration();
    private WrappedTask dailyRefreshTask;
    private WrappedTask weeklyRefreshTask;
    private LocalDate activeDailyDate;
    private String activeWeeklyKey;
    private int dailyRerollVersion;
    private int weeklyRerollVersion;

    public DailyQuestManager(BukkitQuestsPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload() {
        loadRotatingRewardsConfig();
        refreshDailyIfNeeded(true);
        refreshWeeklyIfNeeded(true);
        scheduleDailyRefresh();
        scheduleWeeklyRefresh();
    }

    public synchronized List<Quest> getActiveQuests() {
        return getActiveDailyQuests();
    }

    public synchronized List<Quest> getActiveDailyQuests() {
        refreshDailyIfNeeded(false);
        return Collections.unmodifiableList(new ArrayList<>(activeDailyQuests));
    }

    public synchronized List<Quest> getActiveWeeklyQuests() {
        refreshWeeklyIfNeeded(false);
        return Collections.unmodifiableList(new ArrayList<>(activeWeeklyQuests));
    }

    public synchronized void handlePlayerLoad(QPlayer qPlayer) {
        refreshDailyIfNeeded(false);
        refreshWeeklyIfNeeded(false);
        clearStaleDailyProgress(qPlayer);
        clearStaleWeeklyProgress(qPlayer);
    }

    public synchronized void forceRegenerateAll() {
        dailyRerollVersion++;
        weeklyRerollVersion++;

        for (QPlayer qPlayer : plugin.getPlayerManager().getQPlayers()) {
            clearRotatingQuestProgress(qPlayer);
        }

        refreshDailyIfNeeded(true);
        refreshWeeklyIfNeeded(true);
        scheduleDailyRefresh();
        scheduleWeeklyRefresh();
    }

    private void clearRotatingQuestProgress(QPlayer qPlayer) {
        QuestProgressFile progressFile = qPlayer.getQuestProgressFile();
        List<String> rotatingIds = progressFile.getQuestProgressMap().keySet().stream()
                .filter(id -> id.startsWith(DAILY_PREFIX) || id.startsWith(WEEKLY_PREFIX))
                .toList();

        for (String rotatingId : rotatingIds) {
            progressFile.getQuestProgressMap().remove(rotatingId);
        }

        String trackedQuestId = qPlayer.getPlayerPreferences().getTrackedQuestId();
        if (trackedQuestId != null && (trackedQuestId.startsWith(DAILY_PREFIX) || trackedQuestId.startsWith(WEEKLY_PREFIX))) {
            qPlayer.getPlayerPreferences().setTrackedQuestId(null);
        }
    }

    private void clearStaleDailyProgress(QPlayer qPlayer) {
        QuestProgressFile progressFile = qPlayer.getQuestProgressFile();
        Set<String> activeIds = activeDailyQuests.stream().map(Quest::getId).collect(Collectors.toSet());
        List<String> staleIds = progressFile.getQuestProgressMap().keySet().stream()
                .filter(id -> id.startsWith(DAILY_PREFIX) && !activeIds.contains(id))
                .toList();

        for (String staleId : staleIds) {
            progressFile.getQuestProgressMap().remove(staleId);
        }

        String trackedQuestId = qPlayer.getPlayerPreferences().getTrackedQuestId();
        if (trackedQuestId != null && trackedQuestId.startsWith(DAILY_PREFIX) && !activeIds.contains(trackedQuestId)) {
            qPlayer.getPlayerPreferences().setTrackedQuestId(null);
        }
    }

    private void clearStaleWeeklyProgress(QPlayer qPlayer) {
        QuestProgressFile progressFile = qPlayer.getQuestProgressFile();
        Set<String> activeIds = activeWeeklyQuests.stream().map(Quest::getId).collect(Collectors.toSet());
        List<String> staleIds = progressFile.getQuestProgressMap().keySet().stream()
                .filter(id -> id.startsWith(WEEKLY_PREFIX) && !activeIds.contains(id))
                .toList();

        for (String staleId : staleIds) {
            progressFile.getQuestProgressMap().remove(staleId);
        }

        String trackedQuestId = qPlayer.getPlayerPreferences().getTrackedQuestId();
        if (trackedQuestId != null && trackedQuestId.startsWith(WEEKLY_PREFIX) && !activeIds.contains(trackedQuestId)) {
            qPlayer.getPlayerPreferences().setTrackedQuestId(null);
        }
    }

    private synchronized void refreshDailyIfNeeded(boolean force) {
        LocalDate today = LocalDate.now(ZONE);
        if (!force && Objects.equals(activeDailyDate, today) && !activeDailyQuests.isEmpty()) {
            return;
        }

        activeDailyQuests.clear();
        activeDailyDate = today;

        long seed = today.toEpochDay();
        Random random = new Random(seed);
        List<Supplier<Quest>> dailyPool = List.of(
                () -> createDailyMiningQuest(seed, random),
                () -> createDailyFarmingQuest(seed, random),
                () -> createDailyMobQuest(seed, random),
                () -> createDailyWoodcuttingQuest(seed, random),
                () -> createDailySmeltingQuest(seed, random),
                () -> createDailyCraftingQuest(seed, random),
                () -> createDailyFishingQuest(seed, random),
                () -> createDailyInventoryQuest(seed, random)
        );
        List<Quest> generated = pickQuests(dailyPool, 3, seed * 13L + 5L + dailyRerollVersion * 101L);

        registerGeneratedQuests(generated, activeDailyQuests);

        cleanupLoadedPlayers();
    }

    private synchronized void refreshWeeklyIfNeeded(boolean force) {
        LocalDate today = LocalDate.now(ZONE);
        String weeklyKey = today.getYear() + "-W" + today.get(WEEK_FIELDS.weekOfWeekBasedYear());
        if (!force && Objects.equals(activeWeeklyKey, weeklyKey) && !activeWeeklyQuests.isEmpty()) {
            return;
        }

        activeWeeklyQuests.clear();
        activeWeeklyKey = weeklyKey;

        long seed = today.getYear() * 100L + today.get(WEEK_FIELDS.weekOfWeekBasedYear());
        Random random = new Random(seed * 31L + 17L);
        List<Supplier<Quest>> weeklyPool = List.of(
                () -> createWeeklyMiningQuest(seed, random),
                () -> createWeeklyFarmingQuest(seed, random),
                () -> createWeeklyMobQuest(seed, random),
                () -> createWeeklyFishingQuest(seed, random),
                () -> createWeeklyWalkingQuest(seed, random),
                () -> createWeeklyWoodcuttingQuest(seed, random),
                () -> createWeeklySmeltingQuest(seed, random),
                () -> createWeeklyCraftingQuest(seed, random),
                () -> createWeeklyTamingQuest(seed, random),
                () -> createWeeklyGatheringQuest(seed, random),
                () -> createWeeklyFarmAnimalQuest(seed, random),
                () -> createWeeklyMountedTravelQuest(seed, random)
        );
        List<Quest> generated = pickQuests(weeklyPool, 5, seed * 29L + 11L + weeklyRerollVersion * 131L);

        registerGeneratedQuests(generated, activeWeeklyQuests);

        cleanupLoadedPlayers();
    }

    private void registerGeneratedQuests(List<Quest> generated, List<Quest> target) {
        for (Quest quest : generated) {
            plugin.getQuestManager().registerQuest(quest);
            plugin.getTaskTypeManager().registerQuestTasksWithTaskTypes(quest);
            target.add(quest);
        }
    }

    private List<Quest> pickQuests(List<Supplier<Quest>> pool, int amount, long shuffleSeed) {
        List<Supplier<Quest>> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, new Random(shuffleSeed));
        List<Quest> picked = new ArrayList<>();
        for (int i = 0; i < Math.min(amount, shuffled.size()); i++) {
            picked.add(shuffled.get(i).get());
        }
        return picked;
    }

    private void cleanupLoadedPlayers() {
        for (QPlayer qPlayer : plugin.getPlayerManager().getQPlayers()) {
            clearStaleDailyProgress(qPlayer);
            clearStaleWeeklyProgress(qPlayer);
        }
    }

    private void scheduleDailyRefresh() {
        if (dailyRefreshTask != null && !dailyRefreshTask.isCancelled()) {
            dailyRefreshTask.cancel();
        }

        long delayTicks = Math.max(20L, Duration.between(Instant.now(), LocalDate.now(ZONE).plusDays(1).atStartOfDay(ZONE).toInstant()).getSeconds() * 20L);
        dailyRefreshTask = plugin.getScheduler().runTaskLater(() -> {
            reload();
        }, delayTicks);
    }

    private void scheduleWeeklyRefresh() {
        if (weeklyRefreshTask != null && !weeklyRefreshTask.isCancelled()) {
            weeklyRefreshTask.cancel();
        }

        LocalDate nextWeek = LocalDate.now(ZONE).plusWeeks(1).with(WEEK_FIELDS.dayOfWeek(), 1);
        long delayTicks = Math.max(20L, Duration.between(Instant.now(), nextWeek.atStartOfDay(ZONE).toInstant()).getSeconds() * 20L);
        weeklyRefreshTask = plugin.getScheduler().runTaskLater(() -> {
            reload();
        }, delayTicks);
    }

    private Quest createDailyMiningQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE, Material.STONE, Material.OAK_LOG);
        Material material = pool.get(Math.floorMod((int) (seed + 3), pool.size()));
        int amount = 48 + Math.floorMod((int) seed, 33);
        String questId = DAILY_PREFIX + activeDailyDate + versionSuffix(dailyRerollVersion) + "_mine";

        Task task = new Task("mine", "blockbreak");
        task.addConfigValue("amount", amount);
        task.addConfigValue("block", material.name());

        return buildQuest(questId,
                "&eMisión de minería &8- &eMinero del día",
                List.of(
                        "&7Rompe &e" + amount + " &7bloques de &6" + prettify(material.name()) + "&7.",
                        "&8Se reinicia con la rotación diaria."
                ),
                new ItemStack(material),
                task,
                 325 + random.nextInt(126),
                 90 + random.nextInt(41));
    }

    private Quest createDailyFarmingQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);
        Material material = pool.get(Math.floorMod((int) (seed + 7), pool.size()));
        Material iconMaterial = switch (material) {
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            default -> material;
        };
        int amount = 40 + Math.floorMod((int) (seed * 3), 31);
        String questId = DAILY_PREFIX + activeDailyDate + versionSuffix(dailyRerollVersion) + "_farm";

        Task task = new Task("farm", "farming");
        task.addConfigValue("amount", amount);
        task.addConfigValue("block", material.name());
        task.addConfigValue("mode", "break");

        return buildQuest(questId,
                "&eMisión de cultivo &8- &aCosecha fresca",
                List.of(
                        "&7Recoge &e" + amount + " &7cultivos de &a" + prettify(material.name()) + "&7.",
                        "&8Solo cuenta la cosecha del día."
                ),
                new ItemStack(iconMaterial),
                task,
                 280 + random.nextInt(111),
                 80 + random.nextInt(41));
    }

    private Quest createDailyMobQuest(long seed, Random random) {
        List<EntityType> pool = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER);
        EntityType entityType = pool.get(Math.floorMod((int) (seed + 11), pool.size()));
        int amount = 14 + Math.floorMod((int) (seed * 5), 11);
        String questId = DAILY_PREFIX + activeDailyDate + versionSuffix(dailyRerollVersion) + "_mob";

        Task task = new Task("mob", "mobkilling");
        task.addConfigValue("amount", amount);
        task.addConfigValue("mob", entityType.name());

        Material icon = switch (entityType) {
            case ZOMBIE -> Material.ROTTEN_FLESH;
            case SKELETON -> Material.BONE;
            case CREEPER -> Material.GUNPOWDER;
            default -> Material.STRING;
        };

        return buildQuest(questId,
                "&eMisión de caza &8- &cCazador del día",
                List.of(
                        "&7Elimina &e" + amount + " &7enemigos tipo &c" + prettify(entityType.name()) + "&7.",
                        "&8La misión cambia cada 24 horas."
                ),
                new ItemStack(icon),
                task,
                 360 + random.nextInt(141),
                 110 + random.nextInt(51));
    }

    private Quest createDailyWoodcuttingQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG, Material.DARK_OAK_LOG);
        Material material = pool.get(Math.floorMod((int) (seed + 17), pool.size()));
        int amount = 36 + Math.floorMod((int) (seed * 2), 29);
        Task task = new Task("wood", "blockbreak");
        task.addConfigValue("amount", amount);
        task.addConfigValue("block", material.name());

        return buildQuest(DAILY_PREFIX + activeDailyDate + versionSuffix(dailyRerollVersion) + "_wood",
                "&eMisión de leñador &8- &6Madera del día",
                List.of(
                        "&7Tala &e" + amount + " &7bloques de &6" + prettify(material.name()) + "&7.",
                        "&8Perfecta para constructores y granjeros."
                ),
                new ItemStack(material),
                task,
                290 + random.nextInt(121),
                85 + random.nextInt(41));
    }

    private Quest createDailySmeltingQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.IRON_INGOT, Material.GOLD_INGOT, Material.GLASS, Material.STONE, Material.BAKED_POTATO);
        Material material = pool.get(Math.floorMod((int) (seed + 19), pool.size()));
        int amount = 20 + Math.floorMod((int) (seed * 4), 17);
        Task task = new Task("smelt", "smelting");
        task.addConfigValue("amount", amount);
        task.addConfigValue("item", material.name());

        return buildQuest(DAILY_PREFIX + activeDailyDate + versionSuffix(dailyRerollVersion) + "_smelt",
                "&eMisión de horno &8- &cCocina activa",
                List.of(
                        "&7Cocina u hornea &e" + amount + " &7de &6" + prettify(material.name()) + "&7.",
                        "&8Cuenta lo retirado de hornos, hornos altos y ahumadores."
                ),
                new ItemStack(material),
                task,
                300 + random.nextInt(121),
                90 + random.nextInt(41));
    }

    private Quest createDailyCraftingQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.TORCH, Material.CHEST, Material.BREAD, Material.OAK_PLANKS, Material.FURNACE);
        Material material = pool.get(Math.floorMod((int) (seed + 23), pool.size()));
        int amount = 12 + Math.floorMod((int) (seed * 3), 11);
        Task task = new Task("craft", "crafting");
        task.addConfigValue("amount", amount);
        task.addConfigValue("item", material.name());

        return buildQuest(DAILY_PREFIX + activeDailyDate + versionSuffix(dailyRerollVersion) + "_craft",
                "&eMisión de crafteo &8- &fManos a la obra",
                List.of(
                        "&7Craftea &e" + amount + " &7unidades de &6" + prettify(material.name()) + "&7.",
                        "&8Ideal para jugadores productivos."
                ),
                new ItemStack(material),
                task,
                275 + random.nextInt(116),
                75 + random.nextInt(41));
    }

    private Quest createDailyFishingQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.COD, Material.SALMON, Material.PUFFERFISH, Material.TROPICAL_FISH);
        Material material = pool.get(Math.floorMod((int) (seed + 29), pool.size()));
        int amount = 10 + Math.floorMod((int) (seed * 5), 8);
        Task task = new Task("fish", "fishing");
        task.addConfigValue("amount", amount);
        task.addConfigValue("item", material.name());

        return buildQuest(DAILY_PREFIX + activeDailyDate + versionSuffix(dailyRerollVersion) + "_fish",
                "&eMisión de pesca &8- &bCaptura diaria",
                List.of(
                        "&7Pesca &e" + amount + " &7de &b" + prettify(material.name()) + "&7.",
                        "&8Solo cuentan capturas reales de pesca."
                ),
                new ItemStack(material),
                task,
                260 + random.nextInt(101),
                70 + random.nextInt(31));
    }

    private Quest createDailyInventoryQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.COBBLESTONE, Material.WHEAT, Material.COAL, Material.OAK_LOG, Material.LEATHER);
        Material material = pool.get(Math.floorMod((int) (seed + 31), pool.size()));
        int amount = 32 + Math.floorMod((int) (seed * 6), 25);
        Task task = new Task("gather", "inventory");
        task.addConfigValue("amount", amount);
        task.addConfigValue("item", material.name());

        return buildQuest(DAILY_PREFIX + activeDailyDate + versionSuffix(dailyRerollVersion) + "_gather",
                "&eMisión de recolección &8- &aAcopio diario",
                List.of(
                        "&7Consigue &e" + amount + " &7de &6" + prettify(material.name()) + "&7 en tu inventario.",
                        "&8Se actualiza al recoger o almacenar objetos."
                ),
                new ItemStack(material),
                task,
                250 + random.nextInt(111),
                70 + random.nextInt(31));
    }

    private Quest createWeeklyMiningQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_COPPER_ORE, Material.STONE, Material.SPRUCE_LOG);
        Material material = pool.get(Math.floorMod((int) (seed + 5), pool.size()));
        int amount = 320 + Math.floorMod((int) seed, 121);
        Task task = new Task("mine", "blockbreak");
        task.addConfigValue("amount", amount);
        task.addConfigValue("block", material.name());

        return buildQuest(WEEKLY_PREFIX + activeWeeklyKey + versionSuffix(weeklyRerollVersion) + "_mine",
                "&6Misión semanal &8- &6Maestro minero",
                List.of(
                        "&7Rompe &e" + amount + " &7bloques de &6" + prettify(material.name()) + "&7.",
                        "&8Disponible durante toda la semana."
                ),
                new ItemStack(material),
                task,
                2400 + random.nextInt(601),
                220 + random.nextInt(81));
    }

    private Quest createWeeklyFarmingQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);
        Material material = pool.get(Math.floorMod((int) (seed + 9), pool.size()));
        Material iconMaterial = switch (material) {
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            default -> material;
        };
        int amount = 260 + Math.floorMod((int) (seed * 2), 111);
        Task task = new Task("farm", "farming");
        task.addConfigValue("amount", amount);
        task.addConfigValue("block", material.name());
        task.addConfigValue("mode", "break");

        return buildQuest(WEEKLY_PREFIX + activeWeeklyKey + versionSuffix(weeklyRerollVersion) + "_farm",
                "&6Misión semanal &8- &aGranjero experto",
                List.of(
                        "&7Cosecha &e" + amount + " &7cultivos de &a" + prettify(material.name()) + "&7.",
                        "&8Ideal para granjas activas y automatizadas."
                ),
                new ItemStack(iconMaterial),
                task,
                2100 + random.nextInt(551),
                200 + random.nextInt(81));
    }

    private Quest createWeeklyMobQuest(long seed, Random random) {
        List<EntityType> pool = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER, EntityType.ENDERMAN);
        EntityType entityType = pool.get(Math.floorMod((int) (seed + 13), pool.size()));
        int amount = 90 + Math.floorMod((int) (seed * 4), 41);
        Task task = new Task("mob", "mobkilling");
        task.addConfigValue("amount", amount);
        task.addConfigValue("mob", entityType.name());

        Material icon = switch (entityType) {
            case ZOMBIE -> Material.ROTTEN_FLESH;
            case SKELETON -> Material.BONE;
            case CREEPER -> Material.GUNPOWDER;
            case ENDERMAN -> Material.ENDER_PEARL;
            default -> Material.STRING;
        };

        return buildQuest(WEEKLY_PREFIX + activeWeeklyKey + versionSuffix(weeklyRerollVersion) + "_mob",
                "&6Misión semanal &8- &cCazador veterano",
                List.of(
                        "&7Elimina &e" + amount + " &7enemigos tipo &c" + prettify(entityType.name()) + "&7.",
                        "&8Una misión larga para jugadores combativos."
                ),
                new ItemStack(icon),
                task,
                2600 + random.nextInt(701),
                260 + random.nextInt(101));
    }

    private Quest createWeeklyFishingQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.COD, Material.SALMON, Material.PUFFERFISH, Material.TROPICAL_FISH);
        Material material = pool.get(Math.floorMod((int) (seed + 21), pool.size()));
        int amount = 36 + Math.floorMod((int) (seed * 7), 19);
        Task task = new Task("fish", "fishing");
        task.addConfigValue("amount", amount);
        task.addConfigValue("item", material.name());

        return buildQuest(WEEKLY_PREFIX + activeWeeklyKey + versionSuffix(weeklyRerollVersion) + "_fish",
                "&6Misión semanal &8- &bPescador constante",
                List.of(
                        "&7Pesca &e" + amount + " &7piezas de &b" + prettify(material.name()) + "&7.",
                        "&8La mar también tiene su recompensa."
                ),
                new ItemStack(material),
                task,
                1800 + random.nextInt(451),
                180 + random.nextInt(71));
    }

    private Quest createWeeklyWalkingQuest(long seed, Random random) {
        int distance = 5500 + Math.floorMod((int) (seed * 9), 2501);
        Task task = new Task("walk", "walking");
        task.addConfigValue("distance", distance);

        return buildQuest(WEEKLY_PREFIX + activeWeeklyKey + versionSuffix(weeklyRerollVersion) + "_walk",
                "&6Misión semanal &8- &fExplorador incansable",
                List.of(
                        "&7Recorre &e" + distance + " &7bloques por el mundo.",
                        "&8Una misión ideal para jugadores activos."
                ),
                new ItemStack(Material.LEATHER_BOOTS),
                task,
                2300 + random.nextInt(601),
                210 + random.nextInt(81));
    }

    private Quest createWeeklyWoodcuttingQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG);
        Material material = pool.get(Math.floorMod((int) (seed + 33), pool.size()));
        int amount = 220 + Math.floorMod((int) (seed * 3), 121);
        Task task = new Task("wood", "blockbreak");
        task.addConfigValue("amount", amount);
        task.addConfigValue("block", material.name());

        return buildQuest(WEEKLY_PREFIX + activeWeeklyKey + versionSuffix(weeklyRerollVersion) + "_wood",
                "&6Misión semanal &8- &6Leñador experto",
                List.of(
                        "&7Tala &e" + amount + " &7bloques de &6" + prettify(material.name()) + "&7.",
                        "&8Una buena misión para abastecer construcciones."
                ),
                new ItemStack(material),
                task,
                2200 + random.nextInt(551),
                200 + random.nextInt(81));
    }

    private Quest createWeeklySmeltingQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.IRON_INGOT, Material.GOLD_INGOT, Material.GLASS, Material.STONE, Material.BRICK, Material.BAKED_POTATO);
        Material material = pool.get(Math.floorMod((int) (seed + 37), pool.size()));
        int amount = 128 + Math.floorMod((int) (seed * 4), 65);
        Task task = new Task("smelt", "smelting");
        task.addConfigValue("amount", amount);
        task.addConfigValue("item", material.name());

        return buildQuest(WEEKLY_PREFIX + activeWeeklyKey + versionSuffix(weeklyRerollVersion) + "_smelt",
                "&6Misión semanal &8- &cForja constante",
                List.of(
                        "&7Cocina u hornea &e" + amount + " &7de &6" + prettify(material.name()) + "&7.",
                        "&8Ideal para bases con producción constante."
                ),
                new ItemStack(material),
                task,
                2100 + random.nextInt(501),
                190 + random.nextInt(71));
    }

    private Quest createWeeklyCraftingQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.TORCH, Material.CHEST, Material.BREAD, Material.RAIL, Material.OAK_PLANKS, Material.FURNACE);
        Material material = pool.get(Math.floorMod((int) (seed + 41), pool.size()));
        int amount = 72 + Math.floorMod((int) (seed * 2), 49);
        Task task = new Task("craft", "crafting");
        task.addConfigValue("amount", amount);
        task.addConfigValue("item", material.name());

        return buildQuest(WEEKLY_PREFIX + activeWeeklyKey + versionSuffix(weeklyRerollVersion) + "_craft",
                "&6Misión semanal &8- &fArtesano mayor",
                List.of(
                        "&7Craftea &e" + amount + " &7unidades de &6" + prettify(material.name()) + "&7.",
                        "&8Pensada para jugadores de producción y comercio."
                ),
                new ItemStack(material),
                task,
                2000 + random.nextInt(551),
                180 + random.nextInt(71));
    }

    private Quest createWeeklyTamingQuest(long seed, Random random) {
        List<EntityType> pool = List.of(EntityType.WOLF, EntityType.CAT, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA);
        EntityType entityType = pool.get(Math.floorMod((int) (seed + 43), pool.size()));
        int amount = entityType == EntityType.WOLF || entityType == EntityType.CAT ? 4 + Math.floorMod((int) seed, 3) : 2 + Math.floorMod((int) seed, 3);
        Task task = new Task("tame", "taming");
        task.addConfigValue("amount", amount);
        task.addConfigValue("mob", entityType.name());

        return buildQuest(WEEKLY_PREFIX + activeWeeklyKey + versionSuffix(weeklyRerollVersion) + "_tame",
                "&6Misión semanal &8- &dDomador experto",
                List.of(
                        "&7Domestica &e" + amount + " &7animales tipo &d" + prettify(entityType.name()) + "&7.",
                        "&8Una misión distinta para jugadores de supervivencia."
                ),
                new ItemStack(Material.LEAD),
                task,
                2600 + random.nextInt(651),
                250 + random.nextInt(91));
    }

    private Quest createWeeklyGatheringQuest(long seed, Random random) {
        List<Material> pool = List.of(Material.COBBLESTONE, Material.WHEAT, Material.COAL, Material.OAK_LOG, Material.LEATHER, Material.IRON_INGOT);
        Material material = pool.get(Math.floorMod((int) (seed + 47), pool.size()));
        int amount = 192 + Math.floorMod((int) (seed * 5), 97);
        Task task = new Task("gather", "inventory");
        task.addConfigValue("amount", amount);
        task.addConfigValue("item", material.name());

        return buildQuest(WEEKLY_PREFIX + activeWeeklyKey + versionSuffix(weeklyRerollVersion) + "_gather",
                "&6Misión semanal &8- &aRecolector mayor",
                List.of(
                        "&7Reúne &e" + amount + " &7de &6" + prettify(material.name()) + "&7 en tu inventario.",
                        "&8Cuenta cualquier método legítimo de obtención."
                ),
                new ItemStack(material),
                task,
                2050 + random.nextInt(551),
                180 + random.nextInt(71));
    }

    private Quest createWeeklyFarmAnimalQuest(long seed, Random random) {
        List<EntityType> pool = List.of(EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN, EntityType.RABBIT);
        EntityType entityType = pool.get(Math.floorMod((int) (seed + 53), pool.size()));
        int amount = 45 + Math.floorMod((int) (seed * 3), 21);
        Task task = new Task("farmmob", "mobkilling");
        task.addConfigValue("amount", amount);
        task.addConfigValue("mob", entityType.name());
        task.addConfigValue("hostile", false);

        Material icon = switch (entityType) {
            case COW -> Material.BEEF;
            case SHEEP -> Material.WHITE_WOOL;
            case PIG -> Material.PORKCHOP;
            case CHICKEN -> Material.CHICKEN;
            default -> Material.RABBIT_HIDE;
        };

        return buildQuest(WEEKLY_PREFIX + activeWeeklyKey + versionSuffix(weeklyRerollVersion) + "_farmmob",
                "&6Misión semanal &8- &eGanadero activo",
                List.of(
                        "&7Derrota &e" + amount + " &7animales de granja tipo &6" + prettify(entityType.name()) + "&7.",
                        "&8Pensada para granjas y supervivencia activa."
                ),
                new ItemStack(icon),
                task,
                2300 + random.nextInt(551),
                210 + random.nextInt(81));
    }

    private Quest createWeeklyMountedTravelQuest(long seed, Random random) {
        List<String> pool = List.of("horse", "boat", "pig", "minecart", "camel", "strider");
        String mode = pool.get(Math.floorMod((int) (seed + 59), pool.size()));
        int distance = 4200 + Math.floorMod((int) (seed * 7), 2201);
        Task task = new Task("travel", "walking");
        task.addConfigValue("distance", distance);
        task.addConfigValue("mode", mode);

        Material icon = switch (mode) {
            case "boat" -> Material.OAK_BOAT;
            case "pig" -> Material.CARROT_ON_A_STICK;
            case "minecart" -> Material.MINECART;
            case "camel" -> Material.SADDLE;
            case "strider" -> Material.WARPED_FUNGUS_ON_A_STICK;
            default -> Material.LEATHER_HORSE_ARMOR;
        };

        return buildQuest(WEEKLY_PREFIX + activeWeeklyKey + versionSuffix(weeklyRerollVersion) + "_travel",
                "&6Misión semanal &8- &bViajero montado",
                List.of(
                        "&7Recorre &e" + distance + " &7bloques usando modo &b" + prettify(mode) + "&7.",
                        "&8Una misión distinta para explorar el mapa."
                ),
                new ItemStack(icon),
                task,
                2400 + random.nextInt(651),
                220 + random.nextInt(81));
    }

    private Quest buildQuest(String questId, String name, List<String> description, ItemStack icon, Task task, int vaultReward, int experienceReward) {
        List<RotatingReward> itemRewards = getConfiguredItemRewards(new Random(questId.hashCode()), questId.startsWith(WEEKLY_PREFIX));
        List<String> rewardCommands = new ArrayList<>();
        rewardCommands.add("minecraft:xp add {player} " + experienceReward + " points");
        List<String> rewardLines = new ArrayList<>();
        rewardLines.add("&7Has completado una &6misión " + (questId.startsWith(WEEKLY_PREFIX) ? "semanal" : "diaria") + "&7.");
        rewardLines.add("&7Recompensa: &a$" + vaultReward + " &7y &b" + experienceReward + " xp");
        for (RotatingReward reward : itemRewards) {
            rewardCommands.add(reward.command());
            rewardLines.add("&7Objeto extra: &e" + reward.displayName() + " x" + reward.amount());
        }

        Quest quest = new Quest.Builder(questId)
                .withRepeatEnabled(false)
                .withCancellable(true)
                .withCountsTowardsCompleted(false)
                .withCountsTowardsLimit(false)
                .withHidden(true)
                .withSortOrder(Integer.MIN_VALUE)
                .withStartString(List.of())
                .withRewardString(rewardLines)
                .withRewards(rewardCommands)
                .withVaultReward(String.valueOf(vaultReward))
                .withPlaceholders(Map.of("progress", questId.startsWith(WEEKLY_PREFIX) ? "&6Progreso semanal" : "&6Progreso diario"))
                .build();
        quest.registerTask(task);

        QItemStack qItemStack = new QItemStack(plugin,
                Chat.legacyColor(name),
            Chat.legacyColor(buildNormalLore(description, task, vaultReward, experienceReward, itemRewards)),
                Chat.legacyColor(List.of()),
                icon);
        qItemStack = getConfiguredRotatingQItemStackOrFallback(qItemStack, icon, task, questId.startsWith(WEEKLY_PREFIX), itemRewards);
        plugin.getQItemStackRegistry().register(quest, qItemStack);
        return quest;
    }

    private List<RotatingReward> getConfiguredItemRewards(Random random, boolean weekly) {
        String sectionPath = weekly ? "WeeklyRewards" : "DailyRewards";
        if (rotatingRewardsConfig.getConfigurationSection(sectionPath) == null) {
            return List.of();
        }

        List<RotatingReward> rewards = new ArrayList<>();
        for (String key : rotatingRewardsConfig.getConfigurationSection(sectionPath).getKeys(false)) {
            String path = sectionPath + "." + key;
            String provider = rotatingRewardsConfig.getString(path + ".type", "mmoitems").trim().toLowerCase(Locale.ROOT);
            String itemId = rotatingRewardsConfig.getString(path + ".id", "").trim();
            String commandTemplate = rotatingRewardsConfig.getString(path + ".command", "").trim();
            String itemType = rotatingRewardsConfig.getString(path + ".item-type", "MATERIAL").trim();
            double chance = rotatingRewardsConfig.getDouble(path + ".chance", 0D);
            int amount = parseRewardAmount(rotatingRewardsConfig.getString(path + ".amount", "1"), random);
            if (!provider.equals("mmoitems") || !plugin.getServer().getPluginManager().isPluginEnabled("MMOItems")
                    || itemId.isEmpty() || commandTemplate.isEmpty() || chance <= 0D || random.nextDouble() * 100D >= chance) {
                continue;
            }

            ItemStack itemStack;
            try {
                itemStack = MMOItems.plugin.getItem(Type.get(itemType), itemId);
            } catch (Exception exception) {
                itemStack = null;
            }
            if (itemStack == null) {
                plugin.getQuestsLogger().warning("Ignoring rotating MMOItems reward '" + itemType + ":" + itemId + "': item not found.");
                continue;
            }

            String command = commandTemplate
                    .replace("{item}", itemId)
                    .replace("{type}", itemType)
                    .replace("{amount}", String.valueOf(amount));
            String displayName = itemStack.getItemMeta() != null && itemStack.getItemMeta().getDisplayName() != null
                    ? itemStack.getItemMeta().getDisplayName() : itemId;
            rewards.add(new RotatingReward(command, displayName, amount));
        }
        return rewards;
    }

    private int parseRewardAmount(String configuredAmount, Random random) {
        String value = configuredAmount == null ? "1" : configuredAmount.trim();
        if (value.matches("\\d+\\s*-\\s*\\d+")) {
            String[] bounds = value.split("\\s*-\\s*");
            int minimum = Integer.parseInt(bounds[0]);
            int maximum = Integer.parseInt(bounds[1]);
            int lowerBound = Math.max(1, Math.min(minimum, maximum));
            int upperBound = Math.max(lowerBound, Math.max(minimum, maximum));
            return lowerBound + random.nextInt(upperBound - lowerBound + 1);
        }

        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            plugin.getQuestsLogger().warning("Invalid rotating reward amount '" + configuredAmount + "'. Using 1.");
            return 1;
        }
    }

    private void loadRotatingRewardsConfig() {
        File file = new File(plugin.getDataFolder(), "daily-rewards.yml");
        rotatingRewardsConfig = YamlConfiguration.loadConfiguration(file);
    }

    private record RotatingReward(String command, String displayName, int amount) {
    }

    private QItemStack getConfiguredRotatingQItemStackOrFallback(QItemStack fallback, ItemStack iconFallback, Task task, boolean weekly, List<RotatingReward> itemRewards) {
        FileConfiguration config = plugin.getConfig();
        String path = weekly ? "gui.weekly-quest-display" : "gui.daily-quest-display";
        if (!config.isConfigurationSection(path)) {
            return fallback;
        }

        String configuredName = config.getString(path + ".name", fallback.getName());
        List<String> configuredLoreNormal = config.getStringList(path + ".lore-normal");
        List<String> configuredLoreStarted = config.getStringList(path + ".lore-started");

        List<String> loreNormal = normalizeTaskPlaceholders(Chat.legacyColor(configuredLoreNormal), task);
        List<String> loreStarted = normalizeTaskPlaceholders(Chat.legacyColor(configuredLoreStarted), task);

        if (loreNormal.isEmpty()) {
            loreNormal = fallback.getLoreNormal();
        }
        if (loreStarted.isEmpty()) {
            loreStarted = fallback.getLoreStarted();
        } else if (loreStarted.equals(loreNormal)) {
            loreStarted = List.of();
        }

        if (!itemRewards.isEmpty()) {
            loreNormal = new ArrayList<>(loreNormal);
            loreNormal.add("");
            loreNormal.add("&eRecompensas extra:");
            loreNormal.addAll(buildItemRewardLore(itemRewards));
        }

        ItemStack configuredItem = plugin.getConfiguredItemStack(path, config,
                com.leonardobishop.quests.bukkit.hook.itemgetter.ItemGetter.Filter.DISPLAY_NAME,
                com.leonardobishop.quests.bukkit.hook.itemgetter.ItemGetter.Filter.LORE,
                com.leonardobishop.quests.bukkit.hook.itemgetter.ItemGetter.Filter.ENCHANTMENTS);

        ItemStack baseItem = configuredItem == null || configuredItem.getType().isAir() ? iconFallback : configuredItem;
        return new QItemStack(plugin, Chat.legacyColor(configuredName), loreNormal, loreStarted, baseItem);
    }

    private List<String> normalizeTaskPlaceholders(List<String> lore, Task task) {
        List<String> normalized = new ArrayList<>(lore.size());
        String taskPrefix = "{" + task.getId() + ":";
        for (String line : lore) {
            normalized.add(line.replace("{this:", taskPrefix));
        }
        return normalized;
    }

    private List<String> buildNormalLore(List<String> description, Task task, int vaultReward, int experienceReward, List<RotatingReward> itemRewards) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(buildObjectiveLine(task));
        lore.add(buildProgressLine(task));
        lore.add("");
        lore.add("&eRecompensas:");
        lore.add("&7• &f" + vaultReward + " &fmonedas ");
        lore.add("&7• &f" + experienceReward + " &fexperiencia ψ");
        if (!itemRewards.isEmpty()) {
            lore.add("");
            lore.add("&eRecompensas extra:");
            lore.addAll(buildItemRewardLore(itemRewards));
        }
        lore.add("");
        return lore;
    }

    private List<String> buildItemRewardLore(List<RotatingReward> itemRewards) {
        List<String> lore = new ArrayList<>();
        for (RotatingReward reward : itemRewards) {
            lore.add("&7• &f" + reward.displayName() + " &7x" + reward.amount());
        }
        return lore;
    }

    private String buildObjectiveLine(Task task) {
        return switch (task.getType()) {
            case "blockbreak" -> "&fObjetivo: &fRompe {" + task.getId() + ":goal} bloques de " + prettify(String.valueOf(task.getConfigValue("block"))) + ".";
            case "farming" -> "&fObjetivo: &fRecoge {" + task.getId() + ":goal} cultivos de " + prettify(String.valueOf(task.getConfigValue("block"))) + ".";
            case "mobkilling" -> "&fObjetivo: &fElimina {" + task.getId() + ":goal} enemigos de tipo " + prettify(String.valueOf(task.getConfigValue("mob"))) + ".";
            case "fishing" -> "&fObjetivo: &fPesca {" + task.getId() + ":goal} de " + prettify(String.valueOf(task.getConfigValue("item"))) + ".";
            case "walking" -> "&fObjetivo: &fRecorre {" + task.getId() + ":goal} bloques.";
            case "smelting" -> "&fObjetivo: &fCocina u hornea {" + task.getId() + ":goal} de " + prettify(String.valueOf(task.getConfigValue("item"))) + ".";
            case "crafting" -> "&fObjetivo: &fCraftea {" + task.getId() + ":goal} de " + prettify(String.valueOf(task.getConfigValue("item"))) + ".";
            case "inventory" -> "&fObjetivo: &fConsigue {" + task.getId() + ":goal} de " + prettify(String.valueOf(task.getConfigValue("item"))) + ".";
            case "taming" -> "&fObjetivo: &fDomestica {" + task.getId() + ":goal} animales de tipo " + prettify(String.valueOf(task.getConfigValue("mob"))) + ".";
            default -> "&fObjetivo: &f{" + task.getId() + ":goal}";
        };
    }

    private String buildProgressLine(Task task) {
        return switch (task.getType()) {
            case "blockbreak" -> "&fProgreso: &f{" + task.getId() + ":progress}/{" + task.getId() + ":goal} bloques de " + prettify(String.valueOf(task.getConfigValue("block"))) + ".";
            case "farming" -> "&fProgreso: &f{" + task.getId() + ":progress}/{" + task.getId() + ":goal} cultivos de " + prettify(String.valueOf(task.getConfigValue("block"))) + ".";
            case "mobkilling" -> "&fProgreso: &f{" + task.getId() + ":progress}/{" + task.getId() + ":goal} enemigos de tipo " + prettify(String.valueOf(task.getConfigValue("mob"))) + ".";
            case "fishing" -> "&fProgreso: &f{" + task.getId() + ":progress}/{" + task.getId() + ":goal} de " + prettify(String.valueOf(task.getConfigValue("item"))) + ".";
            case "walking" -> "&fProgreso: &f{" + task.getId() + ":progress}/{" + task.getId() + ":goal} bloques.";
            case "smelting" -> "&fProgreso: &f{" + task.getId() + ":progress}/{" + task.getId() + ":goal} de " + prettify(String.valueOf(task.getConfigValue("item"))) + ".";
            case "crafting" -> "&fProgreso: &f{" + task.getId() + ":progress}/{" + task.getId() + ":goal} de " + prettify(String.valueOf(task.getConfigValue("item"))) + ".";
            case "inventory" -> "&fProgreso: &f{" + task.getId() + ":progress}/{" + task.getId() + ":goal} de " + prettify(String.valueOf(task.getConfigValue("item"))) + ".";
            case "taming" -> "&fProgreso: &f{" + task.getId() + ":progress}/{" + task.getId() + ":goal} animales de tipo " + prettify(String.valueOf(task.getConfigValue("mob"))) + ".";
            default -> "&fProgreso: &f{" + task.getId() + ":progress}/{" + task.getId() + ":goal}";
        };
    }

    private String buildStartedProgressLine(Task task) {
        return buildProgressLine(task);
    }

    private String prettify(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        String translated = DISPLAY_NAMES.get(normalized);
        if (translated != null) {
            return translated;
        }

        return Arrays.stream(normalized.split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String versionSuffix(int version) {
        return version > 0 ? "_r" + version : "";
    }

    private static Map<String, String> createDisplayNames() {
        Map<String, String> names = new HashMap<>();

        names.put("leather", "cuero");
        names.put("coal", "carbón");
        names.put("coal_ore", "mena de carbón");
        names.put("deepslate_coal_ore", "mena de carbón de pizarra profunda");
        names.put("iron_ore", "mena de hierro");
        names.put("deepslate_iron_ore", "mena de hierro de pizarra profunda");
        names.put("copper_ore", "mena de cobre");
        names.put("deepslate_copper_ore", "mena de cobre de pizarra profunda");
        names.put("stone", "piedra");
        names.put("glass", "cristal");
        names.put("brick", "ladrillo");
        names.put("iron_ingot", "lingote de hierro");
        names.put("gold_ingot", "lingote de oro");
        names.put("baked_potato", "patata asada");
        names.put("torch", "antorcha");
        names.put("chest", "cofre");
        names.put("bread", "pan");
        names.put("oak_planks", "tablas de roble");
        names.put("furnace", "horno");
        names.put("rail", "raíl");
        names.put("cod", "bacalao");
        names.put("salmon", "salmón");
        names.put("pufferfish", "pez globo");
        names.put("tropical_fish", "pez tropical");
        names.put("cobblestone", "piedra labrada");
        names.put("wheat", "trigo");
        names.put("oak_log", "tronco de roble");
        names.put("spruce_log", "tronco de abeto");
        names.put("birch_log", "tronco de abedul");
        names.put("jungle_log", "tronco de jungla");
        names.put("acacia_log", "tronco de acacia");
        names.put("dark_oak_log", "tronco de roble oscuro");
        names.put("carrots", "zanahorias");
        names.put("potatoes", "patatas");
        names.put("beetroots", "remolachas");
        names.put("carrot", "zanahoria");
        names.put("potato", "patata");
        names.put("beetroot_seeds", "semillas de remolacha");
        names.put("beef", "ternera");
        names.put("porkchop", "chuleta de cerdo");
        names.put("chicken", "pollo");
        names.put("white_wool", "lana blanca");
        names.put("rabbit_hide", "piel de conejo");
        names.put("lead", "correa");
        names.put("leather_boots", "botas de cuero");
        names.put("oak_boat", "barco de roble");
        names.put("minecart", "vagoneta");
        names.put("saddle", "silla de montar");
        names.put("carrot_on_a_stick", "caña con zanahoria");
        names.put("warped_fungus_on_a_stick", "caña con hongo distorsionado");
        names.put("leather_horse_armor", "armadura de cuero para caballo");

        names.put("zombie", "zombi");
        names.put("skeleton", "esqueleto");
        names.put("creeper", "creeper");
        names.put("spider", "araña");
        names.put("enderman", "enderman");
        names.put("cow", "vaca");
        names.put("sheep", "oveja");
        names.put("pig", "cerdo");
        names.put("chicken", "pollo");
        names.put("rabbit", "conejo");
        names.put("wolf", "lobo");
        names.put("cat", "gato");
        names.put("horse", "caballo");
        names.put("donkey", "burro");
        names.put("mule", "mula");
        names.put("llama", "llama");
        names.put("strider", "zancudo");
        names.put("camel", "camello");
        names.put("boat", "barco");

        return names;
    }
}