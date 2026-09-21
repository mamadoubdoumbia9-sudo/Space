# -*- coding: utf-8 -*-
"""Patch audio : melodies consonantes (progression Am-Fa-Do-Sol), lits de
bruit divises par ~3, souffle plus doux. Fini l'errance de gamme dissonante."""
import sys

ROOT = "/home/user/Space/android/src/java/com/velmora/lohen/sim"
miss = []


def load(p):
    return [open(ROOT + p, encoding="utf-8").read()]


def rep(store, old, new, count=1):
    n = store[0].count(old)
    if n < count:
        miss.append("x%d<%d %s" % (n, count, old.splitlines()[0][:64]))
        return
    store[0] = store[0].replace(old, new, count if count else n)


# ---------------------------------------------------------------- Synth
sy = load("/audio/Synth.java")
rep(sy, """    public static float scaleNote(int degree, int octave) {""",
    """    /* La colonne melodique : Am - Fa - Do - Sol (i-VI-III-VII). Les degres
     * sont dans l'espace de A_MINOR ; scaleNote se charge des octaves. Un
     * accord par mesure de 4 temps — jamais deux degres consecutifs au
     * hasard, qui faisaient des secondes mineures « effrayantes ». */
    public static final int[][] CHORD_PROG = {{0, 2, 4}, {5, 7, 9}, {2, 4, 6}, {4, 8, 10}};

    /** k-ieme note de l'accord de la mesure bar, a l'octave donnee. */
    public static float chordNote(int bar, int k, int octave) {
        int[] c = CHORD_PROG[((bar % 4) + 4) % 4];
        return scaleNote(c[((k % 3) + 3) % 3], octave);
    }

    public static float scaleNote(int degree, int octave) {""")

# ---------------------------------------------------------------- MusicDirector
md = load("/audio/MusicDirector.java")

# M02
rep(md, """                playPiano(Synth.scaleNote(barIndex % 7, barIndex / 7), gain * 1.1f, 2.2f, 0.8f);""",
    """                playPiano(Synth.chordNote(barIndex, barIndex % 3,
                        1 + (barIndex / 8) % 2), gain * 1.1f, 2.2f, 0.8f);""")
# M03 adouci
rep(md, """                engine.play(Synth.glass(f, gain * 0.5f, 5.5f, 0.35f, rng),""",
    """                engine.play(Synth.glass(f, gain * 0.34f, 5.5f, 0.24f, rng),""")
# M05
rep(md, """                engine.play(Synth.guitar(Synth.scaleNote((barIndex * 3) % 7, 1), gain * att,
                        1.8f, rng), AudioEngine.BUS_MUSIC, pan, dist);""",
    """                engine.play(Synth.guitar(Synth.chordNote(barIndex / 2, barIndex % 3, 1),
                        gain * att, 1.8f, rng), AudioEngine.BUS_MUSIC, pan, dist);""")
rep(md, """                engine.play(Synth.bell(Synth.scaleNote((barIndex * 5) % 7, 2), gain * att * 0.6f,
                        2.4f), AudioEngine.BUS_MUSIC, pan, dist);""",
    """                engine.play(Synth.bell(Synth.chordNote(barIndex / 2, (barIndex + 1) % 3, 2),
                        gain * att * 0.6f, 2.4f), AudioEngine.BUS_MUSIC, pan, dist);""")
# M07/M11
rep(md, """            int degree = (barIndex * 2) % 7;
            engine.play(Synth.guitar(Synth.scaleNote(degree, 1), gain, 1.6f, rng),
                    AudioEngine.BUS_MUSIC, -0.15f, 0f);
            if (barIndex % 4 == 3) {
                engine.play(Synth.guitar(Synth.scaleNote(degree + 2, 1), gain * 0.7f, 1.2f, rng),
                        AudioEngine.BUS_MUSIC, 0.15f, 0f);
            }""",
    """            int chord = barIndex / 2;
            engine.play(Synth.guitar(Synth.chordNote(chord, chord % 3, 1), gain, 1.6f, rng),
                    AudioEngine.BUS_MUSIC, -0.15f, 0f);
            if (barIndex % 4 == 3) {
                engine.play(Synth.guitar(Synth.chordNote(chord, (chord + 1) % 3, 1),
                        gain * 0.7f, 1.2f, rng), AudioEngine.BUS_MUSIC, 0.15f, 0f);
            }""")
