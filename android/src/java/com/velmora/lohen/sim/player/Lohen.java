/*
 * LOHEN — sim/player/Lohen.java
 *
 * L'entite joueur. 27 ans, 1,74 m, matricule 0114 (02.07) — les proportions
 * ne sont pas des nombres magiques, elles viennent du dossier personnage.
 *
 * Contient uniquement de l'etat + des acces ; la logique vit dans les
 * composants (Motor, GrappleComponent, CombatComponent, PlayerFsm…) pour
 * rester testable hors device (18.06) et modulaire (04.05).
 */
package com.velmora.lohen.sim.player;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Tuning;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.math.Vec3;

public final class Lohen {

    public static final float HEIGHT = 1.74f;
    public static final float RADIUS = 0.32f;
    public static final float SHOULDER_HEIGHT = 1.42f;
    public static final float HEAD_HEIGHT = 1.62f;
    public static final float HIP_HEIGHT = 0.92f;
    public static final int MATRICULE = 0114;

    /* transform (pieds au sol) */
    public float x, y, z;
    public float yaw;
    public float vx, vy, vz;
    public float speed;
    public float groundedSpeed;

    /* contact */
    public boolean grounded;
    public float groundY = Float.NaN;
    public int groundMaterial = -1;
    public int groundFlags;
    public boolean onGlassSea;
    public boolean onHeatWell;
    public float wallNormalX, wallNormalZ;
    public boolean wallContact;
    public float coyote;

    /* etats */
    public int state = PlayerFsm.ST_IDLE;
    public int prevState = PlayerFsm.ST_IDLE;
    public float stateTime;
    public boolean inCombat;
    public boolean inDialogue;
    public boolean inCinematic;
    public boolean inEcho;
    public boolean carrying;
    /** 09.14 : plafond de 1,4 m dans les conduits — Lohen est accroupi. */
    public boolean crouch;
    public float crouchFactor = 1f;
    public String carriedObjectId = "";
    public boolean dead;
    public float time;

    /* animation */
    public String clip = "IDLE_01";
    public float clipTime;
    public String overlayClip = "";
    public float overlayTime;
    public float blend;
    public float leanDeg;
    public float stridePhase;
    public float strideLength;
    public int footfall;         /* 0 = gauche, 1 = droit */
    public float leftFootIK, rightFootIK;
    public float leftHandIK, rightHandIK;
    public float ikWeight;
    public float gestureWeight;
    public String gesture = "";

    /* regard / IK de tete (07.06) */
    public float headYaw, headPitch;
    public float gazeTargetX, gazeTargetY, gazeTargetZ;
    public boolean gazeActive;
    public float gazeWeight;

    /* grappin */
    public boolean grappleAttached;
    public float grappleAnchorX, grappleAnchorY, grappleAnchorZ;
    public int grappleAnchorId = -1;
    public float cableLength;
    public float cableTension;

    /* combat */
    public float guardTime;
    public boolean guarding;
    public boolean parryWindowOpen;
    public float parryWindowTime;
    public boolean invulnerable;
    public float staggerTime;
    public float hitstopTime;

    /* traversee */
    public float ledgeX, ledgeY, ledgeZ, ledgeYaw;
    public int ledgeIndex = -1;
    public float ledgeShimmy;
    public float wallrunTime;
    public float climbProgress;

    /* souffle (08.13) */
    public final BreathComponent breath;

    /* entrees */
    public float inputX, inputY;
    public float cameraYaw, cameraPitch;
    public boolean sprintHeld;
    public boolean jumpBuffered;
    public boolean interactBuffered;
    public boolean grappleBuffered;
    public boolean guardHeld;
    public boolean attackBuffered;
    public boolean heavyBuffered;
    public boolean dodgeBuffered;
    public float dodgeX, dodgeZ;

    /* rendu / debug */
    public final Vec3 tmp = new Vec3();
    private final Tuning tuning;

    public Lohen(Tuning tuning, EventBus bus) {
        this.tuning = tuning;
        this.breath = new BreathComponent(tuning, bus);
    }

