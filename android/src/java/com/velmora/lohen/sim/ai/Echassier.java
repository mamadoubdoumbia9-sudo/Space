/*
 * LOHEN — sim/ai/Echassier.java
 *
 * 02.09 : « Echassier — grande figure de verre sur echasses, lente,
 * balayages amples ». Elle ne poursuit pas : elle balaye. Son tell de 0,8 s
 * est lisible a 20 m (08.14) — le joueur a le temps de parer ou d'esquiver.
 *
 * Point faible : les echasses. Frapper pendant le stagger brise net (16.12 :
 * 47 eclats). L'Echassier ne parle jamais, il rejoue un geste de chantier.
 */
package com.velmora.lohen.sim.ai;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Rng;
import com.velmora.lohen.sim.core.Tuning;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.player.Lohen;
import com.velmora.lohen.sim.world.PhysicsWorld;

public final class Echassier extends Figure {

    public static final float SWEEP_RANGE = 3.2f;
    public static final float SWEEP_ARC_DEG = 150f;
    public static final float STILT_HEIGHT = 1.6f;

    private final Rng rng;
    private float sweepDirection = 1f;
    private int sweepCount;
    private boolean stiltBroken;
    private float legDamage;
    private int stomps;

    public Echassier(Tuning tuning, EventBus bus, Rng rng) {
        super(tuning, bus);
        this.rng = rng;
        this.height = 3.2f;
        this.radius = 0.55f;
        this.speed = 1.65f;
        this.sightRange = 38f;
        this.sightAngleDeg = 120f;
        this.hearingRange = 16f;
        this.attackRange = SWEEP_RANGE;
        this.attackDamage = 22f;
        this.attackHeavy = true;
        this.fragmentCount = 47;
        this.patrolRadius = 11f;
    }

    @Override
    public String typeName() {
        return "ECHASSIER";
    }

    @Override
    protected float defaultHealth() {
        return tuning.figureHealthEchassier;
    }

    @Override
    protected float tellDuration() {
        return tuning.tellEchassier;      /* 0,8 s — lisible a 20 m */
    }

    @Override
    protected float attackDuration() {
        return 0.75f;                     /* balayage ample, pas un snap */
    }

    @Override
    protected float attackHitMoment() {
        return 0.42f;
    }

    @Override
    protected float recoverDuration() {
        return stiltBroken ? 1.8f : 1.15f;
    }

    @Override
    protected void onTellStart(Lohen lohen) {
        sweepDirection = rng.nextFloat() < 0.5f ? 1f : -1f;
        sweepCount++;
        bus.emit(EventBus.ECHASSIER_TELL, sweepDirection, tellDuration());
    }

    @Override
    protected void onAttackStart(Lohen lohen) {
        /* le balayage fait reculer l'air : effet visuel + son (05.21, 13.28) */
        bus.emit(EventBus.ECHASSIER_SWEEP, sweepDirection);
    }

    @Override
    protected void tryHit(Lohen lohen) {
        float dx = lohen.x - x;
        float dz = lohen.z - z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > attackRange + 0.35f) {
            bus.emit(EventBus.ATTACK_MISSED, typeName());
            return;
        }
        float inv = 1f / Math.max(0.001f, dist);
        float fx = (float) Math.sin(yaw);
        float fz = (float) Math.cos(yaw);
        float dot = (dx * inv * fx + dz * inv * fz);
        if (dot < (float) Math.cos(Math.toRadians(SWEEP_ARC_DEG * 0.5f))) {
            bus.emit(EventBus.ATTACK_MISSED, typeName());
            return;
        }
        bus.emit(EventBus.ATTACK_LANDED, typeName(), attackDamage, attackHeavy);
    }

    /**
     * Les echasses encaissent separement : un coup porte bas accumule des
     * degats de patte. A 40, l'Echassier s'effondre sur un genou et devient
     * vulnerable — sans QTE (08.04).
     */
    @Override
    public boolean takeHit(float damage, float fromX, float fromY, float fromZ) {
        float hitHeight = fromY - y;
        if (hitHeight < STILT_HEIGHT * 0.75f) {
            legDamage += damage * 1.4f;
        } else {
            legDamage += damage * 0.35f;
        }
        if (legDamage >= 40f && !stiltBroken) {
            stiltBroken = true;
            speed *= 0.55f;
            attackRange *= 0.75f;
            stagger(1.6f);
            bus.emit(EventBus.ECHASSIER_STILT_BROKEN);
        }
        if (staggering()) {
            damage *= 2f;                  /* fenetre de riposte (08.14) */
        }
        return super.takeHit(damage, fromX, fromY, fromZ);
    }

    /** Certains Echassiers pietinent au lieu de balayer (variante 06.14). */
    @Override
    public void update(float dt, Lohen lohen, PhysicsWorld world, AttackTokenScheduler tokens) {
        super.update(dt, lohen, world, tokens);
        if (state == ST_PURSUE && stateTime > 6f && !stiltBroken) {
            float dist = (float) Math.sqrt((lohen.x - x) * (lohen.x - x) + (lohen.z - z) * (lohen.z - z));
            if (dist < 4.5f && rng.nextFloat() < 0.02f) {
                stomps++;
                stagger(0.4f);
                bus.emit(EventBus.ECHASSIER_STOMP);
            }
        }
        /* l'Echassier est lourd : ses pas font vibrer le verre (05.21) */
        if (Math.abs(vx) + Math.abs(vz) > 0.4f && world != null) {
            float gh = world.groundHeight(x, z, y, radius, null);
            if (!Float.isNaN(gh) && Math.abs(y - gh) < 0.2f) {
                bus.emit(EventBus.GLASS_VIBRATION, x, y, z, 0.35f);
            }
        }
    }

    public boolean stiltBroken() {
        return stiltBroken;
    }

    public float legDamage() {
        return legDamage;
    }

    public int sweepCount() {
        return sweepCount;
    }

    public int stomps() {
        return stomps;
    }

    public float sweepDirection() {
        return sweepDirection;
    }

    @Override
    protected void shatter() {
        super.shatter();
        bus.emit(EventBus.GLASS_SHATTER_BURST, x, y + height * 0.5f, z, fragmentCount);
    }

    /** Le balayage se lit aussi dans l'animation : inclinaison du buste. */
    public float sweepLean() {
        if (state == ST_TELL) {
            return Maths.lerp(0f, 14f * sweepDirection, tellProgress());
        }
        if (state == ST_ATTACK) {
            float t = Maths.clamp01(stateTime / attackDuration());
            return 14f * sweepDirection * (1f - t);
        }
        return 0f;
    }
}
