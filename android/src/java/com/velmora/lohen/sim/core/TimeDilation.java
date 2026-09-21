/*
 * LOHEN — sim/core/TimeDilation.java
 *
 * 07.13 : hit-stop (70/130/160 ms), ralentis de Echo (11.04 : le temps
 * "respire" pendant un Echo — 0,7x), et fige cinematique.
 * 04.11 : la boucle principale est a pas fixe (1/60 s) ; la dilatation ne
 * change PAS le pas de simulation, elle change le nombre de pas joues.
 */
package com.velmora.lohen.sim.core;

public final class TimeDilation {

    public static final float FIXED_STEP = 1f / 60f;
    public static final float MAX_STEPS_PER_FRAME = 5;
    public static final float ECHO_SCALE = 0.7f;
    public static final float CINEMATIC_SCALE = 1f;
    public static final float PARRY_SCALE = 0.35f;
    public static final float PARRY_DURATION = 0.16f;
    public static final float DEATH_SCALE = 0.45f;

    private float scale = 1f;
    private float targetScale = 1f;
    private float blend = 6f;
    private float hitstop;
    private float parrySlowmo;
    private float echoSlowmo;
    private float deathSlowmo;
    private float cinematicScale = 1f;
    private float accumulator;
    private long simSteps;
    private float playedTime;

    public void setHitstop(float seconds) {
        hitstop = Math.max(hitstop, seconds);
    }

    public void setParrySlowmo(float seconds) {
        parrySlowmo = Math.max(parrySlowmo, seconds);
    }

    public void setEcho(boolean active) {
        echoSlowmo = active ? 1f : 0f;
    }

    public void setDeathSlowmo(float seconds) {
        deathSlowmo = Math.max(deathSlowmo, seconds);
    }

    public void setCinematicScale(float s) {
        cinematicScale = s;
    }

    public float scale() {
        return scale;
    }

    /** Avance la dilatation ; renvoie le dt "ressenti" pour les effets. */
    public float update(float realDt) {
        if (hitstop > 0f) {
            hitstop -= realDt;
            scale = 0f;
            return 0f;
        }
        if (deathSlowmo > 0f) {
            deathSlowmo -= realDt;
            targetScale = DEATH_SCALE;
        } else if (parrySlowmo > 0f) {
            parrySlowmo -= realDt;
            targetScale = PARRY_SCALE;
        } else if (echoSlowmo > 0f) {
            targetScale = ECHO_SCALE;
        } else {
            targetScale = cinematicScale;
        }
        scale += (targetScale - scale) * Math.min(1f, blend * realDt);
        if (Math.abs(scale - targetScale) < 0.001f) {
            scale = targetScale;
        }
        return realDt * scale;
    }

    /** Accumulateur a pas fixe : renvoie le nombre de pas a jouer. */
    public int accumulate(float scaledDt) {
        accumulator += scaledDt;
        int steps = 0;
        while (accumulator >= FIXED_STEP && steps < MAX_STEPS_PER_FRAME) {
            accumulator -= FIXED_STEP;
            steps++;
            simSteps++;
            playedTime += FIXED_STEP;
        }
        if (accumulator > FIXED_STEP * MAX_STEPS_PER_FRAME) {
            accumulator = 0f;   /* spirale de la mort : on jette le retard */
        }
        return steps;
    }

    public float accumulator() {
        return accumulator;
    }

    public float alpha() {
        return accumulator / FIXED_STEP;
    }

    public long simSteps() {
        return simSteps;
    }

    public float playedTime() {
        return playedTime;
    }

    public float hitstopRemaining() {
        return hitstop;
    }

    public void reset() {
        scale = 1f;
        targetScale = 1f;
        hitstop = 0f;
        parrySlowmo = 0f;
        echoSlowmo = 0f;
        deathSlowmo = 0f;
        cinematicScale = 1f;
        accumulator = 0f;
    }
}
