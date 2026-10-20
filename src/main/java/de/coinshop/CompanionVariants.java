package de.coinshop;

import org.bukkit.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Varianten der Begleiter (z. B. Wolf aus einem anderen Biom, Katzenfell, Papageienfarbe).
 * Die Variante wird beim Kauf gewürfelt und gilt für dieses Exemplar. Der Zugriff läuft über Reflexion,
 * damit es auch dann kompiliert, wenn eine Version eine Variantenart nicht kennt.
 */
public final class CompanionVariants {

    private CompanionVariants() {
    }

    /** Kreatur -> {Oberklasse, Variantenklasse, Setter}. */
    private static final Map<String, String[]> TABLE = Map.ofEntries(
            Map.entry("WOLF", new String[]{"org.bukkit.entity.Wolf", "org.bukkit.entity.Wolf$Variant", "setVariant"}),
            Map.entry("CAT", new String[]{"org.bukkit.entity.Cat", "org.bukkit.entity.Cat$Type", "setCatType"}),
            Map.entry("FROG", new String[]{"org.bukkit.entity.Frog", "org.bukkit.entity.Frog$Variant", "setVariant"}),
            Map.entry("FOX", new String[]{"org.bukkit.entity.Fox", "org.bukkit.entity.Fox$Type", "setFoxType"}),
            Map.entry("PARROT", new String[]{"org.bukkit.entity.Parrot", "org.bukkit.entity.Parrot$Variant", "setVariant"}),
            Map.entry("AXOLOTL", new String[]{"org.bukkit.entity.Axolotl", "org.bukkit.entity.Axolotl$Variant", "setVariant"}),
            Map.entry("LLAMA", new String[]{"org.bukkit.entity.Llama", "org.bukkit.entity.Llama$Color", "setColor"}),
            Map.entry("TRADER_LLAMA", new String[]{"org.bukkit.entity.Llama", "org.bukkit.entity.Llama$Color", "setColor"}),
            Map.entry("HORSE", new String[]{"org.bukkit.entity.Horse", "org.bukkit.entity.Horse$Color", "setColor"}),
            Map.entry("MOOSHROOM", new String[]{"org.bukkit.entity.MushroomCow", "org.bukkit.entity.MushroomCow$Variant", "setVariant"}),
            Map.entry("PIG", new String[]{"org.bukkit.entity.Pig", "org.bukkit.entity.Pig$Variant", "setVariant"}),
            Map.entry("COW", new String[]{"org.bukkit.entity.Cow", "org.bukkit.entity.Cow$Variant", "setVariant"}),
            Map.entry("CHICKEN", new String[]{"org.bukkit.entity.Chicken", "org.bukkit.entity.Chicken$Variant", "setVariant"}),
            Map.entry("VILLAGER", new String[]{"org.bukkit.entity.Villager", "org.bukkit.entity.Villager$Type", "setVillagerType"}),
            Map.entry("PANDA", new String[]{"org.bukkit.entity.Panda", "org.bukkit.entity.Panda$Gene", "setMainGene"}),
            Map.entry("TROPICAL_FISH", new String[]{"org.bukkit.entity.TropicalFish", "org.bukkit.entity.TropicalFish$Pattern", "setPattern"}),
            Map.entry("RABBIT", new String[]{"org.bukkit.entity.Rabbit", "org.bukkit.entity.Rabbit$Type", "setRabbitType"}));

    private static final Map<String, String> DE = Map.ofEntries(
            Map.entry("ASHEN", "Aschgrau"), Map.entry("BLACK", "Schwarz"), Map.entry("CHESTNUT", "Kastanie"),
            Map.entry("PALE", "Blass"), Map.entry("RUSTY", "Rostrot"), Map.entry("SNOWY", "Verschneit"),
            Map.entry("SPOTTED", "Gefleckt"), Map.entry("STRIPED", "Gestreift"), Map.entry("WOODS", "Wald"),
            Map.entry("RED", "Rot"), Map.entry("SNOW", "Schnee"), Map.entry("WHITE", "Weiß"), Map.entry("BROWN", "Braun"),
            Map.entry("GOLD", "Gold"), Map.entry("BLUE", "Blau"), Map.entry("GREEN", "Grün"), Map.entry("CYAN", "Türkis"),
            Map.entry("GRAY", "Grau"), Map.entry("CREAMY", "Creme"), Map.entry("TABBY", "Getigert"),
            Map.entry("TUXEDO", "Frack"), Map.entry("RAGDOLL", "Ragdoll"), Map.entry("CALICO", "Dreifarbig"),
            Map.entry("BRITISH_SHORTHAIR", "Britisch Kurzhaar"), Map.entry("JELLIE", "Jellie"),
            Map.entry("PERSIAN", "Perser"), Map.entry("SIAMESE", "Siam"), Map.entry("ALL_BLACK", "Ganz schwarz"),
            Map.entry("TEMPERATE", "Gemäßigt"), Map.entry("WARM", "Warm"), Map.entry("COLD", "Kalt"),
            Map.entry("LUCY", "Lucy"), Map.entry("WILD", "Wild"), Map.entry("DESERT", "Wüste"),
            Map.entry("JUNGLE", "Dschungel"), Map.entry("PLAINS", "Ebene"), Map.entry("SAVANNA", "Savanne"),
            Map.entry("SWAMP", "Sumpf"), Map.entry("TAIGA", "Taiga"), Map.entry("LAZY", "Faul"),
            Map.entry("WORRIED", "Besorgt"), Map.entry("PLAYFUL", "Verspielt"), Map.entry("WEAK", "Schwach"),
            Map.entry("AGGRESSIVE", "Aggressiv"), Map.entry("NORMAL", "Normal"), Map.entry("SALT_AND_PEPPER", "Salz und Pfeffer"),
            Map.entry("BLACK_AND_WHITE", "Schwarzweiß"), Map.entry("DARK_BROWN", "Dunkelbraun"));

    private static List<Field> options(String typeName) {
        String[] row = TABLE.get(typeName);
        List<Field> list = new ArrayList<>();
        if (row == null) {
            return list;
        }
        try {
            Class<?> variant = Class.forName(row[1]);
            for (Field f : variant.getFields()) {
                if (Modifier.isStatic(f.getModifiers()) && variant.isAssignableFrom(f.getType())) {
                    list.add(f);
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // diese Version kennt die Variantenart nicht
        }
        return list;
    }

    /** Würfelt eine Variante für diese Kreatur (leer = diese Art hat keine Varianten). */
    public static String roll(Random random, String typeName) {
        List<Field> list = options(typeName);
        if (list.isEmpty()) {
            return "";
        }
        return list.get(random.nextInt(list.size())).getName();
    }

    /** Gibt der Kreatur ihre Variante (still, wenn etwas nicht passt). */
    public static void apply(LivingEntity entity, String variant) {
        if (variant == null || variant.isEmpty()) {
            return;
        }
        String[] row = TABLE.get(entity.getType().name());
        if (row == null) {
            return;
        }
        try {
            Class<?> variantClass = Class.forName(row[1]);
            Field f = variantClass.getField(variant);
            Object value = f.get(null);
            Method m = Class.forName(row[0]).getMethod(row[2], variantClass);
            m.invoke(entity, value);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Variante nicht möglich: Standardaussehen
        }
    }

    /** Anzeigename der Variante. */
    public static String label(String variant) {
        if (variant == null || variant.isEmpty()) {
            return "";
        }
        String de = DE.get(variant);
        if (de != null) {
            return de;
        }
        String lower = variant.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
