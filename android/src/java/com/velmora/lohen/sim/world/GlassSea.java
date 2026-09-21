/*
 * LOHEN — sim/world/GlassSea.java
 *
 * 10.01 LA MAREE DE VERRE : le 14 octobre a 23 h 41, la mer autour de
 * Velmora est devenue solide. Pas gelee : vitrifiee, a 41 degres, sur une
 * profondeur de 30 a 60 metres et un rayon de 4 kilometres. Le phenomene a
 * dure 9 secondes. 641 personnes se trouvaient sur l'eau, sous l'eau ou sur
 * les quais bas. Aucune n'a ete retrouvee. Aucune n'a ete vue mourir.
 * Le verre est stable, tiede, et conserve intactes les choses qu'il a
 * prises. Il ne se brise pas. Il ne fond pas. On peut marcher dessus.
 * [OBL] Le Chapitre 1 n'explique JAMAIS pourquoi c'est arrive.
 *
 * 09.11 S2 — REGLE UNIQUE : le grappin ne s'accroche pas au verre.
 * Le Souffle diminue passivement (1/s) tant qu'on est sur la Maree (le
 * verre est tiede, l'air y est mauvais). Des « puits » de chaleur (zones
 * plus claires) rendent le verre mou : on s'y enfonce en 2,4 s. Il faut
 * courir. C'est la seule sequence de pression temporelle du chapitre.
 * SPECTACLE : sous ses pieds, a 3 m de profondeur, un tramway complet avec
 * des silhouettes assises. Le jeu ne s'arrete pas. La camera ne cadre pas.
 * C'est juste la, et on marche dessus.
 *
 * 10.03 : quand on brise une Figure, un objet tombe — une cle, un bouton,
 * un peigne. 11 objets a la fin du chapitre. Aucune explication.
 */
package com.velmora.lohen.sim.world;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Rng;
import com.velmora.lohen.sim.math.Geom;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.player.BreathComponent;

import java.util.ArrayList;
import java.util.List;

public final class GlassSea {

    /* ---------------- 10.01 verite interne (jamais revelee) -------------- */
    public static final String DATE = "14 octobre";
    public static final String TIME = "23:41";
    public static final float VITRIFICATION_C = 41f;
    public static final float DEPTH_MIN_M = 30f;
    public static final float DEPTH_MAX_M = 60f;
    public static final float RADIUS_KM = 4f;
    public static final float PHENOMENON_SECONDS = 9f;
    public static final int MISSING = 641;
    public static final int STAYED_IN_HEIGHT = 400;
    /** [OBL] aucune explication, scientifique ou magique, nulle part. */
    public static final boolean EXPLAINED = false;

    /* ---------------- 09.11 regles de jeu ---------------- */
    public static final float BREATH_DRAIN_PER_SECOND = 1f;
    public static final float HEAT_WELL_SINK_SECONDS = 2.4f;
    public static final float HEAT_WELL_WARNING_SECONDS = 0.9f;
    public static final float SPECTACLE_DEPTH_M = 3f;
    public static final boolean GRAPPLE_ON_GLASS = false;
    public static final boolean CAMERA_FRAMES_SPECTACLE = false;
    public static final boolean GAME_STOPS_FOR_SPECTACLE = false;

    /* ---------------- les spectacles sous le verre ---------------- */
    public static final class Spectacle {
        public final String id;
        public final String what;
        public final float x;
        public final float z;
        public final float depth;
        public final boolean figures;

        Spectacle(String id, String what, float x, float z, float depth, boolean figures) {
            this.id = id;
            this.what = what;
            this.x = x;
            this.z = z;
            this.depth = depth;
            this.figures = figures;
        }
    }

    public static final List<Spectacle> SPECTACLES = new ArrayList<Spectacle>(9);

