/*
 * LOHEN — sim/player/GrappleComponent.java
 *
 * 08.08 : le Grappin est LA signature. Projectile 62 m/s, portee 28 m,
 * accroche -> traction + pendule, gravite majoree de 15 % pendant le
 * balancement, liberation conserve 78 % de la vitesse.
 * Le grappin REFUSE le verre (on ne s'accroche pas a la Maree).
 * Cable : 32 segments de chatenaire, tendu sous tension (05.22).
 * Auto-aim : cone 40 deg, poids alignement 0,5 / distance 0,3 / progression 0,2.
 * Souffle : 4 par tir, 1,5/s en balancement (08.13).
 */
package com.velmora.lohen.sim.player;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Tuning;
import com.velmora.lohen.sim.math.Geom;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.world.PhysicsWorld;

public final class GrappleComponent {

    public static final int IDLE = 0;
    public static final int FLYING = 1;
    public static final int PULLING = 2;
    public static final int SWINGING = 3;
    public static final int ZIPPING = 4;

    private final Lohen lohen;
    private final Tuning tuning;
    private final EventBus bus;
    private final PlayerFsm fsm;

    private int phase = IDLE;
    private float cooldown;
    private float hookX, hookY, hookZ;
    private float hookVX, hookVY, hookVZ;
    private float hookTravel;
    private int anchorId = -1;
    private int targetPropId = -1;
    private float tension;
    private float swingTime;
    private float breathAccum;
    private int shots, attaches, refusals, releases;
    private float strainTimer;
    private float aimHintX, aimHintY, aimHintZ;
    private boolean aimHintValid;
    private int bestAnchorHint = -1;
    private float zipDistance;
    private float zipSpeed;

    /* cable : 32 segments (05.22) */
    private final float[] cable = new float[33 * 3];

    public GrappleComponent(Lohen lohen, Tuning tuning, EventBus bus, PlayerFsm fsm) {
        this.lohen = lohen;
        this.tuning = tuning;
        this.bus = bus;
        this.fsm = fsm;
    }

    /** Tentative de tir. Renvoie vrai si le projectile part. */
    public boolean fire(PhysicsWorld world, float aimX, float aimY, float aimZ) {
        if (cooldown > 0f || phase != IDLE) {
            return false;
        }
        if (lohen.state == PlayerFsm.ST_DIALOGUE || lohen.state == PlayerFsm.ST_CINEMATIC
                || lohen.state == PlayerFsm.ST_ECHO || lohen.state == PlayerFsm.ST_LETTER) {
            return false;
        }
        /* le Souffle est la seule ressource : 4 par tir (08.13) */
        if (!lohen.breath.spend(tuning.grappleBreathShot, tuning.breathRegenDelay)) {
            bus.emit(EventBus.GRAPPLE_REFUSED, "breath");
            refusals++;
            return false;
        }
        float ox = lohen.x;
        float oy = lohen.y + Lohen.SHOULDER_HEIGHT;
        float oz = lohen.z;
        /* 08.08c : le grappin refuse le verre */
        int[] info = new int[3];
        float dist = world.raycast(ox, oy, oz, aimX, aimY, aimZ, tuning.grappleRange, info);
        if (info[0] >= 0 && (info[2] & Geom.FLAG_GLASS) != 0) {
            bus.emit(EventBus.GRAPPLE_REFUSED, "glass");
            refusals++;
            cooldown = tuning.grappleCooldown;
            return false;
        }
        /* recherche d'une ancre dans le cone d'auto-aim (08.03) */
        float[] anchor = new float[4];
        int idx = world.findAnchor(ox, oy, oz, aimX, aimY, aimZ, tuning.grappleRange,
                lohen.y, anchor);
        if (idx >= 0) {
            anchorId = idx;
            targetPropId = -1;
            hookX = anchor[0];
            hookY = anchor[1];
            hookZ = anchor[2];
            float dx = hookX - ox, dy = hookY - oy, dz = hookZ - oz;
            float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            hookVX = dx / l * tuning.grappleProjectileSpeed;
            hookVY = dy / l * tuning.grappleProjectileSpeed;
            hookVZ = dz / l * tuning.grappleProjectileSpeed;
            hookTravel = 0f;
            phase = FLYING;
            lohen.grappleAnchorX = hookX;
            lohen.grappleAnchorY = hookY;
            lohen.grappleAnchorZ = hookZ;
            lohen.grappleAnchorId = idx;
            lohen.cableLength = l;
            shots++;
            bus.emit(EventBus.GRAPPLE_FIRED, l);
            fsm.forceState(PlayerFsm.ST_GRAPPLE_FLY);
            return true;
        }
        /* aucune ancre : le projectile part quand meme et retombe (feedback) */
        anchorId = -1;
        hookX = ox;
        hookY = oy;
        hookZ = oz;
        hookVX = aimX * tuning.grappleProjectileSpeed;
        hookVY = aimY * tuning.grappleProjectileSpeed;
        hookVZ = aimZ * tuning.grappleProjectileSpeed;
        hookTravel = 0f;
        phase = FLYING;
        shots++;
        bus.emit(EventBus.GRAPPLE_REFUSED, "no_anchor");
        refusals++;
        cooldown = tuning.grappleCooldown;
        return false;
    }

