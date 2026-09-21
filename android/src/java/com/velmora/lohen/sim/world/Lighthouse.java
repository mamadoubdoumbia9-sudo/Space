/*
 * LOHEN — sim/world/Lighthouse.java
 *
 * 10.04 LE PHARE DE SAINT-AMBRE : 212 m. Construit en 1871. Il n'a jamais
 * cesse de tourner depuis la Maree, alors que personne ne l'alimente.
 * L'escalier interieur est effondre au tiers. Le chapitre entier est une
 * montee vers cette lumiere.
 *
 * 05.31 : une seule source de 900 m de portee qui balaye a 0,05 tour/s
 * (donc un tour toutes les 20 s). Quand le faisceau passe sur la zone du
 * joueur, l'exposition monte de 0,4 EV pendant 1,8 s et les ombres
 * tournent. Cet evenement est synchronise avec la musique (13.19 M15 :
 * une seule note de cloche a chaque passage). C'est le battement de coeur
 * du jeu.
 *
 * 19.04 : dans la chambre, le faisceau passe toutes les 20 s par la
 * fenetre (7800 K, tres bref, il balaye la piece et fait bouger toutes les
 * ombres pendant 1,8 s), l'aube monte de 0 a 0,6 EV sur 9 minutes.
 *
 * 20.04 : sur la ligne « trois cent quatorze fois », le faisceau eclaire le
 * mur de feuilles punaisees derriere Lohen. Le joueur voit les 314 papiers
 * d'un coup. Raccord scripte a la frame pres — le plan le plus important
 * du chapitre.
 *
 * 14.03 : si le joueur se perd plus de 90 s, un evenement diegetique
 * l'oriente. L'un d'eux est « le faisceau du Phare qui s'attarde ».
 * Jamais une fleche.
 */
package com.velmora.lohen.sim.world;

import com.velmora.lohen.sim.audio.MusicDirector;
import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.math.Maths;

public final class Lighthouse {

    /* ---------------- geometrie ---------------- */
    public static final float HEIGHT = 212f;              /* 09.02 / 10.04 */
    public static final int BUILT = 1871;
    public static final float CHAMBER_ALTITUDE = 206f;    /* 19.02 : le palier */
    public static final float CHAMBER_DIAMETER = 6f;      /* piece ronde de 6 m */
    public static final float BELL_LANDING = 178f;        /* E27 le palier des cloches */
    public static final float STAIRCASE_COLLAPSE_FROM = HEIGHT * 0.33f;
    public static final float STAIRCASE_COLLAPSE_TO = HEIGHT * 0.41f;
    public static final float DOOR_HOLD_SECONDS = 2.2f;   /* 19.02 */
    public static final float DOOR_OPEN_SECONDS = 1.4f;

    /* ---------------- lumiere ---------------- */
    public static final float BEAM_RANGE = 900f;          /* 05.31 */
    public static final float SWEEP_TURNS_PER_SECOND = 0.05f;
    public static final float SWEEP_PERIOD = 1f / SWEEP_TURNS_PER_SECOND;   /* 20 s */
    public static final float BEAM_EXPOSURE_EV = 0.4f;    /* 05.31 */
    public static final float BEAM_EXPOSURE_SECONDS = 1.8f;
    public static final int BEAM_COLOR_K = 7800;          /* 19.04 */
    public static final float LAMP_RANGE_M = 1.2f;        /* lampe a huile */
    public static final int LAMP_COLOR_K = 2700;

    /* ---------------- aube (19.04) ---------------- */
    public static final float DAWN_EV_END = 0.6f;
    public static final float DAWN_MINUTES = 9f;

    /* ---------------- navigation (14.03) ---------------- */
    public static final float STALL_SECONDS = 90f;
    public static final float LINGER_SECONDS = 3.4f;

    /* ---------------- 20.04 : les 314 papiers ---------------- */
    public static final int PINNED_SHEETS = 314;
    public static final String SHEET_LINE = "LETTRE_L314";

    private final EventBus bus;

    /* position du Phare dans le monde (x, y=0, z) */
    private float x;
    private float z;

    private float angle;                 /* radians, 0 = nord */
    private float sweeps;
    private float bellsRung;
    private float exposureBoost;
    private float exposureTimer;
    private boolean beamOverPlayer;
    private int beamOverPlayerCount;
    private float lingerTimer;
    private boolean lingering;
    private float stallTimer;
    private float dawnTimer;
    private boolean dawnActive;
    private boolean chamber;
    private boolean lit = true;
    private int sheetsRevealed;

    public Lighthouse(EventBus bus) {
        this.bus = bus;
    }

    public void place(float x, float z) {
        this.x = x;
        this.z = z;
    }

    public float x() {
        return x;
    }

    public float z() {
        return z;
    }

