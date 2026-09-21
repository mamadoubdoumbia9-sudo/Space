/*
 * LOHEN — sim/player/Motor.java
 *
 * Locomotion et traversee basse : acceleration, gravite, saut, coyote time,
 * buffer de saut, atterrissages (08.05), glissades (08.20), escaliers,
 * pentes, wallrun (07.08), magnet de plateforme (08.16), correction aerienne
 * limitee a 12 deg/s, et "pas de saut si arret < 300 ms avant le vide".
 *
 * Deterministe : aucun tirage aleatoire, aucune allocation par frame.
 */
package com.velmora.lohen.sim.player;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Tuning;
import com.velmora.lohen.sim.math.Geom;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.world.PhysicsWorld;

public final class Motor {

    public static final float TERMINAL_VELOCITY = 28f;
    public static final float STEP_HEIGHT = 0.38f;
    public static final float SNAP_DISTANCE = 0.35f;
    public static final float MAX_SLOPE_WALK_DEG = 48f;
    public static final float SLIDE_MIN_DIST = 8f;
    public static final float SLIDE_MAX_DIST = 14f;

    private final Lohen lohen;
    private final Tuning tuning;
    private final EventBus bus;
    private final PlayerFsm fsm;

    private final int[] groundInfo = new int[3];
    private final float[] wallNormal = new float[3];
    private float stopSinceEdge;       /* temps depuis l'arret pres du vide */
    private boolean wasGrounded;
    private float fallStartSpeed;
    private float slideDistance;
    private float desiredYaw;
    private float wallrunTimeLeft;
    private int groundSamples;

    /**
     * 08.11 V6 — PORTER : porter modifie la locomotion (plus lent). Le
     * CarryComponent pose ce facteur ; 1 = aucune alteration.
     */
    private float speedScale = 1f;

    public Motor(Lohen lohen, Tuning tuning, EventBus bus, PlayerFsm fsm) {
        this.lohen = lohen;
        this.tuning = tuning;
        this.bus = bus;
        this.fsm = fsm;
    }

    public void update(float dt, PhysicsWorld world) {
        if (dt <= 0f) {
            return;
        }
        int state = fsm.state();
        boolean narrative = fsm.isNarrative();
        if (narrative && state != PlayerFsm.ST_CARRY) {
            lohen.vx *= Maths.dampFactor(6f, dt);
            lohen.vz *= Maths.dampFactor(6f, dt);
            lohen.vy = 0f;
            return;
        }

        /* 1. direction souhaitee dans l'espace monde (locomotion orientee camera) */
        float camYaw = lohen.cameraYaw;
        float fwdX = (float) Math.sin(camYaw);
        float fwdZ = (float) Math.cos(camYaw);
        float rightX = (float) Math.cos(camYaw);
        float rightZ = -(float) Math.sin(camYaw);
        float wishX = rightX * lohen.inputX + fwdX * lohen.inputY;
        float wishZ = rightZ * lohen.inputX + fwdZ * lohen.inputY;
        float wishLen = (float) Math.sqrt(wishX * wishX + wishZ * wishZ);
        if (wishLen > 1f) {
            wishX /= wishLen;
            wishZ /= wishLen;
            wishLen = 1f;
        }

        /* 2. vitesse cible selon l'etat et l'input (08.06) */
        float targetSpeed = targetSpeedFor(state, wishLen);
        boolean sliding = state == PlayerFsm.ST_SLIDE;
        if (sliding) {
            targetSpeed = Math.max(targetSpeed, tuning.runSpeed);
        }
        if (state == PlayerFsm.ST_GUARD) {
            targetSpeed = Math.min(targetSpeed, tuning.walkSlow);
        }
        if (state == PlayerFsm.ST_STRIKE_LIGHT || state == PlayerFsm.ST_STRIKE_HEAVY
                || state == PlayerFsm.ST_STAGGER || state == PlayerFsm.ST_DODGE) {
            targetSpeed = 0f;
        }
        if (state == PlayerFsm.ST_DODGE) {
            /* esquive : 3 m pendant 0,55 s, invulnerable 12 frames (08.14) */
            float dodgeSpeed = 3f / tuning.dodgeDuration;
            float dl = (float) Math.sqrt(lohen.dodgeX * lohen.dodgeX + lohen.dodgeZ * lohen.dodgeZ);
            if (dl < 1e-4f) {
                lohen.dodgeX = (float) Math.sin(lohen.yaw);
                lohen.dodgeZ = (float) Math.cos(lohen.yaw);
            } else {
                lohen.dodgeX /= dl;
                lohen.dodgeZ /= dl;
            }
            wishX = lohen.dodgeX;
            wishZ = lohen.dodgeZ;
            targetSpeed = dodgeSpeed;
        }
        if (state == PlayerFsm.ST_CARRY) {
            targetSpeed = Math.min(targetSpeed, tuning.walkSpeed * 0.85f);
        }

        /* 3. acceleration / friction (au sol vs en l'air) */
        float accel = lohen.grounded ? tuning.accelGround : tuning.accelAir;
        float friction = lohen.grounded ? tuning.frictionGround : 0.6f;
        if (sliding) {
            friction = 2.2f;   /* la glissade decroit sur 8-14 m */
        }
        applyHorizontal(dt, wishX, wishZ, targetSpeed, accel, friction, state);

        /* 4. orientation du corps vers la direction de course */
        if (wishLen > 0.05f && state != PlayerFsm.ST_DODGE) {
            desiredYaw = (float) Math.atan2(wishX, wishZ);
            float target = desiredYaw;
            if (state == PlayerFsm.ST_GUARD || state == PlayerFsm.ST_STRIKE_LIGHT
                    || state == PlayerFsm.ST_STRIKE_HEAVY || state == PlayerFsm.ST_LEDGE_HANG) {
                target = lohen.cameraYaw;   /* le buste suit la camera en combat */
            }
            float turnRate = lohen.grounded ? 14f : 3.5f;
            lohen.yaw = Maths.dampAngle(lohen.yaw, target, turnRate, dt);
            lohen.leanDeg = Maths.lerp(lohen.leanDeg,
                    state == PlayerFsm.ST_SPRINT ? tuning.runLeanDeg
                            : (state == PlayerFsm.ST_RUN ? tuning.runLeanDeg * 0.55f : 0f),
                    1f - Maths.dampFactor(6f, dt));
        } else {
            lohen.leanDeg = Maths.damp(lohen.leanDeg, 0f, 5f, dt);
        }

        /* 5. gravite, saut, coyote, buffer (08.05) */
        handleVertical(dt, world, state);

        /* 6. murs, wallrun, contact */
        handleWalls(dt, world, state);

        /* 7. glissade sur pentes / materiaux SLIDE (08.20, 09.21) */
        handleSlide(dt, state, world);

        /* 8. pas de course : phase et longueur de foulee (07.02) */
        updateStride(dt);

        wasGrounded = lohen.grounded;
    }

