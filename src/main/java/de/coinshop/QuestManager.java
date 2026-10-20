package de.coinshop;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Tägliche Aufgaben. Jeden Tag (zusammen mit dem Shop-Wechsel um 12:00 Uhr) gibt es für ALLE Spieler
 * dieselben Aufgaben, z. B. "Baue 300 Blöcke ab". Gemessen wird mit den normalen Minecraft-Statistiken:
 * Beim ersten Mal, dass das Plugin einen Spieler an diesem Tag sieht, wird der Stand notiert; geschafft
 * ist eine Aufgabe, wenn der Wert um die Zielmenge gestiegen ist.
 *
 * Welche Aufgaben es gibt, steht in der config.yml unter "quests.pool". Das Plugin würfelt daraus
 * jeden Tag (für alle gleich) 3 leichte und 3 schwere Aufgaben aus.
 */
public final class QuestManager {

    /** Eine Aufgabe von heute. idx = Platz 0-2 = leichte, 3-5 = schwere Aufgaben */
    public record Quest(int idx, String id, String name, String unit, String stat, Material icon,
                        long amount, long reward, boolean hard) { }

    /** Fortschritt eines Spielers bei einer Aufgabe. */
    public record Progress(Quest quest, long current, boolean done) { }

    /** Platz für den Eintrag "Bonus für alle Aufgaben" in der Datenbank (damit er nur einmal ausgezahlt wird). */
    private static final int BONUS_IDX = 99;

    private final CoinShopPlugin plugin;
    private String cachedDay = "";
    private List<Quest> cachedQuests = new ArrayList<>();

