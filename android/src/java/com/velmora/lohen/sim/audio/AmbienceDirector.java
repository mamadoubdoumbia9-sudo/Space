/*
 * LOHEN — sim/audio/AmbienceDirector.java
 *
 * 13.30 : 14 lits d'ambiance QUADRAPHONIQUES, chacun avec 3 couches
 * (fond constant, elements moyens aleatoires, one-shots lointains).
 * Le vent a 6 intensites pilotees par l'altitude ET par l'exposition
 * (calcul de l'occlusion par raycast TOUTES LES 0,5 s).
 *
 * 13.31 SILENCE ACTIF : dans 8 endroits du chapitre, l'ambiance se COUPE
 * brutalement pendant 2 a 5 s. Le cerveau du joueur remplit le vide.
 * Le plus long est a l'entree de la salle de bal : 5 secondes de silence
 * numerique absolu.
 *
 * 13.32 : bus de reverb par zone — 9 impulsions convolutives enregistrees
 * dans des lieux reels : une eglise, un parking, un hangar, une cage
 * d'escalier, une piece vide, un tunnel, une falaise, une chambre meublee,
 * une cabine de bateau.
 */
package com.velmora.lohen.sim.audio;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.core.Rng;
import com.velmora.lohen.sim.world.PhysicsWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AmbienceDirector {

    /* ---------------- les 14 lits d'ambiance ---------------- */
    public static final Map<String, float[]> BEDS = new LinkedHashMap<String, float[]>();
    /* {densite, brillance, profondeur de reverb, activite du milieu, rarete des one-shots} */
    static {
        BEDS.put("port_mort", new float[]{0.35f, 0.30f, 0.75f, 0.20f, 0.10f});
        BEDS.put("maree_verre", new float[]{0.45f, 0.85f, 0.55f, 0.25f, 0.08f});
        BEDS.put("marche_suspendu", new float[]{0.95f, 0.60f, 0.35f, 0.85f, 0.25f});
        BEDS.put("falaise_vent", new float[]{0.70f, 0.45f, 0.85f, 0.30f, 0.12f});
        BEDS.put("bibliotheque_eaux", new float[]{0.20f, 0.25f, 0.95f, 0.10f, 0.05f});
        BEDS.put("puits_central", new float[]{0.25f, 0.20f, 1.00f, 0.12f, 0.04f});
        BEDS.put("conduits", new float[]{0.40f, 0.35f, 0.65f, 0.35f, 0.10f});
        BEDS.put("poste_milice", new float[]{0.30f, 0.40f, 0.50f, 0.22f, 0.09f});
        BEDS.put("salle_de_bal", new float[]{0.55f, 0.70f, 0.90f, 0.40f, 0.15f});
        BEDS.put("ancienne_halle", new float[]{0.50f, 0.40f, 0.80f, 0.30f, 0.12f});
        BEDS.put("pluie_battante", new float[]{0.85f, 0.55f, 0.60f, 0.45f, 0.10f});
        BEDS.put("cage_escalier", new float[]{0.30f, 0.35f, 0.88f, 0.18f, 0.07f});
        BEDS.put("chambre_phare", new float[]{0.15f, 0.20f, 0.45f, 0.08f, 0.03f});
        BEDS.put("aube_verre", new float[]{0.40f, 0.60f, 0.70f, 0.20f, 0.06f});
    }

    /* ---------------- les 9 reverb de zone (13.32) ---------------- */
    public static final Map<String, float[]> REVERBS = new LinkedHashMap<String, float[]>();
    /* {taille, humide, amortissement} */
    static {
        REVERBS.put("eglise", new float[]{1.00f, 0.52f, 0.20f});
        REVERBS.put("parking", new float[]{0.70f, 0.38f, 0.35f});
        REVERBS.put("hangar", new float[]{0.85f, 0.44f, 0.28f});
        REVERBS.put("cage_escalier", new float[]{0.60f, 0.46f, 0.40f});
        REVERBS.put("piece_vide", new float[]{0.40f, 0.34f, 0.55f});
        REVERBS.put("tunnel", new float[]{0.75f, 0.50f, 0.30f});
        REVERBS.put("falaise", new float[]{0.95f, 0.42f, 0.25f});
        REVERBS.put("chambre_meublee", new float[]{0.25f, 0.22f, 0.70f});
        REVERBS.put("cabine_bateau", new float[]{0.18f, 0.26f, 0.75f});
    }

    /* ---------------- les 8 silences actifs (13.31) ---------------- */
    public static final class SilenceBeat {
        public final String id;
        public final String seq;
        public final float seconds;
        public final String what;

        public SilenceBeat(String id, String seq, float seconds, String what) {
            this.id = id;
            this.seq = seq;
            this.seconds = seconds;
            this.what = what;
        }
    }

    public static final List<SilenceBeat> SILENCES = new ArrayList<SilenceBeat>(8);

    static {
        SILENCES.add(new SilenceBeat("SB1", "S1", 3.0f, "le muret aux bottes — avant E01"));
        SILENCES.add(new SilenceBeat("SB2", "S2", 4.0f, "la premiere fois sur la Maree"));
        SILENCES.add(new SilenceBeat("SB3", "S3", 2.0f, "Sol s'arrete net — c'est grave"));
        SILENCES.add(new SilenceBeat("SB4", "S4", 3.5f, "la chemise cartonnee est vide"));
        SILENCES.add(new SilenceBeat("SB5", "S5", 4.0f, "poser la lanterne — C06 « LE NOIR »"));
        SILENCES.add(new SilenceBeat("SB6", "S6", 5.0f, "L'ENTREE DE LA SALLE DE BAL — 5 s de silence numerique absolu"));
        SILENCES.add(new SilenceBeat("SB7", "S7", 2.5f, "le Verrier s'agenouille"));
        SILENCES.add(new SilenceBeat("SB8", "S8", 3.5f, "19.07 — il n'y a pas d'Echo"));
    }

    public static final int WIND_LEVELS = 6;
    public static final float OCCLUSION_INTERVAL = 0.5f;
    public static final int LAYERS_PER_BED = 3;

    private final AudioEngine engine;
    private final EventBus bus;
    private final Rng rng;

    private String bed = "port_mort";
    private String reverb = "falaise";
    private int windLevel = 2;
    private float exposure = 1f;
    private float altitude = 8f;
    private float occlusionTimer;
    private float layerTimer1, layerTimer2, layerTimer3;
    private float silenceRemaining;
    private String silenceId = "";
    private int silencesPlayed;
    private boolean silent;
    private int bedChanges;
    private float oneShotTimer;
    private boolean rain;

    public AmbienceDirector(AudioEngine engine, EventBus bus, Rng rng) {
        this.engine = engine;
        this.bus = bus;
        this.rng = rng;
    }

    /* ------------------------------------------------------------------ */
    /* Configuration                                                       */
    /* ------------------------------------------------------------------ */

    public void setBed(String bedId) {
        if (bedId == null || !BEDS.containsKey(bedId) || bedId.equals(bed)) {
            return;
        }
        bed = bedId;
        bedChanges++;
        layerTimer1 = 0f;
        layerTimer2 = rng.range(1f, 4f);
        layerTimer3 = rng.range(4f, 12f);
        oneShotTimer = rng.range(6f, 20f);
        if (bus != null) {
            bus.emit(EventBus.AMBIENCE_CHANGED, bedId);
        }
    }

    public void setReverb(String reverbId) {
        if (reverbId == null || reverbId.equals(reverb)) {
            return;
        }
        reverb = REVERBS.containsKey(reverbId) ? reverbId : "falaise";
        float[] r = REVERBS.get(reverb);
        engine.setReverb(reverb);
        if (bus != null) {
            bus.emit(EventBus.REVERB_CHANGED, reverb, r[0], r[1]);
        }
    }

    public void setRain(boolean rain) {
        this.rain = rain;
    }

    /** 13.30 : le vent est pilote par l'altitude ET par l'exposition. */
    public void setAltitude(float altitude) {
        this.altitude = altitude;
        int level = windLevelForAltitude(altitude);
        if (level != windLevel) {
            windLevel = level;
            if (bus != null) {
                bus.emit(EventBus.WIND_LEVEL, windLevel, altitude);
            }
        }
    }

    public static int windLevelForAltitude(float altitude) {
        if (altitude < 20f) {
            return 0;
        }
        if (altitude < 55f) {
            return 1;
        }
        if (altitude < 95f) {
            return 2;
        }
        if (altitude < 140f) {
            return 3;
        }
        if (altitude < 185f) {
            return 4;
        }
        return 5;
    }

    /* ------------------------------------------------------------------ */
    /* Boucle                                                              */
    /* ------------------------------------------------------------------ */

    public void update(float dt, PhysicsWorld world, float px, float py, float pz) {
        /* occlusion : raycast toutes les 0,5 s (13.30) */
        occlusionTimer += dt;
        if (occlusionTimer >= OCCLUSION_INTERVAL) {
            occlusionTimer = 0f;
            exposure = computeExposure(world, px, py, pz);
            int level = Math.min(WIND_LEVELS - 1,
                    windLevelForAltitude(altitude) + (exposure > 0.65f ? 1 : exposure < 0.25f ? -1 : 0));
            level = Maths.clamp(level, 0, WIND_LEVELS - 1);
            if (level != windLevel) {
                windLevel = level;
                if (bus != null) {
                    bus.emit(EventBus.WIND_LEVEL, windLevel, altitude);
                }
            }
        }
        if (silent) {
            silenceRemaining -= dt;
            if (silenceRemaining <= 0f) {
                silent = false;
                silenceId = "";
                if (bus != null) {
                    bus.emit(EventBus.SILENCE_ENDED);
                }
            }
            return;      /* silence numerique absolu : aucune voix */
        }
        float[] params = BEDS.get(bed);
        if (params == null) {
            params = BEDS.get("port_mort");
        }
        /* COUCHE 1 — fond constant */
        layerTimer1 -= dt;
        if (layerTimer1 <= 0f) {
            layerTimer1 = 6f;
            engine.play(Synth.noise(0.030f * params[0] + windLevel * 0.010f, 7f,
                    320f + params[1] * 900f + windLevel * 260f, 60f,
                    0.05f + windLevel * 0.02f, 0.35f, rng),
                    AudioEngine.BUS_AMBIENCE, 0f, 0f);
            /* le lit est quadraphonique : deux voix decorrelees gauche/droite */
            engine.play(Synth.noise(0.018f * params[0] + windLevel * 0.007f, 7f,
                    280f + params[1] * 700f, 50f, 0.04f, 0.30f, rng),
                    AudioEngine.BUS_AMBIENCE, -0.75f, 0f);
            engine.play(Synth.noise(0.018f * params[0] + windLevel * 0.007f, 7f,
                    300f + params[1] * 760f, 50f, 0.045f, 0.32f, rng),
                    AudioEngine.BUS_AMBIENCE, 0.75f, 0f);
            if (rain) {
                engine.play(Synth.noise(0.075f, 7f, 5200f, 900f, 0f, 0.15f, rng),
                        AudioEngine.BUS_AMBIENCE, 0f, 0f);
            }
        }
        /* COUCHE 2 — elements moyens aleatoires */
        layerTimer2 -= dt;
        if (layerTimer2 <= 0f) {
            layerTimer2 = rng.range(2.5f, 9f) / Math.max(0.2f, params[3]);
            emitMiddleElement(params);
        }
        /* COUCHE 3 — one-shots lointains */
        oneShotTimer -= dt;
        if (oneShotTimer <= 0f) {
            oneShotTimer = rng.range(9f, 34f) / Math.max(0.05f, params[4]);
            emitDistantOneShot(params);
        }
    }

    private void emitMiddleElement(float[] params) {
        String b = bed;
        if ("marche_suspendu".equals(b)) {
            /* voix lointaines, treuils, linge, une dispute, quelqu'un qui chante */
            int kind = rng.nextInt(6);
            switch (kind) {
                case 0:
                    engine.play(Synth.voice(rng.range(180f, 260f), 0.045f, 2.4f,
                            rng.nextInt(4), rng), AudioEngine.BUS_AMBIENCE,
                            rng.range(-0.8f, 0.8f), rng.range(8f, 26f));
                    break;
                case 1:
                    engine.play(Synth.impulse(rng.range(240f, 420f), 0.06f, 0.35f, 0.7f, rng),
                            AudioEngine.BUS_AMBIENCE, rng.range(-0.9f, 0.9f), rng.range(6f, 20f));
                    break;
                case 2:
                    /* cable de treuil sous tension */
                    engine.play(Synth.sine(rng.range(70f, 120f), 0.05f, 1.4f, 0.2f, 0.8f),
                            AudioEngine.BUS_AMBIENCE, rng.range(-0.6f, 0.6f), rng.range(5f, 16f));
                    break;
                case 3:
                    engine.play(Synth.noise(0.05f, 0.8f, 2600f, 900f, 0f, 0f, rng),
                            AudioEngine.BUS_AMBIENCE, rng.range(-0.9f, 0.9f), rng.range(6f, 22f));
                    break;
                case 4:
                    /* quelqu'un chante, tres loin — jamais une melodie claire */
                    engine.play(Synth.voice(rng.range(220f, 330f), 0.03f, 4.5f, rng.nextInt(4), rng),
                            AudioEngine.BUS_AMBIENCE, rng.range(-0.9f, 0.9f), rng.range(20f, 40f));
                    break;
                default:
                    engine.play(Synth.guitar(rng.range(196f, 330f), 0.035f, 1.8f, rng),
                            AudioEngine.BUS_AMBIENCE, rng.range(-0.5f, 0.5f), rng.range(10f, 28f));
                    break;
            }
            return;
        }
        if ("maree_verre".equals(b)) {
            /* le verre travaille : craquements lointains, notes de bord */
            engine.play(Synth.glass(rng.range(700f, 2100f), 0.030f, 3.2f, 0.30f, rng),
                    AudioEngine.BUS_AMBIENCE, rng.range(-1f, 1f), rng.range(10f, 45f));
            if (rng.nextFloat() < 0.4f) {
                engine.play(Synth.impulse(rng.range(160f, 420f), 0.045f, 0.6f, 0.9f, rng),
                        AudioEngine.BUS_AMBIENCE, rng.range(-1f, 1f), rng.range(15f, 60f));
            }
            return;
        }
        if ("chambre_phare".equals(b)) {
            /* la lampe : un gresillement, le vent dehors, rien d'autre */
            engine.play(Synth.noise(0.028f, 5f, 2400f, 1200f, 18f, 0.5f, rng),
                    AudioEngine.BUS_AMBIENCE, 0.1f, 1.5f);
            return;
        }
        if ("salle_de_bal".equals(b)) {
            engine.play(Synth.voice(rng.range(240f, 400f), 0.028f, 5f, rng.nextInt(4), rng),
                    AudioEngine.BUS_AMBIENCE, rng.range(-0.9f, 0.9f), rng.range(12f, 30f));
            return;
        }
        if ("pluie_battante".equals(b) || "ancienne_halle".equals(b)) {
            engine.play(Synth.impulse(rng.range(300f, 900f), 0.05f, 0.28f, 0.85f, rng),
                    AudioEngine.BUS_AMBIENCE, rng.range(-1f, 1f), rng.range(6f, 24f));
            return;
        }
        /* defaut : un craquement, un objet qui bouge, un oiseau absent */
        engine.play(Synth.impulse(rng.range(180f, 700f), 0.04f * (0.5f + params[3]),
                0.3f, 0.8f, rng), AudioEngine.BUS_AMBIENCE,
                rng.range(-1f, 1f), rng.range(8f, 34f));
    }

    private void emitDistantOneShot(float[] params) {
        float dist = rng.range(60f, 320f);
        int kind = rng.nextInt(5);
        switch (kind) {
            case 0:
                /* une cloche, quelque part — l'instrument d'Esteban */
                engine.play(Synth.bell(Synth.scaleNote(rng.nextInt(7), 2), 0.055f, 6f),
                        AudioEngine.BUS_AMBIENCE, rng.range(-1f, 1f), dist);
                break;
            case 1:
                /* une structure qui travaille */
                engine.play(Synth.sine(rng.range(35f, 70f), 0.07f, 3.5f, 0.4f, 2f),
                        AudioEngine.BUS_AMBIENCE, rng.range(-1f, 1f), dist);
                break;
            case 2:
                /* du verre qui tombe, tres loin */
                engine.play(Synth.noise(0.05f, 1.6f, 5200f, 1600f, 0f, 0f, rng),
                        AudioEngine.BUS_AMBIENCE, rng.range(-1f, 1f), dist * 0.7f);
                break;
            case 3:
                /* une porte */
                engine.play(Synth.noise(0.045f, 2.2f, 1200f, 260f, 5f, 0.5f, rng),
                        AudioEngine.BUS_AMBIENCE, rng.range(-1f, 1f), dist * 0.5f);
                break;
            default:
                /* le Phare : le moteur de la lentille, toutes les 20 s */
                engine.play(Synth.sine(48f, 0.06f, 2.4f, 0.6f, 1.2f),
                        AudioEngine.BUS_AMBIENCE, 0f, dist);
                break;
        }
    }

    /**
     * Exposition : 4 raycasts vers le ciel + 4 vers l'horizon.
     * 1 = a decouvert, 0 = enterre.
     */
    private float computeExposure(PhysicsWorld world, float px, float py, float pz) {
        if (world == null) {
            return 1f;
        }
        int free = 0;
        int total = 0;
        int[] info = new int[3];
        /* vers le ciel */
        for (int i = 0; i < 4; i++) {
            float a = i * Maths.PI * 0.5f;
            float dx = (float) Math.cos(a) * 0.35f;
            float dz = (float) Math.sin(a) * 0.35f;
            float hit = world.raycast(px, py + 1.5f, pz, dx, 0.94f, dz, 40f, info);
            total++;
            if (Float.isNaN(hit)) {
                free++;
            }
        }
        /* vers l'horizon */
        for (int i = 0; i < 4; i++) {
            float a = i * Maths.PI * 0.5f + 0.4f;
            float dx = (float) Math.cos(a);
            float dz = (float) Math.sin(a);
            float hit = world.raycast(px, py + 1.2f, pz, dx, 0.02f, dz, 60f, info);
            total++;
            if (Float.isNaN(hit) || hit > 35f) {
                free++;
            }
        }
        return total == 0 ? 1f : free / (float) total;
    }

    /* ------------------------------------------------------------------ */
    /* Silence actif (13.31)                                               */
    /* ------------------------------------------------------------------ */

    public boolean triggerSilence(String id) {
        for (int i = 0; i < SILENCES.size(); i++) {
            SilenceBeat b = SILENCES.get(i);
            if (b.id.equals(id)) {
                silent = true;
                silenceRemaining = b.seconds;
                silenceId = id;
                silencesPlayed++;
                engine.stopBus(AudioEngine.BUS_AMBIENCE);
                if (bus != null) {
                    bus.emit(EventBus.SILENCE_ACTIVE, id, b.seconds);
                }
                return true;
            }
        }
        return false;
    }

    /** Silence libre (2 a 5 s) — utilise par les declencheurs de niveau. */
    public void silence(float seconds) {
        silent = true;
        silenceRemaining = Maths.clamp(seconds, 2f, 5f);
        silenceId = "libre";
        silencesPlayed++;
        engine.stopBus(AudioEngine.BUS_AMBIENCE);
        if (bus != null) {
            bus.emit(EventBus.SILENCE_ACTIVE, silenceId, silenceRemaining);
        }
    }

    public boolean silent() {
        return silent;
    }

    public String silenceId() {
        return silenceId;
    }

    public int silencesPlayed() {
        return silencesPlayed;
    }

    /* ------------------------------------------------------------------ */
    /* Acces                                                               */
    /* ------------------------------------------------------------------ */

    public String bed() {
        return bed;
    }

    public String reverb() {
        return reverb;
    }

    public int windLevel() {
        return windLevel;
    }

    public float exposure() {
        return exposure;
    }

    public float altitude() {
        return altitude;
    }

    public int bedChanges() {
        return bedChanges;
    }

    /** Garde CI : 14 lits, 3 couches, 6 vents, 8 silences, 9 reverb (13.30-13.32). */
    public static boolean specCompliant() {
        return BEDS.size() == 14 && LAYERS_PER_BED == 3 && WIND_LEVELS == 6
                && SILENCES.size() == 8 && REVERBS.size() == 9;
    }

    public static float longestSilence() {
        float max = 0f;
        for (SilenceBeat b : SILENCES) {
            max = Math.max(max, b.seconds);
        }
        return max;      /* 5 s — l'entree de la salle de bal */
    }

    public void reset() {
        silent = false;
        silenceRemaining = 0f;
        silenceId = "";
        layerTimer1 = 0f;
        layerTimer2 = 2f;
        oneShotTimer = 8f;
        occlusionTimer = 0f;
        windLevel = 2;
        exposure = 1f;
    }
}