    public void update(float dt, PhysicsWorld world) {
        if (cooldown > 0f) {
            cooldown -= dt;
        }
        strainTimer += dt;
        if (phase == IDLE) {
            updateAimHint(world);
            return;
        }
        if (phase == FLYING) {
            hookX += hookVX * dt;
            hookY += hookVY * dt;
            hookZ += hookVZ * dt;
            hookTravel += tuning.grappleProjectileSpeed * dt;
            if (anchorId >= 0 && hookTravel >= lohen.cableLength * 0.98f) {
                attach();
            } else if (hookTravel > tuning.grappleRange) {
                miss();
            }
        } else if (phase == PULLING) {
            /* traction : rapproche Lohen de l'ancre (08.08) */
            float ox = lohen.x, oy = lohen.y + Lohen.SHOULDER_HEIGHT, oz = lohen.z;
            float dx = hookX - ox, dy = hookY - oy, dz = hookZ - oz;
            float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (l < 0.9f) {
                beginSwing();
            } else {
                float speed = tuning.grapplePullSpeed + tuning.grapplePullAccel * swingTime;
                lohen.vx = dx / l * speed;
                lohen.vy = dy / l * speed;
                lohen.vz = dz / l * speed;
                swingTime += dt;
                tension = Maths.clamp01(1f - l / tuning.grappleRange);
            }
        } else if (phase == SWINGING) {
            swing(dt);
        } else if (phase == ZIPPING) {
            zip(dt);
        }
        if (phase != IDLE) {
            buildCable();
            /* haptique de tension toutes les 400 ms (08.31) */
            if (tension > 0.55f && strainTimer > HapticPeriod.CABLE) {
                strainTimer = 0f;
                bus.emit(EventBus.CABLE_STRAIN, tension);
            }
        }
    }

    /** Astuce de visée : la meilleure ancre dans le cone est suggeree au HUD. */
    private void updateAimHint(PhysicsWorld world) {
        float ax = (float) (Math.sin(lohen.cameraYaw) * Math.cos(lohen.cameraPitch));
        float ay = (float) Math.sin(lohen.cameraPitch);
        float az = (float) (Math.cos(lohen.cameraYaw) * Math.cos(lohen.cameraPitch));
        float[] anchor = new float[4];
        int idx = world.findAnchor(lohen.x, lohen.y + Lohen.SHOULDER_HEIGHT, lohen.z,
                ax, ay, az, tuning.grappleRange, lohen.y, anchor);
        aimHintValid = idx >= 0;
        bestAnchorHint = idx;
        if (idx >= 0) {
            aimHintX = anchor[0];
            aimHintY = anchor[1];
            aimHintZ = anchor[2];
        }
    }

