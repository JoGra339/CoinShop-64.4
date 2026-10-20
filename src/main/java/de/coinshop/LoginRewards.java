package de.coinshop;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Login-Serie, fest verbunden mit dem täglichen Einkommen (Grundeinkommen).
 *
 * Ein "Tag" geht von Shop-Wechsel zu Shop-Wechsel (12:00 bis 12:00 Uhr). Wer in einem Tag mindestens einmal
 * auf dem Server war, setzt seine Serie fort. Verpasst man einen ganzen Tag, beginnt sie wieder bei 1.
 *
 * Ausgezahlt wird NICHTS beim Beitreten. Stattdessen wächst das tägliche Einkommen mit der Serie:
 *   Einkommen = Grundeinkommen + Serien-Bonus + Sets + Ranglisten
 * Der Serien-Bonus wird zusammen mit allem anderen beim Shop-Wechsel ausgezahlt (siehe InvestmentManager).
 * Er wächst pro Tag der Serie ("per-day"), bis "max-days" Tage; jeder 7. Tag gibt zusätzlich "weekly-bonus".
 * Einstellungen: "login-streak" in der config.yml.
 */
public final class LoginRewards {

    private final CoinShopPlugin plugin;

    public LoginRewards(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("login-streak.enabled", true);
    }

    /** Der aktuelle "Tag" (wechselt zur Shop-Wechsel-Uhrzeit). */
    private LocalDate today() {
        return ZonedDateTime.now(plugin.serverShop().zone())
                .minusHours(plugin.serverShop().resetHour()).toLocalDate();
    }

    /** Serien-Bonus für einen bestimmten Tag der Serie (1 = erster Tag, Bonus 0). */
    public long bonusFor(int streak) {
        if (!enabled() || streak < 1) {
            return 0;
        }
        var cfg = plugin.getConfig();
        long perDay = Math.max(0, cfg.getLong("login-streak.per-day", 5));
        int maxDays = Math.max(1, cfg.getInt("login-streak.max-days", 7));
        long bonus = perDay * (Math.min(streak, maxDays) - 1);
        if (streak % 7 == 0) {
            bonus += Math.max(0, cfg.getLong("login-streak.weekly-bonus", 100));
        }
        return bonus;
    }

    /** Wie viele Tage hintereinander ist der Spieler aktuell dabei? (0 = Serie ist gerissen) */
    public int currentStreak(UUID uuid) {
        Optional<Database.Streak> s = plugin.database().getStreak(uuid);
        if (s.isEmpty()) {
            return 0;
        }
        try {
            LocalDate last = LocalDate.parse(s.get().lastDay());
            LocalDate today = today();
            if (last.equals(today) || last.plusDays(1).equals(today)) {
                return s.get().streak();
            }
        } catch (RuntimeException ignored) {
            // kaputtes Datum -> Serie gilt als gerissen
        }
        return 0;
    }

    /** Serien-Bonus mit Lizenz "treue" (Prozent mehr). */
    public long bonusFor(UUID uuid, int streak) {
        return plugin.licenses().boost(uuid, "treue", bonusFor(streak));
    }

    /** Der Serien-Bonus, den der Spieler beim nächsten Shop-Wechsel zusätzlich bekommt. */
    public long bonusOf(UUID uuid) {
        return bonusFor(uuid, currentStreak(uuid));
    }

    /** Wird beim Beitreten aufgerufen: führt die Serie weiter (zahlt aber nichts aus). */
    public void onJoin(Player player) {
        if (!enabled() || plugin.timeline().previewFor(player)) {
            return;
        }
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        String todayText = today().toString();
        Optional<Database.Streak> old = db.getStreak(uuid);

        int streak = 1;
        int best = 1;
        if (old.isPresent()) {
            if (old.get().lastDay().equals(todayText)) {
                return; // heute schon gezählt
            }
            best = old.get().best();
            try {
                if (LocalDate.parse(old.get().lastDay()).plusDays(1).equals(today())) {
                    streak = old.get().streak() + 1;
                }
            } catch (RuntimeException ignored) {
                // kaputtes Datum -> neu anfangen
            }
        }
        best = Math.max(best, streak);
        try {
            db.setStreak(uuid, todayText, streak, best);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Login-Serie konnte nicht gespeichert werden: " + e);
            return;
        }
        plugin.messages().send(player, "login-streak", "days", streak, "bonus", bonusFor(uuid, streak),
                "next", bonusFor(uuid, streak + 1));
        if (streak % 7 == 0) {
            plugin.messages().send(player, "login-streak-weekly");
            // Alle 7 Tage eine Mystery Box, die mit der Zeit besser wird (Liste: eine Stufe je Woche, danach bleibt die letzte)
            List<Integer> tiers = plugin.getConfig().getIntegerList("login-streak.box-tiers");
            if (tiers.isEmpty()) {
                tiers = List.of(1, 1, 2, 2, 3, 3, 4, 4, 5);
            }
            int week = streak / 7;
            plugin.mysteryBoxes().give(player, tiers.get(Math.min(tiers.size(), week) - 1), "Loginserie " + streak + " Tage");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
    }
}
