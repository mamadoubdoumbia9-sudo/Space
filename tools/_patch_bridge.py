# -*- coding: utf-8 -*-
"""Derniers ponts Activity <-> rendu : activation clavier, etape de boot, plafond d'images."""
import sys

H = "/home/user/Space/android/src/java/com/velmora/lohen/render/HudRenderer.java"
G = "/home/user/Space/android/src/java/com/velmora/lohen/render/GameRenderer.java"
miss = []


def rep(path, old, new, holder):
    s = holder[0]
    if old not in s:
        miss.append(path.split("/")[-1] + " :: " + old.splitlines()[0][:60])
        return
    holder[0] = s.replace(old, new, 1)


h = [open(H, encoding="utf-8").read()]
g = [open(G, encoding="utf-8").read()]

# --- HudRenderer : activation depuis une manette ou un clavier -------------
rep(H, """    private boolean mainMenuAction(LohenGame game, int index) {""",
    """    /**
     * Activation sans le doigt : une manette, un clavier, un lecteur d'ecran.
     * Le meme code que le tap, donc le meme resultat (08.22).
     */
    public boolean activateMain(LohenGame game, int index) {
        if (index == 0 && !game.hasSave()) {
            index = 1;         /* « Continuer » n'existe pas : on saute la ligne */
        }
        return mainMenuAction(game, index);
    }

    public boolean activatePause(LohenGame game, int index) {
        return pauseAction(game, index);
    }

    /** Un emplacement de sauvegarde : charge depuis le menu, ecrit depuis la pause. */
    public boolean activateSlot(LohenGame game, int slot) {
        game.menu.setSlot(slot);
        if (savesLoad) {
            if (game.loadSlot(slot)) {
                game.menu.close();
                return true;
            }
            return false;
        }
        return game.saveToSlot(slot);
    }

    private boolean mainMenuAction(LohenGame game, int index) {""", h)

# --- HudRenderer : l'etape de boot est donnee par la simulation -------------
rep(H, """    public void drawBoot(LohenGame game, int w, int h, float dt, float progress) {""",
    """    public void drawBoot(LohenGame game, int w, int h, float dt, float progress,
                         String stage) {""", h)
rep(H, """            String stage = game == null || game.loc == null
                    ? "Velmora" : game.loc.text("boot.stage", "Velmora");""",
    """            if (stage == null || stage.length() == 0) {
                stage = "Velmora";
            }""", h)

# --- GameRenderer : acces au HUD, etape de boot, plafond d'images -----------
rep(G, """    public void setDensity(float d) {""",
    """    public HudRenderer hud() {
        return hud;
    }

    public void setDensity(float d) {""", g)

rep(G, """    private volatile boolean started;
    private volatile float bootProgress;""",
    """    private volatile boolean started;
    private volatile float bootProgress;
    private volatile String bootStage = "";""", g)

rep(G, """    public void setBootProgress(float p) {
        bootProgress = p;
    }""",
    """    public void setBootProgress(float p) {
        bootProgress = p;
    }

    /** 02.13 : le chargement dit ce qu'il charge. */
    public void setBootStage(String s) {
        bootStage = s == null ? "" : s;
    }""", g)

rep(G, """            hud.drawBoot(game, width, height, smoothDt, bootProgress);""",
    """            hud.drawBoot(game, width, height, smoothDt, bootProgress, bootStage);""", g)

rep(G, """        long t0 = System.nanoTime();
        synchronized (game) {
            game.frame(smoothDt);
        }""",
    """        long frameStart = System.nanoTime();
        long t0 = frameStart;
        synchronized (game) {
            game.frame(smoothDt);
        }""", g)

rep(G, """        lastFrameMs = (System.nanoTime() - t0) * 1e-6f;
        renderedFrames++;""",
    """        lastFrameMs = (System.nanoTime() - t0) * 1e-6f;
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
        }""", g)

open(H, "w", encoding="utf-8").write(h[0])
open(G, "w", encoding="utf-8").write(g[0])
if miss:
    print("MANQUES:")
    for m in miss:
        print("  - " + m)
    sys.exit(1)
print("ponts ok")