    static {
        SPECTACLES.add(new Spectacle("SP1", "un tramway complet, silhouettes assises",
                18f, -34f, SPECTACLE_DEPTH_M, true));
        SPECTACLES.add(new Spectacle("SP2", "une rangee de chaises de cafe, alignees",
                -26f, 12f, 2.2f, false));
        SPECTACLES.add(new Spectacle("SP3", "un etal de marche, ses cagettes, son auvent",
                41f, 26f, 4.1f, false));
        SPECTACLES.add(new Spectacle("SP4", "une barque retournee, une rame encore tenue",
                -58f, -19f, 1.6f, true));
        SPECTACLES.add(new Spectacle("SP5", "un abri de bus, ses horaires, deux personnes",
                64f, -61f, 3.4f, true));
        SPECTACLES.add(new Spectacle("SP6", "un piano a queue, le couvercle ouvert",
                -12f, 74f, 5.2f, false));
        SPECTACLES.add(new Spectacle("SP7", "une file de voitures, portieres ouvertes",
                88f, 44f, 6.8f, true));
        SPECTACLES.add(new Spectacle("SP8", "un chien, assis, qui attend",
                6f, 8f, 1.1f, false));
        SPECTACLES.add(new Spectacle("SP9", "la halle aux poissons, ses crochets, ses bacs",
                -84f, 38f, 8.4f, false));
    }

    /* ---------------- L'Hirondelle de Mer (09.11) ---------------- */
    public static final String TRAWLER = "L'HIRONDELLE DE MER";
    public static final float TRAWLER_ROLL_DEG = 68f;      /* couche sur le flanc */
    public static final float TRAWLER_SWALLOWED = 0.55f;   /* a moitie avale */
    public static final String CABIN_ECHO = "E04";         /* la veste d'Esteban, 2:40 */
    public static final float TRAWLER_CRACK_SECONDS = 3.2f;

    private final EventBus bus;
    private final Rng rng;

    private boolean onGlass;
    private float timeOnGlass;
    private float sinkTimer;
    private boolean sinking;
    private boolean sank;
    private int heatWellEntries;
    private int sinkEscapes;
    private float breathSpent;
    private int spectaclesPassed;
    private String lastSpectacle = "";
    private boolean figureSeen;
    private float figureDistance;
    private float figureTimer;
    private boolean trawlerReached;
    private boolean trawlerCracked;
    private float crackTimer;
    private int grappleRefusals;
    private float longestRun;
    private float runTimer;

    public GlassSea(EventBus bus, Rng rng) {
        this.bus = bus;
        this.rng = rng;
    }

