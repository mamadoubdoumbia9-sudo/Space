/*
 * LOHEN — render/GameRenderer.java
 *
 * Le rendu d'une image. C'est ici que la simulation et l'ecran se rencontrent :
 *
 *   1. `game.frame(dt)` — la simulation avance a pas fixe de 1/60 s (00.04),
 *      quel que soit le taux de rafraichissement de l'ecran.
 *   2. le monde est trace dans un FBO a l'echelle `options.renderScale`
 *      (50 a 100 %, 02.11 — le premier levier du gouverneur de performance).
 *   3. une passe plein ecran applique la direction artistique : saturation de
 *      la sequence (05.08), grain 0,035 a 24 images/s (05.13), vignettage,
 *      barres 2.39:1 des cinematiques (15.01), flou de pause (14.05),
 *      fondu de mort (12.08).
 *   4. l'interface est tracee par-dessus : quatre elements de HUD, jamais cinq
 *      (14.01), les sous-titres, les menus, le journal, la lettre.
 *
 * 02.05 : OpenGL ES 2.0 seulement. Pas de GLES 3, pas de compute, pas de
 * dependance externe. Un telephone de 2017 doit pouvoir y jouer.
 */
package com.velmora.lohen.render;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.velmora.lohen.gl.PostProcess;
import com.velmora.lohen.gl.TextAtlas;
import com.velmora.lohen.sim.core.LohenGame;
import com.velmora.lohen.sim.math.Mat4;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.narrative.CinematicPlayer;
import com.velmora.lohen.sim.player.CameraRig;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class GameRenderer implements GLSurfaceView.Renderer {

    private final LohenGame game;
    private final TextAtlas atlas = new TextAtlas();
    private final WorldRenderer world = new WorldRenderer();
    private final HudRenderer hud = new HudRenderer();
    private final PostProcess post = new PostProcess();

    private final float[] view = new float[16];
    private final float[] proj = new float[16];

    private int width = 1280;
    private int height = 720;
    private float density = 2.75f;
    private long lastFrameNs;
    private float smoothDt = 1f / 60f;
    private boolean atlasReady;
    private int renderedFrames;
    private float lastFrameMs;
    private volatile boolean started;
    private volatile float bootProgress;
    private volatile String bootStage = "";

    public GameRenderer(LohenGame game) {
        this.game = game;
    }

    /**
     * Le contenu est lu sur un fil separe (02.13 : un chargement annonce,
     * jamais silencieux). Tant qu'il n'est pas termine, ce rendu n'affiche que
     * l'ecran de demarrage : aucune lecture de la simulation, donc aucune
     * course entre le fil de boot et le fil GL.
     */
    public void setStarted(boolean b) {
        started = b;
    }

    public boolean started() {
        return started;
    }

    public void setBootProgress(float p) {
        bootProgress = p;
    }

    /** 02.13 : le chargement dit ce qu'il charge. */
    public void setBootStage(String s) {
        bootStage = s == null ? "" : s;
    }

    public HudRenderer hud() {
        return hud;
    }

    public void setDensity(float d) {
        density = d > 0f ? d : 1f;
        hud.setDensity(density);
    }

    public TextAtlas atlas() {
        return atlas;
    }

    public int renderedFrames() {
        return renderedFrames;
    }

    public float lastFrameMs() {
        return lastFrameMs;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0.02f, 0.03f, 0.05f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        atlas.load(null);      /* les polices sont chargees par setAssets() */
        world.init();
        hud.init();
        hud.setAtlas(atlas);
        hud.setDensity(density);
        post.init();
        lastFrameNs = System.nanoTime();
    }

    /** Les polices viennent des assets : l'Activity les passe ici. */
    public void setAssets(android.content.res.AssetManager assets) {
        atlasReady = atlas.load(assets);
    }

    public boolean fontsReady() {
        return atlasReady;
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        post.resize(width, height, game.options.renderScale);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0 ? 1f / 60f : (now - lastFrameNs) * 1e-9f;
        lastFrameNs = now;
        /* le dt est lisse : une image longue ne doit pas faire sauter la
         * simulation, et une image courte ne doit pas la hacher. */
        smoothDt = smoothDt + (Maths.clamp(dt, 1f / 240f, 0.1f) - smoothDt) * 0.35f;

        if (!started) {
            GLES20.glViewport(0, 0, width, height);
            GLES20.glClearColor(0.016f, 0.024f, 0.039f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            hud.drawBoot(game, width, height, smoothDt, bootProgress, bootStage);
            renderedFrames++;
            return;
        }

        long frameStart = System.nanoTime();
        long t0 = frameStart;
        synchronized (game) {
            game.frame(smoothDt);
        }

        /* 1. le monde, dans le FBO */
        post.resize(width, height, game.options.renderScale);
        post.beginScene();
        GLES20.glClearColor(0.02f, 0.03f, 0.05f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        CameraRig cam = game.camera;
        cam.viewMatrix(view);
        cam.projectionMatrix(proj, width / (float) height);
        world.draw(game, view, proj, game.gameTime());

        /* 2. le traitement d'image */
        float letterbox = 0f;
        if (game.mode() == LohenGame.MODE_CINEMATIC && game.cine.active()) {
            letterbox = game.cine.letterbox();
        } else if (game.mode() == LohenGame.MODE_LETTER) {
            letterbox = 1f;
        }
        float blur = game.menu.paused() ? MenuBlur.PAUSE : 0f;
        float fade = game.hud.deathFade();
        int fadeColor = 0xFF04060A;
        float saturation = Palette.saturationFor(game.sequence());
        if (game.level() != null) {
            saturation = game.level().sky.saturation;
        }
        if (game.echo.active()) {
            /* 09.15 : l'Echo de la salle de bal reste le pic absolu */
            saturation = game.echo.mode() == 'C' ? 0.34f : 0.85f;
        }
        float exposure = game.options.brightness * (1f + game.lighthouse.dawnEv());
        float grain = game.options.grain && !game.options.reducedFlashes ? 0.012f : 0f;
        float vignette = game.options.vignette ? 0.08f : 0f;
        if (game.echo.active()) {
            vignette = 0.55f;
            saturation *= 1f - EchoSystemBridge.DESATURATION;
        }
        post.apply(width, height, smoothDt, game.gameTime(), saturation, exposure,
                vignette, grain, letterbox, blur, fade, fadeColor);

        /* 3. l'interface, par-dessus, a la resolution de l'ecran */
        GLES20.glViewport(0, 0, width, height);
        synchronized (game) {
            hud.draw(game, width, height, smoothDt);
        }
        lastFrameMs = (System.nanoTime() - t0) * 1e-6f;
        renderedFrames++;

        /* 02.11 : le plafond d'images suit le reglage du joueur. Dormir sur le
         * fil GL est volontaire : ca tient le telephone froid (02.16) sans
         * jamais decaler la simulation, qui reste a pas fixe de 1/60 s. */
        int fps = game.options.targetFps;
        if (fps > 0) {
            long budgetNs = 1000000000L / fps;
            long spent = System.nanoTime() - frameStart;
            long sleepNs = budgetNs - spent;
            if (sleepNs > 1500000L) {
                try {
                    Thread.sleep(sleepNs / 1000000L, (int) (sleepNs % 1000000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** 14.05 : le flou de pause est un flou leger, jamais une galerie. */
    static final class MenuBlur {
        static final float PAUSE = 0.55f;
    }

    /** Le pont vers les constantes de l'Echo, sans dupliquer les valeurs. */
    static final class EchoSystemBridge {
        static final float DESATURATION =
                com.velmora.lohen.sim.narrative.EchoSystem.DESATURATION;
    }

    public void release() {
        world.release();
        hud.release();
        post.release();
        atlas.release();
    }
}