# M06
rep(md, """                playPiano(Synth.scaleNote((barIndex * 3) % 7, 1), gain * 0.8f, 1.8f, 0.9f);""",
    """                playPiano(Synth.chordNote(barIndex / 2, barIndex % 3, 1),
                        gain * 0.8f, 1.8f, 0.9f);""")
# M10 : souffle de bol chanvre -> tres doux, cloches consonantes
rep(md, """            engine.play(Synth.noise(gain * 0.35f, 6f, 900f, 120f, 0.11f, 0.5f, rng),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
            if (barIndex % 3 == 0) {
                playBell(Synth.scaleNote((barIndex * 2) % 7, 1), gain * 0.8f, 4f);
            }""",
    """            engine.play(Synth.noise(gain * 0.12f, 6f, 620f, 90f, 0.09f, 0.4f, rng),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
            if (barIndex % 3 == 0) {
                playBell(Synth.chordNote(barIndex / 3, barIndex % 3, 1), gain * 0.8f, 4f);
            }""")
# M12/M13 valse
rep(md, """                engine.play(Synth.cello(Synth.scaleNote(step % 7, 0), gain * 0.9f, 2.6f,
                        true, rng), AudioEngine.BUS_MUSIC, -0.2f, 0f);""",
    """                engine.play(Synth.cello(Synth.chordNote(barIndex / 2, step % 3, 0),
                        gain * 0.9f, 2.6f, true, rng), AudioEngine.BUS_MUSIC, -0.2f, 0f);""")
rep(md, """                playPiano(Synth.scaleNote((step * 2) % 7, 1), gain, 2.4f, 0.55f);""",
    """                playPiano(Synth.chordNote(barIndex / 2, (step + 1) % 3, 1), gain, 2.4f, 0.55f);""")
rep(md, """                engine.play(Synth.voice(Synth.scaleNote((step * 3) % 7, 1), gain * 0.55f, 5f,
                        step % 4, rng), AudioEngine.BUS_MUSIC, 0.25f, 0f);""",
    """                engine.play(Synth.voice(Synth.chordNote(barIndex / 2, step % 3, 1),
                        gain * 0.40f, 5f, step % 4, rng), AudioEngine.BUS_MUSIC, 0.25f, 0f);""")
rep(md, """                playBell(Synth.scaleNote(step % 7, 2), gain * 0.7f, 4.5f);""",
    """                playBell(Synth.chordNote(barIndex / 2, (step + 2) % 3, 2), gain * 0.7f, 4.5f);""")
# M14 boss : basse = fondamentale de l'accord, plus de degres errants
rep(md, """            engine.play(Synth.bass(Synth.scaleNote(barIndex % 3, 0), gain, 3.4f),
                    AudioEngine.BUS_MUSIC, 0f, 0f);""",
    """            engine.play(Synth.bass(Synth.chordNote(barIndex / 2, 0, 0), gain, 3.4f),
                    AudioEngine.BUS_MUSIC, 0f, 0f);""")
# M16
rep(md, """                engine.play(Synth.cello(Synth.scaleNote(barIndex % 5, 0), gain, 4f, true, rng),
                        AudioEngine.BUS_MUSIC, 0f, 0f);""",
    """                engine.play(Synth.cello(Synth.chordNote(barIndex / 2, 0, 0), gain, 4f,
                        true, rng), AudioEngine.BUS_MUSIC, 0f, 0f);""")
rep(md, """                playPiano(Synth.scaleNote((barIndex * 2) % 7, 1), gain * 0.9f, 2.6f, 0.6f);""",
    """                playPiano(Synth.chordNote(barIndex / 2, barIndex % 3, 1),
                        gain * 0.9f, 2.6f, 0.6f);""")