    private void attach() {
        phase = PULLING;
        swingTime = 0f;
        lohen.grappleAttached = true;
        lohen.grappleAnchorX = hookX;
        lohen.grappleAnchorY = hookY;
        lohen.grappleAnchorZ = hookZ;
        attaches++;
        bus.emit(EventBus.GRAPPLE_ATTACHED, anchorId);
        fsm.forceState(PlayerFsm.ST_GRAPPLE_PULL);
    }

    private void miss() {
        phase = IDLE;
        cooldown = tuning.grappleCooldown;
        lohen.grappleAttached = false;
        bus.emit(EventBus.GRAPPLE_MISSED);
    }

    private void beginSwing() {
        phase = SWINGING;
        bus.emit(EventBus.GRAPPLE_SWING_START);
        fsm.forceState(PlayerFsm.ST_GRAPPLE_SWING);
    }

    /**
     * Pendule : la vitesse est projetee sur le plan tangent au cable,
     * avec gravite majoree de 15 % (08.08).
     */
    private void swing(float dt) {
        float ox = lohen.x, oy = lohen.y + Lohen.SHOULDER_HEIGHT, oz = lohen.z;
        float dx = ox - hookX, dy = oy - hookY, dz = oz - hookZ;
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float rope = Math.max(0.8f, lohen.cableLength);
        if (l < 1e-4f) {
            l = 1e-4f;
        }
        float nx = dx / l, ny = dy / l, nz = dz / l;
        /* gravite majoree */
        lohen.vy -= tuning.gravity * tuning.pendulumGravityFactor * dt;
        /* entree du joueur : impulsion tangentielle (le joueur pompe) */
        float wishX = (float) Math.sin(lohen.cameraYaw) * lohen.inputY
                + (float) Math.cos(lohen.cameraYaw) * lohen.inputX;
        float wishZ = (float) Math.cos(lohen.cameraYaw) * lohen.inputY
                - (float) Math.sin(lohen.cameraYaw) * lohen.inputX;
        lohen.vx += wishX * 9f * dt;
        lohen.vz += wishZ * 9f * dt;
        /* projection sur le plan tangent : v -= (v.n) n */
        float vn = lohen.vx * nx + lohen.vy * ny + lohen.vz * nz;
        lohen.vx -= vn * nx;
        lohen.vy -= vn * ny;
        lohen.vz -= vn * nz;
        /* contrainte de longueur : on replace le joueur sur la sphere */
        float px = hookX + nx * rope;
        float py = hookY + ny * rope;
        float pz = hookZ + nz * rope;
        lohen.x = px;
        lohen.y = py - Lohen.SHOULDER_HEIGHT;
        lohen.z = pz;
        tension = Maths.clamp01(Math.abs(vn) / 12f);
        lohen.cableTension = tension;
        swingTime += dt;
        /* le balancement coute 1,5 de Souffle par seconde (08.13) */
        lohen.breath.drain(tuning.grappleBreathSwing, dt);
        if (swingTime > 9f) {
            release(true);   /* securite : pas de boucle infinie */
        }
    }

    /** 08.09 : les tyroliennes sont resolues par le meme composant. */
    public void startZip(PhysicsWorld world, float toX, float toY, float toZ) {
        hookX = toX;
        hookY = toY;
        hookZ = toZ;
        float dx = toX - lohen.x, dy = toY - (lohen.y + Lohen.SHOULDER_HEIGHT), dz = toZ - lohen.z;
        zipDistance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        lohen.cableLength = zipDistance;
        lohen.grappleAttached = true;
        phase = ZIPPING;
        zipSpeed = tuning.zipRideSpeed;
        bus.emit(EventBus.ZIP_STARTED, zipDistance);
        fsm.forceState(PlayerFsm.ST_GRAPPLE_ZIP);
    }

