/*
 * LOHEN — sim/ai/Figure.java
 *
 * Les Figures : ce ne sont PAS des ennemis, ce sont des restes (02.09, 06.14).
 * Elles ne parlent jamais, elles rejouent. Lohen ne les tue pas : il les brise.
 *
 * Classe de base : perception (vue + ouie), annonce (tell) obligatoire avant
 * chaque attaque (08.14 : le joueur lit l'intention, jamais un QTE),
 * stagger apres parade, fragmentation en eclats de verre.
 */
package com.velmora.lohen.sim.ai;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Tuning;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.player.Lohen;
import com.velmora.lohen.sim.world.PhysicsWorld;

public abstract class Figure {

    public static final int ST_DORMANT = 0;
    public static final int ST_PATROL = 1;
    public static final int ST_ALERT = 2;
    public static final int ST_PURSUE = 3;
    public static final int ST_TELL = 4;
    public static final int ST_ATTACK = 5;
    public static final int ST_RECOVER = 6;
    public static final int ST_STAGGER = 7;
    public static final int ST_SHATTERED = 8;
    public static final int ST_PEACEFUL = 9;

    protected final Tuning tuning;
    protected final EventBus bus;

    protected float x, y, z;
    protected float vx, vy, vz;
    protected float yaw;
    protected float health;
    protected float maxHealth;
    protected float radius = 0.45f;
    protected float height = 2.4f;
    protected int state = ST_DORMANT;
    protected float stateTime;
    protected float staggerTime;
    protected float tellTime;
    protected float attackTime;
    protected float recoverTime;
    protected boolean attackLanded;
    protected float sightRange = 34f;
    protected float sightAngleDeg = 130f;
    protected float hearingRange = 12f;
    protected float attackRange = 2.4f;
    protected float attackDamage = 14f;
    protected boolean attackHeavy;
    protected float speed = 2.2f;
    protected boolean alive = true;
    protected boolean hasAttackToken;
    protected float patrolCenterX, patrolCenterZ, patrolRadius = 8f;
    protected float patrolAngle;
    protected int id;
    protected String instanceId = "";
    protected boolean seenPlayer;
    protected float lastKnownX, lastKnownY, lastKnownZ;
    protected float alertDecay = 8f;
    protected float alertTimer;
    protected int hitsReceived;
    protected float aggression = 1f;
    /* fragmentation (16.12) */
    protected int fragmentCount = 47;

    protected Figure(Tuning tuning, EventBus bus) {
        this.tuning = tuning;
        this.bus = bus;
    }

    public abstract String typeName();

    /** Configure depuis les donnees du niveau (content/levels/*.json). */
    public void configure(int id, String instanceId, float x, float y, float z, float yawDeg) {
        this.id = id;
        this.instanceId = instanceId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = (float) Math.toRadians(yawDeg);
        this.patrolCenterX = x;
        this.patrolCenterZ = z;
        this.maxHealth = defaultHealth();
        this.health = maxHealth;
        this.alive = true;
        this.state = ST_DORMANT;
    }

    protected float defaultHealth() {
        return tuning.figureHealthEchassier;
    }

    public void update(float dt, Lohen lohen, PhysicsWorld world, AttackTokenScheduler tokens) {
        stateTime += dt;
        if (!alive) {
            return;
        }
        if (staggerTime > 0f) {
            staggerTime -= dt;
            if (state != ST_STAGGER) {
                setState(ST_STAGGER);
            }
            vx *= Maths.dampFactor(6f, dt);
            vz *= Maths.dampFactor(6f, dt);
            integrate(dt);
            if (staggerTime <= 0f) {
                setState(ST_RECOVER);
                recoverTime = 0.5f;
            }
            return;
        }
        switch (state) {
            case ST_STAGGER:
                break;
            case ST_SHATTERED:
            case ST_PEACEFUL:
                peacefulUpdate(dt, lohen);
                return;
            default:
                break;
        }
        /* perception : vue + ouie, jamais de vision 360 deg (08.14) */
        boolean sees = perceive(lohen, world);
        if (sees) {
            lastKnownX = lohen.x;
            lastKnownY = lohen.y;
            lastKnownZ = lohen.z;
            alertTimer = alertDecay;
            seenPlayer = true;
        } else if (alertTimer > 0f) {
            alertTimer -= dt;
            if (alertTimer <= 0f) {
                seenPlayer = false;
            }
        }
        if (state == ST_TELL) {
            tellTime -= dt;
            faceTowards(dt, lastKnownX, lastKnownZ, 6f);
            if (tellTime <= 0f) {
                setState(ST_ATTACK);
                attackTime = attackDuration();
                attackLanded = false;
                onAttackStart(lohen);
            }
        } else if (state == ST_ATTACK) {
            attackTime -= dt;
            float progress = 1f - Maths.clamp01(attackTime / attackDuration());
            if (!attackLanded && progress >= attackHitMoment()) {
                attackLanded = true;
                tryHit(lohen);
            }
            if (attackTime <= 0f) {
                setState(ST_RECOVER);
                recoverTime = recoverDuration();
                releaseToken(tokens);
            }
        } else if (state == ST_RECOVER) {
            recoverTime -= dt;
            if (recoverTime <= 0f) {
                setState(seenPlayer ? ST_PURSUE : ST_PATROL);
            }
        } else if (seenPlayer) {
            pursue(dt, lohen, tokens);
        } else {
            patrol(dt);
        }
        integrate(dt);
    }

