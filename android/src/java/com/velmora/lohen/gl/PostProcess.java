/*
 * LOHEN — gl/PostProcess.java
 *
 * Le traitement d'image, en une passe plein ecran sur un FBO :
 *   05.08  saturation par sequence (0,24 a 0,55 ; pic absolu 0,70 dans l'Echo
 *          de la salle de bal, 09.15)
 *   05.13  grain 0,035 echantillonne a 24 images/s — pas a 60 : le grain du
 *          film, pas le bruit du capteur
 *   05.14  le flou de mouvement est absent par defaut ; le flou de PAUSE
 *          (14.05) est un flou a 9 prelevements, jamais une galerie
 *   15.01  barres noires 2.39:1 qui arrivent en 0,5 s
 *   09.31  +0,4 EV quand le faisceau du Phare passe sur le joueur
 *
 * Le FBO est redimensionne selon `options.renderScale` (50 a 100 %, 02.11) :
 * c'est le premier levier du gouverneur de performance sur un palier bas.
 */
package com.velmora.lohen.gl;

import android.opengl.GLES20;

public final class PostProcess {

    private int program;
    private int aPos;
    private int aUv;
    private int uTex;
    private int uTexel;
    private int uSaturation;
    private int uExposure;
    private int uVignette;
    private int uGrain;
    private int uTime;
    private int uLetterbox;
    private int uBlur;
    private int uFade;
    private int uFadeColor;

    private int fbo;
    private int colorTex;
    private int depthRb;
    private int width;
    private int height;

    private int quadVbo;

    /** Grain echantillonne a 24 Hz : on garde la valeur entre deux pas. */
    private float grainClock;
    private float grainValue;

    public void init() {
        program = GlUtil.program(ShaderLib.POST_VS, ShaderLib.POST_FS);
        aPos = GlUtil.attrib(program, "aPos");
        aUv = GlUtil.attrib(program, "aUv");
        uTex = GlUtil.uniform(program, "uTex");
        uTexel = GlUtil.uniform(program, "uTexel");
        uSaturation = GlUtil.uniform(program, "uSaturation");
        uExposure = GlUtil.uniform(program, "uExposure");
        uVignette = GlUtil.uniform(program, "uVignette");
        uGrain = GlUtil.uniform(program, "uGrain");
        uTime = GlUtil.uniform(program, "uTime");
        uLetterbox = GlUtil.uniform(program, "uLetterbox");
        uBlur = GlUtil.uniform(program, "uBlur");
        uFade = GlUtil.uniform(program, "uFade");
        uFadeColor = GlUtil.uniform(program, "uFadeColor");

        float[] quad = {
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                1f, 1f, 1f, 1f,
                -1f, 1f, 0f, 1f,
        };
        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        quadVbo = ids[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, quad.length * 4,
                GlUtil.floats(quad), GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    /** Cree (ou recree) la cible de rendu a l'echelle demandee. */
    public void resize(int screenW, int screenH, float renderScale) {
        float scale = Math.max(0.5f, Math.min(1f, renderScale));
        int w = Math.max(64, Math.round(screenW * scale));
        int h = Math.max(64, Math.round(screenH * scale));
        if (w == width && h == height && fbo != 0) {
            return;
        }
        releaseTarget();
        width = w;
        height = h;
        int[] ids = new int[1];
        GLES20.glGenFramebuffers(1, ids, 0);
        fbo = ids[0];
        GLES20.glGenTextures(1, ids, 0);
        colorTex = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colorTex);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glGenRenderbuffers(1, ids, 0);
        depthRb = ids[0];
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthRb);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER,
                GLES20.GL_DEPTH_COMPONENT16, w, h);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, colorTex, 0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER,
                GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depthRb);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GlUtil.check("FBO incomplet " + status);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);
    }

    public void beginScene() {
        if (fbo == 0) {
            return;
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glViewport(0, 0, width, height);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /**
     * Applique le traitement et trace sur l'ecran.
     *
     * @param dt        delta reel, pour l'horloge du grain
     * @param time      temps de jeu en secondes
     */
    public void apply(int screenW, int screenH, float dt, float time,
                      float saturation, float exposure, float vignette,
                      float grain, float letterbox, float blur, float fade,
                      int fadeColor) {
        /* 05.13 : le grain est echantillonne a 24 images par seconde. */
        grainClock += dt;
        if (grainClock >= 1f / 24f) {
            grainClock = 0f;
            grainValue = grain;
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, screenW, screenH);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colorTex);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniform2f(uTexel, 1f / Math.max(1, width), 1f / Math.max(1, height));
        GLES20.glUniform1f(uSaturation, saturation);
        GLES20.glUniform1f(uExposure, exposure);
        GLES20.glUniform1f(uVignette, vignette);
        GLES20.glUniform1f(uGrain, grainValue);
        GLES20.glUniform1f(uTime, time);
        GLES20.glUniform1f(uLetterbox, Math.max(0f, Math.min(1f, letterbox)));
        GLES20.glUniform1f(uBlur, Math.max(0f, Math.min(1f, blur)));
        GLES20.glUniform1f(uFade, Math.max(0f, Math.min(1f, fade)));
        GLES20.glUniform3f(uFadeColor, ShaderLib.r(fadeColor), ShaderLib.g(fadeColor),
                ShaderLib.b(fadeColor));
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo);
        if (aPos >= 0) {
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, 0);
        }
        if (aUv >= 0) {
            GLES20.glEnableVertexAttribArray(aUv);
            GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 16, 8);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4);
        if (aPos >= 0) {
            GLES20.glDisableVertexAttribArray(aPos);
        }
        if (aUv >= 0) {
            GLES20.glDisableVertexAttribArray(aUv);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public void releaseTarget() {
        if (colorTex != 0) {
            GLES20.glDeleteTextures(1, new int[]{colorTex}, 0);
            colorTex = 0;
        }
        if (depthRb != 0) {
            GLES20.glDeleteRenderbuffers(1, new int[]{depthRb}, 0);
            depthRb = 0;
        }
        if (fbo != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            fbo = 0;
        }
        width = 0;
        height = 0;
    }

    public void release() {
        releaseTarget();
        if (quadVbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{quadVbo}, 0);
            quadVbo = 0;
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }
}