    private void zip(float dt) {
        float ox = lohen.x, oy = lohen.y + Lohen.SHOULDER_HEIGHT, oz = lohen.z;
        float dx = hookX - ox, dy = hookY - oy, dz = hookZ - oz;
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 0.6f) {
            bus.emit(EventBus.ZIP_ENDED);
            phase = IDLE;
            lohen.grappleAttached = false;
            fsm.forceState(PlayerFsm.ST_FALL);
            return;
        }
        /* deceleration en fin de course (08.09) */
        float brake = Math.min(1f, l / 6f);
        float speed = zipSpeed * (0.35f + 0.65f * brake);
        lohen.vx = dx / l * speed;
        lohen.vy = dy / l * speed;
        lohen.vz = dz / l * speed;
        tension = 0.8f;
    }

    /** Liberation : conserve 78 % de la vitesse (08.08). */
    public void release(boolean auto) {
        if (phase == IDLE) {
            return;
        }
        float keep = tuning.releaseVelocityKeep;
        lohen.vx *= keep;
        lohen.vy *= keep;
        lohen.vz *= keep;
        phase = IDLE;
        lohen.grappleAttached = false;
        lohen.grappleAnchorId = -1;
        lohen.cableTension = 0f;
        anchorId = -1;
        cooldown = tuning.grappleCooldown;
        releases++;
        bus.emit(auto ? EventBus.GRAPPLE_TIMEOUT : EventBus.GRAPPLE_RELEASED,
                lohen.vx, lohen.vy, lohen.vz);
        if (lohen.state == PlayerFsm.ST_GRAPPLE_SWING || lohen.state == PlayerFsm.ST_GRAPPLE_PULL
                || lohen.state == PlayerFsm.ST_GRAPPLE_FLY || lohen.state == PlayerFsm.ST_GRAPPLE_ZIP) {
            fsm.forceState(PlayerFsm.ST_FALL);
        }
    }

    /** Cable en chatenaire : 32 segments, tendu sous tension (05.22). */
    private void buildCable() {
        int segments = tuning.cableSegments;
        float handX = lohen.x + (float) Math.sin(lohen.yaw) * 0.18f;
        float handY = lohen.y + Lohen.SHOULDER_HEIGHT - 0.05f;
        float handZ = lohen.z + (float) Math.cos(lohen.yaw) * 0.18f;
        float dx = hookX - handX;
        float dy = hookY - handY;
        float dz = hookZ - handZ;
        float span = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        /* fleche de la chatenaire : 1,1 m au repos, 0 sous tension */
        float sag = 1.1f * (1f - Maths.clamp01(tension));
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float x = handX + dx * t;
            float y = handY + dy * t;
            float z = handZ + dz * t;
            /* parabole de chatenaire approximee */
            y -= sag * 4f * t * (1f - t);
            cable[i * 3] = x;
            cable[i * 3 + 1] = y;
            cable[i * 3 + 2] = z;
        }
        lohen.cableLength = span;
    }

    public float[] cablePoints() {
        return cable;
    }

    public int cablePointCount() {
        return tuning.cableSegments + 1;
    }

    public int phase() {
        return phase;
    }

    public boolean active() {
        return phase != IDLE;
    }

    public float tension() {
        return tension;
    }

    public float hookX() {
        return hookX;
    }

    public float hookY() {
        return hookY;
    }

    public float hookZ() {
        return hookZ;
    }

    public int anchorId() {
        return anchorId;
    }

    public boolean aimHintValid() {
        return aimHintValid;
    }

    public float aimHintX() {
        return aimHintX;
    }

    public float aimHintY() {
        return aimHintY;
    }

    public float aimHintZ() {
        return aimHintZ;
    }

    public int shots() {
        return shots;
    }

    public int attaches() {
        return attaches;
    }

    public int refusals() {
        return refusals;
    }

    public int releases() {
        return releases;
    }

    public float cooldown() {
        return cooldown;
    }

    public void reset() {
        phase = IDLE;
        cooldown = 0f;
        tension = 0f;
        swingTime = 0f;
        anchorId = -1;
        lohen.grappleAttached = false;
        lohen.cableTension = 0f;
        strainTimer = 0f;
    }

    /** Constantes haptiques partagees (08.31). */
    public static final class HapticPeriod {
        public static final float CABLE = 0.4f;
    }
}
