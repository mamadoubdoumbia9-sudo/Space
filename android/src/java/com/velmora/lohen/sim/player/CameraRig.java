/*
 * LOHEN — sim/player/CameraRig.java
 *
 * 08.23 [OBL] TUNING DE CAMERA (camera_tuning.tres) :
 *   distance 3,4 m / hauteur de cible 1,55 m / offset lateral +0,45 m
 *   (epaule droite) / sensibilite tactile 0,22 deg/dp, courbe x^1,25 /
 *   lissage positionnel 0,12 s, rotationnel 0,08 s / collision SpringArm
 *   rayon 0,32, rentree adoucie sur 0,15 s / recentrage auto apres 2,5 s
 *   de course sans input camera (45 deg/s, desactivable) / clamp vertical
 *   -62 / +58 deg / FOV dynamique +6 deg a vitesse max, interpole sur 0,8 s.
 *
 * 05.10 : focale 38 mm (FOV 46) en exploration, 52 mm (FOV 34) en dialogue,
 *         28 mm (FOV 62) en chute libre / grappin long. Transitions
 *         TOUJOURS en ease_out_cubic, jamais instantanees.
 * 05.11 : ligne d'horizon au tiers SUPERIEUR en exploration (on voit le vide),
 *         au tiers INFERIEUR dans les interieurs (on voit les plafonds).
 * 05.12 : DOF — near blur DESACTIVE en gameplay, far blur a partir de 45 m,
 *         intensite 0,6. En dialogue : f/2.0 simule, le locuteur est net,
 *         l'autre a 0,35 de flou.
 * 19.08 : plan rapproche par-dessus l'epaule DROITE, legerement en plongee.
 */
package com.velmora.lohen.sim.player;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Options;
import com.velmora.lohen.sim.core.Tuning;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.math.Vec3;
import com.velmora.lohen.sim.world.PhysicsWorld;

public final class CameraRig {

    /* modes */
    public static final int MODE_EXPLORE = 0;
    public static final int MODE_DIALOGUE = 1;
    public static final int MODE_FALL = 2;
    public static final int MODE_CINEMATIC = 3;
    public static final int MODE_ECHO = 4;
    public static final int MODE_LETTER = 5;
    public static final int MODE_COMBAT = 6;

    /* reglages canoniques (08.23) */
    public static final float DISTANCE = 3.4f;
    public static final float TARGET_HEIGHT = 1.55f;
    public static final float SHOULDER_OFFSET = 0.45f;
    public static final float TOUCH_SENSITIVITY = 0.22f;
    public static final float TOUCH_CURVE = 1.25f;
    public static final float SMOOTH_POS = 0.12f;
    public static final float SMOOTH_ROT = 0.08f;
    public static final float SPRING_RADIUS = 0.32f;
    public static final float SPRING_RETURN = 0.15f;
    public static final float RECENTER_AFTER = 2.5f;
    public static final float RECENTER_SPEED = 45f;
    public static final float PITCH_MIN = -62f;
    public static final float PITCH_MAX = 58f;
    public static final float FOV_SPEED_BONUS = 6f;
    public static final float FOV_LERP = 0.8f;

    /* focales (05.10) */
    public static final float FOV_EXPLORE = 46f;    /* 38 mm */
    public static final float FOV_DIALOGUE = 34f;   /* 52 mm */
    public static final float FOV_FALL = 62f;       /* 28 mm */
    public static final float FOV_LETTER = 30f;     /* plan rapproche (19.08) */
    public static final float FOV_ECHO = 40f;

    /* horizon rule (05.11) */
    public static final float HORIZON_UPPER_THIRD = 0.333f;
    public static final float HORIZON_LOWER_THIRD = 0.667f;

    /* DOF (05.12) */
    public static final float DOF_FAR_START = 45f;
    public static final float DOF_FAR_INTENSITY = 0.6f;
    public static final float DOF_DIALOGUE_BLUR_OTHER = 0.35f;

    private final Tuning tuning;
    private final Options options;
    private final EventBus bus;
    private final Lohen lohen;

    private int mode = MODE_EXPLORE;
    private float yaw;
    private float pitch = -6f;
    private final Vec3 position = new Vec3();
    private final Vec3 target = new Vec3();
    private final Vec3 smoothPosition = new Vec3();
    private float smoothYaw, smoothPitch;
    private float fov = FOV_EXPLORE;
    private float fovCurrent = FOV_EXPLORE;
    private float springLength = DISTANCE;
    private float springTarget = DISTANCE;
    private float noInputTimer;
    private float shakeAmp;
    private float shakeFreq = 22f;
    private float shakeTime;
    private float shakeX, shakeY;
    private float horizonRule = HORIZON_UPPER_THIRD;
    private float dofFar = 0f;
    private float dofNear = 0f;
    private boolean indoor;
    private float letterPlongee = 8f;
    private final Vec3 dialogueSubject = new Vec3();
    private boolean dialogueSubjectSet;
    private float recenterProgress;
    private int frames;

