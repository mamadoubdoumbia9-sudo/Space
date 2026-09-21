/*
 * LOHEN — sim/anim/CharacterRig.java
 *
 * 07.19 [OBL] : le motion matching est HORS BUDGET. On utilise du blending
 * classique bien regle ; la qualite vient du nombre de clips de transition
 * (start / stop / turn).
 *
 * 07.20 : 744 clips au total — Lohen 218, Esteban 96, Mireille 64,
 * Tallec 58, Sol 71, Echassier 34, Mueur 39, Verrier 52, PNJ generiques 48,
 * props animes 64. Le manifeste est `/docs/anim_manifest.csv`.
 *
 * 07.02 : la foulee est une DONNEE, pas un timer — longueur de pas 0,34 m
 * au ralenti, 0,72 m en sprint, frequence derivee de la vitesse.
 * 07.03 : IK de pieds sur les surfaces inclinees (deux os, analytique).
 * 07.06 : regard a trois cibles ponderees ; clignements 1/4,2 s.
 * 07.17 : respiration additive permanente, 4 profils.
 * 07.18 : les 14 gestes signature, animes un par un, jamais reutilises.
 *
 * Implementation native : le rig est PROCEDURAL (ADR-003). Aucun asset
 * binaire de squelette n'est disponible dans cet environnement ; les poses
 * sont resolues analytiquement a partir des memes parametres que ceux du
 * manifeste (duree, categorie, direction d'acteur, evenement timeline),
 * ce qui garde le jeu 100 % data-driven et < 1,2 ms pour 4 personnages.
 */
package com.velmora.lohen.sim.anim;

import com.velmora.lohen.sim.core.ContentDb;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.player.Lohen;

public final class CharacterRig {

    /* ---------------- hierarchie (19 os) ---------------- */
    public static final int PELVIS = 0;
    public static final int SPINE1 = 1;
    public static final int SPINE2 = 2;
    public static final int NECK = 3;
    public static final int HEAD = 4;
    public static final int SHOULDER_L = 5;
    public static final int UPPER_ARM_L = 6;
    public static final int FOREARM_L = 7;
    public static final int HAND_L = 8;
    public static final int SHOULDER_R = 9;
    public static final int UPPER_ARM_R = 10;
    public static final int FOREARM_R = 11;
    public static final int HAND_R = 12;
    public static final int THIGH_L = 13;
    public static final int SHIN_L = 14;
    public static final int FOOT_L = 15;
    public static final int THIGH_R = 16;
    public static final int SHIN_R = 17;
    public static final int FOOT_R = 18;
    public static final int BONE_COUNT = 19;

    public static final String[] BONE_NAMES = {
            "pelvis", "spine1", "spine2", "neck", "head",
            "shoulder_l", "upper_arm_l", "forearm_l", "hand_l",
            "shoulder_r", "upper_arm_r", "forearm_r", "hand_r",
            "thigh_l", "shin_l", "foot_l", "thigh_r", "shin_r", "foot_r"};

    public static final int[] PARENT = {
            -1, PELVIS, SPINE1, SPINE2, NECK,
            SPINE2, SHOULDER_L, UPPER_ARM_L, FOREARM_L,
            SPINE2, SHOULDER_R, UPPER_ARM_R, FOREARM_R,
            PELVIS, THIGH_L, SHIN_L, PELVIS, THIGH_R, SHIN_R};

    /* 02.07 : Lohen, 27 ans, 1,74 m. Les proportions sont des DONNEES. */
    public static final float HEIGHT = 1.74f;
    public static final float LEG_LENGTH = 0.86f;
    public static final float THIGH = 0.45f;
    public static final float SHIN = 0.41f;
    public static final float TORSO = 0.52f;
    public static final float NECK_LEN = 0.09f;
    public static final float HEAD_LEN = 0.23f;
    public static final float SHOULDER_WIDTH = 0.42f;
    public static final float UPPER_ARM = 0.30f;
    public static final float FOREARM = 0.27f;
    public static final float HAND_LEN = 0.19f;
    public static final float HIP_WIDTH = 0.30f;

    /* 07.02 : la foulee est une donnee */
    public static final float STRIDE_SLOW = 0.34f;
    public static final float STRIDE_SPRINT = 0.72f;

