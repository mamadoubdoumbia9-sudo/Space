/*
 * LOHEN — sim/player/LedgeScanner.java
 *
 * 08.07 : la grimpette n'est pas un test ponctuel mais un LedgeScanner
 * permanent (fenetre de 0,35 s, capsule elargie de 20 cm). Les surfaces
 * grimpables portent un LedgeVolume declare ; Lohen s'y accroche
 * AUTOMATIQUEMENT si le saut passe a portee.
 *
 * 07.09 : une prise est trouvée en 0,35 s, jamais un scan de 3 secondes.
 */
package com.velmora.lohen.sim.player;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Tuning;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.world.PhysicsWorld;

public final class LedgeScanner {

    private final Lohen lohen;
    private final Tuning tuning;
    private final EventBus bus;

    private final float[] ledgePos = new float[4];
    private float scanWindow;          /* fenetre restante apres un saut */
    private int currentLedge = -1;
    private float shimmyOffset;
    private float climbTimer;
    private float climbDuration;
    private int grabs;
    private int shimmyEvents;
    private float bestScanTime = Float.MAX_VALUE;
    private float worstScanTime;
    private float accumScan;
    private int scanCount;

    public LedgeScanner(Lohen lohen, Tuning tuning, EventBus bus) {
        this.lohen = lohen;
        this.tuning = tuning;
        this.bus = bus;
    }

    public void update(float dt, PhysicsWorld world) {
        long t0 = System.nanoTime();
        if (scanWindow > 0f) {
            scanWindow -= dt;
        }
        int state = lohen.state;
        float dirX = lohen.vx;
        float dirZ = lohen.vz;
        float dl = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (dl < 0.3f) {
            dirX = (float) Math.sin(lohen.yaw);
            dirZ = (float) Math.cos(lohen.yaw);
        } else {
            dirX /= dl;
            dirZ /= dl;
        }

        if (state == PlayerFsm.ST_LEDGE_HANG || state == PlayerFsm.ST_LEDGE_SHIMMY) {
            maintainHang(dt, world);
        } else if (state == PlayerFsm.ST_LEDGE_CLIMB) {
            doClimb(dt);
        } else if (!lohen.grounded && scanWindow > 0f
                && (state == PlayerFsm.ST_JUMP || state == PlayerFsm.ST_FALL)) {
            /* capsule elargie de 20 cm : fenetre genéreuse (08.05, 08.07) */
            float reachMin = tuning.ledgeReachMin;
            float reachMax = tuning.ledgeReachMax;
            int idx = world.findLedge(lohen.x, lohen.y + Lohen.HIP_HEIGHT, lohen.z,
                    dirX, dirZ, reachMin, reachMax, tuning.ledgeCapsuleWiden, ledgePos);
            if (idx >= 0) {
                attach(idx, world);
            }
        }
        long elapsed = System.nanoTime() - t0;
        accumScan += elapsed / 1e6f;
        scanCount++;
        float ms = elapsed / 1e6f;
        if (ms < bestScanTime) {
            bestScanTime = ms;
        }
        if (ms > worstScanTime) {
            worstScanTime = ms;
        }
    }

    /** Le saut ouvre la fenetre de 0,35 s (08.05). */
    public void onJump() {
        scanWindow = tuning.ledgeGrabWindow;
    }

    public void onFallStart() {
        scanWindow = Math.max(scanWindow, tuning.ledgeGrabWindow);
    }

    private void attach(int idx, PhysicsWorld world) {
        currentLedge = idx;
        lohen.ledgeIndex = idx;
        lohen.ledgeX = ledgePos[0];
        lohen.ledgeY = ledgePos[1];
        lohen.ledgeZ = ledgePos[2];
        lohen.ledgeYaw = ledgePos[3];
        shimmyOffset = 0f;
        /* Lohen se colle sous la prise, pieds contre la paroi */
        float nx = (float) Math.sin(ledgePos[3]);
        float nz = (float) Math.cos(ledgePos[3]);
        lohen.x = ledgePos[0] - nx * (Lohen.RADIUS + 0.06f);
        lohen.z = ledgePos[2] - nz * (Lohen.RADIUS + 0.06f);
        lohen.y = ledgePos[1] - Lohen.SHOULDER_HEIGHT;
        lohen.vy = 0f;
        lohen.vx *= 0.15f;
        lohen.vz *= 0.15f;
        lohen.yaw = ledgePos[3];
        lohen.grounded = false;
        scanWindow = 0f;
        grabs++;
        bus.emit(EventBus.LEDGE_FOUND, idx);
        bus.emit(EventBus.LEDGE_GRABBED, idx);
    }