    /* ------------------------------------------------------------------ */
    /* Boucle                                                              */
    /* ------------------------------------------------------------------ */

    public void update(float dt, float px, float py, float pz) {
        if (!lit) {
            exposureBoost = 0f;
            beamOverPlayer = false;
            return;
        }
        float before = angle;
        angle += Maths.TWO_PI * SWEEP_TURNS_PER_SECOND * dt;
        while (angle >= Maths.TWO_PI) {
            angle -= Maths.TWO_PI;
            sweeps++;
            /* 13.19 : une seule note de cloche a chaque passage du faisceau */
            bellsRung++;
            if (bus != null) {
                bus.emit(EventBus.LIGHTHOUSE_BEAM, sweeps, angle);
            }
        }
        /* le faisceau passe-t-il sur la zone du joueur ? */
        boolean over = beamCovers(px, py, pz);
        if (over && !beamOverPlayer) {
            beamOverPlayer = true;
            beamOverPlayerCount++;
            exposureTimer = BEAM_EXPOSURE_SECONDS;
            exposureBoost = BEAM_EXPOSURE_EV;
            if (bus != null) {
                bus.emit(EventBus.LIGHTHOUSE_EXPOSURE, BEAM_EXPOSURE_EV,
                        BEAM_EXPOSURE_SECONDS, beamOverPlayerCount);
            }
        } else if (!over) {
            beamOverPlayer = false;
        }
        if (exposureTimer > 0f) {
            exposureTimer -= dt;
            /* 05.31 : les ombres tournent pendant 1,8 s */
            exposureBoost = BEAM_EXPOSURE_EV * Maths.clamp01(exposureTimer / BEAM_EXPOSURE_SECONDS);
            if (exposureTimer <= 0f) {
                exposureBoost = 0f;
            }
        }
        /* 14.03 : le faisceau qui s'attarde quand le joueur est perdu */
        if (lingering) {
            lingerTimer -= dt;
            if (lingerTimer <= 0f) {
                lingering = false;
                if (bus != null) {
                    bus.emit(EventBus.LIGHTHOUSE_LINGER_ENDED);
                }
            }
        }
        /* 19.04 : l'aube monte de 0 a 0,6 EV sur 9 minutes */
        if (dawnActive) {
            dawnTimer += dt;
        }
        /* angle franchi : utile pour le raccord 20.04 */
        if (before > angle && chamber) {
            /* un tour complet depuis la chambre */
            if (bus != null) {
                bus.emit(EventBus.LIGHTHOUSE_SWEEP_IN_CHAMBER, sweeps);
            }
        }
    }

