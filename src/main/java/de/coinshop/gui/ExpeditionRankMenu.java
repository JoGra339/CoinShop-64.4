package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bestenliste der Expeditionen: höchstes Level, bestandene Prüfungen, beste Verteidigungswelle. */
public final class ExpeditionRankMenu extends Menu {

    public ExpeditionRankMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, Component.text("Expeditions-Bestenliste"));
        UUID me = viewer.getUniqueId();
        plugin.expeditions().refreshRankStats(me);
        board(11, Material.DIAMOND_SWORD, "Höchstes Level", "ex_level", "Level", me);
        board(13, Material.NETHER_STAR, "Bestandene Prüfungen", "ex_exams", "Prüfungen", me);
        board(15, Material.BEACON, "Beste Verteidigungswelle", "ex_tdbest", "Wellen", me);
        set(18, backButton(), (p, c) -> new ExpeditionMenu(plugin, p).open(p));
        set(26, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }

    private void board(int slot, Material icon, String title, String stat, String unit, UUID me) {
        List<Database.RankEntry> top = plugin.database().topStat(stat, 10);
        List<Component> lore = new ArrayList<>();
        if (top.isEmpty()) {
            lore.add(ExpeditionMenu.line("Noch keine Einträge.", NamedTextColor.GRAY));
        }
        int place = 1;
        for (Database.RankEntry e : top) {
            lore.add(ExpeditionMenu.line(place + ". " + e.name() + ": " + e.value() + " " + unit,
                    e.uuid().equals(me) ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            place++;
        }
        lore.add(ExpeditionMenu.line("Du: " + plugin.database().getStat(me, stat) + " " + unit, NamedTextColor.AQUA));
        set(slot, de.coinshop.ItemUtil.icon(icon, ExpeditionMenu.line(title, NamedTextColor.GOLD), lore), null);
    }
}
