/*
 * LOHEN — sim/player/BreathComponent.java
 *
 * Reproduction fidele de l'exemple canonique 04.11, portee en Java natif.
 * Gere le "Souffle" de Lohen : ressource unique servant a la fois
 * d'endurance de traversee, de sante au combat et de stabilite mentale
 * face aux Echos (08.13 — UNE SEULE RESSOURCE).
 *
 * Emet   : EventBus.breath_changed, breath_broken, breath_restored
 * Ecoute : echo_started, combat_entered, combat_cleared
 */
package com.velmora.lohen.sim.player;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Tuning;

public final class BreathComponent implements EventBus.Listener {

    private final Tuning tuning;
    private final EventBus bus;

    private float max;
    private float current;
    private float regenLockedFor;
    private boolean inCombat;
    private boolean inEcho;
    private boolean collapsed;
    private float collapseTimer;
    private float sprintTimer;
    private float drainAccumulator;
    private int breaks;
    private float lowestThisSession = 100f;

    public BreathComponent(Tuning tuning, EventBus bus) {
        this.tuning = tuning;
        this.bus = bus;
        this.max = tuning.breathMax;
        this.current = max;
        if (bus != null) {
            bus.connect(EventBus.ECHO_STARTED, this);
            bus.connect(EventBus.ECHO_FINISHED, this);
            bus.connect(EventBus.COMBAT_ENTERED, this);
            bus.connect(EventBus.COMBAT_CLEARED, this);
        }
    }

    @Override
    public void onEvent(String signal, Object[] args) {
        if (EventBus.ECHO_STARTED.equals(signal)) {
            inEcho = true;              /* pendant un Echo : PAS de regeneration */
        } else if (EventBus.ECHO_FINISHED.equals(signal)) {
            inEcho = false;
        } else if (EventBus.COMBAT_ENTERED.equals(signal)) {
            inCombat = true;
            regenLockedFor = Math.max(regenLockedFor, tuning.breathRegenCombatDelay);
        } else if (EventBus.COMBAT_CLEARED.equals(signal)) {
            inCombat = false;
        }
    }

    public void update(float dt) {
        if (collapsed) {
            /* 08.13 : a 0, Lohen tombe a genoux, 3,5 s de vulnerabilite,
               puis se releve avec 35 de Souffle. */
            collapseTimer -= dt;
            if (collapseTimer <= 0f) {
                collapsed = false;
                setCurrent(tuning.breathCollapseRestore);
                bus.emit(EventBus.BREATH_RESTORED);
            }
            return;
        }
        if (inEcho) {
            return;
        }
        if (regenLockedFor > 0f) {
            regenLockedFor -= dt;
            return;
        }
        float rate = inCombat ? tuning.breathRegenCombat : tuning.breathRegen;
        setCurrent(current + rate * dt);
    }

    /** Depense ; renvoie false si le Souffle casse (08.13). */
    public boolean spend(float amount) {
        return spend(amount, 0f);
    }

    public boolean spend(float amount, float lock) {
        if (amount <= 0f) {
            return true;
        }
        if (current < amount) {
            breakBreath();
            return false;
        }
        regenLockedFor = Math.max(regenLockedFor, lock);
        setCurrent(current - amount);
        return true;
    }

    /** Depense continue (sprint, pendule, wallrun) : peut descendre a 0. */
    public void drain(float perSecond, float dt) {
        if (perSecond <= 0f || dt <= 0f) {
            return;
        }
        drainAccumulator += perSecond * dt;
        float whole = (float) Math.floor(drainAccumulator);
        if (whole >= 1f) {
            drainAccumulator -= whole;
            setCurrent(current - whole);
            if (current <= 0f) {
                breakBreath();
            }
        }
        regenLockedFor = Math.max(regenLockedFor, tuning.breathRegenDelay);
    }

    /** Sprint : 6/s au-dela de 3 s (08.13). */
    public void sprinting(boolean active, float dt) {
        if (active) {
            sprintTimer += dt;
            if (sprintTimer > tuning.breathSprintAfter) {
                drain(tuning.breathSprintPerSec, dt);
            }
        } else {
            sprintTimer = 0f;
        }
    }

    public void gain(float amount) {
        setCurrent(current + amount);
    }

    private void breakBreath() {
        if (collapsed) {
            return;
        }
        collapsed = true;
        collapseTimer = tuning.breathCollapseVulnerable;
        breaks++;
        setCurrent(0f);
        bus.emit(EventBus.BREATH_BROKEN);
    }

    /** Cout d'un Echo : -18 a -40 selon l'intensite (08.13, 11.08). */
    public boolean payEcho(float cost) {
        return spend(cost, tuning.breathRegenDelay);
    }

    public void refuseEcho() {
        /* refuser un Echo coute 0 : pas de punition (08.13) */
    }

    public void onHardLanding() {
        spend(tuning.landHardBreath);
    }

    public void onParrySuccess() {
        gain(tuning.breathParryGain);        /* recompense (08.13) */
    }

    public void onParryFail() {
        spend(tuning.breathParryFail);
    }

    public void onHitTaken(boolean heavy) {
        spend(heavy ? tuning.breathHitHeavy : tuning.breathHitLight);
    }

    /** S2 : le verre est tiede, l'air y est mauvais (09.11) : -1/s passif. */
    public void glassPassiveDrain(float dt) {
        drain(tuning.breathGlassDrain, dt);
    }

    private void setCurrent(float value) {
        float clamped = value < 0f ? 0f : (value > max ? max : value);
        if (Math.abs(clamped - current) < 1e-4f) {
            return;
        }
        current = clamped;
        if (current < lowestThisSession) {
            lowestThisSession = current;
        }
        bus.emit(EventBus.BREATH_CHANGED, current, max);
    }

    public float current() {
        return current;
    }

    public float max() {
        return max;
    }

    public float fraction() {
        return max <= 0f ? 0f : current / max;
    }

    public boolean collapsed() {
        return collapsed;
    }

    public float collapseTimer() {
        return collapseTimer;
    }

    public boolean inCombat() {
        return inCombat;
    }

    public boolean inEcho() {
        return inEcho;
    }

    public int breaks() {
        return breaks;
    }

    public float lowestThisSession() {
        return lowestThisSession;
    }

    /** 08.13 : trois augmentations narratives de +10, jamais achetees. */
    public boolean applyNarrativeUpgrade() {
        if (max >= tuning.breathMaxEnd - 0.01f) {
            return false;
        }
        max = Math.min(tuning.breathMaxEnd, max + 10f);
        setCurrent(max);
        return true;
    }

    public void restore(float maxOverride) {
        max = maxOverride;
        current = max;
        collapsed = false;
        collapseTimer = 0f;
        regenLockedFor = 0f;
        inEcho = false;
        inCombat = false;
        lowestThisSession = max;
    }

    public void setMax(float newMax) {
        this.max = newMax;
        if (current > max) {
            setCurrent(max);
        }
    }
}
