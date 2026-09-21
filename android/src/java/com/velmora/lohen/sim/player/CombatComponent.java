/*
 * LOHEN — sim/player/CombatComponent.java
 *
 * 08.14 : garde + parade 0,22 s + esquive + contre-attaque.
 * 07.12 : les coups sont des animations, pas des hitscan instantanes.
 * 07.13 : hit-stop 70/130/160 ms, camera shake 0,6 deg a 22 Hz decay 0,25 s,
 *         particules et son. AUCUN QTE (08.04), AUCUN input rythme (16.11).
 *
 * Lohen ne tue pas : il brise. Les Figures de verre se fragmentent
 * (47 eclats en S8 — 16.12).
 */
package com.velmora.lohen.sim.player;

import com.velmora.lohen.sim.ai.Figure;
import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Tuning;
import com.velmora.lohen.sim.math.Maths;

import java.util.List;

public final class CombatComponent {

    public static final float LIGHT_WINDUP = 0.16f;
    public static final float LIGHT_ACTIVE = 0.10f;
    public static final float LIGHT_RECOVERY = 0.16f;
    public static final float HEAVY_CHARGE = 1.1f;
    public static final float HEAVY_ACTIVE = 0.14f;
    public static final float HEAVY_RECOVERY = 0.32f;
    public static final float BLOCK_HOLD_MAX = 2.5f;
    public static final float STAGGER_TIME = 0.55f;
    public static final float DODGE_IFRAMES = 12f / 60f;

    private final Lohen lohen;
    private final Tuning tuning;
    private final EventBus bus;
    private final PlayerFsm fsm;

    private float guardHeldTime;
    private float parryTimer;
    private boolean parryArmed;
    private float strikeTimer;
    private int strikeKind;           /* 0 = leger, 1 = lourd */
    private boolean strikeHitApplied;
    private float comboWindow;
    private int comboCount;
    private float chargeTime;
    private float dodgeTimer;
    private float iframeTimer;
    private float blockFatigue;
    private int parries, hitsGiven, hitsTaken, dodges, shatters;
    private float lastRiposteWindow;
    private boolean riposteAvailable;
    private float cameraShakeAmp;
    private float cameraShakeTime;
    private float hitstopRequest;
    private int lightStrikes, heavyStrikes;

    public CombatComponent(Lohen lohen, Tuning tuning, EventBus bus, PlayerFsm fsm) {
        this.lohen = lohen;
        this.tuning = tuning;
        this.bus = bus;
        this.fsm = fsm;
    }

    public void update(float dt, List<Figure> figures) {
        if (hitstopRequest > 0f) {
            lohen.hitstopTime = Math.max(lohen.hitstopTime, hitstopRequest);
            hitstopRequest = 0f;
        }
        if (cameraShakeTime > 0f) {
            cameraShakeTime -= dt;
        } else {
            cameraShakeAmp = 0f;
        }
        if (comboWindow > 0f) {
            comboWindow -= dt;
            if (comboWindow <= 0f) {
                comboCount = 0;
            }
        }
        if (lastRiposteWindow > 0f) {
            lastRiposteWindow -= dt;
            if (lastRiposteWindow <= 0f) {
                riposteAvailable = false;
            }
        }
        if (iframeTimer > 0f) {
            iframeTimer -= dt;
            lohen.invulnerable = iframeTimer > 0f;
        } else {
            lohen.invulnerable = false;
        }

        int state = fsm.state();
        /* garde : 2,5 s maximum puis Lohen recule (08.14) */
        if (state == PlayerFsm.ST_GUARD) {
            guardHeldTime += dt;
            lohen.guarding = true;
            blockFatigue = Maths.clamp01(guardHeldTime / BLOCK_HOLD_MAX);
            if (guardHeldTime >= BLOCK_HOLD_MAX) {
                lohen.guarding = false;
                bus.emit(EventBus.GUARD_BROKEN);
                fsm.forceState(PlayerFsm.ST_STAGGER);
                lohen.staggerTime = STAGGER_TIME;
                guardHeldTime = 0f;
            }
            /* fenetre de parade : 0,22 s apres l'entree en garde */
            if (parryArmed) {
                parryTimer -= dt;
                lohen.parryWindowOpen = parryTimer > 0f;
                lohen.parryWindowTime = parryTimer;
                if (parryTimer <= 0f) {
                    parryArmed = false;
                }
            }
        } else {
            lohen.guarding = false;
            lohen.parryWindowOpen = false;
            guardHeldTime = 0f;
            blockFatigue = 0f;
        }

        if (state == PlayerFsm.ST_PARRY) {
            parryTimer -= dt;
            if (parryTimer <= 0f) {
                fsm.forceState(PlayerFsm.ST_GUARD);
            }
        }

        /* frappes : windup -> actif -> recouvrement (07.12) */
        if (state == PlayerFsm.ST_STRIKE_LIGHT || state == PlayerFsm.ST_STRIKE_HEAVY) {
            strikeTimer += dt;
            float windup = strikeKind == 0 ? LIGHT_WINDUP : HEAVY_CHARGE;
            float active = strikeKind == 0 ? LIGHT_ACTIVE : HEAVY_ACTIVE;
            if (!strikeHitApplied && strikeTimer >= windup && strikeTimer < windup + active) {
                strikeHitApplied = true;
                applyStrike(figures, strikeKind == 1);
            }
            float total = windup + active + (strikeKind == 0 ? LIGHT_RECOVERY : HEAVY_RECOVERY);
            if (strikeTimer >= total) {
                strikeTimer = 0f;
                strikeHitApplied = false;
            }
        } else {
            strikeTimer = 0f;
            strikeHitApplied = false;
        }

        /* charge du coup lourd */
        if (lohen.heavyBuffered && lohen.inCombat) {
            chargeTime += dt;
        }

        /* esquive : 12 frames d'invulnerabilite (08.14) */
        if (state == PlayerFsm.ST_DODGE) {
            dodgeTimer += dt;
            if (iframeTimer <= 0f) {
                iframeTimer = DODGE_IFRAMES;
            }
            if (dodgeTimer >= tuning.dodgeDuration) {
                dodgeTimer = 0f;
            }
        } else {
            dodgeTimer = 0f;
        }

        /* entree en sortie de combat : 1,8 s sans menace (08.14) */
        if (!lohen.inCombat && state != PlayerFsm.ST_COLLAPSE) {
            guardHeldTime = 0f;
        }
    }

