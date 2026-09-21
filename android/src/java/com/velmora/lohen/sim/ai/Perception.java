/*
 * LOHEN — sim/ai/Perception.java
 *
 * Le modele de perception partage : qui voit Lohen, qui l'entend, et qui
 * s'en moque. Trois consommateurs :
 *
 *  1. LES FIGURES (06.20) — l'ouie d'abord. Un pas sur du verre mouille
 *     s'entend plus loin qu'un pas sur du tapis. Le grappin, le combat et
 *     le Souffle qui casse font du bruit. La lumiere ne les interesse pas.
 *  2. LES MUEURS (09.14) — ils ont peur de la lumiere mais pas assez :
 *     la lumiere les repousse a 4 m, rester dans le noir les attire.
 *  3. LES 34 PNJ DU MARCHE (09.12) — routines simples mais LISIBLES. Un
 *     homme qui repeint la meme porte tous les jours. Ils remarquent Lohen
 *     quand il court, quand il porte, quand son Souffle casse ; alors ils
 *     s'arretent, ils regardent, et parfois ils disent quelque chose.
 *
 * 07.15 SYSTEME DE REGARD : trois cibles ponderees — l'interlocuteur,
 * l'objet d'interet contextuel, et un point de « fuite » (le vide, le ciel).
 * Un personnage qui ment ou qui a mal regarde le point de fuite 30 % du
 * temps. Mireille, sequence 4, le regarde 62 % du temps.
 * 07.16 CLIGNEMENTS : 1 toutes les 4,2 s, +40 % en stress, -70 % en choc.
 * Double-clignement apres une ligne difficile. Interdiction de cligner
 * pendant un plan de moins de 1 s.
 * 11.06 : les fantomes ne regardent JAMAIS Lohen. Une seule exception,
 * E23 (la fille du Verrier), ou elle tourne la tete.
 */
package com.velmora.lohen.sim.ai;

import com.velmora.lohen.sim.anim.FootstepSystem;
import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Rng;
import com.velmora.lohen.sim.math.Geom;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.player.Lohen;
import com.velmora.lohen.sim.world.PhysicsWorld;

import java.util.ArrayList;
import java.util.List;

public final class Perception {

    /* ---------------- 07.15 : le regard ---------------- */
    public static final int GAZE_INTERLOCUTOR = 0;
    public static final int GAZE_OBJECT = 1;
    public static final int GAZE_ESCAPE = 2;
    public static final float ESCAPE_WEIGHT_LIAR = 0.30f;
    public static final float ESCAPE_WEIGHT_MIREILLE = 0.62f;
    public static final float ESCAPE_WEIGHT_HONEST = 0.08f;
    public static final float ESCAPE_WEIGHT_IN_PAIN = 0.30f;

    /* ---------------- 07.16 : les clignements ---------------- */
    public static final float BLINK_BASE_SECONDS = 4.2f;
    public static final float BLINK_STRESS_FACTOR = 1.40f;
    public static final float BLINK_SHOCK_FACTOR = 0.30f;
    public static final float BLINK_MIN_SHOT_SECONDS = 1.0f;

    /* ---------------- 09.14 : lumiere / noir ---------------- */
    public static final float LIGHT_REPEL_M = 4.0f;
    public static final float DARK_ATTRACT_M = 26.0f;

    /* ---------------- 09.12 : le marche ---------------- */
    public static final int MARKET_NPC_COUNT = 34;
    public static final int MARKET_BARKS = 96;
    public static final float NOTICE_RUN_SPEED = 4.2f;
    public static final float NOTICE_RADIUS_M = 9.0f;

    /* ---------------- 11.06 : l'exception ---------------- */
    public static final String GHOST_LOOKS_BACK_ECHO = "E23";

    /* ------------------------------------------------------------------ */
    /* Bruit                                                               */
    /* ------------------------------------------------------------------ */

    /** Un evenement sonore bref, avec son rayon d'audibilite. */
    public static final class NoiseEvent {
        public float x, y, z;
        public float radius;
        public float life;
        public String kind;

