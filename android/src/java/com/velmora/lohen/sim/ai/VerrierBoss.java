/*
 * LOHEN — sim/ai/VerrierBoss.java
 *
 * 09.30 : LE VERRIER — boss de la sequence 7 (altitude 34 m, place de
 * l'Ancienne Halle, arene de 42 m de diametre, pluie battante, kiosque a
 * musique au centre = zone de repit, 4 lampadaires allumes — il evite la
 * lumiere directe, pente vers l'est ou le verre a coule).
 *
 * « Pas un monstre : un homme qui n'a pas suporté ce qu'il a vu » (06.19).
 * Anselme Roux, 52 ans, maitre verrier (10.10).
 *
 * PHASE 1 (0-35 %)  : marche lentement, ne frappe qu'a moins de 3 m.
 *                     Trois attaques — balayage de la coulee (tell 1,1 s),
 *                     projection de 3 eclats (0,9 s), frappe verticale
 *                     (1,4 s, parable, ouvre 2,2 s de riposte).
 *                     Le joueur peut passer toute la phase 1 a fuir :
 *                     le Verrier ne poursuit pas au-dela de 12 m. Il attend.
 * PHASE 2 (35-70 %) : la coulee devient une seconde main independante
 *                     (chaine IK 74 os) — deux menaces simultanées ;
 *                     les Mueurs arrivent par 2 et servent de boucliers.
 * PHASE 3 (70-100 %): il cesse d'attaquer. Il MARCHE vers le joueur en
 *                     tendant la main. S'il est frappe, il encaisse.
 *                     Si le joueur arrete de frapper 4 s, il s'arrete aussi
 *                     et tend le dossier. Resolution possible SANS un coup.
 *                     Aucun indice n'est donne (8 % des joueurs le trouvent,
 *                     le jeu ne les recompense pas differemment — c'est le propos).
 *
 * DUREE CIBLE : 3 min 20 en combat normal, 1 min 10 en resolution douce.
 * MORT DU JOUEUR : reprise au debut de la phase en cours, jamais du combat.
 */
package com.velmora.lohen.sim.ai;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Rng;
import com.velmora.lohen.sim.core.Tuning;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.player.Lohen;
import com.velmora.lohen.sim.world.PhysicsWorld;

public final class VerrierBoss extends Figure {

    public static final float ARENA_RADIUS = 21f;      /* 42 m de diametre */
    public static final float KIOSK_RADIUS = 3.2f;     /* zone de repit */
    public static final float STRIKE_RANGE = 3f;       /* ne frappe qu'a -3 m */
    public static final float PURSUIT_LIMIT = 12f;     /* ne poursuit pas au-dela */
    public static final float PHASE2_AT = 0.35f;       /* 0-35 % = phase 1 */
    public static final float PHASE3_AT = 0.70f;       /* 70-100 % = phase 3 */
    public static final float PEACE_AFTER_QUIET = 4f;  /* 4 s sans coup */
    public static final float RIPOSTE_WINDOW = 2.2f;   /* apres la frappe verticale */
    public static final int SHARD_COUNT = 3;           /* projection de 3 eclats */
    public static final int SECOND_HAND_BONES = 74;    /* chaine IK de la coulee */
    public static final float TARGET_DURATION_COMBAT = 200f;   /* 3 min 20 */
    public static final float TARGET_DURATION_PEACE = 70f;     /* 1 min 10 */

    /* attaques de phase 1 */
    public static final int ATK_SWEEP = 0;      /* balayage de la coulee, 1,1 s */
    public static final int ATK_SHARDS = 1;     /* 3 eclats, 0,9 s */
    public static final int ATK_VERTICAL = 2;   /* frappe verticale, 1,4 s, parable */

