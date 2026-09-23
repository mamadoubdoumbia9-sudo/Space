#!/usr/bin/env python3
"""Compose et synthétise les 24 pistes musicales du jeu (+ la lettre, + les échos).
Écriture générative déterministe : chaque piste a une graine, un mode, un tempo, des instruments et une densité.
Le motif d'Esteban (sol si♭ sol ré) traverse les pistes ; il se résout pleinement dans track_23 et track_lettre."""
import os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import *  # noqa

OUT = os.path.join(AUDIO, "music")
MODES = {
    "minor": [0, 2, 3, 5, 7, 8, 10], "dorian": [0, 2, 3, 5, 7, 9, 10], "major": [0, 2, 4, 5, 7, 9, 11],
    "penta": [0, 2, 4, 7, 9], "penta_min": [0, 3, 5, 7, 10], "lydian": [0, 2, 4, 6, 7, 9, 11],
}


def scale_notes(root, mode, low, high):
    st = MODES[mode]
    return [n for n in range(low, high + 1) if (n - root) % 12 in st]


def pad_note(freq, dur, rng, bright=0.3):
    n = int(dur * SR)
    y = np.zeros(n)
    for det in (-0.004, 0.0, 0.004):
        y += osc(freq, dur, "sine", rng.uniform(0, 6.28), det)
        y += 0.35 * osc(freq * 2, dur, "sine", rng.uniform(0, 6.28), det) * bright
    return y / 4 * env_adsr(n, min(1.5, dur * 0.35), 0.3, 0.8, min(2.0, dur * 0.4))


def cello_note(freq, dur, rng):
    n = int(dur * SR)
    y = vibrato(freq, dur, 0.005, 4.8, "tri") * 0.6 + 0.4 * lowpass_fast(osc(freq, dur, "saw", 0, 0.002), freq * 3)
    return y * env_adsr(n, min(0.6, dur * 0.3), 0.2, 0.85, min(1.2, dur * 0.4))


def glock_note(freq, dur):
    n = int(dur * SR)
    t = np.arange(n) / SR
    return (np.sin(2 * np.pi * freq * t) + 0.4 * np.sin(2 * np.pi * freq * 2.76 * t) * np.exp(-t / 0.3)) * np.exp(-t / (dur * 0.35))