    private void maintainHang(float dt, PhysicsWorld world) {
        float lx = world.ledgeX(currentLedge);
        float lz = world.ledgeZ(currentLedge);
        float yaw = world.ledgeYaw(currentLedge);
        float len = world.ledgeLength(currentLedge);
        float hx = (float) Math.cos(yaw) * len * 0.5f;
        float hz = (float) Math.sin(yaw) * len * 0.5f;
        /* 08.07 : le shimmy suit le mouvement du stick, pas la gravite */
        float lateral = lohen.inputX;
        if (Math.abs(lateral) > 0.15f) {
            shimmyOffset += lateral * 1.1f * dt;
            if (lohen.state != PlayerFsm.ST_LEDGE_SHIMMY) {
                shimmyEvents++;
                bus.emit(EventBus.LEDGE_SHIMMY);
            }
        } else if (lohen.state == PlayerFsm.ST_LEDGE_SHIMMY) {
            bus.emit(EventBus.LEDGE_RELEASED, currentLedge);
        }
        float maxOffset = len * 0.5f - 0.25f;
        if (Math.abs(shimmyOffset) > maxOffset) {
            shimmyOffset = Maths.clamp(shimmyOffset, -maxOffset, maxOffset);
            /* arrive au bout de la prise : Lohen glisse */
            if (Math.abs(lateral) > 0.15f) {
                release();
                return;
            }
        }
        lohen.ledgeShimmy = shimmyOffset;
        lohen.x = lx + hx * (shimmyOffset / Math.max(0.001f, len * 0.5f)) - (float) Math.sin(yaw) * 0.36f;
        lohen.z = lz + hz * (shimmyOffset / Math.max(0.001f, len * 0.5f)) - (float) Math.cos(yaw) * 0.36f;
        lohen.y = world.ledgeY(currentLedge) - Lohen.SHOULDER_HEIGHT;
        lohen.vy = 0f;
        lohen.vx = 0f;
        lohen.vz = 0f;
        lohen.yaw = yaw;
        /* suspendu : le Souffle ne se regenere pas, il decroit lentement */
        lohen.breath.drain(1.6f, dt);
        /* 08.07 : une prise n'est jamais un cul-de-sac — le joueur peut lacher */
        if (lohen.dodgeBuffered) {
            release();
        }
    }

    public void release() {
        if (currentLedge < 0) {
            return;
        }
        bus.emit(EventBus.LEDGE_RELEASED, currentLedge);
        currentLedge = -1;
        lohen.ledgeIndex = -1;
        lohen.vy = -1.2f;
    }

    private void doClimb(float dt) {
        if (climbDuration <= 0f) {
            climbDuration = lohen.exhausted() ? tuning.ledgeClimbExhausted : tuning.ledgeClimbFast;
            climbTimer = 0f;
            bus.emit(EventBus.LEDGE_CLIMB_BEGIN, climbDuration);
        }
        climbTimer += dt;
        float t = Maths.clamp01(climbTimer / climbDuration);
        /* interpolation du bassin de la prise au sommet (07.07) */
        float topY = lohen.ledgeY + 0.05f;
        float startY = lohen.ledgeY - Lohen.SHOULDER_HEIGHT;
        lohen.y = Maths.lerp(startY, topY, Maths.smoothstep(t));
        lohen.x = Maths.lerp(lohen.x, lohen.ledgeX, 0.12f);
        lohen.z = Maths.lerp(lohen.z, lohen.ledgeZ, 0.12f);
        lohen.climbProgress = t;
        lohen.vy = 0f;
        if (t >= 1f) {
            climbDuration = 0f;
            climbTimer = 0f;
            lohen.climbProgress = 0f;
            currentLedge = -1;
            lohen.ledgeIndex = -1;
            lohen.grounded = true;
            bus.emit(EventBus.LEDGE_MOUNTED);
        }
    }

    public void reset() {
        currentLedge = -1;
        lohen.ledgeIndex = -1;
        scanWindow = 0f;
        shimmyOffset = 0f;
        climbTimer = 0f;
        climbDuration = 0f;
    }

    public int currentLedge() {
        return currentLedge;
    }

    public boolean hanging() {
        return currentLedge >= 0
                && (lohen.state == PlayerFsm.ST_LEDGE_HANG || lohen.state == PlayerFsm.ST_LEDGE_SHIMMY);
    }

    public int grabs() {
        return grabs;
    }

    public int shimmyEvents() {
        return shimmyEvents;
    }

    public float scanWindow() {
        return scanWindow;
    }

    /** Audit 18.06 : la recherche de prise doit tenir dans 0,35 s reel. */
    public float averageScanMs() {
        return scanCount == 0 ? 0f : accumScan / scanCount;
    }

    public float worstScanMs() {
        return worstScanTime;
    }
}