    public QuestManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("quests.enabled", true);
    }

    public long bonusAll() {
        return Math.max(0, plugin.getConfig().getLong("quests.bonus-all", 100));
    }

    /** Der "Aufgaben-Tag": wechselt zur selben Uhrzeit wie der Shop. */
    public String dayKey() {
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        return now.minusHours(plugin.serverShop().resetHour()).toLocalDate().toString();
    }

    /** Die Aufgaben von heute (für alle gleich). */
    public synchronized List<Quest> today() {
        String day = dayKey();
        if (!day.equals(cachedDay)) {
            cachedQuests = draw(day);
            cachedDay = day;
            try {
                plugin.database().pruneQuests(day);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Alte Aufgaben konnten nicht aufgeräumt werden: " + e);
            }
        }
        return cachedQuests;
    }

    /** Würfelt die Aufgaben eines Tages aus. Der Zufall hängt nur vom Datum ab - also für alle gleich. */
    private List<Quest> draw(String day) {
        List<Quest> result = new ArrayList<>();
        ConfigurationSection pool = plugin.getConfig().getConfigurationSection("quests.pool");
        if (pool == null) {
            return result;
        }
        Random random = new Random(day.hashCode() * 2654435761L + 12345L);
        List<String> ids = new ArrayList<>(pool.getKeys(false));
        Collections.shuffle(ids, random);
        int easy = Math.max(0, plugin.getConfig().getInt("quests.easy-count", 3));
        int hard = Math.max(0, plugin.getConfig().getInt("quests.hard-count", 3));
        double hardAmount = Math.max(1.0, plugin.getConfig().getDouble("quests.hard-amount", 1.5));
        double hardReward = Math.max(1.0, plugin.getConfig().getDouble("quests.hard-reward", 2.2));
        java.util.Set<String> usedStats = new java.util.HashSet<>();

        for (String id : ids) {
            if (result.size() >= easy + hard) {
                break;
            }
            ConfigurationSection s = pool.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            String stat = s.getString("stat", id);
            if (stat.equals("brewed") && plugin.timeline().netherLocked()) {
                continue; // Brauaufgaben gibt es erst, wenn der Nether offen ist (Netherwarze)
            }
            if (!usedStats.add(stat)) {
                continue; // nicht zwei Aufgaben zum selben Thema
            }
            boolean isHard = result.size() >= easy;
            List<Long> amount = s.getLongList("amount");
            List<Long> reward = s.getLongList("reward");
            long aMin = amount.size() > 0 ? amount.get(0) : 100;
            long aMax = amount.size() > 1 ? amount.get(1) : aMin;
            long rMin = reward.size() > 0 ? reward.get(0) : 20;
            long rMax = reward.size() > 1 ? reward.get(1) : rMin;
            // leicht: untere 35 % der Spanne; schwer: obere 40 % der Spanne, dazu mehr Menge und deutlich mehr Belohnung
            double t = isHard ? 0.6 + random.nextDouble() * 0.4 : random.nextDouble() * 0.35;
            double aMul = isHard ? hardAmount : 1.0;
            double rMul = isHard ? hardReward : 1.0;
            long a = round((aMin + t * (aMax - aMin)) * aMul, aMax >= 100 ? 10 : 1);
            long r = round((rMin + t * (rMax - rMin)) * rMul, 5);
            Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
            result.add(new Quest(result.size(), id, s.getString("name", id), s.getString("unit", ""), stat,
                    icon == null ? Material.PAPER : icon, Math.max(1, a), Math.max(0, r), isHard));
        }
        return result;
    }

    private static long round(double value, long step) {
        return Math.max(step, Math.round(value / step) * step);
    }

    // ------------------------------------------------------------------
    //  Fortschritt prüfen
    // ------------------------------------------------------------------

    /** Alle 20 Sekunden für jeden Online-Spieler. */
    public void tickAll() {
        if (!enabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (plugin.expeditions().isIn(player.getUniqueId())) {
                continue; // Was in der Expedition passiert, zaehlt nicht
            }
            try {
                check(player);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Aufgaben von " + player.getName() + " konnten nicht geprüft werden: " + e);
            }
        }
    }

    /**
     * Berechnet den Fortschritt, legt fehlende Startwerte an und zahlt Belohnungen für gerade
     * geschaffte Aufgaben aus.
     */
    public List<Progress> check(Player player) {
        List<Progress> result = new ArrayList<>();
        if (!enabled() || plugin.expeditions().isIn(player.getUniqueId())) {
            return result;
        }
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        String day = dayKey();
        List<Quest> quests = today();
        if (quests.isEmpty()) {
            return result;
        }

        Map<Integer, Database.QuestRow> rows = new HashMap<>();
        for (Database.QuestRow row : db.getQuests(uuid, day)) {
            rows.put(row.idx(), row);
        }
        Map<String, Long> values = plugin.rankings().statValues(player);

        boolean allDone = true;
        for (Quest quest : quests) {
            Database.QuestRow row = rows.get(quest.idx());
            if (row == null) {
                long start = values.getOrDefault(quest.stat(), 0L);
                db.insertQuest(uuid, day, quest.idx(), start);
                row = new Database.QuestRow(quest.idx(), start, false);
            }
            long current = Math.max(0, values.getOrDefault(quest.stat(), 0L) - row.baseline());
            double mascotMult = plugin.mascot().taskMultiplier(uuid, quest.stat()); // Maskottchen: passt es zur Aufgabe?
            if (mascotMult > 1.0) {
                current = Math.round(current * mascotMult);
            }
            double titleBoost = plugin.titles().effect(uuid, "task-boost", quest.stat()); // Titel-Wirkung
            if (titleBoost > 0) {
                current += (long) Math.floor(current * titleBoost / 100.0);
            }
            boolean done = row.done();
            if (!done && current >= quest.amount()) {
                done = complete(player, day, quest);
            }
            if (!done) {
                allDone = false;
            }
            result.add(new Progress(quest, done ? quest.amount() : current, done));
        }

        if (allDone) {
            payBonus(player, day);
        }
        return result;
    }

    /** Zahlt die Belohnung einer Aufgabe. Gibt true zurück, wenn die Aufgabe jetzt als erledigt gilt. */
    private boolean complete(Player player, String day, Quest quest) {
        if (plugin.timeline().previewFor(player)) {
            return false; // Testphase: nichts wird gewertet
        }
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        long reward;
        db.begin();
        try {
            if (!db.markQuestDone(uuid, day, quest.idx())) {
                db.rollback();
                return true; // war schon erledigt
            }
            reward = plugin.licenses().boost(uuid, "aufgaben", quest.reward());
            reward += Math.round(reward * plugin.mascot().questPercent(uuid) / 100.0); // Maskottchen-Bonus
            if (reward > 0) {
                db.addCoins(uuid, reward);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().warning("Aufgaben-Belohnung fehlgeschlagen: " + e);
            return false;
        }
        db.addStat(uuid, "quests_done", 1);
        db.logTx(uuid, player.getName(), "aufgabe", reward, quest.id());
        plugin.messages().send(player, "quest-complete", "name", quest.name(), "coins", reward);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        return true;
    }

    /** Bonus für "alle Aufgaben des Tages geschafft" (nur einmal pro Tag). */
    private void payBonus(Player player, String day) {
        if (plugin.timeline().previewFor(player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        long bonus = bonusAll() + Math.round(bonusAll() * plugin.mascot().questPercent(uuid) / 100.0);
        Database db = plugin.database();
        db.begin();
        try {
            db.insertQuest(uuid, day, BONUS_IDX, 0);
            if (!db.markQuestDone(uuid, day, BONUS_IDX)) {
                db.rollback();
                return;
            }
            if (bonus > 0) {
                db.addCoins(uuid, bonus);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().warning("Aufgaben-Bonus fehlgeschlagen: " + e);
            return;
        }
        db.logTx(uuid, player.getName(), "aufgaben-bonus", bonus, day);
        plugin.messages().send(player, "quest-all-done", "coins", bonus);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        plugin.mysteryBoxes().give(player, 1, "alle Tagesaufgaben geschafft"); // einfache Mystery Box
    }

    /** Wie viele Aufgaben von heute sind schon erledigt? (Ohne neu zu berechnen - für Anzeigen.) */
    public int doneCount(UUID uuid) {
        int done = 0;
        int total = today().size();
        for (Database.QuestRow row : plugin.database().getQuests(uuid, dayKey())) {
            if (row.idx() < total && row.done()) {
                done++;
            }
        }
        return done;
    }

    public int total() {
        return enabled() ? today().size() : 0;
    }

    /** "Blöcke abbauen: 300 Blöcke" als Text für die Anzeige. */
    public static String amountText(Quest q) {
        String unit = q.unit() == null || q.unit().isEmpty() ? "" : " " + q.unit();
        return Messages.number(q.amount()) + unit;
    }

    public static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
