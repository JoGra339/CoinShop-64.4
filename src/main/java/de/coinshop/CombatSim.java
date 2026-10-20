package de.coinshop;

import de.coinshop.ExpeditionManager.Copy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Berechneter Kampf zweier Gruppen (für NPC-Duelle und Untergrund-Arenen). Wie beim Spieler-Duell kämpfen die Ersten
 * beider Seiten gleichzeitig gegeneinander (vordere und hintere Reihe wie im Auto-Battler), Typen zählen, die Begleiter werden nicht verletzt.
 */
public final class CombatSim {

    private CombatSim() {
    }

    /** Ein Kämpfer mit Typ, Leben und Angriff. */
    public record Fighter(String name, CompanionTypes.MobType type, double hp, double atk, boolean defender, String entity, boolean back) {
        /** Gleicher Kämpfer in der vorderen (false) oder hinteren (true) Reihe. */
        public Fighter withBack(boolean b) {
            return new Fighter(name, type, hp, atk, defender, entity, b);
        }
    }

    /** Ein Moment des Kampfes: Leben (Anteil 0 bis 1) jedes Kämpfers beider Seiten, 0 = besiegt. */
    public record Frame(double[] a, double[] b) { }

    /** Kampfablauf als Folge von Momenten (für die Live-Ansicht in der Arena, alle kämpfen gleichzeitig). */
    public record Script(boolean aWins, List<String> log, List<Frame> frames) { }

    private static final java.util.Map<CompanionTypes.MobType, String[]> LOOKS = new java.util.EnumMap<>(CompanionTypes.MobType.class);

    static {
        LOOKS.put(CompanionTypes.MobType.FEUER, new String[]{"MAGMA_CUBE", "PIGLIN_BRUTE", "HOGLIN", "ZOMBIFIED_PIGLIN"});
        LOOKS.put(CompanionTypes.MobType.WASSER, new String[]{"DROWNED", "AXOLOTL", "FROG", "TURTLE"});
        LOOKS.put(CompanionTypes.MobType.NATUR, new String[]{"WOLF", "FOX", "SPIDER", "OCELOT"});
        LOOKS.put(CompanionTypes.MobType.ERDE, new String[]{"IRON_GOLEM", "GOAT", "ARMADILLO", "RAVAGER"});
        LOOKS.put(CompanionTypes.MobType.EIS, new String[]{"POLAR_BEAR", "SNOW_GOLEM", "STRAY"});
        LOOKS.put(CompanionTypes.MobType.LUFT, new String[]{"BREEZE", "CHICKEN", "VEX"});
        LOOKS.put(CompanionTypes.MobType.UNTOTE, new String[]{"SKELETON", "ZOMBIE", "HUSK"});
        LOOKS.put(CompanionTypes.MobType.FINSTER, new String[]{"WITCH", "PILLAGER", "VINDICATOR", "EVOKER"});
    }

    /** Aussehen eines NPC-Kämpfers in der Live-Ansicht (passend zum Typ, je nach Name verschieden). */
    private static String lookOf(CompanionTypes.MobType type, String name) {
        String[] arr = LOOKS.get(type);
        return arr[Math.floorMod(name.hashCode(), arr.length)];
    }

    /** Ergebnis: Sieger (Seite A?) und Kampfbericht. */
    public record Result(boolean aWins, List<String> log) { }

    private static double traitSum(ExpeditionManager ex, UUID owner, Copy k, boolean hp) {
        double t = 1;
        for (CompanionTraits.Trait tr : ex.traits(owner, k.companion(), k.index())) {
            t += hp ? tr.hp : tr.dmg;
        }
        return Math.max(0.3, t);
    }

    /** Kämpfer aus einem echten Begleiter-Exemplar des Spielers "owner". */
    public static Fighter of(ExpeditionManager ex, UUID owner, Copy k) {
        double lb = ex.levelBonus(ex.copyLevel(owner, k.companion(), k.index()));
        double rar = ex.rarity(owner, k.companion(), k.index()).stats;
        double hp = (20 + k.companion().stars * 12.0) * lb * rar * traitSum(ex, owner, k, true) * ex.roleHpFactor(k.companion());
        double atk = (4 + k.companion().stars * 1.5) * lb * rar * traitSum(ex, owner, k, false) * ex.roleDmgFactor(k.companion());
        if (ex.isSupporter(k.companion())) {
            atk = Math.max(1.0, atk * 0.3); // Unterstützer kämpfen kaum, halten aber etwas aus
        }
        return new Fighter(ex.trips().copyName(k.companion(), k.index()), k.companion().mobType(), hp, atk, ex.isDefender(k.companion()), k.companion().type, ex.duel().isBack(owner, k));
    }

    /** Kämpfer eines NPC: Stufe und Sterne bestimmen die Werte wie bei echten Begleitern. */
    public static Fighter npc(ExpeditionManager ex, String name, CompanionTypes.MobType type, int level, int stars, double power) {
        double lb = ex.levelBonus(level);
        double hp = (20 + stars * 12.0) * lb * power;
        double atk = (4 + stars * 1.5) * lb * power;
        return new Fighter(name, type, hp, atk, false, lookOf(type, name), false);
    }

    /** Gesamtstärke wie bei den Duellen (Wurzel aus der Summe von Leben mal Angriff). */
    public static long strength(List<Fighter> team) {
        double sum = 0;
        for (Fighter f : team) {
            sum += f.hp() * f.atk();
        }
        return Math.round(Math.sqrt(sum));
    }