    protected void pursue(float dt, Lohen lohen, AttackTokenScheduler tokens) {
        float dx = lastKnownX - x;
        float dz = lastKnownZ - z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        faceTowards(dt, lastKnownX, lastKnownZ, 3.5f);
        if (dist > attackRange * 0.85f) {
            setState(ST_PURSUE);
            float inv = 1f / Math.max(0.001f, dist);
            vx = dx * inv * speed * aggression;
            vz = dz * inv * speed * aggression;
        } else {
            vx *= Maths.dampFactor(8f, dt);
            vz *= Maths.dampFactor(8f, dt);
            /* demande d'un jeton d'attaque (08.14 : 4 simultanees max,
               2 jetons au-dela) */
            if (tokens != null && !hasAttackToken && tokens.request(this)) {
                hasAttackToken = true;
            }
            if (hasAttackToken && state != ST_TELL) {
                setState(ST_TELL);
                tellTime = tellDuration();
                bus.emit(EventBus.ATTACK_TELL, typeName(), tellTime);
                onTellStart(lohen);
            }
        }
    }

    protected void patrol(float dt) {
        if (state != ST_PATROL) {
            setState(ST_PATROL);
        }
        patrolAngle += dt * 0.18f;
        float tx = patrolCenterX + (float) Math.cos(patrolAngle) * patrolRadius;
        float tz = patrolCenterZ + (float) Math.sin(patrolAngle) * patrolRadius;
        float dx = tx - x;
        float dz = tz - z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.4f) {
            float inv = 1f / dist;
            vx = dx * inv * speed * 0.45f;
            vz = dz * inv * speed * 0.45f;
            faceTowards(dt, tx, tz, 2f);
        } else {
            vx *= Maths.dampFactor(4f, dt);
            vz *= Maths.dampFactor(4f, dt);
        }
    }

    protected void peacefulUpdate(float dt, Lohen lohen) {
        vx *= Maths.dampFactor(3f, dt);
        vz *= Maths.dampFactor(3f, dt);
        integrate(dt);
    }

    protected void integrate(float dt) {
        x += vx * dt;
        y += vy * dt;
        z += vz * dt;
        float l = (float) Math.sqrt(vx * vx + vz * vz);
        if (l > 0.01f) {
            bus.emit(EventBus.FIGURE_MOVED, instanceId, x, y, z, yaw);
        }
    }

    protected void faceTowards(float dt, float tx, float tz, float rate) {
        float target = (float) Math.atan2(tx - x, tz - z);
        yaw = Maths.dampAngle(yaw, target, rate, dt);
    }

    /** Vue : cone de 130 deg + ligne de vue ; ouie : 12 m si le joueur court. */
    protected boolean perceive(Lohen lohen, PhysicsWorld world) {
        float dx = lohen.x - x;
        float dz = lohen.z - z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > sightRange) {
            return hearing(lohen, dist);
        }
        float inv = 1f / Math.max(0.001f, dist);
        float fx = (float) Math.sin(yaw);
        float fz = (float) Math.cos(yaw);
        float dot = dx * inv * fx + dz * inv * fz;
        if (dot < (float) Math.cos(Math.toRadians(sightAngleDeg * 0.5f))) {
            return hearing(lohen, dist);
        }
        /* ligne de vue : le verre arrete le regard (05.21) */
        if (world != null) {
            float eyeY = y + height * 0.8f;
            float toY = lohen.y + Lohen.SHOULDER_HEIGHT;
            float dy = toY - eyeY;
            int[] info = new int[3];
            float hit = world.raycast(x, eyeY, z, dx * inv, dy / Math.max(0.001f, dist), dz * inv,
                    dist, info);
            if (!Float.isNaN(hit) && hit < dist - 0.4f) {
                return false;
            }
        }
        return true;
    }

    protected boolean hearing(Lohen lohen, float dist) {
        float range = hearingRange * (lohen.speed > tuning.runSpeed * 0.9f ? 1.6f : 1f);
        if (dist > range) {
            return false;
        }
        return lohen.speed > 1.2f;
    }

    protected void tryHit(Lohen lohen) {
        float dx = lohen.x - x;
        float dz = lohen.z - z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > attackRange + lohen.speed * 0.12f) {
            bus.emit(EventBus.ATTACK_MISSED, typeName());
            return;
        }
        bus.emit(EventBus.ATTACK_LANDED, typeName(), attackDamage, attackHeavy);
    }

    protected void onAttackStart(Lohen lohen) {
    }

    protected void onTellStart(Lohen lohen) {
    }

    protected float tellDuration() {
        return tuning.tellEchassier;
    }

    protected float attackDuration() {
        return 0.45f;
    }

    protected float attackHitMoment() {
        return 0.45f;
    }

    protected float recoverDuration() {
        return 0.9f;
    }

    public void stagger(float seconds) {
        staggerTime = seconds;
        setState(ST_STAGGER);
        bus.emit(EventBus.FIGURE_STAGGERED, typeName(), seconds);
    }

    /** Renvoie vrai si la Figure se brise (16.12). */
    public boolean takeHit(float damage, float fromX, float fromY, float fromZ) {
        if (!alive) {
            return false;
        }
        health -= damage;
        hitsReceived++;
        float kx = x - fromX;
        float kz = z - fromZ;
        float l = (float) Math.sqrt(kx * kx + kz * kz);
        if (l > 1e-4f) {
            vx += kx / l * 2.4f;
            vz += kz / l * 2.4f;
        }
        if (health <= 0f) {
            shatter();
            return true;
        }
        return false;
    }

    protected void shatter() {
        alive = false;
        setState(ST_SHATTERED);
        releaseToken(null);
        bus.emit(EventBus.FIGURE_SHATTERED, typeName(), fragmentCount);
    }

    /** Phase 3 du Verrier : la Figure cesse d'attaquer (16.12). */
    public void becomePeaceful() {
        setState(ST_PEACEFUL);
        releaseToken(null);
        bus.emit(EventBus.FIGURE_PEACEFUL, typeName());
    }

    protected void releaseToken(AttackTokenScheduler tokens) {
        if (hasAttackToken) {
            hasAttackToken = false;
            if (tokens != null) {
                tokens.release(this);
            }
        }
    }

    protected void setState(int s) {
        if (state == s) {
            return;
        }
        state = s;
        stateTime = 0f;
        bus.emit(EventBus.FIGURE_STATE, instanceId, s);
    }

    /* ---------------- acces ---------------- */

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float radius() {
        return radius;
    }

    public float height() {
        return height;
    }

    public float health() {
        return health;
    }

    public float maxHealth() {
        return maxHealth;
    }

    public boolean alive() {
        return alive;
    }

    public int state() {
        return state;
    }

    public float stateTime() {
        return stateTime;
    }

    public boolean telling() {
        return state == ST_TELL;
    }

    public float tellProgress() {
        float d = tellDuration();
        return d <= 0f ? 0f : 1f - Maths.clamp01(tellTime / d);
    }

    public boolean attacking() {
        return state == ST_ATTACK;
    }

    public boolean staggering() {
        return state == ST_STAGGER;
    }

    public boolean peaceful() {
        return state == ST_PEACEFUL;
    }

    public String instanceId() {
        return instanceId;
    }

    public int id() {
        return id;
    }

    public int fragmentCount() {
        return fragmentCount;
    }

    public void setAggression(float a) {
        aggression = a;
    }

    public float aggression() {
        return aggression;
    }

    public float attackDamage() {
        return attackDamage;
    }

    public boolean attackHeavy() {
        return attackHeavy;
    }

    public void setPatrol(float cx, float cz, float radius) {
        patrolCenterX = cx;
        patrolCenterZ = cz;
        patrolRadius = radius;
    }

    public void setSight(float range, float angleDeg) {
        sightRange = range;
        sightAngleDeg = angleDeg;
    }

    public void revive() {
        alive = true;
        health = maxHealth;
        setState(ST_DORMANT);
    }
}
