#!/usr/bin/env python3
"""Génère tous les effets sonores du jeu (assets/audio/sfx/*.ogg), de façon déterministe."""
import os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import *  # noqa

OUT = os.path.join(AUDIO, "sfx")
made = []


def out(name, x, q=0.4, peak=0.85):
    p = os.path.join(OUT, name + ".ogg")
    write(p, fade(normalize(x, peak)), q)
    made.append(name)


def rng_for(name):
    return np.random.RandomState(abs(hash(name)) % (2 ** 31))


# ── cloches (12 hauteurs + spéciales) ──────────────────────────────────────
BELL_NOTES = [67, 70, 72, 74, 75, 77, 79, 62, 65, 69, 64, 60]  # sol si♭ do ré mi♭ fa sol ré fa la mi do
for i, n in enumerate(BELL_NOTES, 1):
    x = bell(midi(n), 3.2 + 0.1 * i)
    if i == 5:  # la fêlée : partiel dissonant et souffle
        x = bell(midi(n), 2.8, partials=[(0.5, 1.0, 1.0), (1.0, 0.8, 0.6), (1.27, 0.6, 0.5), (1.5, 0.4, 0.4), (2.1, 0.3, 0.3)]) * 0.8
    out("cloche_%02d" % i, reverb_fast(x, 0.5, 0.25))
big = bell(midi(48), 9.5, partials=[(0.5, 1.0, 1.2), (1.0, 0.9, 0.9), (1.19, 0.5, 0.7), (1.5, 0.45, 0.6), (2.0, 0.3, 0.4), (2.5, 0.15, 0.3)])
out("cloche_sans_nom", reverb_fast(big, 0.9, 0.35), q=0.5)
far = lowpass_fast(bell(midi(72), 4.0), 900) * 0.4
out("cloche_lointaine", reverb_fast(far, 0.9, 0.5))
out("cloche_bac", reverb_fast(bell(midi(76), 1.6, strike=1.4) + place(np.zeros(int(SR * 1.6)), bell(midi(76), 1.2), 0.35), 0.4, 0.2))
# toutes les cloches du verger répondent à la fois
r = rng_for("verger")
canvas = np.zeros(int(SR * 7))
for k in range(38):
    place(canvas, bell(midi(r.choice(BELL_NOTES) + r.choice([0, 12])), 3.5) * r.uniform(0.2, 0.7), r.uniform(0, 2.5))
out("verger_toutes_cloches", reverb_fast(canvas, 0.8, 0.35), q=0.5)
out("horloge_tic", np.concatenate([bandpass(rng_for("tic").standard_normal(int(SR * 0.03)), 1500, 5000) * env_exp(int(SR * 0.03), 0.006), np.zeros(int(SR * 0.4))]))

# ── harpe (12 notes pentatoniques) & pétrels ───────────────────────────────
HARP = [55, 57, 60, 62, 64, 67, 69, 72, 74, 76, 79, 81]
for i, n in enumerate(HARP, 1):
    out("harpe_%02d" % i, reverb_fast(pluck(midi(n), 2.2, rng_for("harpe%d" % i), 0.6), 0.5, 0.3))
PETREL = [67, 70, 67, 62]
for i, n in enumerate(PETREL, 1):
    x = vibrato(midi(n + 12), 0.55, 0.012, 6.5) * env_adsr(int(SR * 0.55), 0.03, 0.1, 0.7, 0.2)
    x += 0.3 * vibrato(midi(n + 24), 0.55, 0.012, 6.5) * env_adsr(int(SR * 0.55), 0.03, 0.1, 0.5, 0.2)
    out("petrel_note_%d" % i, reverb_fast(x, 0.7, 0.3))


def whistle_phrase(offset_cents=0.0, dur_note=0.45, gap=0.12, breath=0.2):
    canvas = np.zeros(int(SR * (4 * (dur_note + gap) + 1.2)))
    r = rng_for("whistle")
    for k, n in enumerate(PETREL):
        f = midi(n + 12) * 2 ** (offset_cents / 1200)
        x = vibrato(f, dur_note, 0.008, 5.0) * env_adsr(int(SR * dur_note), 0.02, 0.05, 0.85, 0.12)
        x += breath * bandpass(r.standard_normal(int(SR * dur_note)), f * 0.9, f * 1.1) * env_adsr(int(SR * dur_note), 0.02, 0.05, 0.6, 0.12)
        place(canvas, x, k * (dur_note + gap))
    return canvas