    /**
     * Le faisceau couvre-t-il ce point ? Le cone fait 6 degres d'ouverture
     * et porte a 900 m. Le joueur est "touche" quand l'angle du faisceau
     * pointe vers lui a 3 degres pres.
     */
    public boolean beamCovers(float px, float py, float pz) {
        float dx = px - x;
        float dz = pz - z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > BEAM_RANGE || dist < 2f) {
            return false;
        }
        float toPlayer = (float) Math.atan2(dx, dz);
        float d = Math.abs(Maths.wrapRadians(toPlayer - angle));
        /* ouverture du cone : plus large de pres, plus fin de loin */
        float halfAngle = lingering ? 0.30f : 0.0524f;      /* 3 degres, 17 degres en attard */
        return d < halfAngle;
    }

    /* ------------------------------------------------------------------ */
    /* 14.03 : evenement diegetique d'orientation                          */
    /* ------------------------------------------------------------------ */

    /** Le joueur n'a pas progresse depuis `seconds` : doit-on l'orienter ? */
    public boolean onNavigationStall(float secondsLost) {
        stallTimer = secondsLost;
        if (secondsLost >= STALL_SECONDS && !lingering) {
            lingering = true;
            lingerTimer = LINGER_SECONDS;
            if (bus != null) {
                bus.emit(EventBus.LIGHTHOUSE_LINGER, LINGER_SECONDS, secondsLost);
            }
            return true;
        }
        return false;
    }

    public float stallSeconds() {
        return stallTimer;
    }

    public boolean lingering() {
        return lingering;
    }

    /* ------------------------------------------------------------------ */
    /* 20.04 : le raccord des 314 papiers                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Raccord scripte a la frame pres : quand la ligne « trois cent quatorze
     * fois » est lue, le faisceau doit passer par la fenetre et eclairer le
     * mur de feuilles punaisees. On attend le prochain passage (<= 20 s).
     */
    public boolean requestSheetReveal(String lineId) {
        if (!SHEET_LINE.equals(lineId)) {
            return false;
        }
        chamber = true;
        dawnActive = true;
        if (bus != null) {
            bus.emit(EventBus.SHEETS_REVEAL_REQUESTED, PINNED_SHEETS, angle);
        }
        return true;
    }

    /** Appele a chaque passage du faisceau dans la chambre pendant la lettre. */
    public boolean revealSheetsIfDue() {
        if (!chamber || sheetsRevealed > 0) {
            return false;
        }
        sheetsRevealed = PINNED_SHEETS;
        if (bus != null) {
            bus.emit(EventBus.SHEETS_REVEALED, PINNED_SHEETS);
        }
        return true;
    }

    public int sheetsRevealed() {
        return sheetsRevealed;
    }

    /* ------------------------------------------------------------------ */
    /* Chambre / aube                                                      */
    /* ------------------------------------------------------------------ */

    public void enterChamber() {
        chamber = true;
        dawnActive = true;
        dawnTimer = 0f;
        sheetsRevealed = 0;
        if (bus != null) {
            bus.emit(EventBus.CHAMBER_ENTERED, CHAMBER_ALTITUDE);
        }
    }

    public boolean inChamber() {
        return chamber;
    }

    /** 19.04 : l'aube naissante, de 0 a 0,6 EV sur 9 minutes. */
    public float dawnEv() {
        if (!dawnActive) {
            return 0f;
        }
        return DAWN_EV_END * Maths.clamp01(dawnTimer / (DAWN_MINUTES * 60f));
    }

    public float dawnProgress() {
        return Maths.clamp01(dawnTimer / (DAWN_MINUTES * 60f));
    }

    /** Exposition totale du Phare : aube + passage du faisceau. */
    public float exposureEv() {
        return dawnEv() + exposureBoost;
    }

    /* ------------------------------------------------------------------ */
    /* Paliers (13.20 M16 : 6 paliers par altitude)                        */
    /* ------------------------------------------------------------------ */

    /** Palier musical M16 pour une altitude donnee (0 a 5). */
    public static int altitudeTier(float altitude) {
        return Maths.altitudeTier(altitude, MusicDirector.M16_TIERS);
    }

    /**
     * 10.04 : l'escalier interieur est effondre au tiers. Entre 70 et 87 m
     * il n'y a plus de marches — il faut passer par l'exterieur (grappin,
     * corniches), et c'est la que Sol attend.
     */
    public static boolean staircaseCollapsed(float altitude) {
        return altitude >= STAIRCASE_COLLAPSE_FROM && altitude <= STAIRCASE_COLLAPSE_TO;
    }

    /** Distance horizontale au Phare (le repere absolu de la ville). */
    public float distanceTo(float px, float pz) {
        float dx = px - x;
        float dz = pz - z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    /** 09.02 : le Phare est visible depuis n'importe quelle altitude. */
    public boolean visibleFrom(float altitude, float fogVisibilityM, float distance) {
        if (distance > BEAM_RANGE) {
            return false;
        }
        /* au-dessus de 150 m la brume passe SOUS le joueur (09.17) */
        float vis = altitude > 150f ? Math.max(fogVisibilityM, 400f) : fogVisibilityM;
        return distance < vis * 6f;
    }

    /* ------------------------------------------------------------------ */
    /* Acces                                                               */
    /* ------------------------------------------------------------------ */

    public float angle() {
        return angle;
    }

    public float angleDeg() {
        return angle * Maths.DEG;
    }

    public float sweeps() {
        return sweeps;
    }

    public float bellsRung() {
        return bellsRung;
    }

    public boolean beamOverPlayer() {
        return beamOverPlayer;
    }

    public int beamOverPlayerCount() {
        return beamOverPlayerCount;
    }

    public float exposureBoost() {
        return exposureBoost;
    }

    public boolean lit() {
        return lit;
    }

    public void setLit(boolean lit) {
        this.lit = lit;
    }

    /** Garde CI : 212 m, 20 s, 0,4 EV / 1,8 s, 900 m (05.31 / 10.04). */
    public static boolean specCompliant() {
        return HEIGHT == 212f
                && Math.abs(SWEEP_PERIOD - 20f) < 0.001f
                && BEAM_RANGE == 900f
                && BEAM_EXPOSURE_EV == 0.4f
                && BEAM_EXPOSURE_SECONDS == 1.8f
                && PINNED_SHEETS == 314
                && Math.abs(DAWN_EV_END - 0.6f) < 0.0001f
                && DAWN_MINUTES == 9f
                && STALL_SECONDS == 90f;
    }

    public void reset() {
        angle = 0f;
        sweeps = 0f;
        bellsRung = 0f;
        exposureBoost = 0f;
        exposureTimer = 0f;
        beamOverPlayer = false;
        beamOverPlayerCount = 0;
        lingering = false;
        lingerTimer = 0f;
        stallTimer = 0f;
        dawnTimer = 0f;
        dawnActive = false;
        chamber = false;
        sheetsRevealed = 0;
        lit = true;
    }
}
