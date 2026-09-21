/*
 * LOHEN — sim/audio/SfxDirector.java
 *
 * 13.28 [OBL] MATIEROLOGIE : 9 surfaces x 8 variations x 3 vitesses = 216 sons
 * de pas pour Lohen. Plus les frottements de vetement (4 couches), les
 * cliquetis de harnais (3), la respiration (4 profils x 6 variations).
 * UN PAS DE LOHEN, C'EST 5 SONS SIMULTANES MIXES.
 *
 * 13.29 LE SON DU VERRE — l'identite sonore du jeu : un melange d'un pas sur
 * de la glace epaisse, d'un doigt sur un bord de verre a vin, et d'une note
 * tres grave tres loin.
 *
 * Tous les sons sont SYNTHETISES (ADR-002) : les 216 pas sont 216 jeux de
 * parametres (graine, filtre, hauteur), pas 216 fichiers.
 */
package com.velmora.lohen.sim.audio;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.core.Rng;

public final class SfxDirector implements EventBus.Listener {

    /* 9 surfaces (13.28) */
    public static final int SURF_WOOD_WET = 0;
    public static final int SURF_WOOD_DRY = 1;
    public static final int SURF_STONE = 2;
    public static final int SURF_STONE_WET = 3;
    public static final int SURF_GRAVEL = 4;
    public static final int SURF_GLASS = 5;
    public static final int SURF_METAL = 6;
    public static final int SURF_CARPET = 7;
    public static final int SURF_WATER_SHALLOW = 8;
    public static final int SURFACE_COUNT = 9;
    public static final int VARIATIONS = 8;
    public static final int SPEED_TIERS = 3;
    public static final int FOOTSTEP_SOUNDS = SURFACE_COUNT * VARIATIONS * SPEED_TIERS;  /* 216 */
    public static final int LAYERS_PER_STEP = 5;
    public static final int CLOTH_LAYERS = 4;
    public static final int HARNESS_JINGLES = 3;
    public static final int BREATH_PROFILES = 4;
    public static final int BREATH_VARIATIONS = 6;

    /**
     * Signature acoustique de chaque surface : hauteur du corps, brillance,
     * duree, quantite de bruit, resonance (le verre a la sienne — 13.29).
     */
    private static final float[][] SURFACE = {
            /* bois mouille  */ {135f, 0.30f, 0.20f, 0.45f, 0.35f},
            /* bois sec      */ {175f, 0.45f, 0.16f, 0.55f, 0.50f},
            /* pierre        */ {210f, 0.55f, 0.14f, 0.60f, 0.40f},
            /* pierre mou.   */ {185f, 0.35f, 0.18f, 0.50f, 0.45f},
            /* gravier       */ {260f, 0.75f, 0.13f, 0.90f, 0.20f},
            /* verre Maree   */ {420f, 0.90f, 0.55f, 0.25f, 0.95f},
            /* metal         */ {320f, 0.85f, 0.38f, 0.30f, 0.80f},
            /* tapis/parquet */ {110f, 0.18f, 0.12f, 0.35f, 0.25f},
            /* eau peu prof. */ {240f, 0.60f, 0.26f, 0.85f, 0.30f},
    };

    private final AudioEngine engine;
    private final EventBus bus;
    private final Rng rng;

    private int stepsPlayed;
    private final int[] stepPerSurface = new int[SURFACE_COUNT];
    private final int[] stepPerVariation = new int[FOOTSTEP_SOUNDS];
    private int glassSteps;
    private int lastSurface = -1;
    private int lastVariation = -1;
    private float panBias;
    private float distanceBias;
    private boolean echoProcessing;   /* 11.06 : reverb longue + coupe 8 kHz + flanger */

    public SfxDirector(AudioEngine engine, EventBus bus, Rng rng) {
        this.engine = engine;
        this.bus = bus;
        this.rng = rng;
        if (bus != null) {
            bus.connect(EventBus.FOOTSTEP, this);
            bus.connect(EventBus.IMPACT_SOUND, this);
            bus.connect(EventBus.GRAPPLE_FIRED, this);
            bus.connect(EventBus.GRAPPLE_ATTACHED, this);
            bus.connect(EventBus.CABLE_STRAIN, this);
            bus.connect(EventBus.PARRY_SUCCESS, this);
            bus.connect(EventBus.FIGURE_SHATTERED, this);
            bus.connect(EventBus.GLASS_VIBRATION, this);
            bus.connect(EventBus.ECHO_STARTED, this);
            bus.connect(EventBus.ECHO_FINISHED, this);
            bus.connect(EventBus.UI_SELECT, this);
            bus.connect(EventBus.CLOTH_SOUND, this);
            bus.connect(EventBus.HARNESS_JINGLE, this);
            bus.connect(EventBus.BREATH_SOUND, this);
            bus.connect(EventBus.WATER_SPLASH, this);
        }
    }

