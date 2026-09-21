/*
 * LOHEN — sim/core/PerfGovernor.java
 *
 * Autoload n°5 (04.03) : mesure du frametime, ajuste FSR / ombres / VFX.
 * 02.11 : scaling_3d pilote entre 0,55 et 1,0 ; l'UI reste en resolution native.
 * 00.07 : cibles 30 fps (LOW), 45-60 (MID), 60 (HIGH) ; "stable" = p99 du
 * frametime < 1,35 x la cible ; aucune chute sous 24 fps sur 3 frames.
 * 02.23 : mode Economie (30 fps, FSR 0,65, 1 cascade, volumetrique off).
 */
package com.velmora.lohen.sim.core;

import com.velmora.lohen.sim.math.Maths;

import java.util.Arrays;

public final class PerfGovernor {

    public static final int TIER_LOW = 0;
    public static final int TIER_MID = 1;
    public static final int TIER_HIGH = 2;

    private static final int HISTORY = 240;

    private final EventBus bus;
    private final Options options;

    private final float[] frameTimes = new float[HISTORY];
    private int frameIndex;
    private int frameFill;
    private int tier = TIER_MID;
    private int autoTier = TIER_MID;
    private float renderScale = 1.0f;
    private int cascades = 2;
    private int shadowAtlas = 2048;
    private int targetFps = 60;
    private float frameBudgetMs = 16.6f;
    private int lowFrameStreak;
    private boolean economyMode;
    private boolean volumetric = true;
    private float vfxBudget = 1.0f;
    private float thermalFactor = 1.0f;   /* pilote par ThermalGovernor (18.09) */
    private long frames;
    private float accumulated;
    private float lastAverageMs;
    private float lastP99Ms;
    private int drawCalls;
    private int triangles;
    private int particles;
    private boolean qualityLockedByUser;

    public PerfGovernor(EventBus bus, Options options) {
        this.bus = bus;
        this.options = options;
        applyTier(TIER_MID, true);
    }

    /** Detection du palier au demarrage, a partir des caracteristiques device. */
    public int detectTier(long totalRamBytes, int cores, String gpuRenderer) {
        String gpu = gpuRenderer == null ? "" : gpuRenderer.toLowerCase();
        boolean weakGpu = gpu.contains("adreno 6") || gpu.contains("adreno 5")
                || gpu.contains("mali-g5") || gpu.contains("mali-t")
                || gpu.contains("powervr");
        boolean strongGpu = gpu.contains("adreno 7") || gpu.contains("adreno 8")
                || gpu.contains("mali-g7") || gpu.contains("immortalis")
                || gpu.contains("xclipse");
        long ramGb = totalRamBytes / (1024L * 1024L * 1024L);
        if (ramGb <= 4 || (weakGpu && cores <= 8)) {
            return TIER_LOW;
        }
        if (strongGpu && ramGb >= 8) {
            return TIER_HIGH;
        }
        return TIER_MID;
    }

    public void setTier(int newTier, boolean fromUser) {
        if (fromUser) {
            qualityLockedByUser = newTier != TIER_LOW || options.quality != 0;
        }
        if (newTier == tier) {
            return;
        }
        int old = tier;
        applyTier(newTier, false);
        bus.emit(EventBus.QUALITY_CHANGED, tierName(newTier), old);
    }

    public void setAutoTier(int detected) {
        autoTier = detected;
        if (options.quality == 0 && !qualityLockedByUser) {
            setTier(detected, false);
        }
    }