    public void setSpeedScale(float scale) {
        speedScale = scale <= 0f ? 1f : scale;
    }

    public float speedScale() {
        return speedScale;
    }

    private float targetSpeedFor(int state, float wishLen) {
        if (wishLen <= 0.001f) {
            return 0f;
        }
        return rawSpeedFor(state, wishLen) * speedScale;
    }

    private float rawSpeedFor(int state, float wishLen) {
        switch (state) {
            case PlayerFsm.ST_SPRINT:
                return tuning.sprintSpeed * wishLen;
            case PlayerFsm.ST_RUN:
                return tuning.runSpeed * wishLen;
            case PlayerFsm.ST_WALK:
                return tuning.walkSpeed * wishLen;
            case PlayerFsm.ST_JUMP:
                return tuning.runSpeed * wishLen;
            case PlayerFsm.ST_FALL:
                return tuning.runSpeed * wishLen;
            case PlayerFsm.ST_LEDGE_SHIMMY:
                return 1.1f * wishLen;      /* 08.07 : deplacement lateral lent */
            case PlayerFsm.ST_LEDGE_HANG:
                return 0f;
            case PlayerFsm.ST_GRAPPLE_SWING:
                return 0f;
            case PlayerFsm.ST_SLIDE:
                return tuning.runSpeed;
            default:
                return tuning.walkSpeed * wishLen;
        }
    }

