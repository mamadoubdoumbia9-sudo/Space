#!/usr/bin/env python3
"""Ambiances sonores par zone (boucles de 60 s, assets/audio/ambience/*.ogg) : mer, vent, oiseaux, machines, feu, café."""
import os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import *  # noqa

OUT = os.path.join(AUDIO, "ambience")
DUR = 60.0 * float(os.environ.get("AUDIO_LEN", "1"))


def sea(rng, level=0.4, period=9.0, cutoff=900):
    t = t_axis(DUR)
    swell = 0.45 + 0.55 * (0.5 + 0.5 * np.sin(2 * np.pi * t / period)) ** 1.6
    return lowpass_fast(rng.standard_normal(len(t)), cutoff) * swell * level


def wind(rng, level=0.3, cutoff=600, gust=0.07):
    t = t_axis(DUR)
    g = 0.5 + 0.5 * np.sin(2 * np.pi * gust * t + rng.uniform(0, 6)) * np.sin(2 * np.pi * gust * 2.3 * t)
    return lowpass_fast(rng.standard_normal(len(t)), cutoff) * (0.3 + 0.7 * g) * level


def birds(rng, count=10, notes=(67, 70, 62), level=0.25, high=12):
    canvas = np.zeros(int(SR * DUR))
    for _ in range(count):
        n = rng.choice(notes) + high
        d = rng.uniform(0.25, 0.6)
        x = vibrato(midi(n) * rng.uniform(0.97, 1.03), d, 0.015, 6.5) * env_adsr(int(SR * d), 0.02, 0.05, 0.6, 0.15)
        place(canvas, x * rng.uniform(0.2, 1) * level, rng.uniform(0, DUR - 1))
    return lowpass_fast(canvas, 6000)


def drips(rng, count=30, level=0.3):
    canvas = np.zeros(int(SR * DUR))
    for _ in range(count):
        f = rng.uniform(900, 2600)
        x = np.sin(2 * np.pi * f * t_axis(0.25) * (1 - 0.3 * t_axis(0.25))) * env_exp(int(SR * 0.25), 0.05)
        place(canvas, x * rng.uniform(0.2, 1) * level, rng.uniform(0, DUR - 1))
    return canvas


def clinks(rng, count=25, level=0.15):
    canvas = np.zeros(int(SR * DUR))
    for _ in range(count):
        x = bell(midi(rng.choice([88, 91, 93, 96])), 0.4, partials=[(1.0, 1.0, 0.4), (2.7, 0.4, 0.3)], strike=0.8)
        place(canvas, x * rng.uniform(0.2, 0.8) * level, rng.uniform(0, DUR - 1))
    return canvas


def murmur(rng, level=0.2):
    t = t_axis(DUR)
    base = bandpass(rng.standard_normal(len(t)), 250, 1200)
    mod = 0.5 + 0.5 * np.sin(2 * np.pi * 0.7 * t) * np.sin(2 * np.pi * 1.9 * t + 1) * np.sin(2 * np.pi * 0.13 * t)
    return base * (0.4 + 0.6 * mod) * level


def tick(rng, level=0.25, bpm=60):
    canvas = np.zeros(int(SR * DUR))
    x = lowpass_fast(rng.standard_normal(int(SR * 0.02)), 3000) * env_exp(int(SR * 0.02), 0.004)
    k = 0
    while k * 60 / bpm < DUR:
        place(canvas, x * (1.0 if k % 2 == 0 else 0.7) * level, k * 60 / bpm)
        k += 1
    return canvas


def machine(rng, hz=27, level=0.2, cutoff=500):
    t = t_axis(DUR)
    x = np.sign(np.sin(2 * np.pi * hz * t)) * 0.4 + lowpass_fast(rng.standard_normal(len(t)), 300) * 0.6
    return lowpass_fast(x, cutoff) * level * (0.85 + 0.15 * np.sin(2 * np.pi * 0.05 * t))


def fire(rng, level=0.25):
    t = t_axis(DUR)
    base = lowpass_fast(rng.standard_normal(len(t)), 400) * 0.5
    crack = np.zeros(len(t))
    for _ in range(90):
        x = bandpass(rng.standard_normal(int(SR * 0.03)), 1500, 6000) * env_exp(int(SR * 0.03), 0.006)
        place(crack, x * rng.uniform(0.3, 1), rng.uniform(0, DUR - 0.1))
    return (base + crack * 0.8) * level


def loom(rng, level=0.2, bpm=72):
    canvas = np.zeros(int(SR * DUR))
    k = 0
    while k * 60 / bpm < DUR:
        place(canvas, bandpass(rng.standard_normal(int(SR * 0.08)), 300, 1500) * env_exp(int(SR * 0.08), 0.02) * level, k * 60 / bpm)
        place(canvas, lowpass_fast(rng.standard_normal(int(SR * 0.06)), 250) * env_exp(int(SR * 0.06), 0.015) * level * 0.7, k * 60 / bpm + 0.5 * 60 / bpm)
        k += 1
    return canvas


def paper_flutter(rng, count=60, level=0.15):
    canvas = np.zeros(int(SR * DUR))
    for _ in range(count):
        x = bandpass(rng.standard_normal(int(SR * 0.09)), 400, 4000) * env_exp(int(SR * 0.09), 0.025)
        place(canvas, x * rng.uniform(0.2, 1) * level, rng.uniform(0, DUR - 0.2))
    return canvas