    private final Rng rng;
    private int phase = 1;
    private float arenaCenterX, arenaCenterZ;
    private float homeX, homeZ;
    private int attackKind = ATK_SWEEP;
    private float riposteWindow;
    private float quietTime;
    private boolean peacefulResolution;
    private boolean dossierOffered;
    private float fightTime;
    private int strikesReceivedInPhase3;
    private int shardsThrown;
    private int sweeps;
    private int verticals;
    private float[] lampX = new float[4];
    private float[] lampZ = new float[4];
    private float[] lampRadius = new float[4];
    private int lampCount;
    /* seconde main (phase 2) */
    private boolean secondHandActive;
    private float secondHandTell;
    private float secondHandX, secondHandY, secondHandZ;
    private float secondHandPhase;
    private final float[] handChain = new float[SECOND_HAND_BONES * 3];
    /* eclats en vol */
    private final float[] shardPos = new float[SHARD_COUNT * 3];
    private final float[] shardVel = new float[SHARD_COUNT * 3];
    private final boolean[] shardAlive = new boolean[SHARD_COUNT];
    private float phaseStartTime;
    private int deaths;
    private boolean defeated;

    public VerrierBoss(Tuning tuning, EventBus bus, Rng rng) {
        super(tuning, bus);
        this.rng = rng;
        this.height = 1.86f;
        this.radius = 0.5f;
        this.speed = 1.35f;         /* il marche lentement */
        this.sightRange = 60f;      /* il voit toujours le joueur dans l'arene */
        this.sightAngleDeg = 360f;
        this.hearingRange = 40f;
        this.attackRange = STRIKE_RANGE;
        this.attackDamage = 26f;
        this.attackHeavy = true;
        this.fragmentCount = 0;     /* le Verrier ne se brise pas */
        this.maxHealth = tuning.bossHealth;
        this.health = maxHealth;
        this.alertDecay = 999f;
    }

    @Override
    public String typeName() {
        return "VERRIER";
    }

    @Override
    protected float defaultHealth() {
        return tuning.bossHealth;
    }

    public void setArena(float centerX, float centerZ, float homeX, float homeZ) {
        arenaCenterX = centerX;
        arenaCenterZ = centerZ;
        this.homeX = homeX;
        this.homeZ = homeZ;
        patrolCenterX = centerX;
        patrolCenterZ = centerZ;
        patrolRadius = ARENA_RADIUS * 0.5f;
    }

    /** Les 4 lampadaires encore allumes : le Verrier evite la lumiere directe. */
    public void setLamps(float[] xs, float[] zs, float radius) {
        lampCount = Math.min(4, xs == null ? 0 : xs.length);
        for (int i = 0; i < lampCount; i++) {
            lampX[i] = xs[i];
            lampZ[i] = zs[i];
            lampRadius[i] = radius;
        }
    }

    @Override
    public void update(float dt, Lohen lohen, PhysicsWorld world, AttackTokenScheduler tokens) {
        fightTime += dt;
        if (defeated) {
            return;
        }
        updatePhase();
        if (phase == 3) {
            updatePhase3(dt, lohen);
            return;
        }
        if (riposteWindow > 0f) {
            riposteWindow -= dt;
        }
        /* il evite la lumiere directe des lampadaires (09.30) */
        avoidLamps(dt);
        /* poursuite limitee a 12 m : sinon il attend (09.30) */
        float dxHome = x - homeX;
        float dzHome = z - homeZ;
        float distHome = (float) Math.sqrt(dxHome * dxHome + dzHome * dzHome);
        float distPlayer = (float) Math.sqrt((lohen.x - x) * (lohen.x - x) + (lohen.z - z) * (lohen.z - z));

        if (state == ST_TELL || state == ST_ATTACK || state == ST_RECOVER) {
            super.update(dt, lohen, world, tokens);
            if (state == ST_ATTACK) {
                updateAttackEffects(dt, lohen);
            }
        } else if (distPlayer > PURSUIT_LIMIT && distHome > PURSUIT_LIMIT) {
            /* il retourne a sa position et attend */
            setState(ST_PATROL);
            walkTowards(dt, homeX, homeZ, 0.75f);
            vx *= 0.9f;
            vz *= 0.9f;
        } else if (distPlayer > STRIKE_RANGE) {
            setState(ST_PURSUE);
            walkTowards(dt, lohen.x, lohen.z, 1f);
        } else {
            vx *= Maths.dampFactor(8f, dt);
            vz *= Maths.dampFactor(8f, dt);
            faceTowards(dt, lohen.x, lohen.z, 2.5f);
            if (stateTime > 0.6f) {
                chooseAttack(lohen);
            }
        }
        /* le kiosque est une zone de repit : il n'y entre pas */
        float kx = x - arenaCenterX;
        float kz = z - arenaCenterZ;
        float kd = (float) Math.sqrt(kx * kx + kz * kz);
        if (kd < KIOSK_RADIUS + radius) {
            float push = (KIOSK_RADIUS + radius - kd);
            x += kx / Math.max(0.001f, kd) * push;
            z += kz / Math.max(0.001f, kd) * push;
        }
        /* seconde main (phase 2) : menace independante */
        if (secondHandActive) {
            updateSecondHand(dt, lohen);
        }
        integrate(dt);
    }