    private void applyHorizontal(float dt, float wishX, float wishZ, float targetSpeed,
                                 float accel, float friction, int state) {
        float tx = wishX * targetSpeed;
        float tz = wishZ * targetSpeed;
        float dvx = tx - lohen.vx;
        float dvz = tz - lohen.vz;
        float dvLen = (float) Math.sqrt(dvx * dvx + dvz * dvz);
        /* correction aerienne limitee a 12 deg/s (08.16) */
        if (!lohen.grounded && state != PlayerFsm.ST_DODGE) {
            float maxDelta = (float) Math.toRadians(tuning.airCorrectionDeg) * dt
                    * Math.max(1f, lohen.speed);
            float maxLen = Math.max(maxDelta, tuning.accelAir * dt);
            if (dvLen > maxLen) {
                dvx *= maxLen / dvLen;
                dvz *= maxLen / dvLen;
                dvLen = maxLen;
            }
        }
        float step = Math.min(dvLen, accel * dt);
        if (dvLen > 1e-5f) {
            lohen.vx += dvx / dvLen * step;
            lohen.vz += dvz / dvLen * step;
        }
        /* friction quand il n'y a pas d'entree */
        if (targetSpeed <= 0.001f && state != PlayerFsm.ST_SLIDE) {
            float f = Maths.dampFactor(friction, dt);
            lohen.vx *= f;
            lohen.vz *= f;
            if (lohen.speed < 0.02f) {
                lohen.vx = 0f;
                lohen.vz = 0f;
            }
        }
        if (state == PlayerFsm.ST_SLIDE) {
            float f = Maths.dampFactor(friction, dt);
            lohen.vx *= f;
            lohen.vz *= f;
            slideDistance += lohen.speed * dt;
        }
    }

    private void handleVertical(float dt, PhysicsWorld world, int state) {
        boolean canJump = state == PlayerFsm.ST_IDLE || state == PlayerFsm.ST_WALK
                || state == PlayerFsm.ST_RUN || state == PlayerFsm.ST_SPRINT
                || state == PlayerFsm.ST_JUMP || state == PlayerFsm.ST_WALLRUN
                || state == PlayerFsm.ST_LEDGE_HANG || state == PlayerFsm.ST_LEDGE_SHIMMY;
        boolean traversalLock = state == PlayerFsm.ST_GRAPPLE_PULL
                || state == PlayerFsm.ST_GRAPPLE_SWING || state == PlayerFsm.ST_GRAPPLE_FLY;

        if (lohen.jumpBuffered && canJump && !traversalLock) {
            boolean allowed = lohen.grounded || lohen.coyote > 0f;
            /* 08.05 : si le joueur s'est arrete il y a moins de 300 ms juste
               avant le vide, le saut est refuse (anti-suicide involontaire). */
            if (allowed && !lohen.grounded && stopSinceEdge > 0f
                    && stopSinceEdge < tuning.edgeStopNoJumpMs / 1000f) {
                allowed = false;
                bus.emit(EventBus.JUMP_REFUSED_EDGE_STOP);
            }
            if (state == PlayerFsm.ST_LEDGE_HANG || state == PlayerFsm.ST_LEDGE_SHIMMY) {
                fsm.forceState(PlayerFsm.ST_LEDGE_CLIMB);
                lohen.jumpBuffered = false;
                return;
            }
            if (allowed) {
                lohen.vy = tuning.jumpVelocity;
                lohen.grounded = false;
                lohen.coyote = 0f;
                lohen.jumpBuffered = false;
                stopSinceEdge = -1f;
                bus.emit(EventBus.PLAYER_JUMPED);
                fsm.forceState(PlayerFsm.ST_JUMP);
                return;
            }
        }

        if (!lohen.grounded && !traversalLock && state != PlayerFsm.ST_LEDGE_CLIMB) {
            float g = tuning.gravity;
            if (state == PlayerFsm.ST_GRAPPLE_SWING) {
                g *= tuning.pendulumGravityFactor;   /* 08.08 : gravite majoree de 15 % */
            }
            if (state == PlayerFsm.ST_JUMP && lohen.vy > 0f) {
                g *= 0.82f;   /* montee un peu plus legere que la chute */
            }
            lohen.vy -= g * dt;
            if (lohen.vy < -TERMINAL_VELOCITY) {
                lohen.vy = -TERMINAL_VELOCITY;
            }
            /* 08.05 : au-dela de 3,5 s de chute, Lohen agite les bras */
            if (state == PlayerFsm.ST_FALL && lohen.stateTime > tuning.fallFlailAfter) {
                bus.emit(EventBus.FALL_FLAIL);
            }
        }

        /* resolution du sol */
        float gh = world.groundHeight(lohen.x, lohen.z, lohen.y, Lohen.RADIUS * 0.9f, groundInfo);
        groundSamples++;
        boolean nowGrounded = false;
        if (!Float.isNaN(gh)) {
            float feet = lohen.y;
            if (feet <= gh + SNAP_DISTANCE && lohen.vy <= 0.4f) {
                if (!lohen.grounded || feet - gh > 1e-3f) {
                    /* magnet de plateforme 0,35 m (08.16) */
                    lohen.y = gh;
                } else {
                    lohen.y = gh;
                }
                if (lohen.vy < 0f) {
                    float impact = -lohen.vy;
                    onLanding(impact, state);
                    lohen.vy = 0f;
                }
                nowGrounded = true;
            } else if (feet > gh && feet - gh < STEP_HEIGHT && lohen.vy <= 0f) {
                lohen.y = gh;
                lohen.vy = 0f;
                nowGrounded = true;
            }
        }
        if (nowGrounded) {
            lohen.grounded = true;
            lohen.coyote = tuning.coyoteTime;
            lohen.groundY = gh;
            lohen.groundMaterial = groundInfo[0];
            lohen.groundFlags = groundInfo[1];
            if (!wasGrounded) {
                bus.emit(EventBus.PLAYER_LANDED, groundInfo[0]);
            }
        } else {
            if (lohen.grounded) {
                lohen.coyote = tuning.coyoteTime;
            }
            lohen.grounded = false;
            lohen.groundY = Float.NaN;
        }

        /* le verre de la Maree : surface tiede, drain passif (09.11) */
        lohen.onGlassSea = world.isOnGlassSea(lohen.x, lohen.y, lohen.z);
        lohen.onHeatWell = world.isHeatWell(lohen.x, lohen.y, lohen.z);
        if (lohen.onGlassSea) {
            lohen.breath.glassPassiveDrain(dt);
        }
        if (lohen.onHeatWell && state != PlayerFsm.ST_DODGE) {
            lohen.breath.drain(3.5f, dt);   /* puits de chaleur : brulure */
        }
    }

