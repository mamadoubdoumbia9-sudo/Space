/*
 * LOHEN — sim/input/HapticDirector.java
 *
 * 02.19 [OBL] : Input.vibrate_handheld encapsule dans un HapticDirector qui
 * respecte un budget (max 3 impulsions/seconde) et un reglage joueur 0-100 %.
 * 08.31 : chaque haptique est nommee et cataloguee.
 */
package com.velmora.lohen.sim.input;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Options;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HapticDirector {

    /** Catalogue complet du Chapitre 1 (08.31). Durees en millisecondes. */
    public static final Map<String, int[]> CATALOGUE = new LinkedHashMap<String, int[]>();

    static {
        CATALOGUE.put("hap_step_stone", new int[]{8});
        CATALOGUE.put("hap_step_glass", new int[]{6, 4, 6});      /* 2 impulsions, cristallin */
        CATALOGUE.put("hap_land_soft", new int[]{14});
        CATALOGUE.put("hap_land_hard", new int[]{30});
        CATALOGUE.put("hap_grapple_fire", new int[]{16});
        CATALOGUE.put("hap_grapple_taut", new int[]{22});
        CATALOGUE.put("hap_cable_strain", new int[]{6});          /* toutes les 400 ms */
        CATALOGUE.put("hap_parry", new int[]{11, 20, 11});        /* 2x11 */
        CATALOGUE.put("hap_hit_given_light", new int[]{18});
        CATALOGUE.put("hap_hit_given_heavy", new int[]{14, 24, 14});
        CATALOGUE.put("hap_hit_taken", new int[]{34});
        CATALOGUE.put("hap_breath_break", new int[]{26, 30, 26, 30, 26});  /* 3x26 */
        CATALOGUE.put("hap_echo_start", new int[]{40});           /* montee douce */
        CATALOGUE.put("hap_echo_end", new int[]{24});
        CATALOGUE.put("hap_letter_open", new int[]{9, 18, 9, 18, 9});      /* 3x9 */
        CATALOGUE.put("hap_phare_sweep", new int[]{6});           /* toutes les 20 s */
        CATALOGUE.put("hap_door_push", new int[]{12, 30, 18, 30, 22});     /* montante (19.02) */
        CATALOGUE.put("hap_ui_select", new int[]{6});
        CATALOGUE.put("hap_choice", new int[]{9});
    }

    public static final int BUDGET_PER_SECOND = 3;
    public static final float CABLE_STRAIN_PERIOD_S = 0.4f;
    public static final float PHARE_SWEEP_PERIOD_S = 20f;

    /** Sortie haptique : Vibrator cote Android, journal cote test. */
    public interface Sink {
        void vibrate(int[] patternMs, int amplitudePct);
    }

    private final Options options;
    private final EventBus bus;
    private Sink sink;
    private float time;
    private final float[] impulseTimes = new float[BUDGET_PER_SECOND];
    private int impulseIndex;
    private int played;
    private int dropped;
    private int lastAmplitude = -1;
    private String lastName = "";
    private boolean enabled = true;
    private boolean reducedForAccessibility;

    public HapticDirector(Options options, EventBus bus) {
        this.options = options;
        this.bus = bus;
    }

    public void setSink(Sink sink) {
        this.sink = sink;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setReduced(boolean reduced) {
        this.reducedForAccessibility = reduced;
    }

    public void update(float dt) {
        time += dt;
    }

    /** Joue un pattern nomme du catalogue. Retourne vrai si vraiment emis. */
    public boolean play(String name) {
        return play(name, 1f);
    }

    public boolean play(String name, float intensity) {
        if (!enabled || name == null) {
            return false;
        }
        int[] pattern = CATALOGUE.get(name);
        if (pattern == null) {
            return false;
        }
        /* budget : 3 impulsions par seconde glissante */
        if (impulseTimes[impulseIndex] > 0f && time - impulseTimes[impulseIndex] < 1f) {
            int recent = 0;
            for (float t : impulseTimes) {
                if (t > 0f && time - t < 1f) {
                    recent++;
                }
            }
            if (recent >= BUDGET_PER_SECOND) {
                dropped++;
                return false;
            }
        }
        impulseTimes[impulseIndex] = time;
        impulseIndex = (impulseIndex + 1) % BUDGET_PER_SECOND;

        float user = options == null ? 1f : options.vibration;
        if (user <= 0f) {
            dropped++;
            return false;
        }
        int amplitude = (int) (Maths01(user * intensity * (reducedForAccessibility ? 0.5f : 1f)) * 100f);
        if (amplitude <= 0) {
            return false;
        }
        played++;
        lastName = name;
        lastAmplitude = amplitude;
        if (sink != null) {
            sink.vibrate(pattern, amplitude);
        }
        if (bus != null) {
            bus.emit(EventBus.HAPTIC, name, amplitude);
        }
        return true;
    }

    private static float Maths01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** Pulsation periodique du cable sous tension (08.31). */
    public void cableStrain(float strain) {
        if (strain <= 0.01f) {
            return;
        }
        if (((int) (time / CABLE_STRAIN_PERIOD_S)) != lastStrainBeat) {
            lastStrainBeat = (int) (time / CABLE_STRAIN_PERIOD_S);
            play("hap_cable_strain", strain);
        }
    }

    private int lastStrainBeat = -1;
    private int lastSweepBeat = -1;

    /** Battement du Phare toutes les 20 s : imperceptible (05.31, 08.31). */
    public void lighthouseSweep() {
        int beat = (int) (time / PHARE_SWEEP_PERIOD_S);
        if (beat != lastSweepBeat) {
            lastSweepBeat = beat;
            play("hap_phare_sweep", 0.35f);
        }
    }

    public int played() {
        return played;
    }

    public int dropped() {
        return dropped;
    }

    public String lastName() {
        return lastName;
    }

    public int lastAmplitude() {
        return lastAmplitude;
    }

    public float time() {
        return time;
    }

    public void reset() {
        time = 0f;
        played = 0;
        dropped = 0;
        impulseIndex = 0;
        lastStrainBeat = -1;
        lastSweepBeat = -1;
        for (int i = 0; i < impulseTimes.length; i++) {
            impulseTimes[i] = 0f;
        }
    }

    /** Garde CI : le catalogue doit etre complet (08.31). */
    public static String[] requiredNames() {
        return new String[]{
                "hap_step_stone", "hap_step_glass", "hap_land_soft", "hap_land_hard",
                "hap_grapple_fire", "hap_grapple_taut", "hap_cable_strain", "hap_parry",
                "hap_hit_given_light", "hap_hit_given_heavy", "hap_hit_taken",
                "hap_breath_break", "hap_echo_start", "hap_echo_end", "hap_letter_open",
                "hap_phare_sweep",
        };
    }

    public static boolean catalogueComplete() {
        for (String n : requiredNames()) {
            if (!CATALOGUE.containsKey(n)) {
                return false;
            }
        }
        return true;
    }
}
