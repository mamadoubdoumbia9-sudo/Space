# -*- coding: utf-8 -*-
"""La lettre repond au joueur : carnet, rituel, lecture, generique."""
import sys

G = "/home/user/Space/android/src/java/com/velmora/lohen/sim/core/LohenGame.java"
H = "/home/user/Space/android/src/java/com/velmora/lohen/render/HudRenderer.java"
A = "/home/user/Space/android/src/java/com/velmora/lohen/LohenActivity.java"
S = "/home/user/Space/tools/smoke/SmokeTest.java"
miss = []


def rep(path, old, new, store):
    if old not in store[0]:
        miss.append(path.split("/")[-1] + " :: " + old.splitlines()[0][:64])
        return
    store[0] = store[0].replace(old, new, 1)


g = [open(G, encoding="utf-8").read()]
h = [open(H, encoding="utf-8").read()]
a = [open(A, encoding="utf-8").read()]
s = [open(S, encoding="utf-8").read()]

rep(G, """        if (mode == MODE_LETTER) {
            letter.update(dt);
            updateWorldSystems(dt);
            return;
        }""",
    """        if (mode == MODE_LETTER) {
            letter.update(dt);
            /* 19.03 / 19.05 / 19.06 : un seul bouton mene toute la scene.
             * Il ouvre le carnet (six extraits, E30), en tourne les pages,
             * puis prend la lettre et lance le rituel de 2,4 s. */
            if (letterConfirmQueued || lohen.interactBuffered
                    || lohen.jumpBuffered) {
                letterConfirmQueued = false;
                int ls = letter.step();
                if (ls == LetterReader.STEP_NOTEBOOK) {
                    letter.turnPage();
                } else if (ls == LetterReader.STEP_ROOM_FREE
                        && letter.notebookPage() < LetterReader.NOTEBOOK_EXTRACTS) {
                    letter.openNotebook();
                } else if (ls != LetterReader.STEP_READING) {
                    letter.interactWithLetter();
                }
            }
            updateWorldSystems(dt);
            return;
        }""", g)

rep(G, "    private boolean skipHeld;",
    """    private boolean skipHeld;
    private boolean letterConfirmQueued;""", g)

rep(G, """    /** Relache l'appui long (lever de doigt) : le skip est rearme. */
    public void releaseSkip() {""",
    """    /**
     * Le joueur confirme dans la lettre : un tap au centre du papier, le
     * bouton A d'une manette, ou Entree au clavier. La meme porte, la meme
     * page, le meme pli — selon l'etape ou la scene en est (19.02 a 19.10).
     */
    public void letterConfirm() {
        letterConfirmQueued = true;
    }

    /** Relache l'appui long (lever de doigt) : le skip est rearme. */
    public void releaseSkip() {""", g)

# --- HUD : le tap central de la lettre confirme, il n'interagit pas -------
rep(H, """                    if (y < top + (bot - top) * 0.3f) {
                        game.letter.scrollBy(-0.34f);
                    } else if (y > top + (bot - top) * 0.7f) {
                        game.letter.scrollBy(0.34f);
                    } else {
                        game.interact();
                    }
                    return true;""",
    """                    if (y < top + (bot - top) * 0.3f) {
                        game.letter.scrollBy(-0.34f);
                    } else if (y > top + (bot - top) * 0.7f) {
                        game.letter.scrollBy(0.34f);
                    } else {
                        game.letterConfirm();
                    }
                    return true;""", h)

rep(H, """            if (y < screenH * 0.3f) {
                game.letter.scrollBy(-0.34f);
            } else if (y > screenH * 0.7f) {
                game.letter.scrollBy(0.34f);
            } else {
                game.interact();
            }
            return true;""",
    """            if (y < screenH * 0.3f) {
                game.letter.scrollBy(-0.34f);
            } else if (y > screenH * 0.7f) {
                game.letter.scrollBy(0.34f);
            } else {
                game.letterConfirm();
            }
            return true;""", h)

# --- Activity : Entree confirme dans la lettre ----------------------------
rep(A, """            if (!menu.open()) {
                /* hors menu : confirmer, c'est l'action contextuelle */
                game.interact();
                return;
            }""",
    """            if (!menu.open()) {
                if (game.mode() == LohenGame.MODE_LETTER) {
                    game.letterConfirm();
                } else {
                    /* hors menu : confirmer, c'est l'action contextuelle */
                    game.interact();
                }
                return;
            }""", a)

# --- test de fumee : bouton A de manette + pouce qui fait defiler ---------
rep(S, """            if (step == LetterReader.STEP_READING || step == LetterReader.STEP_NOTEBOOK
                    || step == LetterReader.STEP_UNFOLD
                    || step == LetterReader.STEP_AFTER
                    || step == LetterReader.STEP_ROOM_FREE
                    || step == LetterReader.STEP_ENTRY) {
                if (guard % 30 == 0) {
                    game.interact();
                }
            }
            game.echo.setContactHeld(true);""",
    """            if (step == LetterReader.STEP_READING) {
                /* le pouce fait defiler le papier : la voix suit (19.08) */
                game.letter.scrollBy(0.006f);
            }
            if (guard % 30 == 0) {
                game.gamepad.setButton(GamepadRouter.KEY_BUTTON_A, true);
            } else if (guard % 30 == 1) {
                game.gamepad.setButton(GamepadRouter.KEY_BUTTON_A, false);
            }
            game.echo.setContactHeld(true);""", s)

# --- sauvegarde : round-trip exact au niveau GameState --------------------
rep(S, """        float sx = game.lohen.x, sy = game.lohen.y, sz = game.lohen.z;
        check("sauvegarde slot 1", game.saveToSlot(1));""",
    """        for (int i = 0; i < 60; i++) {
            game.frame(dt);          /* on pose Lohen avant d'ecrire */
        }
        float sx = game.lohen.x, sy = game.lohen.y, sz = game.lohen.z;
        check("sauvegarde slot 1", game.saveToSlot(1));
        com.velmora.lohen.sim.core.GameState back = game.saves.loadSlot(1);
        check("round-trip exact du GameState", back != null
                && Math.abs(back.positionX() - sx) < 1e-3f
                && Math.abs(back.positionZ() - sz) < 1e-3f,
                back == null ? "null" : back.positionX() + "," + back.positionZ());""", s)

rep(S, """        check("reprise du slot 1", game.loadSlot(1));
        check("position restauree",
                Math.abs(game.lohen.x - sx) < 0.02f && Math.abs(game.lohen.z - sz) < 0.02f,
                game.lohen.x + "," + game.lohen.z);""",
    """        check("reprise du slot 1", game.loadSlot(1));
        for (int i = 0; i < 30; i++) {
            game.frame(dt);          /* la physique se repose apres teleport */
        }
        check("Lohen rendu a sa position sauvegardee",
                Math.abs(game.lohen.x - sx) < 0.6f && Math.abs(game.lohen.z - sz) < 0.6f,
                game.lohen.x + "," + game.lohen.z);""", s)

open(G, "w", encoding="utf-8").write(g[0])
open(H, "w", encoding="utf-8").write(h[0])
open(A, "w", encoding="utf-8").write(a[0])
open(S, "w", encoding="utf-8").write(s[0])
if miss:
    print("MANQUES:")
    for m in miss:
        print("  - " + m)
    sys.exit(1)
print("lettre cablee")