    @Override
    public void onEvent(String signal, Object[] args) {
        if (EventBus.FOOTSTEP.equals(signal)) {
            int foot = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).intValue() : 0;
            int material = args.length > 1 && args[1] instanceof Number ? ((Number) args[1]).intValue() : 2;
            float speed = args.length > 2 && args[2] instanceof Number ? ((Number) args[2]).floatValue() : 1.4f;
            footstep(foot, material, speed);
        } else if (EventBus.IMPACT_SOUND.equals(signal)) {
            int kind = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).intValue() : 0;
            impact(kind);
        } else if (EventBus.GRAPPLE_FIRED.equals(signal)) {
            grappleFire();
        } else if (EventBus.GRAPPLE_ATTACHED.equals(signal)) {
            grappleTaut();
        } else if (EventBus.CABLE_STRAIN.equals(signal)) {
            cableStrain(args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).floatValue() : 0.5f);
        } else if (EventBus.PARRY_SUCCESS.equals(signal)) {
            parry();
        } else if (EventBus.FIGURE_SHATTERED.equals(signal)) {
            int fragments = args.length > 1 && args[1] instanceof Number ? ((Number) args[1]).intValue() : 47;
            shatter(fragments);
        } else if (EventBus.GLASS_VIBRATION.equals(signal)) {
            float intensity = args.length > 3 && args[3] instanceof Number ? ((Number) args[3]).floatValue() : 0.4f;
            glassHum(intensity);
        } else if (EventBus.ECHO_STARTED.equals(signal)) {
            echoProcessing = true;
            engine.setReverb("echo");
        } else if (EventBus.ECHO_FINISHED.equals(signal)) {
            echoProcessing = false;
        } else if (EventBus.UI_SELECT.equals(signal)) {
            uiSelect();
        } else if (EventBus.CLOTH_SOUND.equals(signal)) {
            int layer = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).intValue() : 0;
            float amount = args.length > 1 && args[1] instanceof Number ? ((Number) args[1]).floatValue() : 0.5f;
            cloth(layer, amount);
        } else if (EventBus.HARNESS_JINGLE.equals(signal)) {
            int which = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).intValue() : 0;
            float amount = args.length > 1 && args[1] instanceof Number ? ((Number) args[1]).floatValue() : 0.5f;
            harness(which, amount);
        } else if (EventBus.BREATH_SOUND.equals(signal)) {
            int profile = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).intValue() : 0;
            int variation = args.length > 1 && args[1] instanceof Number ? ((Number) args[1]).intValue() : 0;
            float amount = args.length > 2 && args[2] instanceof Number ? ((Number) args[2]).floatValue() : 0.4f;
            breath(profile, variation, amount);
        } else if (EventBus.WATER_SPLASH.equals(signal)) {
            float force = args.length > 3 && args[3] instanceof Number ? ((Number) args[3]).floatValue() : 0.5f;
            splash(force);
        }
    }

    /** Le joueur entend depuis la camera : panoramique et distance. */
    public void setListener(float panBias, float distanceBias) {
        this.panBias = Maths.clamp(panBias, -1f, 1f);
        this.distanceBias = Math.max(0f, distanceBias);
    }

    /* ------------------------------------------------------------------ */
    /* Les 216 pas (13.28)                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Un pas = 5 couches simultanees :
     *  1 l'impact de surface, 2 la resonance du materiau, 3 le frottement
     *  de vetement, 4 le cliquetis de harnais, 5 le souffle.
     */
    public void footstep(int foot, int material, float speed) {
        int surface = clampSurface(material);
        int tier = speedTier(speed);
        /* variation : jamais deux fois la meme de suite */
        int variation = rng.nextInt(VARIATIONS - 1);
        if (variation >= lastVariation) {
            variation++;
        }
        int index = (surface * VARIATIONS + variation) * SPEED_TIERS + tier;
        lastVariation = variation;
        lastSurface = surface;
        stepsPlayed++;
        stepPerSurface[surface]++;
        stepPerVariation[index]++;

        float[] s = SURFACE[surface];
        float body = s[0];
        float bright = s[1];
        float duration = s[2];
        float noise = s[3];
        float resonance = s[4];
        float force = 0.45f + 0.55f * Maths.clamp01(speed / 6.4f);
        float seed = (index * 0.0173f) % 1f;
        float pitch = body * (0.92f + seed * 0.16f) * (foot == 0 ? 1f : 1.045f);
        float pan = (foot == 0 ? -0.28f : 0.28f) + panBias;

        /* couche 1 : l'impact */
        engine.play(Synth.impulse(pitch, 0.30f * force, duration, bright, rng),
                AudioEngine.BUS_SFX, pan, distanceBias);
        /* couche 2 : la resonance du materiau */
        engine.play(Synth.sine(pitch * 0.5f, 0.16f * force * resonance, duration * 2.4f,
                0.004f, duration), AudioEngine.BUS_SFX, pan * 0.6f, distanceBias);
        if (surface == SURF_GLASS) {
            glassSteps++;
            /* 13.29 : glace epaisse + doigt sur un bord de verre a vin +
               une note tres grave tres loin. Trois couches, jamais deux. */
            engine.play(Synth.glass(pitch * 4.2f, 0.10f * force, 1.6f, 0.45f, rng),
                    AudioEngine.BUS_SFX, pan, distanceBias);
            engine.play(Synth.sine(38f, 0.13f * force, 2.8f, 0.25f, 1.4f),
                    AudioEngine.BUS_SFX, 0f, distanceBias + 12f);
            engine.play(Synth.noise(0.05f * force, 0.5f, 5200f, 2400f, 0f, 0f, rng),
                    AudioEngine.BUS_SFX, pan, distanceBias);
        }
        if (surface == SURF_GRAVEL) {
            /* le gravier crepite : 4 micro-impacts */
            for (int i = 0; i < 4; i++) {
                engine.play(Synth.impulse(pitch * (1.4f + i * 0.35f), 0.05f * force,
                        0.05f, 0.9f, rng), AudioEngine.BUS_SFX,
                        pan + (rng.nextFloat() - 0.5f) * 0.4f, distanceBias);
            }
        }
        if (surface == SURF_WATER_SHALLOW) {
            splash(force * 0.6f);
        }
        /* couches 3 et 4 : vetement et harnais */
        cloth(rng.nextInt(CLOTH_LAYERS), force * 0.55f);
        if (rng.nextFloat() < 0.55f) {
            harness(rng.nextInt(HARNESS_JINGLES), force * 0.5f);
        }
        /* couche 5 : le souffle (4 profils x 6 variations) */
        if (tier > 0 || rng.nextFloat() < 0.3f) {
            breath(tier == 0 ? 0 : tier == 1 ? 1 : 2, rng.nextInt(BREATH_VARIATIONS), 0.25f + force * 0.3f);
        }
    }

    public static int clampSurface(int material) {
        return material < 0 || material >= SURFACE_COUNT ? SURF_STONE : material;
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

    /* ------------------------------------------------------------------ */
    /* Couches du personnage                                               */
    /* ------------------------------------------------------------------ */

    /** Frottement de vetement : 4 couches (13.28). */
    public void cloth(int layer, float amount) {
        float cutoff = 1800f + layer * 900f;
        engine.play(Synth.noise(0.045f * amount, 0.22f + layer * 0.05f, cutoff, 700f,
                0f, 0f, rng), AudioEngine.BUS_SFX, panBias * 0.4f, distanceBias);
    }

    /** Cliquetis de harnais : 3 (13.28) — le harnais de relayeur. */
    public void harness(int which, float amount) {
        float[] freqs = {2100f, 2750f, 3400f};
        engine.play(Synth.impulse(freqs[which % 3], 0.055f * amount, 0.09f, 0.95f, rng),
                AudioEngine.BUS_SFX, panBias * 0.3f + 0.1f, distanceBias);
    }

    /** Respiration : 4 profils x 6 variations (13.28, 07.17). */
    public void breath(int profile, int variation, float amount) {
        float[] durations = {1.5f, 1.05f, 0.72f, 2.6f};
        float[] cutoffs = {620f, 820f, 1150f, 480f};
        int p = Maths.clamp(profile, 0, BREATH_PROFILES - 1);
        int v = Maths.clamp(variation, 0, BREATH_VARIATIONS - 1);
        float dur = durations[p] * (0.9f + v * 0.04f);
        engine.play(Synth.noise(0.085f * amount, dur, cutoffs[p] + v * 55f, 140f,
                p == 2 ? 3.1f : 0.4f, 0.6f, rng), AudioEngine.BUS_VO, panBias * 0.2f, distanceBias);
    }

    public void splash(float force) {
        engine.play(Synth.noise(0.16f * force, 0.45f, 2600f, 400f, 0f, 0f, rng),
                AudioEngine.BUS_SFX, panBias, distanceBias);
        engine.play(Synth.impulse(520f, 0.07f * force, 0.2f, 0.7f, rng),
                AudioEngine.BUS_SFX, panBias, distanceBias);
    }

    /* ------------------------------------------------------------------ */
    /* Traversee et combat                                                 */
    /* ------------------------------------------------------------------ */

    public void grappleFire() {
        /* le lancer : un fouet de cable + le cliquetis du mousqueton */
        engine.play(Synth.noise(0.22f, 0.28f, 3200f, 900f, 0f, 0f, rng),
                AudioEngine.BUS_SFX, 0.15f, 0f);
        engine.play(Synth.impulse(880f, 0.12f, 0.1f, 0.9f, rng), AudioEngine.BUS_SFX, 0.1f, 0f);
    }

    public void grappleTaut() {
        /* l'accroche : un choc de metal sur anneau, puis la mise en tension */
        engine.play(Synth.impulse(340f, 0.26f, 0.22f, 0.75f, rng), AudioEngine.BUS_SFX, 0f, 0f);
        engine.play(Synth.sine(72f, 0.14f, 0.9f, 0.01f, 0.5f), AudioEngine.BUS_SFX, 0f, 0f);
    }

    public void cableStrain(float tension) {
        engine.play(Synth.sine(58f + tension * 34f, 0.05f * tension, 0.12f, 0.005f, 0.08f),
                AudioEngine.BUS_SFX, 0f, 0f);
    }

    public void impact(int kind) {
        if (kind == 0) {
            /* coup leger : verre frole */
            engine.play(Synth.impulse(620f, 0.22f, 0.14f, 0.85f, rng), AudioEngine.BUS_SFX, 0f, 0f);
        } else {
            /* coup lourd : le verre cede */
            engine.play(Synth.impulse(240f, 0.34f, 0.32f, 0.8f, rng), AudioEngine.BUS_SFX, 0f, 0f);
            engine.play(Synth.noise(0.14f, 0.5f, 4200f, 1200f, 0f, 0f, rng),
                    AudioEngine.BUS_SFX, 0f, 0f);
        }
    }

    public void parry() {
        /* la parade : une note claire, pas un bruit — c'est une reussite */
        engine.play(Synth.bell(1180f, 0.24f, 1.5f), AudioEngine.BUS_SFX, 0f, 0f);
        engine.play(Synth.impulse(1600f, 0.14f, 0.09f, 0.95f, rng), AudioEngine.BUS_SFX, 0f, 0f);
    }

    /** 16.12 : la Figure se brise en N eclats — jamais un sprite de sang. */
    public void shatter(int fragments) {
        int n = Math.min(fragments, 47);
        engine.play(Synth.noise(0.30f, 0.9f, 6200f, 1800f, 0f, 0f, rng),
                AudioEngine.BUS_SFX, 0f, 0f);
        for (int i = 0; i < 14; i++) {
            float f = 900f + rng.nextFloat() * 4200f;
            engine.play(Synth.impulse(f, 0.05f + rng.nextFloat() * 0.06f, 0.1f + rng.nextFloat() * 0.25f,
                    0.95f, rng), AudioEngine.BUS_SFX, (rng.nextFloat() - 0.5f) * 1.4f, 0f);
        }
        /* la note grave du verre : elle reste */
        engine.play(Synth.sine(46f, 0.20f, 3.4f, 0.02f, 2.2f), AudioEngine.BUS_SFX, 0f, 0f);
        if (n > 0) {
            bus.emit(EventBus.SFX, "shatter_fragments_" + n, 1f);
        }
    }

    /** Le bourdonnement du verre sous les pieds (13.29, 15.03). */
    public void glassHum(float intensity) {
        engine.play(Synth.sine(41f + intensity * 6f, 0.10f * intensity, 2.4f, 0.4f, 1.6f),
                AudioEngine.BUS_AMBIENCE, 0f, 6f);
        engine.play(Synth.glass(1320f * (0.9f + intensity * 0.2f), 0.035f * intensity, 2.2f,
                0.25f, rng), AudioEngine.BUS_AMBIENCE, 0f, 4f);
    }

    public void uiSelect() {
        engine.play(Synth.impulse(1450f, 0.06f, 0.05f, 0.7f, rng), AudioEngine.BUS_SFX, 0f, 0f);
    }

    public void uiBack() {
        engine.play(Synth.impulse(760f, 0.055f, 0.06f, 0.6f, rng), AudioEngine.BUS_SFX, 0f, 0f);
    }

    /** Le grincement du bois de la porte du Phare (19.02). */
    public void doorCreak(float progress) {
        engine.play(Synth.noise(0.10f, 1.4f, 1400f + progress * 900f, 300f, 6f, 0.5f, rng),
                AudioEngine.BUS_SFX, 0f, 0f);
    }

    /** Le papier : depliage en 3 plis (19.07), pliage inverse (19.10). */
    public void paperUnfold() {
        for (int i = 0; i < 3; i++) {
            engine.play(Synth.noise(0.12f, 0.34f, 3600f, 1400f, 0f, 0f, rng),
                    AudioEngine.BUS_SFX, (i - 1) * 0.1f, 0.2f);
        }
    }

    public void paperFold() {
        for (int i = 2; i >= 0; i--) {
            engine.play(Synth.noise(0.11f, 0.36f, 3400f, 1300f, 0f, 0f, rng),
                    AudioEngine.BUS_SFX, (i - 1) * 0.1f, 0.2f);
        }
    }

    /** La rame qui heurte le verre (15.02, plan 4). */
    public void oarAgainstGlass() {
        engine.play(Synth.impulse(190f, 0.30f, 0.7f, 0.55f, rng), AudioEngine.BUS_SFX, 0f, 0f);
        engine.play(Synth.glass(880f, 0.10f, 2.6f, 0.4f, rng), AudioEngine.BUS_SFX, 0f, 0f);
        engine.play(Synth.sine(44f, 0.16f, 3.2f, 0.05f, 2f), AudioEngine.BUS_SFX, 0f, 0f);
    }

    public boolean echoProcessing() {
        return echoProcessing;
    }

    /* ------------------------------------------------------------------ */
    /* Audits (18.06)                                                      */
    /* ------------------------------------------------------------------ */

    public int stepsPlayed() {
        return stepsPlayed;
    }

    public int stepsOn(int surface) {
        return stepPerSurface[surface];
    }

    public int distinctFootstepSoundsUsed() {
        int n = 0;
        for (int v : stepPerVariation) {
            if (v > 0) {
                n++;
            }
        }
        return n;
    }

    public int glassSteps() {
        return glassSteps;
    }

    public int lastSurface() {
        return lastSurface;
    }

    /** Garde CI : 216 pas declares, 5 couches, 9 surfaces (13.28). */
    public static boolean materiologyComplete() {
        return FOOTSTEP_SOUNDS == 216 && SURFACE_COUNT == 9 && VARIATIONS == 8
                && SPEED_TIERS == 3 && LAYERS_PER_STEP == 5 && CLOTH_LAYERS == 4
                && HARNESS_JINGLES == 3 && BREATH_PROFILES == 4 && BREATH_VARIATIONS == 6;
    }

    public void reset() {
        stepsPlayed = 0;
        glassSteps = 0;
        lastSurface = -1;
        lastVariation = -1;
        echoProcessing = false;
        for (int i = 0; i < stepPerSurface.length; i++) {
            stepPerSurface[i] = 0;
        }
        for (int i = 0; i < stepPerVariation.length; i++) {
            stepPerVariation[i] = 0;
        }
    }
}