    /** Le joueur arme la garde : ouvre la fenetre de parade de 0,22 s. */
    public void armParry() {
        parryArmed = true;
        parryTimer = tuning.parryWindow;
        lohen.parryWindowOpen = true;
        fsm.forceState(PlayerFsm.ST_PARRY);
        bus.emit(EventBus.PARRY_WINDOW_OPEN, tuning.parryWindow);
    }

    public void startStrike(boolean heavy) {
        if (!lohen.inCombat && !lohen.guardHeld) {
            return;
        }
        strikeKind = heavy ? 1 : 0;
        strikeTimer = 0f;
        strikeHitApplied = false;
        if (heavy) {
            heavyStrikes++;
            chargeTime = 0f;
            fsm.forceState(PlayerFsm.ST_STRIKE_HEAVY);
        } else {
            lightStrikes++;
            comboCount = comboWindow > 0f ? Math.min(3, comboCount + 1) : 0;
            comboWindow = 0.55f;
            fsm.forceState(PlayerFsm.ST_STRIKE_LIGHT);
        }
        bus.emit(EventBus.STRIKE_STARTED, strikeKind, comboCount);
    }

    public void startDodge(float dirX, float dirZ) {
        if (lohen.state == PlayerFsm.ST_COLLAPSE || lohen.state == PlayerFsm.ST_STAGGER) {
            return;
        }
        lohen.dodgeX = dirX;
        lohen.dodgeZ = dirZ;
        dodges++;
        fsm.forceState(PlayerFsm.ST_DODGE);
        iframeTimer = DODGE_IFRAMES;
        bus.emit(EventBus.DODGE, dirX, dirZ);
    }

