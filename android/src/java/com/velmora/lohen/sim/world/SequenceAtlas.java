/*
 * LOHEN — sim/world/SequenceAtlas.java
 *
 * BLOC 09 : la ville de Velmora, zone par zone. Cette classe est la
 * reference NUMERIQUE du chapitre 1 : les 8 sequences (altitude, duree
 * cible, regle de traversee), les 7 reperes visuels de la regle des
 * 212 metres, les 6 salles a lampe, les 7 couloirs de transition,
 * les 47 checkpoints, les 14 raccourcis et les 31 secrets.
 *
 * 09.02 [OBL] : la verticale est continue et coherente. Un joueur a 180 m
 * doit pouvoir identifier a l'oeil nu l'endroit exact ou il etait a 20 m.
 * Pas de teleportation, pas de coupures geographiques, un repere unique
 * par etage.
 */
package com.velmora.lohen.sim.world;

import com.velmora.lohen.sim.math.Maths;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SequenceAtlas {

    /* ------------------------------------------------------------------ */
    /* 09.03 CARTE DES 8 SEQUENCES                                         */
    /* ------------------------------------------------------------------ */

    public static final class Sequence {
        public final String id;
        public final String name;
        public final String line;             /* la phrase-titre */
        public final float altitudeFrom;
        public final float altitudeTo;
        public final float targetMinutes;
        public final String rule;
        public final String ambience;
        public final String reverb;
        public final String music;
        public final float saturation;        /* 05.08 */
        public final boolean grappleAllowed;
        public final boolean enemies;
        public final boolean indoor;
        public final int npcCount;
        public final int checkpoints;
        public final int shortcuts;
        public final int secrets;
        public final int corridors;
        public final String landmark;

        Sequence(String id, String name, String line, float a0, float a1, float minutes,
                 String rule, String ambience, String reverb, String music, float saturation,
                 boolean grapple, boolean enemies, boolean indoor, int npc, int cp, int sc,
                 int sec, int corr, String landmark) {
            this.id = id;
            this.name = name;
            this.line = line;
            this.altitudeFrom = a0;
            this.altitudeTo = a1;
            this.targetMinutes = minutes;
            this.rule = rule;
            this.ambience = ambience;
            this.reverb = reverb;
            this.music = music;
            this.saturation = saturation;
            this.grappleAllowed = grapple;
            this.enemies = enemies;
            this.indoor = indoor;
            this.npcCount = npc;
            this.checkpoints = cp;
            this.shortcuts = sc;
            this.secrets = sec;
            this.corridors = corr;
            this.landmark = landmark;
        }

        public float altitudeSpan() {
            return Math.abs(altitudeTo - altitudeFrom);
        }

        public boolean descending() {
            return altitudeTo < altitudeFrom;
        }
    }

    public static final Map<String, Sequence> SEQUENCES = new LinkedHashMap<String, Sequence>();
    public static final List<String> ORDER = new ArrayList<String>(8);

    static {
        add(new Sequence("S1", "LES QUAIS BAS",
                "Ce qui reste quand la mer s'arrete.",
                0f, 14f, 26f,
                "marche, saut, echelles",
                "port_mort", "falaise", "M01", 0.30f,
                true, false, false, 2, 6, 2, 4, 1, "la grue rouge"));
        add(new Sequence("S2", "LE VERRE ET LE BATEAU",
                "On ne marche pas sur une tombe. Si.",
                0f, 6f, 22f,
                "le verre, pas de grappin",
                "maree_verre", "falaise", "M02", 0.28f,
                false, false, false, 1, 4, 1, 3, 1, "le pont casse"));
        add(new Sequence("S3", "LE MARCHE SUSPENDU",
                "La ville a continue. Mal, mais elle a continue.",
                18f, 62f, 34f,
                "grappin pendule, foule",
                "marche_suspendu", "hangar", "M04", 0.55f,
                true, false, false, 34, 9, 4, 7, 1, "la rangee de linge bleu"));
        add(new Sequence("S4", "LA BIBLIOTHEQUE DES EAUX",
                "Ici on garde les noms.",
                55f, 78f, 28f,
                "interieur, silence, Echos",
                "bibliotheque_eaux", "eglise", "M08", 0.34f,
                true, false, true, 3, 6, 2, 5, 1, "la baleine de bois"));
        add(new Sequence("S5", "LES CONDUITS",
                "Le ventre.",
                40f, 96f, 24f,
                "etroit, Sol, furtivite",
                "conduits", "tunnel", "M09", 0.24f,
                false, true, true, 2, 5, 1, 3, 1, "le clocher penche"));
        add(new Sequence("S6", "LA SALLE DE BAL",
                "Le souvenir.",
                96f, 118f, 18f,
                "l'Echo le plus long",
                "salle_de_bal", "piece_vide", "M12", 0.70f,
                true, false, true, 0, 3, 0, 2, 1, "la serre de verre vert"));
        add(new Sequence("S7", "LA DESCENTE",
                "Il faut redescendre.",
                118f, 8f, 30f,
                "sans grappin, le Verrier",
                "pluie_battante", "falaise", "M14", 0.26f,
                false, true, false, 4, 7, 2, 4, 1, "la statue sans tete"));
        add(new Sequence("S8", "LA MONTEE AU PHARE",
                "Tout en haut, une lampe allumee.",
                8f, 212f, 36f,
                "tout, aucun ennemi",
                "aube_verre", "falaise", "M16", 0.40f,
                true, false, false, 2, 7, 2, 3, 0, "le Phare de Saint-Ambre"));
    }

    private static void add(Sequence s) {
        SEQUENCES.put(s.id, s);
        ORDER.add(s.id);
    }

    public static Sequence get(String id) {
        return SEQUENCES.get(id);
    }

    public static int index(String id) {
        return ORDER.indexOf(id);
    }

    public static String nextOf(String id) {
        int i = ORDER.indexOf(id);
        return i < 0 || i + 1 >= ORDER.size() ? "" : ORDER.get(i + 1);
    }

    /* ------------------------------------------------------------------ */
    /* Totaux (09.03 / 09.22 / 09.23 / 09.24)                              */
    /* ------------------------------------------------------------------ */

    /** 3 h 38 de gameplay pur. */
    public static float totalTargetMinutes() {
        float t = 0f;
        for (Sequence s : SEQUENCES.values()) {
            t += s.targetMinutes;
        }
        return t;                 /* 218 min */
    }

    public static float totalGameplayHours() {
        return totalTargetMinutes() / 60f;
    }

    /** 4 h 02 cinematiques incluses (23 min 40 s de cinema, BLOC 15). */
    public static float totalWithCinematicsHours() {
        return (totalTargetMinutes() + CinematicBudget.TOTAL_MINUTES) / 60f;
    }

    public static int totalCheckpoints() {
        int n = 0;
        for (Sequence s : SEQUENCES.values()) {
            n += s.checkpoints;
        }
        return n;                 /* 47 */
    }

    public static int totalShortcuts() {
        int n = 0;
        for (Sequence s : SEQUENCES.values()) {
            n += s.shortcuts;
        }
        return n;                 /* 14 */
    }

    public static int totalSecrets() {
        int n = 0;
        for (Sequence s : SEQUENCES.values()) {
            n += s.secrets;
        }
        return n;                 /* 31 */
    }

    public static int totalCorridors() {
        int n = 0;
        for (Sequence s : SEQUENCES.values()) {
            n += s.corridors;
        }
        return n;                 /* 7 */
    }

    /* ------------------------------------------------------------------ */
    /* 09.02 LES 7 REPERES                                                 */
    /* ------------------------------------------------------------------ */

    /** Repere unique par etage historique — reconnaissable a 180 m. */
    public static final String[] LANDMARKS = {
            "la grue rouge",
            "le clocher penche",
            "la baleine de bois",
            "le pont casse",
            "la serre de verre vert",
            "la rangee de linge bleu",
            "la statue sans tete",
    };

    /** Altitude de reference de chaque repere (09.02 : verticale continue). */
    public static final float[] LANDMARK_ALTITUDES = {
            11f, 46f, 63f, 84f, 108f, 141f, 178f,
    };

    public static final float CITY_TOP = 212f;
    public static final float CITY_BOTTOM = 0f;
    public static final int HISTORIC_LEVELS = 7;

    /**
     * 09.02 : depuis une altitude donnee, quel repere le joueur doit-il
     * pouvoir identifier ? Celui dont l'altitude est la plus proche sans
     * etre au-dessus du regard.
     */
    public static String landmarkFor(float altitude) {
        int best = 0;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < LANDMARKS.length; i++) {
            float d = Math.abs(LANDMARK_ALTITUDES[i] - altitude);
            /* penalite si le repere est au-dessus : on regarde vers le bas */
            if (LANDMARK_ALTITUDES[i] > altitude) {
                d += 6f;
            }
            if (d < bestScore) {
                bestScore = d;
                best = i;
            }
        }
        return LANDMARKS[best];
    }

    /* ------------------------------------------------------------------ */
    /* 09.44 LES 6 SALLES A LAMPE                                          */
    /* ------------------------------------------------------------------ */

    public static final class LampRoom {
        public final String id;
        public final String seq;
        public final String name;
        public final int shadowedLights;      /* max 4 par piece */
        public final int shadowResolution = 512;

        LampRoom(String id, String seq, String name, int lights) {
            this.id = id;
            this.seq = seq;
            this.name = name;
            this.shadowedLights = lights;
        }
    }

    public static final List<LampRoom> LAMP_ROOMS = new ArrayList<LampRoom>(6);

    static {
        LAMP_ROOMS.add(new LampRoom("L1", "S1", "le bureau du Registre", 3));
        LAMP_ROOMS.add(new LampRoom("L2", "S2", "la cabine de L'Hirondelle", 2));
        LAMP_ROOMS.add(new LampRoom("L3", "S3", "le poste de Tallec", 4));
        LAMP_ROOMS.add(new LampRoom("L4", "S4", "la salle des fiches", 3));
        LAMP_ROOMS.add(new LampRoom("L5", "S5", "le conduit du puits", 2));
        LAMP_ROOMS.add(new LampRoom("L6", "S8", "la chambre du Phare", 4));
    }

    /* ------------------------------------------------------------------ */
    /* 09.51 LES 7 COULOIRS DE TRANSITION                                  */
    /* ------------------------------------------------------------------ */

    public static final class Corridor {
        public final String id;
        public final String from;
        public final String to;
        public final float seconds;
        public final int turns;              /* jamais droit : 2 virages minimum */
        public final int drawCalls;          /* < 40 */
        public final String content;

        Corridor(String id, String from, String to, float seconds, int turns,
                 int drawCalls, String content) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.seconds = seconds;
            this.turns = turns;
            this.drawCalls = drawCalls;
            this.content = content;
        }
    }

    public static final List<Corridor> CORRIDORS = new ArrayList<Corridor>(7);

    static {
        CORRIDORS.add(new Corridor("T1", "S1", "S2", 26f, 2, 32,
                "l'echelle qui descend sur la Maree — bark de Lohen"));
        CORRIDORS.add(new Corridor("T2", "S2", "S3", 34f, 3, 38,
                "la monte-charge des cordages — dialogue Sol"));
        CORRIDORS.add(new Corridor("T3", "S3", "S4", 40f, 2, 36,
                "le monte-charge de la Bibliotheque, trois contrepoids"));
        CORRIDORS.add(new Corridor("T4", "S4", "S5", 22f, 3, 28,
                "la porte derriere les fiches — la cle de Mireille"));
        CORRIDORS.add(new Corridor("T5", "S5", "S6", 18f, 2, 24,
                "la grille qui donne sur une salle immense et intacte"));
        CORRIDORS.add(new Corridor("T6", "S6", "S7", 30f, 2, 34,
                "la sortie sous la pluie — le cable casse"));
        CORRIDORS.add(new Corridor("T7", "S7", "S8", 32f, 3, 30,
                "Sol repare le grappin, 90 s, au pied du Phare"));
    }

    public static Corridor corridor(String id) {
        for (Corridor c : CORRIDORS) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    /** 09.51 : chaque couloir est jouable, 18 a 40 s, < 40 draw calls, >= 2 virages. */
    public static boolean corridorsCompliant() {
        for (Corridor c : CORRIDORS) {
            if (c.seconds < 18f || c.seconds > 40f) {
                return false;
            }
            if (c.turns < 2) {
                return false;
            }
            if (c.drawCalls >= 40) {
                return false;
            }
        }
        return CORRIDORS.size() == 7;
    }

    /* ------------------------------------------------------------------ */
    /* 09.24 LES 31 SECRETS — decomposition exacte                         */
    /* ------------------------------------------------------------------ */

    public static final int SECRET_LETTER_FRAGMENTS = 9;
    public static final int SECRET_JOURNAL_OBJECTS = 6;
    public static final int SECRET_BREATH_UPGRADES = 3;
    public static final float SECRET_BREATH_GAIN = 10f;
    public static final int SECRET_VIEWPOINTS = 8;
    public static final float VIEWPOINT_PLAN_SECONDS = 20f;
    public static final int SECRET_OPTIONAL_ECHOS = 5;
    public static final int SECRET_OPTIONAL_ECHOS_DEVASTATING = 2;

    public static boolean secretsDecompositionValid() {
        return SECRET_LETTER_FRAGMENTS + SECRET_JOURNAL_OBJECTS + SECRET_BREATH_UPGRADES
                + SECRET_VIEWPOINTS + SECRET_OPTIONAL_ECHOS == 31;
    }

    /* 09.24 [OBL] : AUCUN compteur de collectibles n'est affiche en jeu. */
    public static final boolean SHOW_COLLECTIBLE_COUNTER = false;

    /* ------------------------------------------------------------------ */
    /* 09.20 REGLE DU POINT D'INTERET VISIBLE                              */
    /* ------------------------------------------------------------------ */

    public static final int POI_AUDIT_SAMPLES = 2000;

    /**
     * 09.20 [OBL] : depuis n'importe quel point du chemin principal, le
     * joueur doit voir au moins UN element qui l'attire. Score de 0 a 1.
     * Un point est attire par : une lumiere, un mouvement, une couleur,
     * une forme verticale.
     */
    public static float poiScore(float altitude, float distanceToLandmark,
                                 float visibleLights, float saturation) {
        float vertical = Maths.clamp01(1f - distanceToLandmark / 180f);
        float light = Maths.clamp01(visibleLights / 3f);
        float colour = Maths.clamp01((saturation - 0.25f) * 2.2f);
        float height = Maths.clamp01((CITY_TOP - altitude) / CITY_TOP);
        return Maths.clamp01(vertical * 0.42f + light * 0.28f + colour * 0.16f + height * 0.14f);
    }

    /* ------------------------------------------------------------------ */
    /* 09.40 DENSITE D'EVENEMENTS                                          */
    /* ------------------------------------------------------------------ */

    public static final float EVENT_MIN_SECONDS = 45f;
    public static final float EVENT_MAX_SECONDS = 90f;
    /** Un trou de plus de 2 minutes est un BUG de design (09.40). */
    public static final float EVENT_BUG_SECONDS = 120f;

    public static boolean pacingValid(float secondsSinceLastEvent) {
        return secondsSinceLastEvent <= EVENT_BUG_SECONDS;
    }

    /* ------------------------------------------------------------------ */
    /* Budget cinematiques (BLOC 15)                                       */
    /* ------------------------------------------------------------------ */

    public static final class CinematicBudget {
        public static final int COUNT = 11;
        public static final float TOTAL_MINUTES = 23f + 40f / 60f;
        public static final float SKIP_HOLD_SECONDS = 1.5f;
        public static final float LETTER_SKIP_HOLD_SECONDS = 20f;
        public static final float FADE_SECONDS = 0.4f;
        public static final float ASPECT = 2.39f;      /* 2.39:1 letterbox */
    }

    /* ------------------------------------------------------------------ */
    /* Garde CI                                                            */
    /* ------------------------------------------------------------------ */

    /** BLOC 22 : la carte du chapitre est complete et coherente. */
    public static boolean atlasCompliant() {
        return ORDER.size() == 8
                && totalCheckpoints() == 47
                && totalShortcuts() == 14
                && totalSecrets() == 31
                && LAMP_ROOMS.size() == 6
                && LANDMARKS.length == 7
                && corridorsCompliant()
                && secretsDecompositionValid()
                && Math.abs(totalTargetMinutes() - 218f) < 0.01f
                && CinematicBudget.COUNT == 11;
    }

    /** 09.03 : la premiere sequence demarre a 0 m, la derniere finit a 212 m. */
    public static boolean verticalContinuity() {
        Sequence first = SEQUENCES.get(ORDER.get(0));
        Sequence last = SEQUENCES.get(ORDER.get(ORDER.size() - 1));
        return first.altitudeFrom == CITY_BOTTOM
                && Math.abs(last.altitudeTo - CITY_TOP) < 0.001f;
    }

    private SequenceAtlas() {
    }
}