def compose(spec):
    rng = np.random.RandomState(spec["seed"])
    length = spec["length"] * float(os.environ.get("AUDIO_LEN", "1")); bpm = spec["bpm"]; beat = 60.0 / bpm
    root = spec["root"]; mode = spec["mode"]
    canvas = np.zeros(int(SR * (length + 4)))
    chords = spec.get("chords", [0, 5, 3, 4])  # degrés (index dans la gamme)
    scale = scale_notes(root, mode, root - 24, root + 24)
    deg = lambda k, octave=0: scale[(scale.index(root) + k) % len(scale)] + 12 * ((scale.index(root) + k) // len(scale)) + 12 * octave
    bars = int(length / (beat * spec.get("meter", 4)))
    meter = spec.get("meter", 4)
    inst = spec["inst"]
    density = spec.get("density", 0.5)
    motif_at = spec.get("motif", [])
    for bar in range(bars):
        t0 = bar * beat * meter
        ch = chords[bar % len(chords)]
        chord_notes = [deg(ch), deg(ch + 2), deg(ch + 4)]
        if "pad" in inst and (bar % 2 == 0 or density > 0.6):
            for i, nn in enumerate(chord_notes):
                place(canvas, pad_note(midi(nn - 12 * (i == 0)), beat * meter * 2.1, rng, spec.get("bright", 0.3)) * inst["pad"], t0)
        if "cello" in inst and bar % 2 == 0:
            place(canvas, cello_note(midi(deg(ch) - 24), beat * meter * 2.2, rng) * inst["cello"], t0)
        if "harp" in inst:
            steps = meter * 2
            for s_ in range(steps):
                if rng.uniform() < density:
                    nn = rng.choice(chord_notes + [deg(ch + 6), deg(ch + 1)]) + rng.choice([0, 12, 12])
                    place(canvas, pluck(midi(nn), 1.8, rng, 0.55) * inst["harp"] * rng.uniform(0.5, 1), t0 + s_ * beat / 2)
        if "glock" in inst:
            for s_ in range(meter):
                if rng.uniform() < density * 0.6:
                    nn = rng.choice(chord_notes) + 24
                    place(canvas, glock_note(midi(nn), 1.5) * inst["glock"] * rng.uniform(0.4, 0.9), t0 + s_ * beat)
        if "bells" in inst and rng.uniform() < density * 0.5:
            nn = rng.choice(chord_notes) + 12
            place(canvas, bell(midi(nn), 3.0) * inst["bells"] * 0.5, t0 + rng.choice(range(meter)) * beat)
        if "pulse" in inst:
            for s_ in range(meter * 2):
                place(canvas, lowpass_fast(rng.standard_normal(int(SR * 0.05)), 400) * env_exp(int(SR * 0.05), 0.012) * inst["pulse"] * (1.0 if s_ % 4 == 0 else 0.5), t0 + s_ * beat / 2)
        if "loom" in inst:
            for s_ in range(meter):
                place(canvas, bandpass(rng.standard_normal(int(SR * 0.08)), 300, 1500) * env_exp(int(SR * 0.08), 0.02) * inst["loom"], t0 + s_ * beat)
                place(canvas, lowpass_fast(rng.standard_normal(int(SR * 0.06)), 250) * env_exp(int(SR * 0.06), 0.015) * inst["loom"] * 0.7, t0 + s_ * beat + beat * 0.5)
        if bar in motif_at:
            voice = spec.get("motif_voice", "harp")
            for k, mn in enumerate(MOTIF):
                nn = mn + spec.get("motif_shift", 0)
                dur = beat * (1.5 if k == 3 else 0.9)
                at = t0 + k * beat * (1.0 if k < 3 else 1.0)
                if voice == "harp":
                    place(canvas, pluck(midi(nn), 2.0, rng, 0.6) * 0.5, at)
                elif voice == "cello":
                    place(canvas, cello_note(midi(nn - 12), dur * 1.3, rng) * 0.6, at)
                elif voice == "glock":
                    place(canvas, glock_note(midi(nn + 12), 1.8) * 0.5, at)
                elif voice == "bell":
                    place(canvas, bell(midi(nn), 3.0) * 0.5, at)
                elif voice == "whistle":
                    place(canvas, vibrato(midi(nn + 12), dur, 0.008, 5) * env_adsr(int(SR * dur), 0.05, 0.1, 0.8, 0.2) * 0.35, at)
    x = canvas[: int(SR * length)]
    x = reverb_fast(x, spec.get("room", 0.6), spec.get("wet", 0.28))[: int(SR * length)]
    # boucle douce : fondu croisé des 2 dernières secondes sur le début
    n = int(SR * 2.5)
    x[:n] = x[:n] * np.linspace(0, 1, n) + x[-n:] * np.linspace(1, 0, n)
    return fade(normalize(lowpass_fast(x, spec.get("cut", 9000)), 0.8), 0.02, 1.5)


TRACKS = [
    dict(id="track_01", seed=1, root=62, mode="dorian", bpm=54, length=96, inst={"pad": 0.5, "harp": 0.35}, density=0.35, motif=[6, 14], motif_voice="harp", room=0.9, wet=0.35),
    dict(id="track_02", seed=2, root=67, mode="major", bpm=68, length=84, inst={"harp": 0.5, "pad": 0.25}, density=0.55, chords=[0, 3, 4, 0], bright=0.4),
    dict(id="track_03", seed=3, root=65, mode="major", bpm=76, length=80, inst={"harp": 0.45, "glock": 0.25, "pad": 0.2}, density=0.5, chords=[0, 5, 3, 4]),
    dict(id="track_04", seed=4, root=69, mode="major", bpm=88, length=78, inst={"glock": 0.35, "harp": 0.4, "pulse": 0.15}, density=0.55, chords=[0, 4, 5, 3], meter=4),
    dict(id="track_05", seed=5, root=62, mode="major", bpm=96, length=72, inst={"harp": 0.5, "pad": 0.2}, density=0.6, meter=3, chords=[0, 3, 4, 0]),
    dict(id="track_06", seed=6, root=57, mode="dorian", bpm=60, length=90, inst={"cello": 0.4, "pad": 0.35, "harp": 0.2}, density=0.3, motif=[8], motif_voice="cello"),
    dict(id="track_07", seed=7, root=64, mode="minor", bpm=58, length=92, inst={"harp": 0.4, "pad": 0.25}, density=0.3, chords=[0, 5, 2, 4], motif=[10], motif_voice="harp", room=0.8),
    dict(id="track_08", seed=8, root=59, mode="minor", bpm=64, length=84, inst={"pad": 0.4, "harp": 0.25, "pulse": 0.1}, density=0.35, chords=[0, 3, 5, 4]),
    dict(id="track_09", seed=9, root=67, mode="lydian", bpm=100, length=76, inst={"pulse": 0.25, "glock": 0.3, "harp": 0.3, "pad": 0.2}, density=0.5),
    dict(id="track_10", seed=10, root=67, mode="penta_min", bpm=66, length=90, inst={"bells": 0.5, "pad": 0.3, "harp": 0.25}, density=0.45, motif=[4, 12], motif_voice="bell", room=0.9, wet=0.4),
    dict(id="track_11", seed=11, root=62, mode="dorian", bpm=72, length=84, inst={"loom": 0.35, "harp": 0.4, "pad": 0.25}, density=0.45, chords=[0, 5, 3, 4]),
    dict(id="track_12", seed=12, root=60, mode="minor", bpm=52, length=96, inst={"pad": 0.45, "bells": 0.3, "harp": 0.2}, density=0.3, motif=[7], motif_voice="glock", room=0.9),
    dict(id="track_13", seed=13, root=67, mode="minor", bpm=70, length=86, inst={"pad": 0.35, "harp": 0.3, "cello": 0.25}, density=0.4, motif=[3, 9, 15], motif_voice="whistle"),
    dict(id="track_14", seed=14, root=57, mode="dorian", bpm=56, length=90, inst={"cello": 0.4, "pad": 0.35}, density=0.3, chords=[0, 4, 3, 0], motif=[6], motif_voice="cello"),
    dict(id="track_15", seed=15, root=55, mode="minor", bpm=50, length=100, inst={"cello": 0.45, "pad": 0.35, "harp": 0.15}, density=0.25, chords=[0, 5, 3, 4], motif=[8, 16], motif_voice="cello", room=0.8),
    dict(id="track_16", seed=16, root=65, mode="major", bpm=62, length=80, inst={"harp": 0.55}, density=0.45, chords=[0, 3, 4, 0], room=0.7, wet=0.35),
    dict(id="track_17", seed=17, root=58, mode="minor", bpm=48, length=100, inst={"pad": 0.5, "harp": 0.15}, density=0.2, chords=[0, 2, 5, 3], cut=3500, room=0.95, wet=0.5),
    dict(id="track_18", seed=18, root=72, mode="major", bpm=84, length=72, inst={"glock": 0.4, "harp": 0.35, "pad": 0.15}, density=0.5, chords=[0, 3, 4, 0]),
    dict(id="track_19", seed=19, root=55, mode="minor", bpm=42, length=110, inst={"pad": 0.4, "harp": 0.12}, density=0.12, chords=[0, 0, 5, 0], cut=4000, room=0.95, wet=0.5),
    dict(id="track_20", seed=20, root=55, mode="minor", bpm=44, length=100, inst={"cello": 0.45, "pad": 0.3}, density=0.15, chords=[0, 0, 3, 0], motif=[6, 12], motif_voice="cello", room=0.9, wet=0.45),
    dict(id="track_21", seed=21, root=62, mode="dorian", bpm=54, length=92, inst={"pad": 0.4, "harp": 0.3, "cello": 0.2}, density=0.35, motif=[5, 13], motif_voice="harp", room=0.85),
    dict(id="track_22", seed=22, root=67, mode="major", bpm=58, length=96, inst={"harp": 0.45, "pad": 0.35, "cello": 0.2}, density=0.4, chords=[0, 3, 4, 0], motif=[4, 12], motif_voice="harp", room=0.85),
    dict(id="track_23", seed=23, root=67, mode="major", bpm=60, length=100, inst={"pad": 0.4, "harp": 0.4, "bells": 0.35, "cello": 0.25}, density=0.5, chords=[0, 3, 4, 0], motif=[2, 6, 10, 14, 18], motif_voice="bell", room=0.9, wet=0.4),
    dict(id="track_24", seed=24, root=62, mode="dorian", bpm=64, length=120, inst={"harp": 0.45, "pad": 0.35, "glock": 0.2, "cello": 0.2}, density=0.45, motif=[3, 11, 19, 27], motif_voice="harp", room=0.85),
    dict(id="track_lettre", seed=25, root=55, mode="minor", bpm=40, length=120, inst={"cello": 0.4, "pad": 0.25}, density=0.05, chords=[0, 0, 0, 5], motif=[10, 20], motif_voice="cello", cut=3000, room=0.95, wet=0.5),
    dict(id="track_echo", seed=26, root=58, mode="dorian", bpm=46, length=90, inst={"pad": 0.5}, density=0.15, chords=[0, 5, 3, 0], cut=2500, room=0.98, wet=0.6),
]

if __name__ == "__main__":
    only = [a for a in sys.argv[1:] if not a.startswith("--")] or None
    os.makedirs(OUT, exist_ok=True)
    for spec in TRACKS:
        if only and spec["id"] not in only:
            continue
        p = os.path.join(OUT, spec["id"] + ".ogg")
        target = os.path.join(os.environ["AUDIO_OUT"], "music", spec["id"] + ".ogg") if os.environ.get("AUDIO_OUT") else p
        if os.path.exists(target) and "--force" not in sys.argv and not only:
            continue
        x = compose(spec)
        write(p, x, 0.4)
        print("♪", spec["id"], "%.0f s" % (spec["length"] * float(os.environ.get("AUDIO_LEN", "1"))), "%.0f Ko" % (os.path.getsize(target) / 1024))