out("sifflet_petrel", reverb_fast(whistle_phrase(), 0.5, 0.25))
out("sifflet_faux", reverb_fast(whistle_phrase(-50), 0.5, 0.25))          # un quart de ton bas (13.4)
out("sifflet_vent", reverb_fast(whistle_phrase(0, 0.8, 0.2, 0.6) * 0.7, 0.8, 0.4))
out("marta_siffle", reverb_fast(whistle_phrase(+15, 0.5, 0.1, 0.3), 0.3, 0.2))
r = rng_for("choeur"); canvas = np.zeros(int(SR * 5))
for k in range(14):
    n = r.choice(PETREL) + 12 + r.choice([0, 12])
    x = vibrato(midi(n) * r.uniform(0.98, 1.02), 0.4, 0.015, 7) * env_adsr(int(SR * 0.4), 0.02, 0.05, 0.6, 0.15) * r.uniform(0.2, 0.6)
    place(canvas, x, r.uniform(0, 4.3))
out("petrels_choeur", reverb_fast(canvas, 0.9, 0.4))

# ── papier, plume, plis ────────────────────────────────────────────────────
for i in range(1, 13):
    r = rng_for("quill%d" % i)
    d = 0.35 + 0.05 * (i % 4)
    x = bandpass(r.standard_normal(int(SR * d)), 1800, 7000) * (0.5 + 0.5 * np.abs(np.sin(np.linspace(0, 3 + i % 3, int(SR * d)))))
    out("quill_scratch_%02d" % i, x * env_adsr(int(SR * d), 0.01, 0.05, 0.8, 0.1), peak=0.5)
for i in range(1, 5):
    r = rng_for("page%d" % i); d = 0.5
    x = bandpass(r.standard_normal(int(SR * d)), 400, 4000) * np.hanning(int(SR * d)) ** 0.5
    x += 0.5 * bandpass(r.standard_normal(int(SR * d)), 2000, 9000) * env_exp(int(SR * d), 0.08)
    out("page_turn_%02d" % i, x, peak=0.6)
for i in range(1, 5):
    r = rng_for("pli%d" % i); d = 0.7
    seg = int(SR * 0.12)
    x = np.zeros(int(SR * d))
    for k in range(3 + i % 2):
        place(x, bandpass(r.standard_normal(seg), 600, 5000) * env_exp(seg, 0.03), k * 0.16 + r.uniform(0, 0.03))
    out("pli_depli_%d" % i, x, peak=0.6)
r = rng_for("etiq"); x = bandpass(r.standard_normal(int(SR * 0.3)), 900, 6000) * env_exp(int(SR * 0.3), 0.05); out("papier_etiquette", x, peak=0.5)
r = rng_for("dech"); x = bandpass(r.standard_normal(int(SR * 0.6)), 300, 6000) * np.linspace(0.3, 1, int(SR * 0.6)) * env_adsr(int(SR * 0.6), 0.01, 0.1, 0.9, 0.05); out("papier_dechire", x, peak=0.7)
r = rng_for("decol"); x = lowpass_fast(bandpass(r.standard_normal(int(SR * 1.4)), 200, 3000), 2500) * env_adsr(int(SR * 1.4), 0.3, 0.3, 0.8, 0.3); out("papier_decolle", x, peak=0.55)
r = rng_for("plie"); x = np.zeros(int(SR * 0.9))
for k in range(4): place(x, bandpass(r.standard_normal(int(SR * 0.1)), 800, 5000) * env_exp(int(SR * 0.1), 0.03), k * 0.2)
out("papier_plie", x, peak=0.55)
r = rng_for("serre"); x = lowpass_fast(r.standard_normal(int(SR * 1.2)), 900) * env_adsr(int(SR * 1.2), 0.4, 0.2, 0.6, 0.5); out("papier_serre", x, peak=0.4)
r = rng_for("ruban"); x = bandpass(r.standard_normal(int(SR * 0.9)), 500, 3500) * env_adsr(int(SR * 0.9), 0.1, 0.2, 0.7, 0.4); out("ruban_denoue", x, peak=0.45)

