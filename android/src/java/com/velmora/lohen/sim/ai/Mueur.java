/*
 * LOHEN — sim/ai/Mueur.java
 *
 * 02.09 : « Mueur — petite, rapide, se deplace par bonds imprevisibles ».
 * Le Mueur ne bloque jamais la progression : il harcele. Tell de 0,5 s
 * (08.14), degats legers, il se brise en 18 eclats.
 *
 * Son deplacement est un bond de 2,4 m toutes les 1,1 a 1,9 s, avec une
 * direction tiree au sort MAIS jamais directement vers le joueur plus de
 * deux fois de suite (lisibilite — 08.14).
 */
package com.velmora.lohen.sim.ai;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Rng;
import com.velmora.lohen.sim.core.Tuning;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.player.Lohen;
import com.velmora.lohen.sim.world.PhysicsWorld;

public final class Mueur extends Figure {

    public static final float HOP_DISTANCE = 2.4f;
    public static final float HOP_MIN_INTERVAL = 1.1f;
    public static final float HOP_MAX_INTERVAL = 1.9f;

    private final Rng rng;
    private float hopTimer;
    private float hopInterval;
    private float hopDirX, hopDirZ;
    private boolean airborne;
    private float airTime;
    private int hops;
    private int directHops;
    private boolean lastHopDirect;
    private float chirpTimer;

    public Mueur(Tuning tuning, EventBus bus, Rng rng) {
        super(tuning, bus);
        this.rng = rng;
        this.height = 1.3f;
        this.radius = 0.34f;
        this.speed = 4.2f;
        this.sightRange = 26f;
        this.sightAngleDeg = 170f;
        this.hearingRange = 18f;
        this.attackRange = 1.6f;
        this.attackDamage = 9f;
        this.attackHeavy = false;
        this.fragmentCount = 18;
        this.patrolRadius = 6f;
        this.hopInterval = HOP_MIN_INTERVAL + rng.nextFloat() * (HOP_MAX_INTERVAL - HOP_MIN_INTERVAL);
    }

    @Override
    public String typeName() {
        return "MUEUR";
    }

    @Override
    protected float defaultHealth() {
        return tuning.figureHealthMueur;
    }

    @Override
    protected float tellDuration() {
        return tuning.tellMueur;          /* 0,5 s */
    }

    @Override
    protected float attackDuration() {
        return 0.38f;
    }

    @Override
    protected float attackHitMoment() {
        return 0.36f;
    }

    @Override
    protected float recoverDuration() {
        return 0.6f;
    }

    @Override
    protected void onTellStart(Lohen lohen) {
        bus.emit(EventBus.MUEUR_TELL, tellDuration());
    }

    @Override
    public void update(float dt, Lohen lohen, PhysicsWorld world, AttackTokenScheduler tokens) {
        super.update(dt, lohen, world, tokens);
        if (!alive) {
            return;
        }
        /* bonds imprevisibles */
        hopTimer += dt;
        if (!airborne && hopTimer >= hopInterval && (seenPlayer || state == ST_PURSUE)) {
            hop(lohen);
        }
        if (airborne) {
            airTime += dt;
            vy -= tuning.gravity * 0.55f * dt;
            if (y <= groundAt(world) && vy < 0f) {
                y = groundAt(world);
                vy = 0f;
                airborne = false;
                bus.emit(EventBus.MUEUR_LANDED, x, y, z);
            }
        }
        /* petit cri de verre periodique (13.28) */
        chirpTimer += dt;
        if (chirpTimer > 3.5f) {
            chirpTimer = 0f;
            bus.emit(EventBus.MUEUR_CHIRP, x, y, z);
        }
    }

    private float groundAt(PhysicsWorld world) {
        if (world == null) {
            return y;
        }
        float gh = world.groundHeight(x, z, y, radius, null);
        return Float.isNaN(gh) ? y : gh;
    }

    private void hop(Lohen lohen) {
        hopTimer = 0f;
        hopInterval = HOP_MIN_INTERVAL + rng.nextFloat() * (HOP_MAX_INTERVAL - HOP_MIN_INTERVAL);
        float dx = lohen.x - x;
        float dz = lohen.z - z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        boolean direct = dist > 0.4f && rng.nextFloat() < 0.55f;
        /* jamais plus de deux bonds directs consecutifs : le joueur peut lire */
        if (direct && lastHopDirect && directHops >= 2) {
            direct = false;
        }
        float angle;
        if (direct) {
            angle = (float) Math.atan2(dx, dz) + (rng.nextFloat() - 0.5f) * 0.5f;
            directHops++;
        } else {
            angle = rng.nextFloat() * Maths.TWO_PI;
            directHops = 0;
        }
        lastHopDirect = direct;
        hopDirX = (float) Math.sin(angle);
        hopDirZ = (float) Math.cos(angle);
        float hopSpeed = HOP_DISTANCE / 0.45f;
        vx = hopDirX * hopSpeed;
        vz = hopDirZ * hopSpeed;
        vy = 3.4f;
        airborne = true;
        airTime = 0f;
        hops++;
        yaw = angle;
        bus.emit(EventBus.MUEUR_HOP, x, y, z, angle);
    }

    @Override
    protected void pursue(float dt, Lohen lohen, AttackTokenScheduler tokens) {
        /* le Mueur n'approche pas en ligne droite : il bondit */
        faceTowards(dt, lohen.x, lohen.z, 5f);
        float dist = (float) Math.sqrt((lohen.x - x) * (lohen.x - x) + (lohen.z - z) * (lohen.z - z));
        if (state != ST_TELL && state != ST_ATTACK) {
            setState(ST_PURSUE);
        }
        if (dist < attackRange && !airborne) {
            vx *= Maths.dampFactor(9f, dt);
            vz *= Maths.dampFactor(9f, dt);
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

    public int hops() {
        return hops;
    }

    public boolean airborne() {
        return airborne;
    }

    public float hopDirX() {
        return hopDirX;
    }

    public float hopDirZ() {
        return hopDirZ;
    }

    @Override
    protected void shatter() {
        super.shatter();
        bus.emit(EventBus.GLASS_SHATTER_BURST, x, y + height * 0.5f, z, fragmentCount);
    }
}