def bells_far(rng, count=8, level=0.12):
    canvas = np.zeros(int(SR * DUR))
    for _ in range(count):
        x = lowpass_fast(bell(midi(rng.choice([67, 70, 72, 74, 77, 79])), 3.0), 1200)
        place(canvas, x * rng.uniform(0.3, 1) * level, rng.uniform(0, DUR - 3))
    return canvas


def pad_drone(rng, notes, level=0.15, cutoff=2000):
    t = t_axis(DUR)
    y = np.zeros(len(t))
    for n in notes:
        for det in (-0.003, 0.003):
            y += np.sin(2 * np.pi * midi(n) * (1 + det) * t + rng.uniform(0, 6))
    return lowpass_fast(y / (2 * len(notes)), cutoff) * level * (0.7 + 0.3 * np.sin(2 * np.pi * 0.03 * t))


AMBIENCES = {
    "amb_cabinet": lambda r: mix(tick(r, 0.18), lowpass_fast(r.standard_normal(int(SR * DUR)), 200) * 0.06, sea(r, 0.05, 11, 500)),
    "amb_ville": lambda r: mix(wind(r, 0.12, 500), birds(r, 6, (72, 76, 79), 0.12, 12), sea(r, 0.08, 10, 600), murmur(r, 0.05)),
    "amb_cafe": lambda r: mix(murmur(r, 0.18), clinks(r, 30, 0.12), lowpass_fast(r.standard_normal(int(SR * DUR)), 150) * 0.05),
    "amb_port": lambda r: mix(sea(r, 0.3, 8, 900), wind(r, 0.15, 700), birds(r, 8, (79, 81, 84), 0.15, 12), clinks(r, 8, 0.05)),
    "amb_estran": lambda r: mix(sea(r, 0.22, 12, 700), wind(r, 0.18, 800), birds(r, 12, (67, 70, 62), 0.15, 24), drips(r, 10, 0.08)),
    "amb_gare": lambda r: mix(wind(r, 0.2, 400), tick(r, 0.08, 60), paper_flutter(r, 10, 0.05), lowpass_fast(r.standard_normal(int(SR * DUR)), 120) * 0.05),
    "amb_voies": lambda r: mix(wind(r, 0.25, 600, 0.05), paper_flutter(r, 20, 0.05), birds(r, 4, (72, 76), 0.08, 12)),
    "amb_funiculaire": lambda r: mix(machine(r, 24, 0.12, 400), tick(r, 0.06, 90), wind(r, 0.1, 500)),
    "amb_verger": lambda r: mix(wind(r, 0.22, 900, 0.09), bells_far(r, 14, 0.14), birds(r, 10, (67, 70, 62), 0.14, 12)),
    "amb_atelier_ysolde": lambda r: mix(loom(r, 0.16), fire(r, 0.14), wind(r, 0.06, 400)),
    "amb_bois": lambda r: mix(wind(r, 0.25, 1100, 0.06), paper_flutter(r, 120, 0.12), birds(r, 5, (76, 79), 0.08, 12)),
    "amb_corniche": lambda r: mix(wind(r, 0.32, 1200, 0.08), birds(r, 26, (67, 70, 62), 0.2, 12), sea(r, 0.18, 9, 700)),
    "amb_sommet": lambda r: mix(wind(r, 0.35, 800, 0.05), sea(r, 0.12, 10, 500), pad_drone(r, [45], 0.05, 400)),
    "amb_phare": lambda r: mix(wind(r, 0.12, 300), machine(r, 3, 0.08, 200), tick(r, 0.05, 60), sea(r, 0.08, 10, 400)),
    "amb_grotte": lambda r: mix(drips(r, 45, 0.3), lowpass_fast(sea(r, 0.25, 6, 500), 700), pad_drone(r, [46, 53], 0.06, 600)),
    "amb_anse": lambda r: mix(sea(r, 0.25, 7, 1100), wind(r, 0.08, 700), birds(r, 5, (79, 84), 0.08, 12)),
    "amb_passage": lambda r: mix(sea(r, 0.3, 5, 800), lowpass_fast(sea(r, 0.2, 7.3, 600), 500), wind(r, 0.15, 900), drips(r, 15, 0.1)),
    "amb_pointe_nuit": lambda r: mix(wind(r, 0.28, 700, 0.04), sea(r, 0.2, 11, 500), pad_drone(r, [43, 50], 0.05, 500)),
    "amb_terrasse": lambda r: mix(wind(r, 0.22, 600, 0.05), sea(r, 0.15, 10, 500), machine(r, 3, 0.04, 200)),
    "amb_reve": lambda r: mix(pad_drone(r, [50, 57, 62], 0.14, 1500), lowpass_fast(sea(r, 0.12, 14, 400), 500), drips(r, 12, 0.08)),
    "amb_mer_douce": lambda r: mix(sea(r, 0.28, 10, 800), wind(r, 0.1, 600), birds(r, 4, (79, 84), 0.06, 12)),
}

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    force = "--force" in sys.argv
    for i, (name, fn) in enumerate(AMBIENCES.items()):
        p = os.path.join(OUT, name + ".ogg")
        target = os.path.join(os.environ["AUDIO_OUT"], "ambience", name + ".ogg") if os.environ.get("AUDIO_OUT") else p
        if os.path.exists(target) and not force:
            continue
        rng = np.random.RandomState(100 + i)
        x = fn(rng)
        n = int(SR * 3)
        x = x[: int(SR * DUR)]
        x[:n] = x[:n] * np.linspace(0, 1, n) + x[-n:] * np.linspace(1, 0, n)   # boucle sans couture
        write(p, fade(normalize(x, 0.7), 0.01, 0.01), 0.35)
        print("≈", name, "%.0f Ko" % (os.path.getsize(target) / 1024))