# ── pas, eau ───────────────────────────────────────────────────────────────
for i in range(1, 4):
    r = rng_for("pas%d" % i); x = np.zeros(int(SR * 0.9))
    for k in range(2):
        place(x, lowpass_fast(r.standard_normal(int(SR * 0.08)), 500 + 200 * i) * env_exp(int(SR * 0.08), 0.02), k * 0.42)
    out("pas_%d" % i, x, peak=0.6)
    r = rng_for("paseau%d" % i); x = np.zeros(int(SR * 1.0))
    for k in range(2):
        place(x, bandpass(r.standard_normal(int(SR * 0.25)), 700, 6000) * env_exp(int(SR * 0.25), 0.06), k * 0.45)
    out("pas_eau_%d" % i, x, peak=0.6)
    r = rng_for("ride%d" % i); d = 1.2 + 0.3 * i
    x = bandpass(r.standard_normal(int(SR * d)), 400 + 100 * i, 3500) * (np.sin(np.linspace(0, np.pi, int(SR * d))) ** 2)
    out("eau_ride_%d" % i, reverb_fast(x, 0.5, 0.3), peak=0.55)
r = rng_for("coque"); x = lowpass_fast(r.standard_normal(int(SR * 2.5)), 700) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.7 * t_axis(2.5))) ; out("eau_coque", x, peak=0.5)
r = rng_for("clap"); x = bandpass(r.standard_normal(int(SR * 0.35)), 600, 4500) * env_exp(int(SR * 0.35), 0.07); out("clapotis", reverb_fast(x, 0.9, 0.5), peak=0.6)
r = rng_for("bassin"); x = lowpass_fast(r.standard_normal(int(SR * 5)), 400) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.12 * t_axis(5) - 1.5)); out("bassin_respire", reverb_fast(x, 0.9, 0.5), peak=0.45)
r = rng_for("vagues"); x = lowpass_fast(r.standard_normal(int(SR * 6)), 900) * (0.4 + 0.6 * np.abs(np.sin(2 * np.pi * 0.15 * t_axis(6)))); out("vagues_douces", x, peak=0.45)
r = rng_for("maree"); x = lowpass_fast(r.standard_normal(int(SR * 6)), 500) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.08 * t_axis(6))); out("maree_basse", x, peak=0.4)
r = rng_for("merloin"); x = lowpass_fast(r.standard_normal(int(SR * 6)), 350) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.1 * t_axis(6))); out("mer_lointaine", x, peak=0.35)
r = rng_for("sable"); x = np.zeros(int(SR * 2.4))
for k in range(6): place(x, bandpass(r.standard_normal(int(SR * 0.3)), 300, 2500) * env_exp(int(SR * 0.3), 0.08), k * 0.38)
out("sable_creuse", x, peak=0.55)

# ── laiton, mécanique, bois ────────────────────────────────────────────────
for i in range(1, 4):
    x = bell(midi(84 + 3 * i), 0.5, partials=[(1.0, 1.0, 0.5), (2.76, 0.4, 0.3), (5.4, 0.2, 0.2)], strike=1.2)
    out("laiton_assemble_%d" % i, x, peak=0.6)
    r = rng_for("levier%d" % i); x = np.zeros(int(SR * 0.8))
    place(x, lowpass_fast(r.standard_normal(int(SR * 0.15)), 900) * env_exp(int(SR * 0.15), 0.04), 0)
    place(x, bell(midi(60 + i), 0.5, partials=[(1.0, 1.0, 0.4), (2.4, 0.5, 0.3)], strike=1.5) * 0.7, 0.18)
    out("levier_%d" % i, x, peak=0.7)