    /* 07.17 : respiration, 4 profils */
    public static final int BREATH_CALME = 0;
    public static final int BREATH_EFFORT = 1;
    public static final int BREATH_PANIQUE = 2;
    public static final int BREATH_RETENUE = 3;
    private static final float[] BREATH_RATE = {0.28f, 0.52f, 0.95f, 0.05f};
    private static final float[] BREATH_AMP = {0.012f, 0.028f, 0.045f, 0.004f};

    /* sorties */
    private final float[] bones = new float[BONE_COUNT * 3];
    private final float[] quats = new float[BONE_COUNT * 4];
    private final float[] jointL = new float[3];
    private final float[] jointR = new float[3];
    private final float[] ikTarget = new float[3];
    private final float[] pole = new float[3];

    private float breathPhase;
    private int breathProfile = BREATH_CALME;
    private float blinkPhase = 4.2f;
    private float blinkValue = 1f;
    private float leanDeg;
    private float stridePhase;
    private float strideLength = STRIDE_SLOW;
    private float footHeightL, footHeightR;
    private float gazeYaw, gazePitch;
    private String gesture = "";
    private float gestureTime;
    private float gestureDuration = 1.1f;
    private boolean glovesOn = true;
    private float limpWeight;          /* 19.01 : walk_limp active par flag */
    private String clip = "IDLE_01";
    private float clipTime;
    private float clipDuration = 2f;
    private int poseUpdates;