    public void reset(float px, float py, float pz, float yawDeg) {
        x = px;
        y = py;
        z = pz;
        yaw = (float) Math.toRadians(yawDeg);
        vx = vy = vz = 0f;
        speed = 0f;
        grounded = false;
        groundY = Float.NaN;
        state = PlayerFsm.ST_FALL;
        stateTime = 0f;
        inCombat = false;
        inDialogue = false;
        inCinematic = false;
        inEcho = false;
        carrying = false;
        carriedObjectId = "";
        dead = false;
        clipTime = 0f;
        blend = 0f;
        leanDeg = 0f;
        stridePhase = 0f;
        grappleAttached = false;
        grappleAnchorId = -1;
        guarding = false;
        invulnerable = false;
        staggerTime = 0f;
        hitstopTime = 0f;
        wallrunTime = 0f;
        ledgeIndex = -1;
        breath.update(0f);
    }

    public void integrate(float dt) {
        time += dt;
        stateTime += dt;
        if (hitstopTime > 0f) {
            hitstopTime -= dt;
        }
        if (staggerTime > 0f) {
            staggerTime -= dt;
        }
        if (coyote > 0f) {
            coyote -= dt;
        }
        /* hit-stop : le monde s'arrete 70 a 160 ms (07.13) */
        float simDt = hitstopTime > 0f ? 0f : dt;
        x += vx * simDt;
        y += vy * simDt;
        z += vz * simDt;
        speed = (float) Math.sqrt(vx * vx + vz * vz);
        groundedSpeed = grounded ? speed : 0f;
        if (clipTime < 1e9f) {
            clipTime += simDt;
        }
        if (overlayTime < 1e9f) {
            overlayTime += simDt;
        }
    }

    public void lookAt(float tx, float ty, float tz, float weight) {
        gazeTargetX = tx;
        gazeTargetY = ty;
        gazeTargetZ = tz;
        gazeActive = true;
        gazeWeight = weight;
        float dx = tx - x;
        float dz = tz - z;
        float dy = ty - (y + HEAD_HEIGHT);
        float targetYaw = (float) Math.atan2(dx, dz);
        float targetPitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float dyaw = Maths.wrapAngle((float) Math.toDegrees(targetYaw) - (float) Math.toDegrees(yaw));
        headYaw = Maths.lerp(headYaw, Maths.clamp(dyaw, -55f, 55f), weight * 0.25f);
        headPitch = Maths.lerp(headPitch, Maths.clamp(targetPitch, -35f, 30f), weight * 0.25f);
    }

    public void clearGaze() {
        gazeActive = false;
        gazeWeight = 0f;
        headYaw = Maths.damp(headYaw, 0f, 8f, 0.016f);
        headPitch = Maths.damp(headPitch, 0f, 8f, 0.016f);
    }

    /** Vecteur avant au sol (le personnage regarde yaw). */
    public void forward(float[] out) {
        out[0] = (float) Math.sin(yaw);
        out[1] = 0f;
        out[2] = (float) Math.cos(yaw);
    }

    public void right(float[] out) {
        out[0] = (float) Math.cos(yaw);
        out[1] = 0f;
        out[2] = -(float) Math.sin(yaw);
    }

    public float eyeX() {
        return x;
    }

    public float eyeY() {
        return y + HEAD_HEIGHT;
    }

    public float eyeZ() {
        return z;
    }

    public Tuning tuning() {
        return tuning;
    }

    public boolean exhausted() {
        return breath.fraction() * 100f < tuning.exhaustedBelowPct;
    }

    /** Distance horizontale parcourue par frame (audit anti-boucle). */
    public float horizontalSpeed() {
        return (float) Math.sqrt(vx * vx + vz * vz);
    }

    public String describe() {
        return "Lohen(" + x + "," + y + "," + z + ") yaw=" + (float) Math.toDegrees(yaw)
                + " state=" + PlayerFsm.name(state) + " breath=" + breath.current();
    }
}