x = bell(midi(96), 0.12, partials=[(1.0, 1.0, 0.3), (3.1, 0.3, 0.2)], strike=1.5); out("molette_cran", x, peak=0.5)
r = rng_for("wood"); x = lowpass_fast(r.standard_normal(int(SR * 0.9)), 600) * np.hanning(int(SR * 0.9)) ** 0.3; x[int(SR * 0.6):] += bell(midi(72), 0.3, strike=1.0)[: len(x) - int(SR * 0.6)] * 0.5; out("wood_groove_01", x, peak=0.65)
r = rng_for("tiroir"); x = lowpass_fast(r.standard_normal(int(SR * 0.7)), 800) * env_adsr(int(SR * 0.7), 0.02, 0.2, 0.6, 0.2); out("tiroir", x, peak=0.55)
x = np.zeros(int(SR * 1.6)); r = rng_for("chaine")
for k in range(7): place(x, bell(midi(88 + r.randint(-3, 3)), 0.25, partials=[(1.0, 1.0, 0.3), (2.3, 0.5, 0.2)], strike=1.5) * r.uniform(0.3, 0.8), 0.08 * k + r.uniform(0, 0.03))
place(x, lowpass_fast(r.standard_normal(int(SR * 0.3)), 400) * env_exp(int(SR * 0.3), 0.08), 0.8)
out("porte_chaine", reverb_fast(x, 0.6, 0.3), peak=0.7)
x = np.zeros(int(SR * 1.2)); place(x, bell(midi(79), 0.4, partials=[(1.0, 1.0, 0.4), (2.7, 0.4, 0.3)], strike=1.2), 0.0); place(x, bell(midi(84), 0.6, partials=[(1.0, 1.0, 0.5), (2.7, 0.4, 0.3)], strike=1.0), 0.5); out("coffret_laiton", x, peak=0.6)
out("laiton_pose", bell(midi(74), 0.9, partials=[(1.0, 1.0, 0.5), (2.5, 0.5, 0.3), (4.1, 0.2, 0.2)], strike=1.2), peak=0.55)
out("coffre_ouvre", reverb_fast(mix(lowpass_fast(rng_for("coffre").standard_normal(int(SR * 0.8)), 500) * env_adsr(int(SR * 0.8), 0.05, 0.3, 0.5, 0.3), place(np.zeros(int(SR * 1.2)), bell(midi(65), 0.5, strike=1.5) * 0.5, 0.4)), 0.4, 0.2), peak=0.6)
x = np.zeros(int(SR * 2.0)); r = rng_for("aig")
for k, n in enumerate([60, 64, 67]): place(x, bell(midi(n), 0.8, partials=[(1.0, 1.0, 0.5), (2.4, 0.4, 0.3)], strike=1.4), k * 0.25)
place(x, lowpass_fast(r.standard_normal(int(SR * 0.6)), 300) * env_exp(int(SR * 0.6), 0.1), 0.9)
out("aiguillage_ok", reverb_fast(x, 0.5, 0.25), peak=0.7)
r = rng_for("sac"); out("sac_pose", lowpass_fast(r.standard_normal(int(SR * 0.4)), 350) * env_exp(int(SR * 0.4), 0.06), peak=0.6)
r = rng_for("sac2"); out("sac_leve", bandpass(r.standard_normal(int(SR * 0.5)), 200, 1500) * env_adsr(int(SR * 0.5), 0.1, 0.1, 0.5, 0.2), peak=0.5)
r = rng_for("egout"); x = np.zeros(int(SR * 1.5))
for k in range(9): place(x, bandpass(r.standard_normal(int(SR * 0.06)), 1500, 6000) * env_exp(int(SR * 0.06), 0.01) * r.uniform(0.2, 0.7), 0.1 + k * 0.14 + r.uniform(0, 0.05))
out("egouttoir", x, peak=0.5)
out("pince_photo", bell(midi(91), 0.2, partials=[(1.0, 1.0, 0.3), (2.9, 0.4, 0.2)], strike=1.5), peak=0.5)
out("metronome", lowpass_fast(rng_for("metro").standard_normal(int(SR * 0.05)), 2500) * env_exp(int(SR * 0.05), 0.008), peak=0.6)
out("metronome_grave", lowpass_fast(rng_for("metro2").standard_normal(int(SR * 0.08)), 900) * env_exp(int(SR * 0.08), 0.015), peak=0.7)
x = np.zeros(int(SR * 3.2))
for k, n in enumerate([60, 62, 64, 67, 69, 72, 74, 76]):
    y = np.sin(2 * np.pi * midi(n + 12) * t_axis(1.2)) * env_exp(int(SR * 1.2), 0.35)
    place(x, y * 0.5, k * 0.28)
out("verres_gamme", reverb_fast(x, 0.7, 0.35), peak=0.6)


def music_box(detune_4th, drop_4th=False, dur_note=0.32):
    seq = [67, 70, 67, 62, 67, 70, 74, 72, 70, 67, 65, 67]
    canvas = np.zeros(int(SR * (len(seq) * dur_note + 2.5)))
    for k, n in enumerate(seq):
        f = midi(n + 24)
        if k % 4 == 3:
            f *= 2 ** (detune_4th / 1200)
            if drop_4th:
                continue
        y = np.sin(2 * np.pi * f * t_axis(1.5)) * env_exp(int(SR * 1.5), 0.3) + 0.3 * np.sin(2 * np.pi * f * 3 * t_axis(1.5)) * env_exp(int(SR * 1.5), 0.08)
        place(canvas, y, k * dur_note)
    return canvas