    private void applyTier(int newTier, boolean silent) {
        tier = newTier;
        if (newTier == TIER_LOW) {
            targetFps = 30;
            frameBudgetMs = 33.3f;
            cascades = 1;
            shadowAtlas = 1024;
            renderScale = economyMode ? 0.65f : 0.70f;
            volumetric = false;
            vfxBudget = 0.45f;
        } else if (newTier == TIER_MID) {
            targetFps = options.targetFps == 0 ? 60 : options.targetFps;
            frameBudgetMs = 1000f / Math.max(15, targetFps);
            cascades = 2;
            shadowAtlas = 2048;
            renderScale = 0.85f;
            volumetric = true;
            vfxBudget = 1.0f;
        } else {
            targetFps = options.targetFps == 0 ? 60 : options.targetFps;
            frameBudgetMs = 1000f / Math.max(15, targetFps);
            cascades = 3;
            shadowAtlas = 4096;
            renderScale = 1.0f;
            volumetric = true;
            vfxBudget = 1.0f;
        }
        /* reglages joueur prioritaires sur le palier */
        if (options.renderScale < 1.0f) {
            renderScale = Math.min(renderScale, options.renderScale);
        }
        if (!options.shadows) {
            cascades = 0;
        }
        if (!options.effects) {
            vfxBudget = 0f;
        }
        if (options.targetFps != 0) {
            targetFps = options.targetFps;
            frameBudgetMs = 1000f / Math.max(15, targetFps);
        }
        applyThermal();
        if (!silent) {
            bus.emit(EventBus.QUALITY_CHANGED, tierName(tier), tier);
        }
    }

    /** Applique un reglage utilisateur sans changer de palier. */
    public void refreshFromOptions() {
        applyTier(tier, true);
    }

    public void setEconomyMode(boolean on) {
        if (economyMode == on) {
            return;
        }
        economyMode = on;
        if (on) {
            targetFps = 30;
            frameBudgetMs = 33.3f;
            renderScale = Math.min(renderScale, 0.65f);
            cascades = Math.min(cascades, 1);
            volumetric = false;
        } else {
            applyTier(tier, true);
        }
    }

    /** Palier thermique (18.09) : NONE/LIGHT/MODERATE/SEVERE/CRITICAL. */
    public void setThermalLevel(int level) {
        float factor;
        switch (level) {
            case 0:
            case 1:
                factor = 1.0f;
                break;
            case 2:  /* MODERATE : FSR -10 %, ombres -1 cascade */
                factor = 0.9f;
                break;
            case 3:  /* SEVERE : 30 fps, volumetrique off, VFX -50 % */
                factor = 0.7f;
                break;
            default: /* CRITICAL : 30 fps, resolution 0,55 */
                factor = 0.55f;
                break;
        }
        if (Math.abs(factor - thermalFactor) < 0.001f) {
            return;
        }
        thermalFactor = factor;
        if (level >= 3) {
            targetFps = 30;
            frameBudgetMs = 33.3f;
            volumetric = false;
        }
        applyThermal();
    }

    private void applyThermal() {
        /* 18.09 : le passage entre paliers est lisse sur 3 s — la valeur
           cible est atteinte progressivement par smoothThermal(). */
        renderScale = Maths.clamp(baseScale() * thermalFactor, 0.55f, 1.0f);
        vfxBudget = tier == TIER_LOW ? 0.45f : thermalFactor;
        particles = (int) ((tier == TIER_LOW ? 400 : 900) * vfxBudget);
    }

    private float baseScale() {
        if (economyMode) {
            return 0.65f;
        }
        switch (tier) {
            case TIER_LOW:
                return 0.70f;
            case TIER_HIGH:
                return 1.0f;
            default:
                return 0.85f;
        }
    }

    /** Lissage thermique sur 3 s (18.09) : jamais de saut visible. */
    public void smoothThermal(float dt) {
        float target = Maths.clamp(baseScale() * thermalFactor, 0.55f, 1.0f);
        float lambda = 1f / 3f;
        renderScale = Maths.damp(renderScale, target, lambda, dt);
    }

    /** A appeler une fois par frame avec le temps de frame en millisecondes. */
    public void submitFrame(float ms) {
        frameTimes[frameIndex] = ms;
        frameIndex = (frameIndex + 1) % HISTORY;
        if (frameFill < HISTORY) {
            frameFill++;
        }
        frames++;
        accumulated += ms;
        if (accumulated >= 500f) {
            lastAverageMs = accumulated / Math.max(1, frames);
            lastP99Ms = percentile(99);
            accumulated = 0f;
            frames = 0;
            evaluate();
        }
        if (ms > 1000f / 24f) {
            lowFrameStreak++;
        } else {
            lowFrameStreak = 0;
        }
    }