    /* cinematique */
    private boolean cinematicOverride;
    private final Vec3 cinePos = new Vec3();
    private final Vec3 cineLook = new Vec3();
    private float cineFov = 46f;

    public CameraRig(Lohen lohen, Tuning tuning, Options options, EventBus bus) {
        this.lohen = lohen;
        this.tuning = tuning;
        this.options = options;
        this.bus = bus;
        this.yaw = lohen.yaw;
        this.smoothYaw = yaw;
        this.smoothPitch = pitch;
        this.smoothPosition.set(lohen.x, lohen.y + TARGET_HEIGHT, lohen.z);
    }

    /** Entree camera : deltas en pixels deja courbes par le routeur. */
    public void applyLook(float dxDeg, float dyDeg) {
        if (cinematicOverride || mode == MODE_LETTER) {
            return;
        }
        float sensX = options == null ? 1f : options.sensitivityX;
        float sensY = options == null ? 1f : options.sensitivityY;
        yaw += dxDeg * sensX;
        pitch += dyDeg * sensY;
        pitch = Maths.clamp(pitch, PITCH_MIN, PITCH_MAX);
        yaw = Maths.wrapPi(yaw);
        noInputTimer = 0f;
    }

    public void setMode(int m) {
        if (mode == m) {
            return;
        }
        mode = m;
        bus.emit(EventBus.CAMERA_MODE, m);
    }

    public int mode() {
        return mode;
    }

    public void setCinematicOverride(boolean on, Vec3 pos, Vec3 look, float fovDeg) {
        cinematicOverride = on;
        if (on) {
            cinePos.set(pos);
            cineLook.set(look);
            cineFov = fovDeg;
        }
    }

    public void setDialogueSubject(float x, float y, float z) {
        dialogueSubject.set(x, y, z);
        dialogueSubjectSet = true;
    }

    public void clearDialogueSubject() {
        dialogueSubjectSet = false;
    }

    public void setIndoor(boolean indoor) {
        if (this.indoor != indoor) {
            this.indoor = indoor;
            /* 05.11 : l'horizon change de tiers */
            horizonRule = indoor ? HORIZON_LOWER_THIRD : HORIZON_UPPER_THIRD;
            bus.emit(EventBus.HORIZON_RULE, horizonRule);
        }
    }

    public void shake(float amplitudeDeg, float freqHz, float duration) {
        float scale = options == null ? 1f : options.cameraShake;
        if (scale <= 0f) {
            return;   /* 14.08 : secousse desactivable (0-100 %) */
        }
        shakeAmp = Math.max(shakeAmp, amplitudeDeg * scale);
        shakeFreq = freqHz;
        shakeTime = Math.max(shakeTime, duration);
    }