out("boite_musique_desaccord", reverb_fast(music_box(-70, True), 0.4, 0.25), peak=0.6)
out("boite_musique_boite", reverb_fast(music_box(-25, False, 0.36), 0.4, 0.25), peak=0.6)
out("boite_musique_juste", reverb_fast(music_box(0), 0.5, 0.3), peak=0.65)
r = rng_for("lime"); x = bandpass(r.standard_normal(int(SR * 1.4)), 2500, 9000) * (0.5 + 0.5 * np.abs(np.sin(2 * np.pi * 3 * t_axis(1.4)))) * env_adsr(int(SR * 1.4), 0.05, 0.1, 0.8, 0.2); out("ressort_lime", x, peak=0.5)
x = mix(np.sin(2 * np.pi * midi(67) * t_axis(1.5)), np.sin(2 * np.pi * midi(67) * 2 ** (-45 / 1200) * 1.5 * t_axis(1.5))) * env_adsr(int(SR * 1.5), 0.05, 0.2, 0.7, 0.4); out("accord_faux", x, peak=0.5)
x = lowpass_fast(rng_for("souffle").standard_normal(int(SR * 2.5)), 180) * env_adsr(int(SR * 2.5), 0.6, 0.3, 0.8, 0.8); out("souffle_grave", x, peak=0.6)
x = np.zeros(int(SR * 6.5)); r = rng_for("fleur")
for k in range(9): place(x, bell(midi(72 + k * 2), 1.2, partials=[(1.0, 1.0, 0.5), (2.5, 0.4, 0.3)], strike=0.8) * 0.4, 0.4 + k * 0.6)
place(x, lowpass_fast(r.standard_normal(int(SR * 5)), 500) * np.linspace(0.05, 0.4, int(SR * 5)) * env_adsr(int(SR * 5), 0.5, 0.5, 0.8, 1.0), 0)
place(x, whistle_phrase(0, 0.6, 0.15, 0.4) * 0.5, 3.0)
out("boite_fleur", reverb_fast(x, 0.8, 0.35), q=0.5, peak=0.7)
x = mix(lowpass_fast(rng_for("avale").standard_normal(int(SR * 1.4)), 300) * env_adsr(int(SR * 1.4), 0.3, 0.2, 0.5, 0.5), place(np.zeros(int(SR * 1.4)), bell(midi(60), 0.6, strike=1.0) * 0.5, 0.9)); out("boite_avale", reverb_fast(x, 0.7, 0.3), peak=0.6)
x = np.zeros(int(SR * 1.1))
for k in range(2): place(x, lowpass_fast(rng_for("tap%d" % k).standard_normal(int(SR * 0.12)), 700) * env_exp(int(SR * 0.12), 0.03) + bell(midi(55), 0.4, strike=1.2)[: int(SR * 0.12)] * 0.3, k * 0.45)
out("garde_corps_deux_tapes", x, peak=0.6)
out("tasse_pose", mix(bell(midi(88), 0.5, partials=[(1.0, 1.0, 0.4), (2.2, 0.5, 0.3), (3.9, 0.2, 0.2)], strike=1.2), lowpass_fast(rng_for("tasse").standard_normal(int(SR * 0.1)), 500) * env_exp(int(SR * 0.1), 0.02)), peak=0.55)
out("pierre_glisse", bandpass(rng_for("pierre").standard_normal(int(SR * 0.6)), 200, 2500) * env_adsr(int(SR * 0.6), 0.02, 0.1, 0.6, 0.2), peak=0.55)
x = lowpass_fast(rng_for("grince").standard_normal(int(SR * 0.9)), 700) * (0.5 + 0.5 * np.sin(2 * np.pi * 9 * t_axis(0.9))) * env_adsr(int(SR * 0.9), 0.1, 0.2, 0.6, 0.3); out("bois_grince", x, peak=0.5)
out("verrou_doux", bell(midi(64), 0.35, partials=[(1.0, 1.0, 0.4), (1.8, 0.3, 0.2)], strike=1.0) * 0.7, peak=0.45)
out("ui_tap", bell(midi(93), 0.09, partials=[(1.0, 1.0, 0.3)], strike=0.6), peak=0.35)
out("montre_bat", np.tile(lowpass_fast(rng_for("montre").standard_normal(int(SR * 0.02)), 3000) * env_exp(int(SR * 0.02), 0.004), 4).reshape(-1) * 0.8 if False else place(place(np.zeros(int(SR * 1.0)), lowpass_fast(rng_for("montre").standard_normal(int(SR * 0.02)), 3000) * env_exp(int(SR * 0.02), 0.004), 0), lowpass_fast(rng_for("montre2").standard_normal(int(SR * 0.02)), 3000) * env_exp(int(SR * 0.02), 0.004), 0.5), peak=0.4)
out("piano_note", reverb_fast(mix(pluck(midi(62), 2.5, rng_for("piano"), 0.3, 0.998), 0.4 * pluck(midi(74), 2.0, rng_for("piano2"), 0.3, 0.997)), 0.6, 0.35), peak=0.6)
r = rng_for("four"); out("four_ronfle", lowpass_fast(r.standard_normal(int(SR * 3)), 250) * (0.7 + 0.3 * np.sin(2 * np.pi * 1.3 * t_axis(3))), peak=0.4)