    private void updatePhase() {
        float frac = 1f - Maths.clamp01(health / maxHealth);
        int p = frac < PHASE2_AT ? 1 : (frac < PHASE3_AT ? 2 : 3);
        if (p != phase) {
            phase = p;
            phaseStartTime = fightTime;
            bus.emit(EventBus.BOSS_PHASE, phase, frac);
            if (phase == 2) {
                secondHandActive = true;
                bus.emit(EventBus.BOSS_SUMMON, "MUEUR", 2);   /* ils arrivent par 2 */
                bus.emit(EventBus.MUSIC_STATE, "boss_phase2_percussion");
            } else if (phase == 3) {
                secondHandActive = false;
                releaseToken(null);
                staggerTime = 0f;
                bus.emit(EventBus.MUSIC_STATE, "boss_phase3_silence");
            }
        }
    }

    /**
     * PHASE 3 : il s'arrete d'attaquer et marche vers le joueur en tendant
     * la main. S'il est frappe, il encaisse. 4 s sans coup -> il tend le dossier.
     */
    private void updatePhase3(float dt, Lohen lohen) {
        if (!peacefulResolution) {
            setState(ST_PEACEFUL);
            peacefulResolution = true;
            quietTime = 0f;
            bus.emit(EventBus.BOSS_PEACE);
        }
        quietTime += dt;
        float dx = lohen.x - x;
        float dz = lohen.z - z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > 1.6f) {
            /* il MARCHE, sans agressivite */
            walkTowards(dt, lohen.x, lohen.z, 0.55f);
        } else {
            vx *= Maths.dampFactor(6f, dt);
            vz *= Maths.dampFactor(6f, dt);
            faceTowards(dt, lohen.x, lohen.z, 1.5f);
        }
        if (quietTime >= PEACE_AFTER_QUIET && !dossierOffered) {
            dossierOffered = true;
            bus.emit(EventBus.BOSS_DOSSIER_OFFERED);
            bus.emit(EventBus.SCENE_STARTED, "S7_VERRIER_FIN");
        }
        integrate(dt);
    }

    @Override
    public boolean takeHit(float damage, float fromX, float fromY, float fromZ) {
        if (defeated) {
            return false;
        }
        if (phase == 3) {
            /* il encaisse sans reagir : le combat est termine, pas la scene */
            strikesReceivedInPhase3++;
            quietTime = 0f;
            health -= damage * 0.5f;
            bus.emit(EventBus.FIGURE_HIT, typeName(), damage);
            if (health <= 0f) {
                health = 0f;
                defeated = true;
                bus.emit(EventBus.SCENE_STARTED, "S7_VERRIER_FIN");
                dossierOffered = true;
            }
            return false;
        }
        if (riposteWindow > 0f) {
            damage *= tuning.riposteFactor;   /* 2,2 s de riposte apres la verticale */
        }
        boolean shattered = super.takeHit(damage, fromX, fromY, fromZ);
        if (health <= 0f) {
            health = 0f;
            defeated = true;
            setState(ST_PEACEFUL);
            bus.emit(EventBus.BOSS_DEFEATED, fightTime, strikesReceivedInPhase3 == 0);
            bus.emit(EventBus.SCENE_STARTED, "S7_VERRIER_FIN");
        }
        return shattered;
    }

    private void chooseAttack(Lohen lohen) {
        float r = rng.nextFloat();
        if (phase == 1) {
            attackKind = r < 0.45f ? ATK_SWEEP : (r < 0.8f ? ATK_SHARDS : ATK_VERTICAL);
        } else {
            attackKind = r < 0.35f ? ATK_SWEEP : (r < 0.65f ? ATK_SHARDS : ATK_VERTICAL);
        }
        setState(ST_TELL);
        tellTime = tellFor(attackKind);
        bus.emit(EventBus.ATTACK_TELL, typeName() + ":" + attackKind, tellTime);
    }

    private float tellFor(int kind) {
        switch (kind) {
            case ATK_SWEEP:
                return 1.1f;
            case ATK_SHARDS:
                return 0.9f;
            case ATK_VERTICAL:
                return 1.4f;
            default:
                return 1.1f;
        }
    }

    @Override
    protected float tellDuration() {
        return tellFor(attackKind);
    }

    @Override
    protected float attackDuration() {
        return attackKind == ATK_VERTICAL ? 0.9f : 0.6f;
    }

    @Override
    protected float attackHitMoment() {
        return attackKind == ATK_VERTICAL ? 0.55f : 0.4f;
    }

    @Override
    protected float recoverDuration() {
        return attackKind == ATK_VERTICAL ? RIPOSTE_WINDOW : 1.1f;
    }

    @Override
    protected void onAttackStart(Lohen lohen) {
        switch (attackKind) {
            case ATK_SWEEP:
                sweeps++;
                bus.emit(EventBus.BOSS_SWEEP, x, y, z, yaw);
                break;
            case ATK_SHARDS:
                shardsThrown += SHARD_COUNT;
                launchShards(lohen);
                bus.emit(EventBus.BOSS_SHARDS, SHARD_COUNT);
                break;
            case ATK_VERTICAL:
                verticals++;
                riposteWindow = RIPOSTE_WINDOW;
                bus.emit(EventBus.BOSS_VERTICAL, yaw);
                break;
            default:
                break;
        }
    }

    private void launchShards(Lohen lohen) {
        for (int i = 0; i < SHARD_COUNT; i++) {
            float spread = (i - 1) * 0.22f;
            float dx = lohen.x - x;
            float dz = lohen.z - z;
            float l = (float) Math.sqrt(dx * dx + dz * dz);
            if (l < 0.001f) {
                l = 0.001f;
            }
            float ca = (float) Math.cos(spread);
            float sa = (float) Math.sin(spread);
            float dirX = (dx / l) * ca - (dz / l) * sa;
            float dirZ = (dx / l) * sa + (dz / l) * ca;
            float speed = 16f;
            shardPos[i * 3] = x + dirX * 0.6f;
            shardPos[i * 3 + 1] = y + 1.3f;
            shardPos[i * 3 + 2] = z + dirZ * 0.6f;
            shardVel[i * 3] = dirX * speed;
            shardVel[i * 3 + 1] = 2.2f;
            shardVel[i * 3 + 2] = dirZ * speed;
            shardAlive[i] = true;
        }
    }

    private void updateAttackEffects(float dt, Lohen lohen) {
        for (int i = 0; i < SHARD_COUNT; i++) {
            if (!shardAlive[i]) {
                continue;
            }
            shardVel[i * 3 + 1] -= tuning.gravity * dt;
            shardPos[i * 3] += shardVel[i * 3] * dt;
            shardPos[i * 3 + 1] += shardVel[i * 3 + 1] * dt;
            shardPos[i * 3 + 2] += shardVel[i * 3 + 2] * dt;
            float dx = shardPos[i * 3] - lohen.x;
            float dy = shardPos[i * 3 + 1] - (lohen.y + Lohen.HIP_HEIGHT);
            float dz = shardPos[i * 3 + 2] - lohen.z;
            if (dx * dx + dy * dy + dz * dz < 0.55f * 0.55f) {
                shardAlive[i] = false;
                bus.emit(EventBus.BOSS_SHARD_HIT, 10f);
                continue;
            }
            if (shardPos[i * 3 + 1] < y - 0.2f) {
                shardAlive[i] = false;
                bus.emit(EventBus.GLASS_VIBRATION, shardPos[i * 3], y, shardPos[i * 3 + 2], 0.6f);
            }
        }
    }

    /** Seconde main : la coulee detachée, chaine IK de 74 os (09.30). */
    private void updateSecondHand(float dt, Lohen lohen) {
        secondHandPhase += dt;
        secondHandTell -= dt;
        float targetX = lohen.x + (float) Math.sin(secondHandPhase * 0.8f) * 2.2f;
        float targetZ = lohen.z + (float) Math.cos(secondHandPhase * 0.8f) * 2.2f;
        secondHandX = Maths.damp(secondHandX, targetX, 1.6f, dt);
        secondHandZ = Maths.damp(secondHandZ, targetZ, 1.6f, dt);
        secondHandY = Maths.damp(secondHandY, lohen.y + 1.1f, 2f, dt);
        buildHandChain(lohen);
        if (secondHandTell <= 0f) {
            secondHandTell = 3.2f + rng.nextFloat() * 2.4f;
            bus.emit(EventBus.BOSS_SECOND_HAND, secondHandX, secondHandY, secondHandZ, 1.3f);
        }
    }

    /** Chaine IK : 74 os du dos du Verrier jusqu'a la main de verre. */
    private void buildHandChain(Lohen lohen) {
        float startX = x;
        float startY = y + 1.4f;
        float startZ = z;
        for (int i = 0; i < SECOND_HAND_BONES; i++) {
            float t = i / (float) (SECOND_HAND_BONES - 1);
            /* courbe de Bezier avec affaissement (la coulee est lourde) */
            float sag = (float) Math.sin(t * Math.PI) * 1.4f;
            handChain[i * 3] = Maths.lerp(startX, secondHandX, t);
            handChain[i * 3 + 1] = Maths.lerp(startY, secondHandY, t) - sag;
            handChain[i * 3 + 2] = Maths.lerp(startZ, secondHandZ, t);
        }
    }

    public float[] handChain() {
        return handChain;
    }

    public float[] shardPositions() {
        return shardPos;
    }

    public boolean shardAlive(int i) {
        return i >= 0 && i < SHARD_COUNT && shardAlive[i];
    }

    private void avoidLamps(float dt) {
        for (int i = 0; i < lampCount; i++) {
            float dx = x - lampX[i];
            float dz = z - lampZ[i];
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < lampRadius[i] && d > 0.001f) {
                float push = (lampRadius[i] - d) * 0.6f;
                x += dx / d * push;
                z += dz / d * push;
            }
        }
    }

    private void walkTowards(float dt, float tx, float tz, float speedFactor) {
        float dx = tx - x;
        float dz = tz - z;
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d < 0.05f) {
            return;
        }
        vx = dx / d * speed * speedFactor;
        vz = dz / d * speed * speedFactor;
        faceTowards(dt, tx, tz, 2f);
    }

    /** 09.30 : mort du joueur = reprise au debut de la PHASE en cours. */
    public float[] phaseResumePoint() {
        return new float[]{homeX, y, homeZ, phase, health};
    }

    public void onPlayerDeath() {
        deaths++;
        /* la sante revient au seuil de la phase en cours, pas au debut */
        float frac = phase == 1 ? 0f : (phase == 2 ? PHASE2_AT : PHASE3_AT);
        health = maxHealth * (1f - frac) + 1f;
        x = homeX;
        z = homeZ;
        vx = vz = 0f;
        setState(ST_PATROL);
        peacefulResolution = phase == 3;
        quietTime = 0f;
        bus.emit(EventBus.RESPAWN, "boss_phase_" + phase);
    }

    public int phase() {
        return phase;
    }

    public boolean peacefulResolution() {
        return peacefulResolution;
    }

    public boolean dossierOffered() {
        return dossierOffered;
    }

    public float fightTime() {
        return fightTime;
    }

    public int strikesReceivedInPhase3() {
        return strikesReceivedInPhase3;
    }

    public int deaths() {
        return deaths;
    }

    public boolean defeated() {
        return defeated;
    }

    public float riposteWindow() {
        return riposteWindow;
    }

    public boolean secondHandActive() {
        return secondHandActive;
    }

    public int sweeps() {
        return sweeps;
    }

    public int shardsThrown() {
        return shardsThrown;
    }

    public int verticals() {
        return verticals;
    }

    /** Le Verrier ne se brise jamais : il s'agenouille (12.16). */
    @Override
    protected void shatter() {
        alive = true;
        health = 0f;
        defeated = true;
        setState(ST_PEACEFUL);
        bus.emit(EventBus.BOSS_DEFEATED, fightTime, strikesReceivedInPhase3 == 0);
    }

    /** 12.16 : l'agenouillement dure 6 secondes, son coupe. */
    public float kneelDuration() {
        return 6f;
    }
}