        NoiseEvent(float x, float y, float z, float radius, float life, String kind) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.life = life;
            this.kind = kind;
        }
    }

    /** Rayon d'audibilite d'un pas, par surface et par allure (metres). */
    public static final float[][] FOOTSTEP_NOISE = {
            /* marche, jog, course */
            {5.0f, 9.0f, 14.0f},      /* bois mouille : ca grince */
            {3.5f, 6.5f, 10.0f},      /* bois sec */
            {4.0f, 7.5f, 12.0f},      /* pierre */
            {5.5f, 10.0f, 15.5f},     /* pierre mouillee : ca claque */
            {7.0f, 12.5f, 19.0f},     /* gravier : le plus bruyant */
            {6.0f, 11.0f, 17.0f},     /* verre de la Maree : ca sonne */
            {6.5f, 11.5f, 18.0f},     /* metal : passerelles, treuils */
            {1.2f, 2.0f, 3.0f},       /* tapis / parquet : la salle de bal */
            {6.0f, 10.5f, 16.0f},     /* eau peu profonde : ca eclabousse */
    };

    public static final float NOISE_GRAPPLE_SHOT = 12f;
    public static final float NOISE_CABLE_STRAIN = 8f;
    public static final float NOISE_IMPACT_LIGHT = 14f;
    public static final float NOISE_IMPACT_HEAVY = 26f;
    public static final float NOISE_GLASS_SHATTER = 34f;
    public static final float NOISE_BREATH_BREAK = 11f;
    public static final float NOISE_LANDING_HARD = 13f;
    public static final float NOISE_SET_LANTERN = 3.5f;

    public static final float EVENT_LIFE = 0.6f;

    private final EventBus bus;
    private final Rng rng;
    private final List<NoiseEvent> noises = new ArrayList<NoiseEvent>(24);

    private float playerNoiseRadius;
    private float playerVisibility;
    private float darkness;
    private int noticesTriggered;
    private int blinkDouble;
    private float lastFootstepNoise;

    public Perception(EventBus bus, Rng rng) {
        this.bus = bus;
        this.rng = rng;
    }

    /* ------------------------------------------------------------------ */
    /* Emission                                                            */
    /* ------------------------------------------------------------------ */

    public void emitNoise(float x, float y, float z, float radius, String kind) {
        if (radius <= 0f) {
            return;
        }
        if (noises.size() > 48) {
            noises.remove(0);
        }
        noises.add(new NoiseEvent(x, y, z, radius, EVENT_LIFE, kind));
        playerNoiseRadius = Math.max(playerNoiseRadius, radius);
    }

    /** Un pas : le rayon depend de la surface ET de l'allure (13.28). */
    public void onFootstep(float x, float y, float z, int material, float speed) {
        int surface = FootstepSystem.mapMaterial(material);
        int tier = FootstepSystem.speedTier(speed);
        float radius = FOOTSTEP_NOISE[Maths.clamp(surface, 0, FOOTSTEP_NOISE.length - 1)]
                [Maths.clamp(tier, 0, 2)];
        lastFootstepNoise = radius;
        emitNoise(x, y, z, radius, "footstep:" + FootstepSystem.SURFACE_NAMES[surface]);
    }

    public float lastFootstepNoise() {
        return lastFootstepNoise;
    }

    /** Rayon d'audibilite courant du joueur (pour l'IA et les audits). */
    public float playerNoiseRadius(Lohen lohen) {
        if (lohen == null) {
            return playerNoiseRadius;
        }
        int tier = FootstepSystem.speedTier(lohen.groundedSpeed);
        int surface = FootstepSystem.mapMaterial(lohen.groundMaterial);
        float base = FOOTSTEP_NOISE[Maths.clamp(surface, 0, FOOTSTEP_NOISE.length - 1)]
                [Maths.clamp(tier, 0, 2)];
        if (lohen.crouch) {
            base *= 0.55f;                    /* accroupi : deux fois plus discret */
        }
        return base;
    }

    /* ------------------------------------------------------------------ */
    /* Reception                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Ce point entend-il le joueur ? Un mur coupe l'audition au-dela de
     * 60 % du rayon (le son tourne, mais il s'affaiblit).
     */
    public boolean hears(float ox, float oy, float oz, PhysicsWorld world,
                         float px, float py, float pz, float earRange) {
        float dx = px - ox;
        float dy = py - oy;
        float dz = pz - oz;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float radius = Math.max(earRange, playerNoiseRadius);
        if (d > radius) {
            return false;
        }
        for (int i = 0; i < noises.size(); i++) {
            NoiseEvent n = noises.get(i);
            float ndx = n.x - ox;
            float ndy = n.y - oy;
            float ndz = n.z - oz;
            float nd = (float) Math.sqrt(ndx * ndx + ndy * ndy + ndz * ndz);
            if (nd < n.radius) {
                return true;
            }
        }
        if (world != null && d > radius * 0.6f) {
            int[] info = new int[3];
            float hit = world.raycast(ox, oy + 1.2f, oz, dx / Math.max(0.001f, d),
                    dy / Math.max(0.001f, d), dz / Math.max(0.001f, d), d, info);
            return Float.isNaN(hit);
        }
        return true;
    }

    /**
     * Visibilite du joueur depuis un observateur : 0 (invisible) a 1
     * (pleinement vu). Combine la distance, la lumiere sur le joueur,
     * l'occlusion et le mouvement.
     */
    public float visibility(Lohen lohen, PhysicsWorld world, float ox, float oy, float oz,
                            float lightOnPlayer, float visionRange) {
        if (lohen == null) {
            return 0f;
        }
        float dx = lohen.x - ox;
        float dy = (lohen.y + 1.2f) - oy;
        float dz = lohen.z - oz;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > visionRange) {
            return 0f;
        }
        float distance = 1f - Maths.clamp01(d / visionRange);
        float light = Maths.clamp01(lightOnPlayer);
        float motion = Maths.clamp01(lohen.groundedSpeed / 5.5f);
        float crouch = lohen.crouch ? 0.55f : 1f;
        float occlusion = 1f;
        if (world != null && d > 2f) {
            int[] info = new int[3];
            float hit = world.raycast(ox, oy, oz, dx / d, dy / d, dz / d, d, info);
            if (!Float.isNaN(hit) && hit < d - 0.4f) {
                occlusion = 0.08f;
            }
        }
        return Maths.clamp01((distance * 0.45f + light * 0.35f + motion * 0.20f)
                * crouch * occlusion);
    }

    /* ------------------------------------------------------------------ */
    /* 09.14 : les Mueurs, la lumiere et le noir                           */
    /* ------------------------------------------------------------------ */

    /**
     * Le joueur est-il dans le noir ? La distance a la source de lumiere la
     * plus proche depasse le rayon de repulsion (4 m).
     */
    public float darkness(Lohen lohen, float nearestLightDistance) {
        if (lohen == null) {
            return 1f;
        }
        darkness = Maths.clamp01((nearestLightDistance - LIGHT_REPEL_M) / 6f);
        return darkness;
    }

    public float darkness() {
        return darkness;
    }

    /** La lumiere repousse a 4 m (09.14). */
    public static boolean lightRepels(float distanceToLight) {
        return distanceToLight < LIGHT_REPEL_M;
    }

    /** Rester dans le noir les attire (09.14). */
    public static boolean darkAttracts(float distanceToLight) {
        return distanceToLight > LIGHT_REPEL_M && distanceToLight < DARK_ATTRACT_M;
    }

    /* ------------------------------------------------------------------ */
    /* 09.12 : les 34 PNJ du marche                                        */
    /* ------------------------------------------------------------------ */

    /**
     * Un PNJ remarque Lohen quand il court pres de lui, quand il porte
     * quelque chose de visible, ou quand son Souffle casse. La reaction est
     * lisible : il s'arrete, il regarde, parfois il parle.
     */
    public boolean npcNotices(Lohen lohen, float npcX, float npcY, float npcZ,
                              float npcYawDeg, boolean carrying, boolean breathBroken) {
        if (lohen == null) {
            return false;
        }
        float dx = lohen.x - npcX;
        float dz = lohen.z - npcZ;
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d > NOTICE_RADIUS_M) {
            return false;
        }
        /* dans son champ de vision : 130 degres */
        float toPlayer = (float) Math.atan2(dx, dz) * Maths.DEG;
        float delta = Math.abs(Maths.wrapAngle(toPlayer - npcYawDeg));
        boolean inView = delta < 65f;
        boolean runs = lohen.groundedSpeed > NOTICE_RUN_SPEED;
        boolean obvious = carrying || breathBroken;
        boolean noticed = inView && (runs || obvious) || (obvious && d < 3f);
        if (noticed) {
            noticesTriggered++;
            if (bus != null) {
                bus.emit(EventBus.NPC_NOTICED, d, runs, carrying);
            }
        }
        return noticed;
    }

    public int noticesTriggered() {
        return noticesTriggered;
    }

    /* ------------------------------------------------------------------ */
    /* 07.15 : les trois poids du regard                                   */
    /* ------------------------------------------------------------------ */

    /**
     * Repartition canonique : {interlocuteur, objet, fuite}. Les trois
     * poids somment a 1. Un menteur ou quelqu'un qui a mal fuit 30 % du
     * temps ; Mireille, 62 %.
     */
    public static float[] gazeWeights(boolean lying, boolean inPain, boolean mireille,
                                      float objectInterest, float[] out) {
        float[] w = out == null || out.length < 3 ? new float[3] : out;
        float escape;
        if (mireille) {
            escape = ESCAPE_WEIGHT_MIREILLE;
        } else if (lying || inPain) {
            escape = ESCAPE_WEIGHT_LIAR;
        } else {
            escape = ESCAPE_WEIGHT_HONEST;
        }
        float object = Maths.clamp01(objectInterest) * (1f - escape) * 0.55f;
        w[GAZE_ESCAPE] = escape;
        w[GAZE_OBJECT] = object;
        w[GAZE_INTERLOCUTOR] = Maths.clamp01(1f - escape - object);
        return w;
    }

    /* ------------------------------------------------------------------ */
    /* 07.16 : les clignements                                             */
    /* ------------------------------------------------------------------ */

    /** Intervalle entre deux clignements, en secondes. */
    public static float blinkInterval(float stress, boolean shocked) {
        float interval = BLINK_BASE_SECONDS;
        interval /= 1f + (BLINK_STRESS_FACTOR - 1f) * Maths.clamp01(stress);
        if (shocked) {
            interval /= BLINK_SHOCK_FACTOR;      /* -70 % de frequence */
        }
        return interval;
    }

    /** Interdiction de cligner pendant un plan de moins de 1 s (07.16). */
    public static boolean blinkAllowed(float shotSeconds) {
        return shotSeconds >= BLINK_MIN_SHOT_SECONDS;
    }

    /** Double-clignement apres une ligne difficile. */
    public boolean noteDifficultLine() {
        blinkDouble++;
        if (bus != null) {
            bus.emit(EventBus.BLINK_DOUBLE, blinkDouble);
        }
        return true;
    }

    public int doubleBlinks() {
        return blinkDouble;
    }

    /* ------------------------------------------------------------------ */
    /* 11.06 : les fantomes                                                */
    /* ------------------------------------------------------------------ */

    /** Les fantomes ne regardent jamais Lohen. Sauf elle, dans E23. */
    public static boolean ghostLooksAtPlayer(String echoId) {
        return GHOST_LOOKS_BACK_ECHO.equals(echoId);
    }

    /* ------------------------------------------------------------------ */
    /* Boucle                                                              */
    /* ------------------------------------------------------------------ */

    public void update(float dt) {
        for (int i = 0; i < noises.size(); i++) {
            NoiseEvent n = noises.get(i);
            n.life -= dt;
            if (n.life <= 0f) {
                noises.remove(i--);
            }
        }
        playerNoiseRadius = Maths.damp(playerNoiseRadius, 0f, 6f, dt);
        playerVisibility = Maths.damp(playerVisibility, 0f, 4f, dt);
        if (rng != null && rng.chance(0f)) {
            /* le rng n'est utilise que par les reactions aleatoires des PNJ */
            noticesTriggered += 0;
        }
    }

    public int noiseCount() {
        return noises.size();
    }

    public float playerVisibility() {
        return playerVisibility;
    }

    public void setPlayerVisibility(float v) {
        playerVisibility = Maths.clamp01(v);
    }

    /** Materiau le plus bruyant du chapitre : le gravier en course (19 m). */
    public static float loudestFootstepRadius() {
        float max = 0f;
        for (float[] row : FOOTSTEP_NOISE) {
            for (float v : row) {
                max = Math.max(max, v);
            }
        }
        return max;
    }

    /** Le tapis de la salle de bal : 3 m en course. Le silence est jouable. */
    public static float quietestFootstepRadius() {
        float min = Float.MAX_VALUE;
        for (float[] row : FOOTSTEP_NOISE) {
            for (float v : row) {
                min = Math.min(min, v);
            }
        }
        return min;
    }

    /** Garde CI : 34 PNJ, 96 barks, 4 m, 4,2 s, 30 % / 62 %, E23 (09.12/09.14/07.15). */
    public static boolean specCompliant() {
        return MARKET_NPC_COUNT == 34
                && MARKET_BARKS == 96
                && LIGHT_REPEL_M == 4f
                && BLINK_BASE_SECONDS == 4.2f
                && ESCAPE_WEIGHT_LIAR == 0.30f
                && ESCAPE_WEIGHT_MIREILLE == 0.62f
                && FOOTSTEP_NOISE.length == Geom.MAT_COUNT
                && "E23".equals(GHOST_LOOKS_BACK_ECHO);
    }

    public void reset() {
        noises.clear();
        playerNoiseRadius = 0f;
        playerVisibility = 0f;
        darkness = 0f;
        noticesTriggered = 0;
        blinkDouble = 0;
        lastFootstepNoise = 0f;
    }
}