    /** 08.05 : trois paliers d'atterrissage, cout de Souffle au palier dur. */
    private void onLanding(float impactSpeed, int state) {
        if (state == PlayerFsm.ST_DODGE || lohen.invulnerable) {
            return;
        }
        fallStartSpeed = impactSpeed;
        if (impactSpeed >= tuning.landHardM) {
            lohen.breath.onHardLanding();
            bus.emit(EventBus.LANDING, 2, impactSpeed);
            fsm.forceState(PlayerFsm.ST_LAND_HEAVY);
            /* 08.05 : trébuchement si vitesse d'atterrissage > 4 m/s */
            if (impactSpeed > tuning.stumbleSpeed + tuning.landHardM) {
                lohen.staggerTime = 0.7f;
            }
        } else if (impactSpeed >= tuning.landMediumM) {
            bus.emit(EventBus.LANDING, 1, impactSpeed);
            fsm.forceState(PlayerFsm.ST_LAND_SOFT);
        } else if (impactSpeed >= tuning.landSoftM) {
            bus.emit(EventBus.LANDING, 0, impactSpeed);
            fsm.forceState(PlayerFsm.ST_LAND_SOFT);
        } else {
            bus.emit(EventBus.LANDING, -1, impactSpeed);
        }
    }

    private void handleWalls(float dt, PhysicsWorld world, int state) {
        boolean traversal = state == PlayerFsm.ST_GRAPPLE_PULL || state == PlayerFsm.ST_GRAPPLE_SWING
                || state == PlayerFsm.ST_GRAPPLE_FLY || state == PlayerFsm.ST_GRAPPLE_ZIP
                || state == PlayerFsm.ST_LEDGE_HANG || state == PlayerFsm.ST_LEDGE_SHIMMY
                || state == PlayerFsm.ST_LEDGE_CLIMB;
        if (traversal) {
            lohen.wallContact = false;
            return;
        }
        float dirX = lohen.vx;
        float dirZ = lohen.vz;
        float dl = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (dl < 0.5f) {
            dirX = (float) Math.sin(lohen.yaw);
            dirZ = (float) Math.cos(lohen.yaw);
            dl = 1f;
        } else {
            dirX /= dl;
            dirZ /= dl;
        }
        boolean moved = world.resolveWalls(new float[]{lohen.x, lohen.y, lohen.z},
                Lohen.RADIUS, Lohen.HEIGHT, wallNormal);
        /* resolveWalls ne modifie pas notre position : on applique manuellement */
        float[] pos = POS;
        pos[0] = lohen.x;
        pos[1] = lohen.y;
        pos[2] = lohen.z;
        boolean contact = world.resolveWalls(pos, Lohen.RADIUS, Lohen.HEIGHT, wallNormal);
        lohen.x = pos[0];
        lohen.z = pos[2];
        lohen.wallContact = contact;
        lohen.wallNormalX = wallNormal[0];
        lohen.wallNormalZ = wallNormal[2];
        if (contact) {
            /* annule la composante de vitesse dans le mur */
            float vn = lohen.vx * wallNormal[0] + lohen.vz * wallNormal[2];
            if (vn < 0f) {
                lohen.vx -= vn * wallNormal[0];
                lohen.vz -= vn * wallNormal[2];
            }
            /* wallrun : 2,1 s maximum, coute 8 de Souffle (07.08, 08.13) */
            if (!lohen.grounded && state == PlayerFsm.ST_FALL && lohen.speed > tuning.jogSpeed
                    && wallrunTimeLeft > 0f) {
                fsm.forceState(PlayerFsm.ST_WALLRUN);
                lohen.breath.spend(tuning.wallrunBreath);
                bus.emit(EventBus.WALLRUN_START);
            }
        }
        if (state == PlayerFsm.ST_WALLRUN) {
            wallrunTimeLeft -= dt;
            lohen.vy = Math.max(lohen.vy, -1.2f);   /* le wallrun ralentit la chute */
            lohen.vy += 3.5f * dt;
            if (wallrunTimeLeft <= 0f || !contact) {
                wallrunTimeLeft = tuning.wallrunMax;
                bus.emit(EventBus.WALLRUN_END);
                fsm.forceState(PlayerFsm.ST_FALL);
            }
        } else if (lohen.grounded) {
            wallrunTimeLeft = tuning.wallrunMax;
        }
        if (moved && !contact) {
            lohen.wallContact = false;
        }
    }