    /** Applique le coup : portee 2,2 m, arc 110 deg (07.12). */
    private void applyStrike(List<Figure> figures, boolean heavy) {
        if (figures == null) {
            return;
        }
        float range = tuning.strikeRange * (heavy ? 1.15f : 1f);
        float arcCos = (float) Math.cos(Math.toRadians(tuning.strikeArcDeg * 0.5f));
        float fx = (float) Math.sin(lohen.yaw);
        float fz = (float) Math.cos(lohen.yaw);
        int hit = 0;
        for (int i = 0; i < figures.size(); i++) {
            Figure f = figures.get(i);
            if (f == null || !f.alive()) {
                continue;
            }
            float dx = f.x() - lohen.x;
            float dz = f.z() - lohen.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist > range + f.radius()) {
                continue;
            }
            float inv = 1f / Math.max(0.001f, dist);
            float dot = (dx * inv * fx + dz * inv * fz);
            if (dot < arcCos) {
                continue;
            }
            float damage = (heavy ? tuning.strikeHeavyDamage : tuning.strikeLightDamage)
                    * (riposteAvailable ? tuning.riposteFactor : 1f);
            boolean shattered = f.takeHit(damage, lohen.x, lohen.y, lohen.z);
            hitsGiven++;
            if (shattered) {
                shatters++;
                bus.emit(EventBus.FIGURE_SHATTERED, f.typeName(), tuning.shatterFragments);
            } else {
                bus.emit(EventBus.FIGURE_HIT, f.typeName(), damage);
            }
            /* triple feedback obligatoire (07.13) */
            requestHitstop(heavy ? tuning.hitstopHeavyMs : tuning.hitstopLightMs);
            requestCameraShake(tuning.shakeAmpDeg, tuning.shakeFreqHz, tuning.shakeDecay);
            bus.emit(EventBus.IMPACT_SOUND, heavy ? 1 : 0);
            riposteAvailable = false;
            hit++;
            if (hit >= 2) {
                break;   /* un coup ne traverse pas toute la foule */
            }
        }
        if (hit == 0) {
            bus.emit(EventBus.STRIKE_WHIFFED, strikeKind);
        }
    }

    /** Recoit un coup : la parade reussie change tout (08.14). */
    public void receiveAttack(Figure attacker, float damage, boolean heavy) {
        if (lohen.invulnerable || lohen.dead) {
            bus.emit(EventBus.ATTACK_DODGED, attacker == null ? "" : attacker.typeName());
            return;
        }
        if (lohen.parryWindowOpen) {
            /* PARADE REUSSIE : +6 de Souffle, l'attaquant est staggered 1,4 s,
               riposte x3 pendant 1,4 s (08.14) */
            lohen.breath.onParrySuccess();
            parryTimer = 0f;
            parryArmed = false;
            lohen.parryWindowOpen = false;
            riposteAvailable = true;
            lastRiposteWindow = tuning.parryStagger;
            parries++;
            if (attacker != null) {
                attacker.stagger(tuning.parryStagger);
            }
            requestHitstop(tuning.hitstopParryMs);
            requestCameraShake(tuning.shakeAmpDeg * 1.4f, tuning.shakeFreqHz, tuning.shakeDecay);
            bus.emit(EventBus.PARRY_SUCCESS);
            return;
        }
        if (lohen.guarding) {
            /* garde : degats reduits mais fatigue */
            lohen.breath.spend(damage * 0.35f);
            guardHeldTime += 0.6f;
            bus.emit(EventBus.BLOCKED, damage);
            requestHitstop(tuning.hitstopLightMs);
            return;
        }
        /* coup encaisse */
        hitsTaken++;
        lohen.breath.onHitTaken(heavy);
        lohen.staggerTime = STAGGER_TIME;
        fsm.forceState(PlayerFsm.ST_STAGGER);
        requestHitstop(heavy ? tuning.hitstopHeavyMs : tuning.hitstopLightMs);
        requestCameraShake(tuning.shakeAmpDeg * (heavy ? 1.8f : 1f), tuning.shakeFreqHz, tuning.shakeDecay);
        bus.emit(EventBus.PLAYER_HIT_TAKEN, damage, heavy);
        if (lohen.breath.collapsed()) {
            fsm.forceState(PlayerFsm.ST_COLLAPSE);
        }
    }

    public void requestHitstop(float ms) {
        hitstopRequest = Math.max(hitstopRequest, ms / 1000f);
    }

    public void requestCameraShake(float ampDeg, float freqHz, float decay) {
        cameraShakeAmp = Math.max(cameraShakeAmp, ampDeg);
        cameraShakeTime = decay;
    }

    public float cameraShakeAmp() {
        return cameraShakeAmp;
    }

    public float cameraShakeOffset(float timeSeconds) {
        if (cameraShakeAmp <= 0f) {
            return 0f;
        }
        return (float) Math.sin(timeSeconds * tuning.shakeFreqHz * Maths.TWO_PI) * cameraShakeAmp;
    }

    public int parries() {
        return parries;
    }

    public int hitsGiven() {
        return hitsGiven;
    }

    public int hitsTaken() {
        return hitsTaken;
    }

    public int dodges() {
        return dodges;
    }

    public int shatters() {
        return shatters;
    }

    public int lightStrikes() {
        return lightStrikes;
    }

    public int heavyStrikes() {
        return heavyStrikes;
    }

    public float blockFatigue() {
        return blockFatigue;
    }

    public boolean riposteAvailable() {
        return riposteAvailable;
    }

    public int comboCount() {
        return comboCount;
    }

    public float chargeTime() {
        return chargeTime;
    }

    public void reset() {
        guardHeldTime = 0f;
        parryTimer = 0f;
        parryArmed = false;
        strikeTimer = 0f;
        comboCount = 0;
        comboWindow = 0f;
        chargeTime = 0f;
        dodgeTimer = 0f;
        iframeTimer = 0f;
        blockFatigue = 0f;
        riposteAvailable = false;
        cameraShakeAmp = 0f;
        cameraShakeTime = 0f;
        lohen.guarding = false;
        lohen.parryWindowOpen = false;
        lohen.invulnerable = false;
    }
}