    /* ------------------------------------------------------------------ */
    /* Boucle                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * @param breath peut etre null (audit, tests) — dans ce cas le Souffle
     *               n'est pas ponctionne, seule la logique d'etat tourne.
     */
    public void update(float dt, PhysicsWorld world, BreathComponent breath,
                       float px, float py, float pz, boolean grappleFiredAtGlass) {
        boolean glass = world != null && world.isOnGlassSea(px, py, pz);
        if (glass && !onGlass) {
            onGlass = true;
            timeOnGlass = 0f;
            if (bus != null) {
                bus.emit(EventBus.GLASS_SEA_ENTERED, px, py, pz);
            }
        } else if (!glass && onGlass) {
            onGlass = false;
            sinking = false;
            sinkTimer = 0f;
            if (bus != null) {
                bus.emit(EventBus.GLASS_SEA_LEFT, timeOnGlass);
            }
        }
        if (grappleFiredAtGlass) {
            grappleRefusals++;
        }
        if (!onGlass) {
            return;
        }
        timeOnGlass += dt;
        /* 09.11 : le Souffle diminue passivement (1/s) — le verre est tiede */
        if (breath != null) {
            float before = breath.current();
            breath.glassPassiveDrain(dt);
            breathSpent += Math.max(0f, before - breath.current());
        }
        /* puits de chaleur : le verre ramollit, on s'y enfonce en 2,4 s */
        boolean well = world.isHeatWell(px, py, pz);
        if (well && !sinking && !sank) {
            sinking = true;
            sinkTimer = 0f;
            heatWellEntries++;
            if (bus != null) {
                bus.emit(EventBus.HEAT_WELL_ENTERED, HEAT_WELL_SINK_SECONDS);
            }
        }
        if (sinking) {
            sinkTimer += dt;
            if (sinkTimer >= HEAT_WELL_WARNING_SECONDS && !sank) {
                if (bus != null) {
                    bus.emit(EventBus.HEAT_WELL_WARNING,
                            HEAT_WELL_SINK_SECONDS - sinkTimer);
                }
            }
            if (sinkTimer >= HEAT_WELL_SINK_SECONDS) {
                sank = true;
                sinking = false;
                if (bus != null) {
                    bus.emit(EventBus.PLAYER_SANK_IN_GLASS);
                }
            } else if (!well) {
                /* sorti du puits en courant : c'est tout l'enjeu de S2 */
                sinking = false;
                sinkTimer = 0f;
                sinkEscapes++;
                if (bus != null) {
                    bus.emit(EventBus.HEAT_WELL_ESCAPED, sinkEscapes);
                }
            }
        }
        /* pression temporelle : la seule sequence ou il FAUT courir */
        runTimer += dt;
        longestRun = Math.max(longestRun, runTimer);
        /* les spectacles sous le verre : rien ne s'arrete, rien ne cadre */
        for (int i = 0; i < SPECTACLES.size(); i++) {
            Spectacle s = SPECTACLES.get(i);
            float dx = px - s.x;
            float dz = pz - s.z;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < 6f && !s.id.equals(lastSpectacle)) {
                lastSpectacle = s.id;
                spectaclesPassed++;
                runTimer = 0f;
                if (bus != null) {
                    /* pas de fondu, pas de ralenti, pas de musique : juste un fait */
                    bus.emit(EventBus.GLASS_SPECTACLE, s.id, s.what, s.depth);
                }
            }
        }
        /* la premiere Figure : vue de loin, immobile, elle regarde */
        if (!figureSeen && timeOnGlass > 240f && rng.chance(0.02f)) {
            figureSeen = true;
            figureDistance = rng.range(48f, 120f);
            figureTimer = 0f;
            if (bus != null) {
                bus.emit(EventBus.FIGURE_SEEN_FAR, figureDistance);
            }
        }
        if (figureSeen) {
            figureTimer += dt;
        }
    }

    /* ------------------------------------------------------------------ */
    /* L'Hirondelle de Mer                                                 */
    /* ------------------------------------------------------------------ */

    public boolean reachTrawler(float px, float py, float pz, float trawlerX, float trawlerZ) {
        float dx = px - trawlerX;
        float dz = pz - trawlerZ;
        if ((float) Math.sqrt(dx * dx + dz * dz) > 14f) {
            return false;
        }
        if (!trawlerReached) {
            trawlerReached = true;
            if (bus != null) {
                bus.emit(EventBus.TRAWLER_REACHED, TRAWLER, CABIN_ECHO);
            }
        }
        return true;
    }

    /**
     * 09.11 SORTIE : le bateau se fissure, le verre s'ouvre, premiere fuite
     * en urgence.
     */
    public void crackTrawler() {
        if (trawlerCracked) {
            return;
        }
        trawlerCracked = true;
        crackTimer = TRAWLER_CRACK_SECONDS;
        if (bus != null) {
            bus.emit(EventBus.TRAWLER_CRACKED, TRAWLER_CRACK_SECONDS);
            bus.emit(EventBus.GLASS_OPENED);
        }
    }

    public float crackTimer() {
        return crackTimer;
    }

    public void tickCrack(float dt) {
        if (crackTimer > 0f) {
            crackTimer = Math.max(0f, crackTimer - dt);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 10.03 : les objets tombes des Figures                               */
    /* ------------------------------------------------------------------ */

    public static final String[] FIGURE_DROPS = {
            "une cle", "un bouton", "un peigne", "une alliance", "un de a coudre",
            "un sifflet", "une montre arretee", "un bilboquet", "une medaille",
            "un cure-dent en argent", "un ticket de tramway",
    };
    public static final int FIGURE_DROP_TOTAL = 11;

    /** Un objet tombe. Le journal l'enregistre. Le joueur n'en fait rien. */
    public String onFigureBroken(int brokenIndex) {
        if (brokenIndex < 0 || brokenIndex >= FIGURE_DROPS.length) {
            return "";
        }
        String item = FIGURE_DROPS[brokenIndex];
        if (bus != null) {
            bus.emit(EventBus.FIGURE_DROPPED_OBJECT, item, brokenIndex);
        }
        return item;
    }

    /* ------------------------------------------------------------------ */
    /* Regles exposees aux autres systemes                                 */
    /* ------------------------------------------------------------------ */

    /** 09.11 : le grappin ne s'accroche pas au verre. */
    public static boolean grappleAllowedOn(int material, int flags) {
        if (material == Geom.MAT_GLASS || (flags & Geom.FLAG_GLASS) != 0) {
            return GRAPPLE_ON_GLASS;
        }
        return true;
    }

    /** 09.11 : la camera ne cadre jamais le spectacle, le jeu ne s'arrete pas. */
    public static boolean stageSpectacle() {
        return CAMERA_FRAMES_SPECTACLE || GAME_STOPS_FOR_SPECTACLE;
    }

    /* ------------------------------------------------------------------ */
    /* Acces                                                               */
    /* ------------------------------------------------------------------ */

    public boolean onGlass() {
        return onGlass;
    }

    public float timeOnGlass() {
        return timeOnGlass;
    }

    public boolean sinking() {
        return sinking;
    }

    public float sinkProgress() {
        return Maths.clamp01(sinkTimer / HEAT_WELL_SINK_SECONDS);
    }

    public boolean sank() {
        return sank;
    }

    public int heatWellEntries() {
        return heatWellEntries;
    }

    public int sinkEscapes() {
        return sinkEscapes;
    }

    public float breathSpent() {
        return breathSpent;
    }

    public int spectaclesPassed() {
        return spectaclesPassed;
    }

    public boolean figureSeen() {
        return figureSeen;
    }

    public float figureDistance() {
        return figureDistance;
    }

    public boolean trawlerReached() {
        return trawlerReached;
    }

    public boolean trawlerCracked() {
        return trawlerCracked;
    }

    public int grappleRefusals() {
        return grappleRefusals;
    }

    public float longestRun() {
        return longestRun;
    }

    /** Garde CI : 1/s, 2,4 s, 3 m, 641, jamais explique (09.11 / 10.01). */
    public static boolean specCompliant() {
        return BREATH_DRAIN_PER_SECOND == 1f
                && Math.abs(HEAT_WELL_SINK_SECONDS - 2.4f) < 0.0001f
                && SPECTACLE_DEPTH_M == 3f
                && MISSING == 641
                && !EXPLAINED
                && !GRAPPLE_ON_GLASS
                && FIGURE_DROP_TOTAL == 11
                && FIGURE_DROPS.length >= FIGURE_DROP_TOTAL;
    }

    public void reset() {
        onGlass = false;
        timeOnGlass = 0f;
        sinkTimer = 0f;
        sinking = false;
        sank = false;
        heatWellEntries = 0;
        sinkEscapes = 0;
        breathSpent = 0f;
        spectaclesPassed = 0;
        lastSpectacle = "";
        figureSeen = false;
        figureTimer = 0f;
        trawlerReached = false;
        trawlerCracked = false;
        crackTimer = 0f;
        grappleRefusals = 0;
        longestRun = 0f;
        runTimer = 0f;
    }
}