rep(md, """                engine.play(Synth.voice(Synth.scaleNote(barIndex % 7, 1), gain * 0.5f, 6f, 0, rng),
                        AudioEngine.BUS_MUSIC, 0f, 0f);""",
    """                engine.play(Synth.voice(Synth.chordNote(barIndex / 2, (barIndex + 1) % 3, 1),
                        gain * 0.40f, 6f, 0, rng), AudioEngine.BUS_MUSIC, 0f, 0f);""")
# M17 : gresillement dur (22 Hz LFO, 2400 Hz) -> tres lointain
rep(md, """            engine.play(Synth.noise(gain * 0.25f, 4f, 2400f, 900f, 22f, 0.6f, rng),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
            if (barIndex < 4) {
                playPiano(Synth.scaleNote(barIndex * 2 % 7, 1), gain, 3f, 0.7f);
            }""",
    """            engine.play(Synth.noise(gain * 0.07f, 4f, 900f, 300f, 6f, 0.4f, rng),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
            if (barIndex < 4) {
                playPiano(Synth.chordNote(barIndex, barIndex % 3, 1), gain, 3f, 0.7f);
            }""")
# M19
rep(md, """                engine.play(Synth.guitar(Synth.scaleNote((barIndex * 3) % 7, 1), gain * 0.9f,
                        2f, rng), AudioEngine.BUS_MUSIC, 0.2f, 0f);""",
    """                engine.play(Synth.guitar(Synth.chordNote(barIndex / 2, barIndex % 3, 1),
                        gain * 0.9f, 2f, rng), AudioEngine.BUS_MUSIC, 0.2f, 0f);""")
# M20 generique
rep(md, """                engine.play(Synth.voice(Synth.scaleNote(barIndex % 7, 1), gain * 0.8f, 5.5f,
                        barIndex % 4, rng), AudioEngine.BUS_VO, 0f, 0f);""",
    """                engine.play(Synth.voice(Synth.chordNote(barIndex / 2, barIndex % 3, 1),
                        gain * 0.6f, 5.5f, barIndex % 4, rng), AudioEngine.BUS_VO, 0f, 0f);""")
# M21 menu
rep(md, """            engine.play(Synth.noise(gain * 0.3f, 8f, 700f, 90f, 0.07f, 0.45f, rng),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
            if (barIndex % 4 == 0) {
                engine.play(Synth.glass(Synth.scaleNote((barIndex * 2) % 7, 2), gain * 0.5f,
                        6f, 0.3f, rng), AudioEngine.BUS_MUSIC, 0f, 0f);
            }""",
    """            engine.play(Synth.noise(gain * 0.10f, 8f, 560f, 70f, 0.06f, 0.35f, rng),
                    AudioEngine.BUS_MUSIC, 0f, 0f);
            if (barIndex % 4 == 0) {
                engine.play(Synth.glass(Synth.chordNote(barIndex / 4, (barIndex / 2) % 3, 2),
                        gain * 0.5f, 6f, 0.3f, rng), AudioEngine.BUS_MUSIC, 0f, 0f);
            }""")
# M01/M04/M09/M15 generique cordes/piano/cloches
rep(md, """            engine.play(Synth.cello(Synth.scaleNote(barIndex % 7, 0), gain, 3.4f, true, rng),
                    AudioEngine.BUS_MUSIC, -0.1f, 0f);""",
    """            engine.play(Synth.cello(Synth.chordNote(barIndex / 2, 0, 0), gain, 3.4f,
                    true, rng), AudioEngine.BUS_MUSIC, -0.1f, 0f);""")
rep(md, """            playPiano(Synth.scaleNote((barIndex * 3) % 7, 1), gain * 0.85f, 2.4f,
                    "M09".equals(id) ? 0.5f : 0.7f);""",
    """            playPiano(Synth.chordNote(barIndex / 2, barIndex % 3, 1), gain * 0.85f, 2.4f,
                    "M09".equals(id) ? 0.5f : 0.7f);""")
