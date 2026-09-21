# -*- coding: utf-8 -*-
"""Ecran de demarrage : rien ne lit la simulation avant qu'elle soit prete."""
import sys

P = "/home/user/Space/android/src/java/com/velmora/lohen/render/HudRenderer.java"
s = open(P, encoding="utf-8").read()
miss = []


def rep(old, new):
    global s
    if old not in s:
        miss.append(old.splitlines()[0][:70])
        return
    s = s.replace(old, new, 1)


rep("""    private void drawLoading(LohenGame game, int w, int h) {""",
    """    /**
     * L'ecran du tout premier chargement : celui ou le contenu est lu depuis
     * les assets. La simulation n'est pas encore prete, donc cette methode ne
     * touche a rien d'autre qu'au modele de HUD et a l'atlas.
     */
    public void drawBoot(LohenGame game, int w, int h, float dt, float progress) {
        beginHits(w, h);
        if (program == 0) {
            return;
        }
        batch.beginFrame();
        batch.clear();
        rect(0, 0, w, h, 0xFF04060A, 1f);
        if (atlas != null) {
            /* le titre, en petites capitales, sans aucune decoration */
            String title = "LOHEN";
            int size = sp(40);
            float tw = atlas.measure(title, TextAtlas.FONT_TITLE, size,
                    TextAtlas.TITLE_TRACKING);
            atlas.drawSmallCaps(batch, title, size, (w - tw) * 0.5f, h * 0.42f,
                    Palette.BLANC_CASSE);
            String sub = "Les Sept Lettres de Velmora";
            int s2 = sp(18);
            float sw = atlas.measure(sub, TextAtlas.FONT_LOHEN, s2);
            atlas.draw(batch, sub, TextAtlas.FONT_LOHEN, s2, (w - sw) * 0.5f,
                    h * 0.42f + dp(56f), withAlpha(0xFF9A9186, 0.9f));
            float bw = Math.min(w * 0.42f, dp(360f));
            float bx = (w - bw) * 0.5f;
            float by = h * 0.62f;
            rect(bx, by, bx + bw, by + dp(2f), 0xFF2A2A28, 0.9f);
            rect(bx, by, bx + bw * Maths.clamp01(progress), by + dp(2f),
                    Palette.BLANC_CASSE, 0.9f);
            String stage = game == null || game.loc == null
                    ? "Velmora" : game.loc.text("boot.stage", "Velmora");
            int s3 = sp(14);
            float gw = atlas.measure(stage, TextAtlas.FONT_UI, s3);
            atlas.draw(batch, stage, TextAtlas.FONT_UI, s3, (w - gw) * 0.5f,
                    by + dp(20f), withAlpha(0xFF7E766A, 0.9f));
        }
        flush(w, h, true);
    }

    private void drawLoading(LohenGame game, int w, int h) {""")

open(P, "w", encoding="utf-8").write(s)

G = "/home/user/Space/android/src/java/com/velmora/lohen/render/GameRenderer.java"
g = open(G, encoding="utf-8").read()


def repg(old, new):
    global g
    if old not in g:
        miss.append("GameRenderer :: " + old.splitlines()[0][:60])
        return
    g = g.replace(old, new, 1)


repg("""    private int renderedFrames;
    private float lastFrameMs;""",
     """    private int renderedFrames;
    private float lastFrameMs;
    private volatile boolean started;
    private volatile float bootProgress;""")

repg("""    public void setDensity(float d) {""",
     """    /**
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

    public void setDensity(float d) {""")

repg("""        long t0 = System.nanoTime();
        synchronized (game) {
            game.frame(smoothDt);
        }""",
     """        if (!started) {
            GLES20.glViewport(0, 0, width, height);
            GLES20.glClearColor(0.016f, 0.024f, 0.039f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            hud.drawBoot(game, width, height, smoothDt, bootProgress);
            renderedFrames++;
            return;
        }

        long t0 = System.nanoTime();
        synchronized (game) {
            game.frame(smoothDt);
        }""")

open(G, "w", encoding="utf-8").write(g)

if miss:
    print("MANQUES:")
    for m in miss:
        print("  - " + m)
    sys.exit(1)
print("boot ok")
