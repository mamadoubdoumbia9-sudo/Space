# -*- coding: utf-8 -*-
"""07.22 : l'appui long de skip devient un ETAT tenu par le doigt."""
import sys

R = "/home/user/Space/android/src/java/com/velmora/lohen/sim/input/TouchInputRouter.java"
G = "/home/user/Space/android/src/java/com/velmora/lohen/sim/core/LohenGame.java"
S = "/home/user/Space/tools/smoke/SmokeTest.java"
miss = []


def rep(path, old, new, store):
    if old not in store[0]:
        miss.append(path.split("/")[-1] + " :: " + old.splitlines()[0][:60])
        return
    store[0] = store[0].replace(old, new, 1)


r = [open(R, encoding="utf-8").read()]
g = [open(G, encoding="utf-8").read()]
s = [open(S, encoding="utf-8").read()]

rep(R, "    private int camPointer = -1;",
    """    private int camPointer = -1;
    /* 07.22 : l'appui long est un ETAT, pas un evenement. Le doigt pose dans
     * la zone camera, sans bouger, est tenu ; c'est le lecteur de
     * cinematique qui compte ses 1,5 s. */
    private int lpPointer = -1;
    private float lpX;
    private float lpY;""", r)

rep(R, """            camPointer = pointerId;""",
    """            camPointer = pointerId;
            lpPointer = pointerId;
            lpX = x;
            lpY = y;""", r)

rep(R, """    public void onPointerUp(int pointerId, float x, float y) {""",
    """    /** Le doigt qui tenait l'appui long vient-il de se lever ? */
    public boolean longPressHeld() {
        return lpPointer >= 0;
    }

    public void onPointerUp(int pointerId, float x, float y) {
        if (pointerId == lpPointer) {
            lpPointer = -1;
        }""", r)

rep(R, """    public void onCancel(int pointerId) {
        onPointerUp(pointerId, -1f, -1f);
    }""",
    """    public void onCancel(int pointerId) {
        if (pointerId == lpPointer) {
            lpPointer = -1;
        }
        onPointerUp(pointerId, -1f, -1f);
    }""", r)

# invalide l'appui long des que le doigt glisse (un swipe n'est pas un skip)
rep(R, """    public void onPointerMove(int pointerId, float x, float y) {""",
    """    public void onPointerMove(int pointerId, float x, float y) {
        if (pointerId == lpPointer
                && dist(x, y, lpX, lpY) > 24f * dpScale) {
            lpPointer = -1;
        }""", r)

rep(G, "            cine.setSkipHeld(skipHeld, dt);",
    "            cine.setSkipHeld(skipHeld || touch.longPressHeld(), dt);", g)

# --- le test de fumee : tenir le doigt, puis viser un ancrage reel ---------
rep(S, """        game.touch.setScreen(1280f, 720f, 2.75f);
        game.touch.onPointerDown(9, 1000f, 360f);
        for (int i = 0; i < 60 * 12 && game.cine.active(); i++) {
            game.frame(dt);
        }
        game.touch.onPointerUp(9, 1000f, 360f);
        game.releaseSkip();
        for (int i = 0; i < 60 * 3 && game.cine.active(); i++) {
            game.frame(dt);
        }
        check("cinematique terminee par l'appui long", !game.cine.active());""",
    """        game.touch.setScreen(1280f, 720f, 2.75f);
        game.touch.onPointerDown(9, 1000f, 360f);
        int held = 0;
        for (int i = 0; i < 60 * 12 && game.cine.active(); i++) {
            game.frame(dt);
            held++;
        }
        game.touch.onPointerUp(9, 1000f, 360f);
        for (int i = 0; i < 60 * 2 && game.cine.active(); i++) {
            game.frame(dt);
        }
        check("cinematique terminee par l'appui long", !game.cine.active(),
                "doigt tenu " + (held / 60f) + " s");""", s)

rep(S, """        int phase0 = game.grapple.phase();
        /* on vise un ancrage reel du niveau, depuis un point proche */
        float[] anchors = game.level().anchorData;
        check("le niveau a des ancrages", anchors.length >= 4,
                String.valueOf(game.level().anchorCount));
        float ax = anchors[0], ay = anchors[1], az = anchors[2];""",
    """        int phase0 = game.grapple.phase();
        /* on cherche une sequence qui a des ancrages (le grappin s'apprend
         * apres le ponton), puis on vise le premier depuis un point proche */
        float[] anchors = new float[0];
        for (String gs : SequenceAtlas.ORDER) {
            game.enterSequence(gs, true);
            if (game.level() != null && game.level().anchorCount > 0) {
                anchors = game.level().anchorData;
                break;
            }
        }
        check("une sequence offre des ancrages", anchors.length >= 4,
                String.valueOf(anchors.length / 4));
        if (anchors.length < 4) {
            anchors = new float[]{game.lohen.x, game.lohen.y + 4f,
                    game.lohen.z + 6f, 0f};
        }
        float ax = anchors[0], ay = anchors[1], az = anchors[2];""", s)

open(R, "w", encoding="utf-8").write(r[0])
open(G, "w", encoding="utf-8").write(g[0])
open(S, "w", encoding="utf-8").write(s[0])
if miss:
    print("MANQUES:")
    for m in miss:
        print("  - " + m)
    sys.exit(1)
print("appui long = etat")
