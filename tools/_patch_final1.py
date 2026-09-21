# -*- coding: utf-8 -*-
"""Trois cordes restantes : cinematiques un seul passage, descente de la lettre,
calibration du mix vers -16 LUFS."""
import sys

G = "/home/user/Space/android/src/java/com/velmora/lohen/sim/core/LohenGame.java"
E = "/home/user/Space/android/src/java/com/velmora/lohen/sim/audio/AudioEngine.java"
S = "/home/user/Space/tools/smoke/SmokeTest.java"
miss = []


def rep(path, old, new, store):
    if old not in store[0]:
        miss.append(path.split("/")[-1] + " :: " + old.splitlines()[0][:64])
        return
    store[0] = store[0].replace(old, new, 1)


g = [open(G, encoding="utf-8").read()]
e = [open(E, encoding="utf-8").read()]
s = [open(S, encoding="utf-8").read()]

# --- A. une cinematique ne passe qu'une fois ------------------------------
rep(G, "    private String pendingCinematic = \"\";",
    """    private String pendingCinematic = "";
    /* Chaque cinematique du chapitre est un evenement vecu une fois : ni un
     * trigger reveille par une reprise, ni un chargement, ne la rejouent. */
    private final java.util.HashSet<String> cineDone =
            new java.util.HashSet<String>(16);""", g)

rep(G, """        pendingCinematic = id;
        if (cine.play(id)) {""",
    """        pendingCinematic = id;
        if (cineDone.contains(id)) {
            pendingCinematic = "";
            return false;
        }
        if (cine.play(id)) {
            cineDone.add(id);""", g)

rep(G, """        } else if (\"cinematic\".equals(type)) {
            playCinematic(t.target);""",
    """        } else if (\"cinematic\".equals(type)) {
            if (!cineDone.contains(t.target)) {
                playCinematic(t.target);
            }""", g)

# --- B. apres la lettre : plier, ranger, redescendre (19.10 / 19.11) ------
rep(G, """                if (ls == LetterReader.STEP_NOTEBOOK) {
                    letter.turnPage();
                } else if (ls == LetterReader.STEP_ROOM_FREE
                        && letter.notebookPage() < LetterReader.NOTEBOOK_EXTRACTS) {
                    letter.openNotebook();
                } else if (ls != LetterReader.STEP_READING) {
                    letter.interactWithLetter();
                }""",
    """                if (ls == LetterReader.STEP_NOTEBOOK) {
                    letter.turnPage();
                } else if (ls == LetterReader.STEP_ROOM_FREE) {
                    if (letter.notebookPage() < LetterReader.NOTEBOOK_EXTRACTS) {
                        letter.openNotebook();
                    } else if (letter.scroll() < 1f) {
                        letter.interactWithLetter();
                    } else {
                        /* la lettre est lue : on la plie, on la range, et la
                         * descente commence (19.11) — 90 s, sans coupure */
                        letter.storeLetter();
                        letter.beginDescent();
                    }
                } else if (ls != LetterReader.STEP_READING) {
                    letter.interactWithLetter();
                }""", g)

# --- C. calibration du mix : -16 LUFS atteignables (13.32) ----------------
rep(E, """    public static final float TARGET_LUFS = -16f;      /* 13.32 */""",
    """    public static final float TARGET_LUFS = -16f;      /* 13.32 */
    /**
     * Calibrage fixe du bus de sortie. Les voix synthetiques sortent autour de
     * -35 LUFS bruts : ce trim de +14 dB les pose a hauteur de mix, et le
     * normalisateur n'a plus que les fines variations a corriger — jamais un
     * compresseur agressif (13.32). Le limiteur tanh encaisse les cretes.
     */
    public static final float MIX_TRIM = 5.0f;""", e)

rep(E, """            float l = softClip(mixL[i] * normalizeGain * masterTrim);
            float r = softClip(mixR[i] * normalizeGain * masterTrim);""",
    """            float l = softClip(mixL[i] * normalizeGain * masterTrim * MIX_TRIM);
            float r = softClip(mixR[i] * normalizeGain * masterTrim * MIX_TRIM);""", e)

rep(E, """                normalizeGain = Maths.clamp(normalizeGain, 0.35f, 2.6f);""",
    """                normalizeGain = Maths.clamp(normalizeGain, 0.4f, 3.2f);""", e)

# --- D. test de fumee : garde de la lettre, reprise exacte, fenetre LUFS --
rep(S, """        int guard = 0;
        while (game.letter.step() != LetterReader.STEP_CREDITS && guard < 60 * 240) {""",
    """        int guard = 0;
        while (game.letter.step() != LetterReader.STEP_CREDITS && guard < 60 * 420) {""", s)

rep(S, """        check("reprise du slot 1", game.loadSlot(1));
        for (int i = 0; i < 30; i++) {
            game.frame(dt);          /* la physique se repose apres teleport */
        }
        check("Lohen rendu a sa position sauvegardee",
                Math.abs(game.lohen.x - sx) < 0.6f && Math.abs(game.lohen.z - sz) < 0.6f,
                game.lohen.x + "," + game.lohen.z);""",
    """        check("reprise du slot 1", game.loadSlot(1));
        check("Lohen rendu exactement a sa position sauvegardee",
                Math.abs(game.lohen.x - sx) < 1e-3f && Math.abs(game.lohen.z - sz) < 1e-3f,
                game.lohen.x + "," + game.lohen.z);
        for (int i = 0; i < 30; i++) {
            game.frame(dt);          /* la physique se repose apres teleport */
        }""", s)

rep(S, """        float lufs = game.audio.shortTermLufs();
        check("loudness court terme dans la fenetre -16 LUFS",
                lufs > -26f && lufs < -8f, lufs + " LUFS");""",
    """        float lufs = game.audio.shortTermLufs();
        check("loudness court terme dans la fenetre -16 LUFS",
                lufs > -26f && lufs < -10f, lufs + " LUFS");""", s)

open(G, "w", encoding="utf-8").write(g[0])
open(E, "w", encoding="utf-8").write(e[0])
open(S, "w", encoding="utf-8").write(s[0])
if miss:
    print("MANQUES:")
    for x in miss:
        print("  - " + x)
    sys.exit(1)
print("correctifs finaux 1 ok")