    public void update(float dt, PhysicsWorld world, float timeSeconds) {
        frames++;
        /* 1. cible : epaule droite, hauteur 1,55 m */
        float targetX = lohen.x;
        float targetY = lohen.y + TARGET_HEIGHT;
        float targetZ = lohen.z;
        target.set(targetX, targetY, targetZ);

        if (cinematicOverride) {
            smoothPosition.set(cinePos);
            position.set(cinePos);
            fovCurrent = Maths.lerp(fovCurrent, cineFov, 1f - Maths.dampFactor(6f, dt));
            lookTowards(cineLook, dt);
            publish(dt, timeSeconds);
            return;
        }

        /* 2. recentrage automatique (08.23) : 2,5 s de course sans input */
        boolean recenterEnabled = options == null || options.autoRecenter;
        if (recenterEnabled && lohen.grounded && lohen.speed > tuning.runSpeed * 0.6f) {
            noInputTimer += dt;
            if (noInputTimer > RECENTER_AFTER) {
                float desired = lohen.yaw;
                float diff = Maths.wrapPi(desired - yaw);
                float step = (float) Math.toRadians(RECENTER_SPEED) * dt;
                if (Math.abs(diff) <= step) {
                    yaw = desired;
                } else {
                    yaw += Math.signum(diff) * step;
                }
                pitch = Maths.damp(pitch, -6f, 2f, dt);
                recenterProgress = Maths.clamp01((noInputTimer - RECENTER_AFTER) / 2f);
            }
        } else if (!lohen.grounded || lohen.speed <= tuning.runSpeed * 0.6f) {
            noInputTimer = 0f;
            recenterProgress = 0f;
        }

        /* 3. FOV cible selon le mode et la vitesse */
        float targetFov;
        switch (mode) {
            case MODE_DIALOGUE:
                targetFov = FOV_DIALOGUE;
                break;
            case MODE_LETTER:
                targetFov = FOV_LETTER;
                break;
            case MODE_ECHO:
                targetFov = FOV_ECHO;
                break;
            case MODE_FALL:
                targetFov = FOV_FALL;
                break;
            case MODE_CINEMATIC:
                targetFov = cineFov;
                break;
            default:
                targetFov = FOV_EXPLORE;
                break;
        }
        if (mode == MODE_EXPLORE || mode == MODE_COMBAT) {
            /* +6 deg a vitesse max, interpole sur 0,8 s (08.23) */
            float speedFrac = Maths.clamp01(lohen.speed / tuning.sprintSpeed);
            targetFov += FOV_SPEED_BONUS * speedFrac;
        }
        if (!lohen.grounded && lohen.vy < -6f && mode != MODE_LETTER) {
            targetFov = Math.max(targetFov, FOV_FALL);   /* chute libre : 28 mm */
        }
        if (lohen.grappleAttached && lohen.cableLength > 14f) {
            targetFov = Math.max(targetFov, FOV_FALL);   /* grappin long */
        }
        fov = targetFov;
        /* transition ease_out_cubic sur 0,8 s, jamais instantanee (05.10) */
        float t = Maths.clamp01(dt / FOV_LERP);
        fovCurrent += (fov - fovCurrent) * Maths.easeOutCubic(t);

        /* 4. position : derriere Lohen, offset epaule droite */
        float shoulder = SHOULDER_OFFSET;
        if (mode == MODE_LETTER) {
            shoulder = SHOULDER_OFFSET;      /* par-dessus l'epaule droite (19.08) */
        }
        float cosP = (float) Math.cos(pitch * Maths.RAD);
        float sinP = (float) Math.sin(pitch * Maths.RAD);
        float dirX = -(float) Math.sin(yaw) * cosP;
        float dirY = -sinP;
        float dirZ = -(float) Math.cos(yaw) * cosP;

        /* SpringArm : collision rayon 0,32, rentree adoucie 0,15 s */
        float desiredLength = distanceForMode();
        springTarget = desiredLength;
        float blocked = desiredLength;
        if (world != null) {
            int[] info = new int[3];
            float hit = world.raycast(targetX, targetY, targetZ, -dirX, -dirY, -dirZ,
                    desiredLength + SPRING_RADIUS, info);
            if (!Float.isNaN(hit)) {
                blocked = Math.max(0.6f, hit - SPRING_RADIUS);
            }
        }
        if (blocked < springLength) {
            springLength = blocked;              /* rentree immediate */
        } else {
            /* sortie adoucie sur 0,15 s */
            springLength = Maths.damp(springLength, blocked, 1f / SPRING_RETURN, dt);
        }
        if (mode == MODE_LETTER) {
            springLength = Maths.damp(springLength, 0.85f, 6f, dt);
        }

        float px = targetX + dirX * springLength;
        float py = targetY + dirY * springLength;
        float pz = targetZ + dirZ * springLength;
        /* offset epaule droite, perpendiculaire au regard */
        float rightX = (float) Math.cos(yaw);
        float rightZ = -(float) Math.sin(yaw);
        px += rightX * shoulder;
        pz += rightZ * shoulder;
        if (mode == MODE_LETTER) {
            /* legerement en plongee (19.08) */
            py += 0.42f;
            pitch = Maths.damp(pitch, -letterPlongee, 4f, dt);
        }
        position.set(px, py, pz);

        /* 5. lissage : 0,12 s positionnel, 0,08 s rotationnel */
        smoothPosition.x = Maths.damp(smoothPosition.x, px, 1f / SMOOTH_POS, dt);
        smoothPosition.y = Maths.damp(smoothPosition.y, py, 1f / SMOOTH_POS, dt);
        smoothPosition.z = Maths.damp(smoothPosition.z, pz, 1f / SMOOTH_POS, dt);
        smoothYaw = Maths.dampAngle(smoothYaw, yaw, 1f / SMOOTH_ROT, dt);
        smoothPitch = Maths.damp(smoothPitch, pitch, 1f / SMOOTH_ROT, dt);

        /* 6. regle de l'horizon (05.11) : decalage vertical de la cible */
        float horizonOffset = (HORIZON_UPPER_THIRD - horizonRule) * 2.2f;
        smoothPosition.y += horizonOffset * 0.35f;

        /* 7. secousse (07.13) */
        if (shakeTime > 0f) {
            shakeTime -= dt;
            float decay = Maths.clamp01(shakeTime / 0.25f);
            shakeX = (float) Math.sin(timeSeconds * shakeFreq * Maths.TWO_PI) * shakeAmp * decay;
            shakeY = (float) Math.cos(timeSeconds * shakeFreq * Maths.TWO_PI * 0.7f)
                    * shakeAmp * decay * 0.6f;
            if (shakeTime <= 0f) {
                shakeAmp = 0f;
                shakeX = shakeY = 0f;
            }
        } else {
            shakeX = shakeY = 0f;
        }

        /* 8. DOF (05.12) : near blur DESACTIVE en gameplay, far a 45 m */
        if (mode == MODE_DIALOGUE) {
            dofNear = 0f;
            dofFar = DOF_FAR_INTENSITY * 1.4f;
        } else {
            dofNear = 0f;
            float dist = dialogueSubjectSet ? 12f : 60f;
            dofFar = Maths.clamp01((dist - DOF_FAR_START) / 60f) * DOF_FAR_INTENSITY;
        }
        publish(dt, timeSeconds);
    }

