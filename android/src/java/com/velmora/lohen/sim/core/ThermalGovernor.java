/*
 * LOHEN — sim/core/ThermalGovernor.java
 *
 * 18.09 : lit PowerManager.getCurrentThermalStatus (fourni par la couche
 * Android) et applique les paliers :
 *   NONE/LIGHT  : qualite cible
 *   MODERATE    : FSR -10 %, ombres -1 cascade
 *   SEVERE      : 30 fps verrouilles, volumetrique off, VFX -50 %
 *   CRITICAL    : 30 fps, resolution 0,55, notification discrete
 * Le passage entre paliers est lisse sur 3 s. Jamais de saut visible.
 *
 * 00.08 : budget thermique — apres 25 min de jeu continu en exterieur
 * (Le Marche Suspendu), la perte de framerate doit etre < 12 %.
 */
package com.velmora.lohen.sim.core;

import com.velmora.lohen.sim.math.Maths;

public final class ThermalGovernor {

    public static final int NONE = 0;
    public static final int LIGHT = 1;
    public static final int MODERATE = 2;
    public static final int SEVERE = 3;
    public static final int CRITICAL = 4;

    /** Source du statut thermique (PowerManager cote Android, simule en test). */
    public interface ThermalSource {
        int thermalStatus();

        float batteryTemperatureC();
    }

    private final PerfGovernor perf;
    private final EventBus bus;
    private ThermalSource source;
    private int level = NONE;
    private int smoothedLevel = NONE;
    private float smoothingTimer;
    private boolean notified;
    private String notificationKey = "thermal.warning";
    private float fpsAtSessionStart = -1f;
    private float fpsLossPct;
    private float outdoorTime;
    private boolean outdoor;

    public ThermalGovernor(PerfGovernor perf, EventBus bus) {
        this.perf = perf;
        this.bus = bus;
    }

    public void setSource(ThermalSource source) {
        this.source = source;
    }

    public int level() {
        return smoothedLevel;
    }

    public int rawLevel() {
        return level;
    }

    public void setOutdoor(boolean outdoor) {
        this.outdoor = outdoor;
    }

    /** 00.08 : mesure continue de la perte de framerate en exterieur. */
    public void trackOutdoorTime(float dt, float fps) {
        if (!outdoor) {
            return;
        }
        outdoorTime += dt;
        if (fpsAtSessionStart < 0f && fps > 1f) {
            fpsAtSessionStart = fps;
        }
        if (fpsAtSessionStart > 1f && fps > 1f) {
            fpsLossPct = Maths.clamp(
                    (fpsAtSessionStart - fps) / fpsAtSessionStart * 100f, 0f, 100f);
        }
    }

    public float outdoorMinutes() {
        return outdoorTime / 60f;
    }

    public float fpsLossPct() {
        return fpsLossPct;
    }

    /** 00.08 : budget thermique respecte si < 12 % de perte a 25 min. */
    public boolean thermalBudgetOk() {
        return outdoorTime < 25f * 60f || fpsLossPct < 12f;
    }

    public void update(float dt) {
        int raw = source != null ? source.thermalStatus() : NONE;
        if (raw != level) {
            level = raw;
            smoothingTimer = 0f;
            bus.emit(EventBus.THERMAL_WARNING, level);
        }
        if (smoothedLevel != level) {
            /* lissage sur 3 s : on ne bascule qu'apres 3 s dans le nouveau palier */
            smoothingTimer += dt;
            if (smoothingTimer >= 3f) {
                smoothedLevel = level;
                perf.setThermalLevel(smoothedLevel);
                if (smoothedLevel >= CRITICAL && !notified) {
                    notified = true;
                    bus.emit("thermal_notification", notificationKey);
                }
                if (smoothedLevel < CRITICAL) {
                    notified = false;
                }
            }
        } else {
            smoothingTimer = 0f;
        }
    }

    public boolean notificationPending() {
        return notified;
    }

    public void acknowledgeNotification() {
        notified = false;
    }

    /** Estimation a partir de la temperature batterie si l'API est absente. */
    public static int levelFromTemperature(float celsius) {
        if (celsius < 37f) {
            return NONE;
        }
        if (celsius < 40f) {
            return LIGHT;
        }
        if (celsius < 43f) {
            return MODERATE;
        }
        if (celsius < 46f) {
            return SEVERE;
        }
        return CRITICAL;
    }

    public void reset() {
        level = NONE;
        smoothedLevel = NONE;
        smoothingTimer = 0f;
        notified = false;
        fpsAtSessionStart = -1f;
        fpsLossPct = 0f;
        outdoorTime = 0f;
    }
}
