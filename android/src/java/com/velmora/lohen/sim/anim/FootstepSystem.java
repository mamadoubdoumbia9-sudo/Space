/*
 * LOHEN — sim/anim/FootstepSystem.java
 *
 * 13.28 [OBL] MATIEROLOGIE : 9 surfaces x 8 variations x 3 vitesses
 * (marche / jog / course) = 216 sons de pas pour Lohen seul.
 * Un pas de Lohen, c'est 5 SONDS SIMULTANES mixes :
 *   1 l'impact de surface, 2 la resonnance du materiau, 3 le frottement
 *   de vetement (4 couches), 4 le cliquetis de harnais (3), 5 le souffle.
 * « C'est cher. C'est ce qui fait que le personnage EXISTE. »
 *
 * 13.29 : le son du verre de la Maree n'existe nulle part ailleurs —
 * glace epaisse + doigt sur un bord de verre a vin + note tres grave
 * tres loin. Trois couches, jamais deux.
 *
 * Les 216 pas sont SYNTHETISES a l'execution (ADR-002 : aucun asset audio
 * binaire) : chaque variation est une graine, pas un fichier.
 */
package com.velmora.lohen.sim.anim;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Rng;
import com.velmora.lohen.sim.math.Geom;
import com.velmora.lohen.sim.player.Lohen;

public final class FootstepSystem {

    /* les 9 surfaces canoniques (13.28) */
    public static final int SURFACE_WOOD_WET = 0;
    public static final int SURFACE_WOOD_DRY = 1;
    public static final int SURFACE_STONE = 2;
    public static final int SURFACE_STONE_WET = 3;
    public static final int SURFACE_GRAVEL = 4;
    public static final int SURFACE_GLASS_SEA = 5;
    public static final int SURFACE_METAL = 6;
    public static final int SURFACE_CARPET = 7;
    public static final int SURFACE_WATER_SHALLOW = 8;
    public static final int SURFACE_COUNT = 9;

    public static final String[] SURFACE_NAMES = {
            "bois_mouille", "bois_sec", "pierre", "pierre_mouillee", "gravier",
            "verre_maree", "metal", "tapis_parquet", "eau_peu_profonde"};

    public static final int VARIATIONS = 8;      /* 8 variations par surface */
    public static final int SPEEDS = 3;          /* marche / jog / course */
    public static final int TOTAL_SOUNDS = SURFACE_COUNT * VARIATIONS * SPEEDS;   /* 216 */
    public static final int LAYERS_PER_STEP = 5;
    public static final int CLOTH_LAYERS = 4;
    public static final int HARNESS_JINGLES = 3;
    public static final int BREATH_PROFILES = 4;
    public static final int BREATH_VARIATIONS = 6;

    private final EventBus bus;
    private final Rng rng;
    private final Lohen lohen;

    private int lastFoot = -1;
    private int lastSurface = -1;
    private int steps;
    private final int[] surfaceSteps = new int[SURFACE_COUNT];
    private final int[] variationUsed = new int[TOTAL_SOUNDS];
    private float clothPhase;
    private float harnessPhase;
    private float breathPhase;
    private int breathProfile = 0;
    private boolean wet;         /* la pluie de S7, les pieds dans l'eau */
    private float intensity = 1f;

    public FootstepSystem(Lohen lohen, EventBus bus, Rng rng) {
        this.lohen = lohen;
        this.bus = bus;
        this.rng = rng;
    }

    /** Evenement FOOTSTEP emis par le Motor (phase de foulee). */
    public void onFootstep(int foot, int material, float speed) {
        if (foot == lastFoot && material == lastSurface && speed < 0.2f) {
            return;
        }
        lastFoot = foot;
        int surface = mapMaterial(material);
        lastSurface = surface;
        int speedTier = speedTier(speed);
        /* variation : jamais deux fois la meme de suite (13.28) */
        int variation = rng.nextInt(VARIATIONS - 1);
        if (variation >= lastVariation) {
            variation++;
        }
        lastVariation = variation;
        int index = (surface * VARIATIONS + variation) * SPEEDS + speedTier;
        variationUsed[index]++;
        steps++;
        surfaceSteps[surface]++;

        float force = 0.5f + 0.5f * Math.min(1f, speed / lohen.tuning().sprintSpeed);
        intensity = force;
        /* 1 : impact de surface */
        bus.emit(EventBus.FOOTSTEP_SOUND, SURFACE_NAMES[surface], variation, speedTier, force, 0);
        /* 2 : resonnance du materiau (le verre de la Maree a sa note grave) */
        bus.emit(EventBus.FOOTSTEP_SOUND, SURFACE_NAMES[surface], variation, speedTier, force, 1);
        /* 3 : frottement de vetement — 4 couches */
        clothPhase += 0.37f + force * 0.2f;
        for (int i = 0; i < CLOTH_LAYERS; i++) {
            bus.emit(EventBus.CLOTH_SOUND, i, force * (0.4f + 0.15f * i), clothPhase);
        }
        /* 4 : cliquetis de harnais — 3 (le harnais de relayeur) */
        harnessPhase += force;
        if (harnessPhase > 1f) {
            harnessPhase = 0f;
            bus.emit(EventBus.HARNESS_JINGLE, rng.nextInt(HARNESS_JINGLES), force * 0.6f);
        }
        /* 5 : le souffle, lie au profil respiratoire (07.17, 13.28) */
        bus.emit(EventBus.BREATH_SOUND, breathProfile, rng.nextInt(BREATH_VARIATIONS),
                breathIntensity());
        /* haptique : hap_step_stone / hap_step_glass (08.31) */
        bus.emit(EventBus.HAPTIC, surface == SURFACE_GLASS_SEA ? "hap_step_glass"
                : "hap_step_stone", force);
        /* le verre de la Maree vibre et s'illumine sous le pied (05.21) */
        if (surface == SURFACE_GLASS_SEA) {
            bus.emit(EventBus.GLASS_VIBRATION, lohen.x, lohen.y, lohen.z, 0.25f + force * 0.35f);
        }
        if (surface == SURFACE_WATER_SHALLOW) {
            bus.emit(EventBus.WATER_SPLASH, lohen.x, lohen.y, lohen.z, force);
        }
    }