    private float distanceForMode() {
        switch (mode) {
            case MODE_DIALOGUE:
                return 2.1f;
            case MODE_LETTER:
                return 0.85f;
            case MODE_ECHO:
                return 2.8f;
            case MODE_FALL:
                return 4.2f;
            case MODE_COMBAT:
                return 3.0f;
            default:
                return DISTANCE;
        }
    }

    private void lookTowards(Vec3 look, float dt) {
        float dx = look.x - position.x;
        float dy = look.y - position.y;
        float dz = look.z - position.z;
        yaw = (float) Math.atan2(dx, dz);
        float horiz = (float) Math.sqrt(dx * dx + dz * dz);
        pitch = (float) Math.toDegrees(Math.atan2(dy, horiz));
        smoothYaw = yaw;
        smoothPitch = pitch;
    }

    private void publish(float dt, float timeSeconds) {
        bus.emit(EventBus.CAMERA_UPDATED, smoothPosition.x + shakeX * 0.01f,
                smoothPosition.y + shakeY * 0.01f, smoothPosition.z,
                smoothYaw, smoothPitch, fovCurrent, dofFar, mode);
    }

    /* ---------------- acces pour le rendu ---------------- */

    public Vec3 position() {
        return smoothPosition;
    }

    public Vec3 rawPosition() {
        return position;
    }

    public Vec3 target() {
        return target;
    }

    public float yaw() {
        return smoothYaw;
    }

    public float pitch() {
        return smoothPitch;
    }

    public float inputYaw() {
        return yaw;
    }

    public float inputPitch() {
        return pitch;
    }

    public float fov() {
        return fovCurrent;
    }

    /** Focale equivalente 35 mm (05.10) : 38 / 52 / 28 mm. */
    public float focalMm() {
        return Maths.fovForFocal35(fovCurrent);
    }

    public float dofFar() {
        return dofFar;
    }

    public float dofNear() {
        return dofNear;
    }

    public float shakeX() {
        return shakeX;
    }

    public float shakeY() {
        return shakeY;
    }

    public float horizonRule() {
        return horizonRule;
    }

    public boolean indoor() {
        return indoor;
    }

    public float springLength() {
        return springLength;
    }

    public int frames() {
        return frames;
    }

    /** Matrice vue (yaw/pitch autour de la position lissee). */
    public void viewMatrix(float[] out16) {
        com.velmora.lohen.sim.math.Mat4.lookAt(out16,
                smoothPosition.x, smoothPosition.y, smoothPosition.z,
                smoothPosition.x + (float) Math.sin(smoothYaw) * (float) Math.cos(smoothPitch * Maths.RAD),
                smoothPosition.y - (float) Math.sin(smoothPitch * Maths.RAD),
                smoothPosition.z + (float) Math.cos(smoothYaw) * (float) Math.cos(smoothPitch * Maths.RAD),
                0f, 1f, 0f);
    }

    public void projectionMatrix(float[] out16, float aspect) {
        com.velmora.lohen.sim.math.Mat4.perspective(out16, fovCurrent, aspect, 0.1f, 900f);
    }

    public void reset(float yawRad) {
        yaw = yawRad;
        smoothYaw = yawRad;
        pitch = -6f;
        smoothPitch = pitch;
        springLength = DISTANCE;
        shakeAmp = 0f;
        shakeTime = 0f;
        fovCurrent = FOV_EXPLORE;
        cinematicOverride = false;
        noInputTimer = 0f;
    }
}