    private void evaluate() {
        if (options.quality != 0 || qualityLockedByUser) {
            return;   /* le joueur a tranche : on ne touche plus au palier */
        }
        float target = frameBudgetMs;
        float p99 = lastP99Ms;
        if (p99 > target * 1.35f || lowFrameStreak >= 3) {
            /* 00.07 : p99 > 1,35 x cible = instable -> on degrade */
            if (renderScale > 0.56f) {
                renderScale = Math.max(0.55f, renderScale - 0.08f);
            } else if (cascades > 1) {
                cascades--;
                shadowAtlas = Math.max(1024, shadowAtlas / 2);
            } else if (volumetric) {
                volumetric = false;
            } else if (vfxBudget > 0.5f) {
                vfxBudget = Math.max(0.45f, vfxBudget - 0.25f);
            } else if (tier > TIER_LOW) {
                setTier(tier - 1, false);
            }
        } else if (p99 < target * 0.72f && frameFill >= 120) {
            /* marge confortable : on remonte prudemment */
            if (renderScale < baseScale() * thermalFactor - 0.01f) {
                renderScale = Math.min(baseScale() * thermalFactor, renderScale + 0.05f);
            } else if (!volumetric && tier >= TIER_MID && thermalFactor >= 0.99f) {
                volumetric = true;
            } else if (tier < autoTier) {
                setTier(tier + 1, false);
            }
        }
    }

    public float percentile(int p) {
        if (frameFill == 0) {
            return 0f;
        }
        float[] copy = Arrays.copyOf(frameTimes, frameFill);
        Arrays.sort(copy);
        int idx = Maths.clamp((int) Math.ceil(p / 100f * copy.length) - 1, 0, copy.length - 1);
        return copy[idx];
    }

    public float fps() {
        return lastAverageMs > 0.01f ? 1000f / lastAverageMs : 0f;
    }

    public float averageFrameMs() {
        return lastAverageMs;
    }

    public float p99FrameMs() {
        return lastP99Ms;
    }

    public boolean stable() {
        return lastP99Ms <= frameBudgetMs * 1.35f && lowFrameStreak < 3;
    }

    public int lowFrameStreak() {
        return lowFrameStreak;
    }

    public int tier() {
        return tier;
    }

    public String tierName() {
        return tierName(tier);
    }

    public static String tierName(int t) {
        return t == TIER_LOW ? "LOW" : (t == TIER_HIGH ? "HIGH" : "MID");
    }

    public float renderScale() {
        return renderScale;
    }

    public int cascades() {
        return cascades;
    }

    public int shadowAtlas() {
        return shadowAtlas;
    }

    public int targetFps() {
        return targetFps;
    }

    public float frameBudgetMs() {
        return frameBudgetMs;
    }

    public boolean volumetric() {
        return volumetric && options.fog;
    }

    public float vfxBudget() {
        return vfxBudget;
    }

    public int particleBudget() {
        return particles;
    }

    public boolean economyMode() {
        return economyMode;
    }

    /* compteurs de frame (02.15, 02.16) --------------------------------- */

    public void setCounters(int drawCalls, int triangles, int liveParticles) {
        this.drawCalls = drawCalls;
        this.triangles = triangles;
        this.particles = liveParticles;
    }

    public int drawCalls() {
        return drawCalls;
    }

    public int triangles() {
        return triangles;
    }

    public int liveParticles() {
        return particles;
    }

    public boolean drawCallBudgetOk() {
        return drawCalls <= 420 || tier != TIER_MID;
    }

    public boolean triangleBudgetOk() {
        int budget = tier == TIER_LOW ? 180000 : (tier == TIER_MID ? 310000 : 480000);
        return triangles <= budget;
    }
}