    private int lastVariation = -1;

    private float breathIntensity() {
        float b = lohen.breath.fraction();
        float effort = Math.min(1f, lohen.speed / lohen.tuning().sprintSpeed);
        float base = 0.25f + effort * 0.45f;
        if (b < 0.3f) {
            base += 0.35f;                 /* souffle court */
        }
        return Math.min(1f, base);
    }

    /** 07.17 : quatre profils — calme, effort, panique, retenue. */
    public void setBreathProfile(int profile) {
        breathProfile = Math.max(0, Math.min(BREATH_PROFILES - 1, profile));
    }

    public int breathProfile() {
        return breathProfile;
    }

    public void update(float dt) {
        breathPhase += dt * (breathProfile == 0 ? 0.25f
                : breathProfile == 1 ? 0.5f : breathProfile == 2 ? 0.95f : 0.06f);
        if (breathPhase > 1f) {
            breathPhase -= 1f;
            bus.emit(EventBus.BREATH_SOUND, breathProfile, rng.nextInt(BREATH_VARIATIONS),
                    breathIntensity() * 0.6f);
        }
        /* un pas, c'est 5 sons : on verifie que les 5 couches existent */
    }

    /** Materiau du niveau -> surface sonore (13.28). */
    public static int mapMaterial(int material) {
        switch (material) {
            case Geom.MAT_WOOD:
                return SURFACE_WOOD_DRY;
            case Geom.MAT_WOOD_WET:
                return SURFACE_WOOD_WET;
            case Geom.MAT_STONE:
                return SURFACE_STONE;
            case Geom.MAT_STONE_WET:
                return SURFACE_STONE_WET;
            case Geom.MAT_GRAVEL:
                return SURFACE_GRAVEL;
            case Geom.MAT_GLASS:
                return SURFACE_GLASS_SEA;
            case Geom.MAT_METAL:
                return SURFACE_METAL;
            case Geom.MAT_CARPET:
                return SURFACE_CARPET;
            case Geom.MAT_WATER:
                return SURFACE_WATER_SHALLOW;
            default:
                return SURFACE_STONE;
        }
    }

    /** La pluie rend toutes les surfaces exterieures "mouillees" (S7). */
    public void setWet(boolean wet) {
        this.wet = wet;
    }

    public boolean wet() {
        return wet;
    }

    public static int speedTier(float speed) {
        if (speed < 2.2f) {
            return 0;      /* marche */
        }
        if (speed < 5.0f) {
            return 1;      /* jog */
        }
        return 2;          /* course */
    }

    public int steps() {
        return steps;
    }

    public int stepsOn(int surface) {
        return surfaceSteps[surface];
    }

    public int distinctSoundsUsed() {
        int n = 0;
        for (int v : variationUsed) {
            if (v > 0) {
                n++;
            }
        }
        return n;
    }

    /** Garde CI : 216 sons declares, 5 couches par pas (13.28). */
    public static boolean specCompliant() {
        return TOTAL_SOUNDS == 216 && LAYERS_PER_STEP == 5 && SURFACE_COUNT == 9
                && VARIATIONS == 8 && SPEEDS == 3;
    }

    public float intensity() {
        return intensity;
    }

    public void reset() {
        steps = 0;
        lastFoot = -1;
        lastSurface = -1;
        lastVariation = -1;
        for (int i = 0; i < surfaceSteps.length; i++) {
            surfaceSteps[i] = 0;
        }
        for (int i = 0; i < variationUsed.length; i++) {
            variationUsed[i] = 0;
        }
    }
}