    private static final float[] POS = new float[3];

    private void handleSlide(float dt, int state, PhysicsWorld world) {
        if (state == PlayerFsm.ST_SLIDE) {
            if (slideDistance >= SLIDE_MAX_DIST || lohen.speed < tuning.walkSpeed * 0.6f) {
                slideDistance = 0f;
                fsm.forceState(PlayerFsm.ST_WALK);
                bus.emit(EventBus.SLIDE_ENDED, slideDistance);
            }
            return;
        }
        slideDistance = 0f;
        /* pente forte ou materiaux SLIDE : glissade automatique (09.21) */
        if (lohen.grounded && (lohen.groundFlags & Geom.FLAG_SLIDE) != 0
                && lohen.speed > tuning.jogSpeed * 0.8f) {
            fsm.requestSlide();
            bus.emit(EventBus.SLIDE_STARTED);
        }
        /* detection du vide devant : utile a l'anti-suicide et au HUD */
        if (lohen.grounded && lohen.speed > 0.5f) {
            float ahead = 0.9f;
            float ax = lohen.x + (float) Math.sin(lohen.yaw) * ahead;
            float az = lohen.z + (float) Math.cos(lohen.yaw) * ahead;
            float gh = world.groundHeight(ax, az, lohen.y, Lohen.RADIUS, groundInfo);
            if (Float.isNaN(gh) || lohen.y - gh > 2.5f) {
                stopSinceEdge = 0f;
            } else if (stopSinceEdge >= 0f) {
                stopSinceEdge += dt;
            }
        }
    }

    /** 07.02 : la foulee est une donnee, pas un timer. */
    private void updateStride(float dt) {
        float v = lohen.grounded ? lohen.speed : 0f;
        if (v < 0.15f) {
            lohen.strideLength = Maths.damp(lohen.strideLength, 0f, 4f, dt);
            return;
        }
        /* longueur de foulee : 0,34 m au pas, 0,72 m en sprint */
        float t = Maths.clamp01((v - tuning.walkSlow) / (tuning.sprintSpeed - tuning.walkSlow));
        float stride = Maths.lerp(0.34f, 0.72f, t);
        lohen.strideLength = Maths.damp(lohen.strideLength, stride, 6f, dt);
        if (lohen.strideLength > 0.05f) {
            float prev = lohen.stridePhase;
            lohen.stridePhase += v / lohen.strideLength * dt;
            /* evenement de pose de pied a chaque demi-cycle */
            if ((int) Math.floor(lohen.stridePhase) != (int) Math.floor(prev)) {
                lohen.footfall = ((int) Math.floor(lohen.stridePhase)) & 1;
                bus.emit(EventBus.FOOTSTEP, lohen.footfall, lohen.groundMaterial, v);
            }
        }
    }

    public void reset() {
        speedScale = 1f;
        wallrunTimeLeft = tuning.wallrunMax;
        slideDistance = 0f;
        stopSinceEdge = -1f;
        wasGrounded = false;
    }

    public float fallStartSpeed() {
        return fallStartSpeed;
    }

    public int groundSamples() {
        return groundSamples;
    }
}