    /** Berechnet den Kampf. Bei Gleichstand gewinnt Seite B (der Verteidiger). */
    public static Result fight(List<Fighter> a, List<Fighter> b, Random r) {
        Script s = script(a, b, r);
        return new Result(s.aWins(), s.log());
    }

    /**
     * Wählt ein lebendes Ziel wie im Auto-Battler: Solange vorne noch jemand steht, wird die hintere Reihe kaum getroffen.
     * Verteidiger werden dreimal so oft gewählt.
     */
    private static int pick(List<Fighter> team, double[] hp, Random r) {
        boolean frontAlive = false;
        for (int i = 0; i < team.size(); i++) {
            if (hp[i] > 0 && !team.get(i).back()) {
                frontAlive = true;
            }
        }
        double sum = 0;
        for (int i = 0; i < team.size(); i++) {
            if (hp[i] > 0) {
                sum += weight(team.get(i), frontAlive);
            }
        }
        double x = r.nextDouble() * sum;
        int last = -1;
        for (int i = 0; i < team.size(); i++) {
            if (hp[i] > 0) {
                last = i;
                x -= weight(team.get(i), frontAlive);
                if (x < 0) {
                    return i;
                }
            }
        }
        return last;
    }

    private static double weight(Fighter f, boolean frontAlive) {
        double w = f.defender() ? 3 : 1;
        return f.back() && frontAlive ? w * 0.12 : w;
    }

    private static Frame snapshot(List<Fighter> a, List<Fighter> b, double[] hpA, double[] hpB) {
        double[] fa = new double[a.size()];
        double[] fb = new double[b.size()];
        for (int i = 0; i < fa.length; i++) {
            fa[i] = hpA[i] <= 0 ? 0 : Math.max(0.02, hpA[i] / a.get(i).hp());
        }
        for (int i = 0; i < fb.length; i++) {
            fb[i] = hpB[i] <= 0 ? 0 : Math.max(0.02, hpB[i] / b.get(i).hp());
        }
        return new Frame(fa, fb);
    }

    private static boolean alive(double[] hp) {
        for (double h : hp) {
            if (h > 0) {
                return true;
            }
        }
        return false;
    }

    private static double total(List<Fighter> team, double[] hp) {
        double t = 0;
        for (int i = 0; i < hp.length; i++) {
            t += Math.max(0, hp[i]) / team.get(i).hp();
        }
        return t;
    }

    /**
     * Berechnet den Kampf: Alle Kämpfer beider Seiten kämpfen gleichzeitig, jeder greift in jeder Runde ein zufälliges
     * lebendes Ziel der Gegenseite an (Verteidiger werden öfter gewählt). Bei Gleichstand gewinnt Seite B.
     */
    public static Script script(List<Fighter> a, List<Fighter> b, Random r) {
        List<String> log = new ArrayList<>();
        List<Frame> frames = new ArrayList<>();
        if (a.isEmpty() || b.isEmpty()) {
            return new Script(!a.isEmpty(), log, frames);
        }
        double[] hpA = new double[a.size()];
        double[] hpB = new double[b.size()];
        for (int i = 0; i < hpA.length; i++) {
            hpA[i] = a.get(i).hp();
        }
        for (int i = 0; i < hpB.length; i++) {
            hpB[i] = b.get(i).hp();
        }
        frames.add(snapshot(a, b, hpA, hpB));
        int rounds = 0;
        while (alive(hpA) && alive(hpB) && rounds < 600) {
            rounds++;
            double[] toA = new double[hpA.length];
            double[] toB = new double[hpB.length];
            int[] hitA = new int[hpA.length];
            int[] hitB = new int[hpB.length];
            for (int i = 0; i < hpA.length; i++) {
                if (hpA[i] > 0) {
                    int j = pick(b, hpB, r);
                    toB[j] += a.get(i).atk() * CompanionTypes.factor(a.get(i).type(), b.get(j).type()) * (0.85 + 0.3 * r.nextDouble());
                    hitB[j] = i;
                }
            }
            for (int j = 0; j < hpB.length; j++) {
                if (hpB[j] > 0) {
                    int i = pick(a, hpA, r);
                    toA[i] += b.get(j).atk() * CompanionTypes.factor(b.get(j).type(), a.get(i).type()) * (0.85 + 0.3 * r.nextDouble());
                    hitA[i] = j;
                }
            }
            for (int i = 0; i < hpA.length; i++) {
                if (hpA[i] > 0) {
                    hpA[i] -= toA[i];
                    if (hpA[i] <= 0) {
                        log.add(b.get(hitA[i]).name() + " besiegt " + a.get(i).name() + ".");
                    }
                }
            }
            for (int j = 0; j < hpB.length; j++) {
                if (hpB[j] > 0) {
                    hpB[j] -= toB[j];
                    if (hpB[j] <= 0) {
                        log.add(a.get(hitB[j]).name() + " besiegt " + b.get(j).name() + ".");
                    }
                }
            }
            frames.add(snapshot(a, b, hpA, hpB));
        }
        boolean aAlive = alive(hpA);
        boolean bAlive = alive(hpB);
        boolean aWins = aAlive && (!bAlive || total(a, hpA) > total(b, hpB));
        return new Script(aWins, log, frames);
    }
}
