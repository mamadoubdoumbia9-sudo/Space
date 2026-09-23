#!/usr/bin/env python3
"""Petite bibliothèque de synthèse (numpy) pour les sons du jeu : oscillateurs, enveloppes,
cordes pincées (Karplus-Strong), cloches (partiels inharmoniques), bruits filtrés, réverbération.
Écriture OGG Vorbis via soundfile (libsndfile). Tout est déterministe (graine fixe par son)."""
import os
import numpy as np
import soundfile as sf

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
AUDIO = os.path.join(ROOT, "app", "src", "main", "assets", "audio")


def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def env_adsr(n, a=0.01, d=0.1, s=0.7, r=0.2, sr=SR):
    a_n, d_n, r_n = int(a * sr), int(d * sr), int(r * sr)
    s_n = max(0, n - a_n - d_n - r_n)
    e = np.concatenate([
        np.linspace(0, 1, max(a_n, 1)),
        np.linspace(1, s, max(d_n, 1)),
        np.full(s_n, s),
        np.linspace(s, 0, max(r_n, 1)),
    ])
    return e[:n] if len(e) >= n else np.pad(e, (0, n - len(e)))


def env_exp(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def osc(freq, dur, wave="sine", phase=0.0, detune=0.0):
    t = t_axis(dur)
    f = freq * (1 + detune)
    ph = 2 * np.pi * f * t + phase
    if wave == "sine":
        return np.sin(ph)
    if wave == "tri":
        return 2 / np.pi * np.arcsin(np.sin(ph))
    if wave == "saw":
        return 2 * ((f * t + phase / (2 * np.pi)) % 1) - 1
    if wave == "square":
        return np.sign(np.sin(ph))
    raise ValueError(wave)


def vibrato(freq, dur, depth=0.006, rate=5.5, wave="sine"):
    t = t_axis(dur)
    f = freq * (1 + depth * np.sin(2 * np.pi * rate * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) if wave == "sine" else 2 / np.pi * np.arcsin(np.sin(ph))


def noise(dur, rng, color="white"):
    n = int(dur * SR)
    x = rng.standard_normal(n)
    if color == "pink":
        # approximation par somme de filtres passe-bas
        b = [0.049922035, -0.095993537, 0.050612699, -0.004408786]
        a = [1, -2.494956002, 2.017265875, -0.522189400]
        y = np.zeros(n)
        xs = np.zeros(4); ys = np.zeros(4)
        for i in range(n):
            xs = np.roll(xs, 1); xs[0] = x[i]
            v = b[0] * xs[0] + b[1] * xs[1] + b[2] * xs[2] + b[3] * xs[3] - a[1] * ys[0] - a[2] * ys[1] - a[3] * ys[2]
            ys = np.roll(ys, 1); ys[0] = v
            y[i] = v
        return y / (np.max(np.abs(y)) + 1e-9)
    if color == "brown":
        y = np.cumsum(x); y -= np.linspace(y[0], y[-1], n)
        return y / (np.max(np.abs(y)) + 1e-9)
    return x


def lowpass(x, cutoff, order=1):
    dt = 1 / SR
    rc = 1 / (2 * np.pi * cutoff)
    alpha = dt / (rc + dt)
    y = x.copy()
    for _ in range(order):
        acc = 0.0
        out = np.empty_like(y)
        for i in range(len(y)):
            acc += alpha * (y[i] - acc)
            out[i] = acc
        y = out
    return y


def lowpass_fast(x, cutoff):
    """Passe-bas 1 pôle vectorisé (filtre récursif via lfilter maison)."""
    from numpy.lib.stride_tricks import sliding_window_view  # noqa (import local pour garder le module léger)
    dt = 1 / SR
    alpha = dt / (1 / (2 * np.pi * cutoff) + dt)
    y = np.empty_like(x)
    acc = 0.0
    # boucle python : acceptable pour des sons courts ; pour les longs on sous-échantillonne le filtrage
    if len(x) > SR * 8:
        step = 4
        xs = x[::step]
        ys = np.empty_like(xs)
        a2 = 1 - (1 - alpha) ** step
        for i, v in enumerate(xs):
            acc += a2 * (v - acc); ys[i] = acc
        return np.interp(np.arange(len(x)), np.arange(len(xs)) * step, ys)
    for i, v in enumerate(x):
        acc += alpha * (v - acc); y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass_fast(x, cutoff)


def bandpass(x, lo, hi):
    return highpass(lowpass_fast(x, hi), lo)


def pluck(freq, dur, rng, brightness=0.5, decay=0.996):
    """Karplus-Strong : corde pincée (harpe, guitare)."""
    n = int(dur * SR)
    period = max(2, int(SR / freq))
    buf = rng.uniform(-1, 1, period)
    buf = lowpass_fast(buf, 800 + brightness * 6000)
    out = np.empty(n)
    idx = 0
    for i in range(n):
        out[i] = buf[idx]
        nxt = buf[(idx + 1) % period]
        buf[idx] = decay * 0.5 * (buf[idx] + nxt)
        idx = (idx + 1) % period
    return out * env_adsr(n, 0.002, 0.05, 0.8, min(0.3, dur / 3))


def bell(freq, dur, partials=None, strike=1.0):
    """Cloche : partiels inharmoniques (hum, prime, tierce, quinte, nominale) à décroissances distinctes."""
    if partials is None:
        partials = [(0.5, 1.0, 1.0), (1.0, 0.8, 0.7), (1.2, 0.5, 0.45), (1.5, 0.45, 0.4), (2.0, 0.35, 0.28), (2.67, 0.2, 0.18), (3.0, 0.12, 0.12)]
    n = int(dur * SR)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for ratio, amp, life in partials:
        y += amp * np.sin(2 * np.pi * freq * ratio * t) * np.exp(-t / (life * dur * 0.6 + 1e-3))
    # attaque de frappe
    y += strike * 0.3 * np.random.RandomState(int(freq)).standard_normal(n) * np.exp(-t / 0.01)
    return y / (np.max(np.abs(y)) + 1e-9)


def reverb(x, size=0.6, mix=0.25, sr=SR):
    """Réverbération de Schroeder : 4 peignes + 2 passe-tout."""
    combs = [int(sr * d * (0.5 + size)) for d in (0.0297, 0.0371, 0.0411, 0.0437)]
    fb = 0.72 + 0.2 * size
    out = np.zeros(len(x) + int(sr * (0.8 + size)))
    xp = np.pad(x, (0, len(out) - len(x)))
    for d in combs:
        y = np.zeros_like(xp)
        for i in range(d, len(xp)):
            y[i] = xp[i] + fb * y[i - d]
        out += y
    out /= len(combs)
    for d in (int(sr * 0.005), int(sr * 0.0017)):
        y = np.zeros_like(out)
        g = 0.5
        for i in range(len(out)):
            y[i] = -g * out[i] + (out[i - d] if i >= d else 0) + (g * y[i - d] if i >= d else 0)
        out = y
    res = xp * (1 - mix) + out * mix
    return res


def reverb_fast(x, size=0.6, mix=0.25):
    """Version rapide par convolution avec une réponse impulsionnelle synthétique (bruit décroissant)."""
    n = int(SR * (0.6 + 1.6 * size))
    rng = np.random.RandomState(7)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (SR * (0.25 + 0.5 * size)))
    ir = lowpass_fast(ir, 4500)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    total = len(x) + n
    wet = np.fft.irfft(np.fft.rfft(x, total) * np.fft.rfft(ir, total), total)[:total]
    dry = np.pad(x, (0, n))
    return dry * (1 - mix) + wet * mix * 3.0


def fade(x, fin=0.01, fout=0.05):
    n = len(x)
    a, b = int(fin * SR), int(fout * SR)
    y = x.copy()
    if a > 0:
        y[:a] *= np.linspace(0, 1, a)
    if b > 0:
        y[-b:] *= np.linspace(1, 0, b)
    return y


def normalize(x, peak=0.9):
    m = np.max(np.abs(x)) + 1e-9
    return x * (peak / m)


def mix(*tracks):
    n = max(len(t) for t in tracks)
    out = np.zeros(n)
    for t in tracks:
        out[: len(t)] += t
    return out


def place(canvas, x, at):
    """Ajoute x dans canvas à la position at (secondes)."""
    i = int(at * SR)
    if i >= len(canvas):
        return canvas
    m = min(len(x), len(canvas) - i)
    canvas[i:i + m] += x[:m]
    return canvas


def write(path, x, quality=0.4, stereo=None):
    """Écrit un OGG Vorbis. AUDIO_OUT redirige la racine (rendu haute fidélité au moment du build), AUDIO_Q force la qualité."""
    if os.environ.get("AUDIO_OUT"):
        path = os.path.join(os.environ["AUDIO_OUT"], os.path.relpath(path, AUDIO))
    quality = float(os.environ.get("AUDIO_Q", quality))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = np.clip(np.nan_to_num(x), -1, 1).astype(np.float32)
    if stereo is not None:
        x = np.stack([x, stereo], axis=1)
    # écriture par blocs : l'encodeur Vorbis de libsndfile plante sur de très gros blocs uniques
    block = SR * 2
    with sf.SoundFile(path, "w", samplerate=SR, channels=1 if x.ndim == 1 else x.shape[1], format="OGG", subtype="VORBIS", compression_level=1.0 - quality) as f:
        for i in range(0, len(x), block):
            f.write(x[i:i + block])


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


# Le motif d'Esteban (13.3) : sol, si bémol, sol, ré — G4 Bb4 G4 D4
MOTIF = [67, 70, 67, 62]