# ── radio, juke-box, encre, animaux ────────────────────────────────────────
r = rng_for("radio"); x = bandpass(r.standard_normal(int(SR * 2.2)), 800, 3500) * (0.4 + 0.6 * (r.uniform(0, 1, int(SR * 2.2)) > 0.97).astype(float)) * env_adsr(int(SR * 2.2), 0.1, 0.3, 0.7, 0.5); out("radio_gresil", x, peak=0.45)
r = rng_for("juke"); x = bandpass(r.standard_normal(int(SR * 1.6)), 300, 2500) * (0.3 + 0.7 * np.abs(np.sin(2 * np.pi * 0.9 * t_axis(1.6)))) * env_adsr(int(SR * 1.6), 0.2, 0.2, 0.6, 0.4); out("jukebox_gresil", x, peak=0.4)
x = np.zeros(int(SR * 3.0)); place(x, lowpass_fast(rng_for("disq").standard_normal(int(SR * 0.6)), 1200) * env_exp(int(SR * 0.6), 0.15), 0)
for k in range(30): place(x, bandpass(rng_for("crk%d" % k).standard_normal(int(SR * 0.02)), 1500, 5000) * env_exp(int(SR * 0.02), 0.004) * 0.3, 0.6 + k * 0.08)
place(x, music_box(0, False, 0.3)[: int(SR * 2)] * 0.5, 0.9)
out("jukebox_disque", x, peak=0.6)
x = np.zeros(int(SR * 3.5)); r = rng_for("encre")
for k in range(40):
    f = midi(r.choice([79, 84, 86, 91, 96]))
    place(x, np.sin(2 * np.pi * f * t_axis(0.5)) * env_exp(int(SR * 0.5), 0.08) * r.uniform(0.1, 0.4), r.uniform(0, 3))
place(x, lowpass_fast(r.standard_normal(int(SR * 3)), 600) * np.linspace(0.3, 0, int(SR * 3)), 0.2)
out("pluie_encre", reverb_fast(x, 0.9, 0.5), peak=0.6)
r = rng_for("souffle_f"); out("filou_souffle", lowpass_fast(r.standard_normal(int(SR * 0.5)), 900) * env_adsr(int(SR * 0.5), 0.05, 0.1, 0.7, 0.25), peak=0.45)
x = np.zeros(int(SR * 1.0)); r = rng_for("pattes")
for k in range(4): place(x, lowpass_fast(r.standard_normal(int(SR * 0.06)), 1200) * env_exp(int(SR * 0.06), 0.015), k * 0.18)
out("filou_pattes", x, peak=0.45)
x = vibrato(midi(74), 0.7, 0.03, 4) * env_adsr(int(SR * 0.7), 0.1, 0.2, 0.6, 0.3) * 0.5 + 0.3 * vibrato(midi(86), 0.7, 0.03, 4) * env_adsr(int(SR * 0.7), 0.1, 0.2, 0.4, 0.3); out("filou_gemit", lowpass_fast(x, 2500), peak=0.4)
x = np.zeros(int(SR * 0.9))
for k in range(2):
    y = (vibrato(midi(62), 0.16, 0.05, 30, "tri") + 0.5 * vibrato(midi(74), 0.16, 0.05, 30, "tri")) * env_adsr(int(SR * 0.16), 0.01, 0.03, 0.8, 0.05)
    place(x, lowpass_fast(y, 3000), k * 0.32)
