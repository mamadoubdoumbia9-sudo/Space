# -*- coding: utf-8 -*-
"""Corrige les attentes du test de fumee sur les vraies API."""
import sys

P = "/home/user/Space/tools/smoke/SmokeTest.java"
s = open(P, encoding="utf-8").read()
miss = []


def rep(old, new):
    global s
    if old not in s:
        miss.append(old.splitlines()[0][:70])
        return
    s = s.replace(old, new, 1)


rep("""        check("86 scenes de dialogue", game.db.allDialogues().size() == 86,
                String.valueOf(game.db.allDialogues().size()));""",
    """        int scenes = 0;
        for (ContentDb.SequenceDialogues sd : game.db.allDialogues().values()) {
            scenes += sd.scenes.size();
        }
        check("86 scenes de dialogue", scenes == 86, String.valueOf(scenes));""")

rep("""        check("8 niveaux", game.db.levels().size() == 8,
                String.valueOf(game.db.levels().size()));""",
    """        check("aucun niveau en memoire au menu", game.db.levels().isEmpty(),
                "chargement a la demande");""")

rep("""        check("C01 skippable apres 1,5 s", game.gameTime() > 1.5f
                && game.cine.skipHoldProgress() >= 0f);
        for (int i = 0; i < 60 * 30 && game.cine.active(); i++) {
            game.cine.setSkipHeld(true, dt);
            game.frame(dt);
        }
        check("cinematique terminee par le skip", !game.cine.active());""",
    """        check("C01 skippable apres 1,5 s", game.gameTime() > 1.5f
                && game.cine.skipHoldProgress() >= 0f);
        /* 07.22 : le skip est un APPUI LONG, pas un tap — on tient le doigt */
        game.touch.setScreen(1280f, 720f, 2.75f);
        game.touch.onPointerDown(9, 1000f, 360f);
        for (int i = 0; i < 60 * 12 && game.cine.active(); i++) {
            game.frame(dt);
        }
        game.touch.onPointerUp(9, 1000f, 360f);
        game.releaseSkip();
        for (int i = 0; i < 60 * 3 && game.cine.active(); i++) {
            game.frame(dt);
        }
        check("cinematique terminee par l'appui long", !game.cine.active());""")

rep("""        int phase0 = game.grapple.phase();
        game.grapple.fire(game.world, game.lohen.x, game.lohen.y + 6f,
                game.lohen.z + 8f);
        boolean flew = game.grapple.phase() != phase0 || game.grapple.active();""",
    """        int phase0 = game.grapple.phase();
        /* on vise un ancrage reel du niveau, depuis un point proche */
        float[] anchors = game.level().anchorData;
        check("le niveau a des ancrages", anchors.length >= 4,
                String.valueOf(game.level().anchorCount));
        float ax = anchors[0], ay = anchors[1], az = anchors[2];
        game.lohen.x = ax;
        game.lohen.y = ay - 1.2f;
        game.lohen.z = az + 4f;
        float gx = ax - game.lohen.x, gy = ay - game.lohen.y, gz = az - game.lohen.z;
        float gl = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
        game.grapple.fire(game.world, gx / gl, gy / gl, gz / gl);
        boolean flew = game.grapple.phase() != phase0 || game.grapple.active();""")

rep("""        short[] buf = new short[2048];
        int frames = game.renderAudio(buf);
        int peak = 0;
        for (short s : buf) {
            peak = Math.max(peak, Math.abs((int) s));
        }
        check("1024 images rendues", frames == 1024, String.valueOf(frames));
        check("le mix n'est pas muet", peak > 0, "pic " + peak);
        check("loudness cible -16 LUFS",
                Math.abs(game.audio.integratedLufs() + 16f) < 12f,
                game.audio.integratedLufs() + " LUFS");""",
    """        short[] buf = new short[2048];
        int frames = game.renderAudio(buf);
        int peak = 0;
        for (short s : buf) {
            peak = Math.max(peak, Math.abs((int) s));
        }
        check("1024 images rendues", frames == 1024, String.valueOf(frames));
        check("le mix n'est pas muet", peak > 0, "pic " + peak);
        /* six secondes de mix en S1 : la mesure LUFS a de la matiere */
        for (int i = 0; i < 6 * 44100 / 1024; i++) {
            game.renderAudio(buf);
        }
        float lufs = game.audio.integratedLufs();
        check("loudness dans la fenetre -16 LUFS", lufs > -26f && lufs < -6f,
                lufs + " LUFS");""")

rep("""            if (step == LetterReader.STEP_READING || step == LetterReader.STEP_NOTEBOOK
                    || step == LetterReader.STEP_UNFOLD
                    || step == LetterReader.STEP_AFTER) {
                if (guard % 30 == 0) {
                    game.interact();
                }
            }""",
    """            if (step == LetterReader.STEP_READING || step == LetterReader.STEP_NOTEBOOK
                    || step == LetterReader.STEP_UNFOLD
                    || step == LetterReader.STEP_AFTER
                    || step == LetterReader.STEP_ROOM_FREE
                    || step == LetterReader.STEP_ENTRY) {
                if (guard % 30 == 0) {
                    game.interact();
                }
            }
            game.echo.setContactHeld(true);""")

rep("""            check(seq + " : 2 s sans crash", Float.isFinite(game.lohen.y));
        }""",
    """            check(seq + " : 2 s sans crash", Float.isFinite(game.lohen.y));
        }
        check("8 niveaux en memoire apres visite", game.db.levels().size() == 8,
                String.valueOf(game.db.levels().size()));""")

open(P, "w", encoding="utf-8").write(s)

A = "/home/user/Space/android/src/java/com/velmora/lohen/LohenActivity.java"
a = open(A, encoding="utf-8").read()
old = """                synchronized (game) {
                    if (isUi(id)) {
                        releaseInterface(x, y);
                        quit = renderer.hud().consumeQuit();
                    } else {
                        game.touch.onPointerUp(id, x, y);
                    }
                }"""
new = """                synchronized (game) {
                    if (isUi(id)) {
                        releaseInterface(x, y);
                        quit = renderer.hud().consumeQuit();
                    } else {
                        game.touch.onPointerUp(id, x, y);
                    }
                    /* 07.22 : doigt leve, l'appui long de skip est rearme,
                     * sinon la cinematique suivante sauterait toute seule */
                    game.releaseSkip();
                }"""
if old not in a:
    miss.append("Activity pointer up")
else:
    a = a.replace(old, new, 1)
old2 = """                    synchronized (game) {
                        if (isUi(id)) {
                            releaseInterface(e.getX(i), e.getY(i));
                        } else {
                            game.touch.onCancel(id);
                        }
                    }"""
new2 = """                    synchronized (game) {
                        if (isUi(id)) {
                            releaseInterface(e.getX(i), e.getY(i));
                        } else {
                            game.touch.onCancel(id);
                        }
                        game.releaseSkip();
                    }"""
if old2 not in a:
    miss.append("Activity cancel")
else:
    a = a.replace(old2, new2, 1)
open(A, "w", encoding="utf-8").write(a)

if miss:
    print("MANQUES:")
    for m in miss:
        print("  - " + m)
    sys.exit(1)
print("smoke + activity corriges")
