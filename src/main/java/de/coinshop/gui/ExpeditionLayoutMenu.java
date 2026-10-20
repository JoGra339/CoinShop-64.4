package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ExpeditionManager.Gear;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Vorschau auf den Rucksack zu Levelbeginn. Klicke einen Gegenstand an und dann den Platz, auf den er soll
 * (ein freier Platz oder ein anderer Gegenstand zum Tauschen). Rüstung und Schild liegen fest.
 * Oben der Rucksack, unten die Schnellleiste.
 */
public final class ExpeditionLayoutMenu extends Menu {

    public ExpeditionLayoutMenu(CoinShopPlugin plugin, Player viewer, int selected) {
        super(plugin, 5, Component.text("Inventar sortieren"));
        ExpeditionManager ex = plugin.expeditions();
        var uuid = viewer.getUniqueId();
        ExpeditionManager.KitItem[] layout = ex.kitLayout(uuid);
        for (int inv = 0; inv < 36; inv++) {
            int menuSlot = inv >= 9 ? inv - 9 : 36 + inv;
            int target = inv;
            ExpeditionManager.KitItem kit = layout[inv];
            ItemStack shown;
            if (kit == null) {
                shown = ItemUtil.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, ExpeditionMenu.line("Frei", NamedTextColor.DARK_GRAY),
                        selected >= 0 ? List.of(ExpeditionMenu.line("Klicken: hierher legen", NamedTextColor.GREEN)) : List.of());
            } else {
                shown = kit.item().clone();
                ItemMeta meta = shown.getItemMeta();
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(ExpeditionMenu.line(selected < 0 ? "Klicken zum Auswählen"
                        : selected == target ? "Ausgewählt (nochmal klicken zum Abwählen)" : "Klicken zum Tauschen", NamedTextColor.GREEN));
                meta.lore(lore);
                if (selected == target) {
                    meta.setEnchantmentGlintOverride(true);
                }
                shown.setItemMeta(meta);
            }
            set(menuSlot, shown, (p, click) -> {
                if (selected < 0) {
                    if (kit != null) {
                        new ExpeditionLayoutMenu(plugin, p, target).open(p);
                    }
                } else if (selected == target) {
                    new ExpeditionLayoutMenu(plugin, p, -1).open(p);
                } else {
                    ex.moveKitItem(p.getUniqueId(), selected, target);
                    new ExpeditionLayoutMenu(plugin, p, -1).open(p);
                }
            });
        }
        // Trennreihe: Zurück, feste Teile, Hinweis
        set(27, backButton(), (p, c) -> new ExpeditionMenu(plugin, p).open(p));
        Gear[] fixed = {Gear.HELMET, Gear.CHESTPLATE, Gear.LEGGINGS, Gear.BOOTS};
        for (int i = 0; i < fixed.length; i++) {
            ItemStack item = ex.itemFor(fixed[i], ex.level(uuid, fixed[i]));
            if (item != null) {
                ItemStack icon = item.clone();
                ItemMeta meta = icon.getItemMeta();
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(ExpeditionMenu.line("Wird angelegt (fester Platz)", NamedTextColor.GRAY));
                meta.lore(lore);
                icon.setItemMeta(meta);
                set(29 + i, icon, null);
            }
        }
        if (ex.hasUtility(uuid, ExpeditionManager.Util.SHIELD)) {
            set(33, ItemUtil.icon(Material.SHIELD, ExpeditionMenu.line("Schild", NamedTextColor.GOLD),
                    List.of(ExpeditionMenu.line("Zweite Hand (fester Platz)", NamedTextColor.GRAY))), null);
        }
        set(35, ItemUtil.icon(Material.OAK_SIGN, ExpeditionMenu.line("So sortierst du", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("1. Gegenstand anklicken", NamedTextColor.GRAY),
                ExpeditionMenu.line("2. Zielplatz anklicken", NamedTextColor.GRAY),
                ExpeditionMenu.line("Unten ist die Schnellleiste.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Neues landet auf dem ersten freien Platz.", NamedTextColor.GRAY))), null);
        fillEmpty();
    }
}