rep(md, """            engine.play(Synth.glass(Synth.scaleNote((barIndex * 5) % 7, 2), gain * 0.5f, 5f,
                    0.3f, rng), AudioEngine.BUS_MUSIC, 0.15f, 0f);""",
    """            engine.play(Synth.glass(Synth.chordNote(barIndex / 2, (barIndex + 2) % 3, 2),
                    gain * 0.5f, 5f, 0.3f, rng), AudioEngine.BUS_MUSIC, 0.15f, 0f);""")
# M18 : vent et gresillement de lampe adoucis (le reste est canon, 19.09)
rep(md, """                engine.play(Synth.noise(gain * 0.4f, 12f, 620f, 80f, 0.05f, 0.5f, rng),
                        AudioEngine.BUS_AMBIENCE, 0f, 0f);
                engine.play(Synth.noise(gain * 0.18f, 4f, 2600f, 1100f, 24f, 0.7f, rng),
                        AudioEngine.BUS_AMBIENCE, 0f, 0f);""",
    """                engine.play(Synth.noise(gain * 0.14f, 12f, 520f, 60f, 0.05f, 0.4f, rng),
                        AudioEngine.BUS_AMBIENCE, 0f, 0f);
                engine.play(Synth.noise(gain * 0.05f, 4f, 1200f, 400f, 8f, 0.4f, rng),
                        AudioEngine.BUS_AMBIENCE, 0f, 0f);""")
rep(md, """            engine.play(Synth.noise(gain * 0.5f, 20f, 620f, 80f, 0.05f, 0.5f, rng),
                    AudioEngine.BUS_AMBIENCE, 0f, 0f);""",
    """            engine.play(Synth.noise(gain * 0.16f, 20f, 520f, 60f, 0.045f, 0.4f, rng),
                    AudioEngine.BUS_AMBIENCE, 0f, 0f);""")
rep(md, """                engine.play(Synth.voice(Synth.ESTEBAN_MOTIF[(barIndex / 6) % 5] * 2f,
                        gain * 0.5f, 7f, (barIndex / 6) % 4, rng),
                        AudioEngine.BUS_VO, 0f, 0f);""",
    """                engine.play(Synth.voice(Synth.ESTEBAN_MOTIF[(barIndex / 6) % 5] * 2f,
                        gain * 0.42f, 7f, (barIndex / 6) % 4, rng),
                        AudioEngine.BUS_VO, 0f, 0f);""")

# ---------------------------------------------------------------- AmbienceDirector
ad = load("/audio/AmbienceDirector.java")
rep(ad, """            engine.play(Synth.noise(0.055f * params[0] + windLevel * 0.020f, 7f,""",
    """            engine.play(Synth.noise(0.030f * params[0] + windLevel * 0.010f, 7f,""")
rep(ad, """0.035f * params[0] + windLevel * 0.014f""",
    """0.018f * params[0] + windLevel * 0.007f""", count=0)

# ---------------------------------------------------------------- souffle (SFX)
sf = load("/audio/SfxDirector.java")
rep(sf, """        engine.play(Synth.noise(0.085f * amount, dur, cutoffs[p] + v * 55f, 140f,
                p == 2 ? 3.1f : 0.4f, 0.6f, rng), AudioEngine.BUS_VO, panBias * 0.2f, distanceBias);""",
    """        engine.play(Synth.noise(0.05f * amount, dur, cutoffs[p] * 0.8f + v * 40f, 110f,
                p == 2 ? 2.4f : 0.3f, 0.5f, rng), AudioEngine.BUS_VO, panBias * 0.2f, distanceBias);""")

for p, store in (("/audio/Synth.java", sy), ("/audio/MusicDirector.java", md),
                 ("/audio/AmbienceDirector.java", ad), ("/audio/SfxDirector.java", sf)):
    open(ROOT + p, "w", encoding="utf-8").write(store[0])

if miss:
    print("MANQUES:")
    for m in miss:
        print("  - " + m)
    sys.exit(1)
print("patch audio ok")