out("filou_aboie", reverb_fast(x, 0.5, 0.2), peak=0.6)
t = t_axis(0.8); f = midi(79) * (1 + 0.15 * np.sin(np.pi * t / 0.8)); x = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_adsr(int(SR * 0.8), 0.05, 0.2, 0.7, 0.2); x = lowpass_fast(x + 0.4 * np.sin(4 * np.pi * np.cumsum(f) / SR), 3500); out("chat_miaule", reverb_fast(x, 0.4, 0.2), peak=0.4)
# tom, au loin : un cri d'enfant joyeux évoqué (voyelle formantique) puis rire
t = t_axis(1.3); f = 330 * (1 + 0.25 * np.sin(np.pi * t / 1.3)); base = np.sin(2 * np.pi * np.cumsum(f) / SR)
formant = bandpass(base * (1 + 0.5 * np.sign(np.sin(2 * np.pi * 8 * t))), 600, 1800) * env_adsr(int(SR * 1.3), 0.05, 0.3, 0.6, 0.3)
x = np.zeros(int(SR * 2.4)); place(x, formant, 0)
for k in range(4): place(x, bandpass(base[: int(SR * 0.12)] * 1.5, 500, 1500) * env_exp(int(SR * 0.12), 0.04) * 0.5, 1.3 + k * 0.17)
out("tom_till_gagne", reverb_fast(lowpass_fast(x, 2200) * 0.6, 0.9, 0.55), peak=0.4)

# ── vents, machines ───────────────────────────────────────────────────────
for name, cutoff, dur, mod in (("vent_quai", 900, 4, 0.2), ("vent_falaise", 1200, 5, 0.15), ("cables_vent", 500, 5, 0.3), ("vent_tole", 700, 4, 0.5)):
    r = rng_for(name); x = lowpass_fast(r.standard_normal(int(SR * dur)), cutoff) * (0.5 + 0.5 * np.sin(2 * np.pi * mod * t_axis(dur)))
    if name == "cables_vent":
        x += 0.35 * vibrato(midi(45), dur, 0.01, 0.3) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.2 * t_axis(dur)))
    if name == "vent_tole":
        x += 0.3 * vibrato(midi(40), dur, 0.02, 0.4, "tri")
    out(name, x, peak=0.5)
r = rng_for("etoffes"); x = np.zeros(int(SR * 3))
for k in range(9): place(x, bandpass(r.standard_normal(int(SR * 0.12)), 300, 3000) * env_exp(int(SR * 0.12), 0.03) * r.uniform(0.4, 1), r.uniform(0, 2.8))
out("etoffes_claquent", x, peak=0.55)
x = np.zeros(int(SR * 2.0))
for k in range(5): place(x, bandpass(rng_for("balise%d" % k).standard_normal(int(SR * 0.05)), 2000, 6000) * env_exp(int(SR * 0.05), 0.01), 0.2 + k * 0.35)
out("balise_clac", x, peak=0.5)
t = t_axis(4); x = lowpass_fast(rng_for("moteur").standard_normal(len(t)), 300) * 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 27 * t)) * 0.3; out("bac_moteur", lowpass_fast(x * env_adsr(len(t), 0.8, 0.3, 0.9, 0.5), 600), peak=0.5)
t = t_axis(3); f = 27 * np.linspace(1, 0.2, len(t)); x = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * 0.3 * np.linspace(1, 0, len(t)) + lowpass_fast(rng_for("moteur2").standard_normal(len(t)), 400) * np.linspace(0.4, 0.05, len(t)); out("bac_moteur_coupe", lowpass_fast(x, 700), peak=0.5)
t = t_axis(5); x = lowpass_fast(rng_for("crev").standard_normal(len(t)), 900) * np.linspace(0.2, 0.6, len(t)) + 0.4 * vibrato(midi(43), 5, 0.02, 0.5, "tri") * np.linspace(0, 1, len(t)); place(x, bell(midi(76), 1.0, strike=1.4) * 0.5, 0.1); out("crevette_depart", x, peak=0.55)

print("%d effets sonores écrits dans %s" % (len(made), OUT))