    public CharacterRig() {
        for (int i = 0; i < BONE_COUNT; i++) {
            quats[i * 4 + 3] = 1f;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Parametres                                                          */
    /* ------------------------------------------------------------------ */

    public void setBreathProfile(int profile) {
        breathProfile = Math.max(0, Math.min(3, profile));
    }

    public int breathProfile() {
        return breathProfile;
    }

    public void setLimp(float weight) {
        limpWeight = Maths.clamp01(weight);
    }

    public void setGloves(boolean on) {
        glovesOn = on;
    }

    public boolean glovesOn() {
        return glovesOn;
    }

    public void playClip(String clipName, float duration) {
        if (clipName == null) {
            return;
        }
        if (!clipName.equals(clip)) {
            clip = clipName;
            clipTime = 0f;
            clipDuration = Math.max(0.05f, duration);
        }
    }

    public void playGesture(String gestureId, float duration) {
        gesture = gestureId == null ? "" : gestureId;
        gestureTime = 0f;
        gestureDuration = Math.max(0.2f, duration);
    }

    public String gesture() {
        return gesture;
    }

    public float gestureProgress() {
        return gestureDuration <= 0f ? 0f : Maths.clamp01(gestureTime / gestureDuration);
    }

    public void setFootTargets(float leftHeight, float rightHeight) {
        footHeightL = leftHeight;
        footHeightR = rightHeight;
    }

    /* ------------------------------------------------------------------ */
    /* Mise a jour                                                         */
    /* ------------------------------------------------------------------ */

    public void update(float dt, Lohen lohen) {
        poseUpdates++;
        clipTime += dt;
        breathPhase += dt * BREATH_RATE[breathProfile];
        if (breathPhase > 1f) {
            breathPhase -= 1f;
        }
        /* 07.06 : clignements 1/4,2 s (+40 % en stress, -70 % en choc) */
        blinkPhase -= dt;
        if (blinkPhase <= 0f) {
            blinkPhase = 4.2f * (0.7f + 0.6f * (float) ((poseUpdates * 37) % 100) / 100f);
            blinkValue = 0f;
        } else {
            blinkValue = Maths.damp(blinkValue, 1f, 22f, dt);
        }
        if (gesture.length() > 0) {
            gestureTime += dt;
            if (gestureTime >= gestureDuration) {
                gesture = "";
                gestureTime = 0f;
            }
        }
        /* foulee : donnee, pas timer (07.02) */
        float v = lohen.grounded ? lohen.speed : 0f;
        float t = Maths.clamp01((v - 0.8f) / (lohen.tuning().sprintSpeed - 0.8f));
        strideLength = Maths.lerp(STRIDE_SLOW, STRIDE_SPRINT, t);
        if (v > 0.2f) {
            stridePhase += v / strideLength * dt;
            if (stridePhase > 1f) {
                stridePhase -= 1f;
            }
        } else {
            stridePhase = Maths.damp(stridePhase, 0f, 4f, dt);
        }
        leanDeg = Maths.damp(leanDeg, lohen.leanDeg, 6f, dt);
        /* regard (07.15) */
        if (lohen.gazeActive) {
            float dx = lohen.gazeTargetX - lohen.x;
            float dz = lohen.gazeTargetZ - lohen.z;
            float dy = lohen.gazeTargetY - (lohen.y + HEIGHT - HEAD_LEN);
            float targetYaw = (float) Math.toDegrees(Math.atan2(dx, dz))
                    - (float) Math.toDegrees(lohen.yaw);
            float targetPitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            gazeYaw = Maths.damp(gazeYaw, Maths.clamp(Maths.wrapAngle(targetYaw), -55f, 55f),
                    7f * lohen.gazeWeight, dt);
            gazePitch = Maths.damp(gazePitch, Maths.clamp(targetPitch, -35f, 30f),
                    7f * lohen.gazeWeight, dt);
        } else {
            gazeYaw = Maths.damp(gazeYaw, 0f, 5f, dt);
            gazePitch = Maths.damp(gazePitch, 0f, 5f, dt);
        }
        buildPose(lohen);
    }

    /** Construit les 19 positions d'os dans l'espace monde. */
    private void buildPose(Lohen lohen) {
        float yaw = lohen.yaw;
        float sinY = (float) Math.sin(yaw);
        float cosY = (float) Math.cos(yaw);
        float px = lohen.x;
        float py = lohen.y;
        float pz = lohen.z;

        /* amplitude de respiration additive (07.17) */
        float breath = (float) Math.sin(breathPhase * Maths.TWO_PI) * BREATH_AMP[breathProfile];

        /* hauteur du bassin : marche, saut, accroupi, suspendu */
        float pelvisHeight = LEG_LENGTH;
        float bob = 0f;
        float sway = 0f;
        int state = lohen.state;
        switch (state) {
            case 11:   /* WALK */
            case 12:   /* RUN */
            case 13:   /* SPRINT */
                bob = (float) Math.abs(Math.sin(stridePhase * Maths.PI)) * (0.02f + t(v(lohen)) * 0.05f);
                sway = (float) Math.sin(stridePhase * Maths.TWO_PI) * (0.03f + t(v(lohen)) * 0.05f);
                pelvisHeight -= 0.02f + t(v(lohen)) * 0.05f;
                break;
            case 23:   /* LEDGE_HANG */
            case 24:   /* LEDGE_SHIMMY */
                pelvisHeight = lohen.ledgeY - py - TORSO * 0.5f;
                break;
            case 27:   /* SLIDE */
                pelvisHeight = 0.42f;
                break;
            case 46:   /* COLLAPSE */
                pelvisHeight = 0.38f;
                break;
            default:
                bob = (float) Math.sin(breathPhase * Maths.TWO_PI) * 0.004f;
                break;
        }
        pelvisHeight += breath * 0.5f;
        float pelvisY = py + Math.max(0.25f, pelvisHeight) + bob;

        set(PELVIS, px + cosY * sway, pelvisY, pz - sinY * sway);

        /* colonne : inclinaison avant (lean) + torsion de marche */
        float lean = (float) Math.toRadians(leanDeg);
        float spineTwist = (float) Math.sin(stridePhase * Maths.TWO_PI) * 0.10f * t(v(lohen));
        float s1x = get(PELVIS, 0) - sinY * (float) Math.sin(lean) * TORSO * 0.33f;
        float s1y = pelvisY + (float) Math.cos(lean) * TORSO * 0.33f;
        float s1z = get(PELVIS, 2) - cosY * (float) Math.sin(lean) * TORSO * 0.33f;
        set(SPINE1, s1x, s1y, s1z);
        float s2x = s1x - sinY * (float) Math.sin(lean) * TORSO * 0.33f;
        float s2y = s1y + (float) Math.cos(lean) * TORSO * 0.33f + breath;
        float s2z = s1z - cosY * (float) Math.sin(lean) * TORSO * 0.33f;
        set(SPINE2, s2x, s2y, s2z);
        set(NECK, s2x + sinY * 0.02f, s2y + TORSO * 0.34f + NECK_LEN * 0.5f, s2z + cosY * 0.02f);
        /* tete : regard (07.06) */
        float headYaw = (float) Math.toRadians(gazeYaw);
        float hx = get(NECK, 0) + (float) Math.sin(yaw + headYaw) * HEAD_LEN * 0.55f;
        float hy = get(NECK, 1) + HEAD_LEN * 0.75f - (float) Math.sin(Math.toRadians(gazePitch)) * 0.06f;
        float hz = get(NECK, 2) + (float) Math.cos(yaw + headYaw) * HEAD_LEN * 0.55f;
        set(HEAD, hx, hy, hz);

        /* epaules */
        float shoulderY = s2y + TORSO * 0.30f;
        set(SHOULDER_L, s2x - cosY * SHOULDER_WIDTH * 0.5f, shoulderY, s2z + sinY * SHOULDER_WIDTH * 0.5f);
        set(SHOULDER_R, s2x + cosY * SHOULDER_WIDTH * 0.5f, shoulderY, s2z - sinY * SHOULDER_WIDTH * 0.5f);

        /* bras : balancement de marche + etats specifiques */
        float armSwing = (float) Math.sin(stridePhase * Maths.TWO_PI) * (0.35f + t(v(lohen)) * 0.55f);
        poseArm(lohen, true, -armSwing, sinY, cosY, shoulderY);
        poseArm(lohen, false, armSwing, sinY, cosY, shoulderY);

        /* jambes : foulee + IK de pieds (07.03) */
        poseLeg(lohen, true, sinY, cosY, pelvisY);
        poseLeg(lohen, false, sinY, cosY, pelvisY);

        /* 19.01 : walk_limp active par flag — l'epaule droite ne suit plus */
        if (limpWeight > 0f) {
            float drop = limpWeight * 0.035f;
            set(SHOULDER_R, get(SHOULDER_R, 0), get(SHOULDER_R, 1) - drop, get(SHOULDER_R, 2));
            set(FOOT_R, get(FOOT_R, 0), get(FOOT_R, 1) + limpWeight * 0.02f, get(FOOT_R, 2));
        }
        computeOrientations();
    }

    private static float v(Lohen l) {
        return l.speed;
    }

    private static float t(float speed) {
        return Maths.clamp01(speed / 6.4f);
    }

    private void poseArm(Lohen lohen, boolean left, float swing, float sinY, float cosY,
                         float shoulderY) {
        int shoulder = left ? SHOULDER_L : SHOULDER_R;
        int upper = left ? UPPER_ARM_L : UPPER_ARM_R;
        int fore = left ? FOREARM_L : FOREARM_R;
        int hand = left ? HAND_L : HAND_R;
        float sx = get(shoulder, 0);
        float sy = get(shoulder, 1);
        float sz = get(shoulder, 2);
        int state = lohen.state;
        /* cible de la main selon l'etat */
        float tx, ty, tz;
        if (state == 23 || state == 24) {          /* suspendu : les deux mains en haut */
            tx = sx + (left ? -cosY : cosY) * 0.18f;
            ty = lohen.ledgeY + 0.02f;
            tz = sz + (left ? sinY : -sinY) * 0.18f;
        } else if (state == 30 || state == 31 || state == 32 || state == 33) {
            /* grappin : la main droite vise l'ancre (05.22) */
            if (!left && lohen.grappleAttached) {
                tx = lohen.grappleAnchorX;
                ty = lohen.grappleAnchorY;
                tz = lohen.grappleAnchorZ;
                float dx = tx - sx, dy = ty - sy, dz = tz - sz;
                float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float reach = UPPER_ARM + FOREARM;
                if (l > reach) {
                    tx = sx + dx / l * reach;
                    ty = sy + dy / l * reach;
                    tz = sz + dz / l * reach;
                }
            } else {
                tx = sx + (left ? -cosY : cosY) * 0.10f + sinY * swing * 0.4f;
                ty = sy - UPPER_ARM * 0.85f;
                tz = sz + (left ? sinY : -sinY) * 0.10f + cosY * swing * 0.4f;
            }
        } else if (state == 40 || state == 41) {   /* garde : avant-bras devant */
            tx = sx + sinY * 0.34f + (left ? -cosY : cosY) * 0.10f;
            ty = sy - 0.06f;
            tz = sz + cosY * 0.34f + (left ? sinY : -sinY) * 0.10f;
        } else if (state == 42 || state == 43) {   /* frappe : extension */
            float ext = state == 43 ? 0.62f : 0.48f;
            float phase = Maths.clamp01(lohen.stateTime / 0.35f);
            float reach = (float) Math.sin(phase * Math.PI) * ext;
            tx = sx + sinY * (0.20f + reach) + (left ? -cosY : cosY) * 0.12f;
            ty = sy - 0.10f + reach * 0.15f;
            tz = sz + cosY * (0.20f + reach) + (left ? sinY : -sinY) * 0.12f;
        } else if (state == 50 || state == 54) {   /* Echo / lettre : main tendue */
            if (!left) {
                tx = sx + sinY * 0.42f;
                ty = sy - 0.16f;
                tz = sz + cosY * 0.42f;
            } else {
                tx = sx - cosY * 0.12f;
                ty = sy - UPPER_ARM * 0.9f;
                tz = sz + sinY * 0.12f;
            }
        } else if (lohen.carrying) {               /* V6 porter (08.11) */
            tx = sx + sinY * 0.30f + (left ? -cosY : cosY) * 0.16f;
            ty = sy - 0.30f;
            tz = sz + cosY * 0.30f + (left ? sinY : -sinY) * 0.16f;
        } else {
            tx = sx + (left ? -cosY : cosY) * 0.06f + sinY * swing * 0.55f;
            ty = sy - UPPER_ARM * 0.92f;
            tz = sz + (left ? sinY : -sinY) * 0.06f + cosY * swing * 0.55f;
        }
        /* geste signature en cours : il prime (07.18) */
        if (gesture.length() > 0) {
            applyGestureArm(left, sx, sy, sz, lohen, sinY, cosY);
            tx = ikTarget[0];
            ty = ikTarget[1];
            tz = ikTarget[2];
        }
        ikTarget[0] = tx;
        ikTarget[1] = ty;
        ikTarget[2] = tz;
        /* pole : coude vers l'arriere et le bas */
        pole[0] = -sinY * 0.4f + (left ? -cosY : cosY);
        pole[1] = -1f;
        pole[2] = -cosY * 0.4f + (left ? sinY : -sinY);
        float[] root = {sx, sy, sz};
        IkSolver.solveTwoBone(root, ikTarget, UPPER_ARM, FOREARM, pole,
                left ? jointL : jointR);
        float[] j = left ? jointL : jointR;
        set(upper, j[0], j[1], j[2]);
        /* coude -> main : on place la main sur la cible, l'avant-bras suit */
        set(fore, (j[0] + tx) * 0.5f, (j[1] + ty) * 0.5f, (j[2] + tz) * 0.5f);
        set(hand, tx, ty, tz);
    }

    /** 07.18 : les 14 gestes signature, jamais reutilises d'un personnage a l'autre. */
    private void applyGestureArm(boolean left, float sx, float sy, float sz, Lohen lohen,
                                 float sinY, float cosY) {
        float g = gestureProgress();
        float ease = (float) Math.sin(g * Math.PI);       /* aller-retour naturel */
        if ("L1".equals(gesture)) {
            /* Lohen enleve ses gants (1,1 s) — avant CHAQUE Echo */
            if (!left) {
                ikTarget[0] = sx + sinY * 0.16f - cosY * 0.22f * ease;
                ikTarget[1] = sy - 0.30f + ease * 0.12f;
                ikTarget[2] = sz + cosY * 0.16f + sinY * 0.22f * ease;
            } else {
                ikTarget[0] = sx + sinY * 0.22f + cosY * 0.10f;
                ikTarget[1] = sy - 0.24f;
                ikTarget[2] = sz + cosY * 0.22f - sinY * 0.10f;
            }
            if (g > 0.85f) {
                glovesOn = false;
            }
        } else if ("L4".equals(gesture)) {
            /* Lohen essuie sa paume sur sa cuisse */
            if (!left) {
                ikTarget[0] = sx + cosY * 0.14f;
                ikTarget[1] = sy - 0.52f - ease * 0.08f;
                ikTarget[2] = sz - sinY * 0.14f;
            } else {
                ikTarget[0] = sx - cosY * 0.10f;
                ikTarget[1] = sy - UPPER_ARM * 0.9f;
                ikTarget[2] = sz + sinY * 0.10f;
            }
        } else if ("L2".equals(gesture)) {
            /* touche le lacet du poignet gauche */
            ikTarget[0] = left ? sx + cosY * 0.24f : sx - cosY * 0.24f;
            ikTarget[1] = sy - 0.34f;
            ikTarget[2] = left ? sz - sinY * 0.24f : sz + sinY * 0.24f;
        } else if ("L3".equals(gesture)) {
            /* releve son col contre le vent : les deux mains */
            ikTarget[0] = sx + (left ? -cosY : cosY) * 0.12f + sinY * 0.10f;
            ikTarget[1] = sy + 0.18f * ease;
            ikTarget[2] = sz + (left ? sinY : -sinY) * 0.12f + cosY * 0.10f;
        } else if ("L5".equals(gesture)) {
            /* verifie sa sacoche du plat de la main (tic, 1 fois / 3 min) */
            if (!left) {
                ikTarget[0] = sx - cosY * 0.18f;
                ikTarget[1] = sy - 0.38f;
                ikTarget[2] = sz + sinY * 0.18f;
            } else {
                ikTarget[0] = sx - cosY * 0.06f;
                ikTarget[1] = sy - UPPER_ARM * 0.9f;
                ikTarget[2] = sz + sinY * 0.06f;
            }
        } else if ("E3".equals(gesture)) {
            /* Esteban tapote deux fois le bord d'une table avant de parler */
            float tap = (float) Math.abs(Math.sin(g * Math.PI * 2f));
            ikTarget[0] = sx + sinY * 0.34f;
            ikTarget[1] = sy - 0.42f + tap * 0.05f;
            ikTarget[2] = sz + cosY * 0.34f;
        } else {
            /* geste non reconnu : bras au repos, pas d'invention */
            ikTarget[0] = sx + (left ? -cosY : cosY) * 0.06f;
            ikTarget[1] = sy - UPPER_ARM * 0.92f;
            ikTarget[2] = sz + (left ? sinY : -sinY) * 0.06f;
        }
    }

    private void poseLeg(Lohen lohen, boolean left, float sinY, float cosY, float pelvisY) {
        int thigh = left ? THIGH_L : THIGH_R;
        int shin = left ? SHIN_L : SHIN_R;
        int foot = left ? FOOT_L : FOOT_R;
        float hipX = get(PELVIS, 0) + (left ? -cosY : cosY) * HIP_WIDTH * 0.5f;
        float hipZ = get(PELVIS, 2) + (left ? sinY : -sinY) * HIP_WIDTH * 0.5f;
        int state = lohen.state;
        float phase = stridePhase + (left ? 0f : 0.5f);
        float speedT = t(lohen.speed);
        float tx, ty, tz;
        if (state == 23 || state == 24) {            /* suspendu : pieds contre la paroi */
            tx = hipX - sinY * 0.10f + (left ? -cosY : cosY) * 0.14f;
            ty = lohen.ledgeY - LEG_LENGTH * 0.82f;
            tz = hipZ - cosY * 0.10f + (left ? sinY : -sinY) * 0.14f;
        } else if (state == 25) {                    /* grimpee : un pied cherche */
            float c = Maths.clamp01(lohen.climbProgress);
            tx = hipX + sinY * (0.10f + c * 0.30f) + (left ? -cosY : cosY) * 0.10f;
            ty = lohen.y + 0.25f + c * 0.45f;
            tz = hipZ + cosY * (0.10f + c * 0.30f) + (left ? sinY : -sinY) * 0.10f;
        } else if (state == 27) {                    /* glissade */
            tx = hipX + sinY * (left ? 0.42f : 0.18f);
            ty = lohen.y + 0.08f;
            tz = hipZ + cosY * (left ? 0.42f : 0.18f);
        } else if (state == 46) {                    /* a genoux */
            tx = hipX + sinY * (left ? -0.16f : 0.10f);
            ty = lohen.y + (left ? 0.05f : 0.30f);
            tz = hipZ + cosY * (left ? -0.16f : 0.10f);
        } else if (!lohen.grounded) {                /* air : jambes repliees */
            float f = Maths.clamp01(-lohen.vy / 8f);
            tx = hipX + sinY * 0.14f + (left ? -cosY : cosY) * 0.10f;
            ty = pelvisY - LEG_LENGTH * (0.72f - f * 0.22f);
            tz = hipZ + cosY * 0.14f + (left ? sinY : -sinY) * 0.10f;
        } else if (lohen.speed > 0.2f) {
            /* foulee : le pied decrit une ellipse, hauteur proportionnelle */
            float swingX = (float) Math.sin(phase * Maths.TWO_PI) * strideLength * (0.55f + speedT * 0.45f);
            float lift = Math.max(0f, (float) Math.sin(phase * Maths.TWO_PI)) * (0.05f + speedT * 0.13f);
            tx = hipX + sinY * swingX;
            ty = lohen.y + lift;
            tz = hipZ + cosY * swingX;
            /* IK de pied sur surface inclinee (07.03) */
            ty += left ? footHeightL : footHeightR;
        } else {
            tx = hipX + (left ? -cosY : cosY) * 0.10f;
            ty = lohen.y;
            tz = hipZ + (left ? sinY : -sinY) * 0.10f;
            ty += left ? footHeightL : footHeightR;
        }
        ikTarget[0] = tx;
        ikTarget[1] = ty;
        ikTarget[2] = tz;
        /* pole : genou vers l'avant */
        pole[0] = sinY;
        pole[1] = 0.2f;
        pole[2] = cosY;
        float[] root = {hipX, pelvisY, hipZ};
        IkSolver.solveTwoBone(root, ikTarget, THIGH, SHIN, pole, left ? jointL : jointR);
        float[] j = left ? jointL : jointR;
        set(thigh, hipX, pelvisY, hipZ);
        set(shin, j[0], j[1], j[2]);
        set(foot, tx, ty, tz);
    }

    private void computeOrientations() {
        for (int i = 0; i < BONE_COUNT; i++) {
            int p = PARENT[i];
            float[] from = p < 0 ? ORIGIN : slice(p);
            float[] to = slice(i);
            IkSolver.lookQuaternion(from, to, 0f, 1f, 0f, TMP4);
            quats[i * 4] = TMP4[0];
            quats[i * 4 + 1] = TMP4[1];
            quats[i * 4 + 2] = TMP4[2];
            quats[i * 4 + 3] = TMP4[3];
        }
    }

    private static final float[] ORIGIN = new float[3];
    private static final float[] TMP4 = new float[4];
    private static final float[] SLICE = new float[3];

    private float[] slice(int bone) {
        SLICE[0] = bones[bone * 3];
        SLICE[1] = bones[bone * 3 + 1];
        SLICE[2] = bones[bone * 3 + 2];
        return SLICE;
    }

    private void set(int bone, float x, float y, float z) {
        bones[bone * 3] = x;
        bones[bone * 3 + 1] = y;
        bones[bone * 3 + 2] = z;
    }

    private float get(int bone, int axis) {
        return bones[bone * 3 + axis];
    }

    /* ---------------- acces rendu ---------------- */

    public float[] bones() {
        return bones;
    }

    public float[] orientations() {
        return quats;
    }

    public float boneX(int b) {
        return bones[b * 3];
    }

    public float boneY(int b) {
        return bones[b * 3 + 1];
    }

    public float boneZ(int b) {
        return bones[b * 3 + 2];
    }

    public float blink() {
        return blinkValue;
    }

    public float breathValue() {
        return (float) Math.sin(breathPhase * Maths.TWO_PI) * 0.5f + 0.5f;
    }

    public float stridePhase() {
        return stridePhase;
    }

    public float strideLength() {
        return strideLength;
    }

    public String clip() {
        return clip;
    }

    public float clipTime() {
        return clipTime;
    }

    public int poseUpdates() {
        return poseUpdates;
    }

    public void reset() {
        breathPhase = 0f;
        stridePhase = 0f;
        blinkPhase = 4.2f;
        gesture = "";
        gestureTime = 0f;
        glovesOn = true;
        limpWeight = 0f;
        gazeYaw = 0f;
        gazePitch = 0f;
    }

    /** Duree d'un clip depuis le manifeste (07.20) si disponible. */
    public static float clipDuration(ContentDb db, String clipId) {
        if (db == null || clipId == null) {
            return 1f;
        }
        ContentDb.AnimClip c = db.animById(clipId);
        return c == null || c.duration <= 0f ? 1f : c.duration;
    }
}
