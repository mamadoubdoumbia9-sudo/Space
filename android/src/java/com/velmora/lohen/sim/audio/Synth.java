/*
 * LOHEN — sim/audio/Synth.java
 *
 * 13.02 INSTRUMENTARIUM (fixe, aucune addition sans ADR) :
 *   violoncelle solo (Lohen) / piano prepare feutre, enregistre tres pres
 *   (on entend les marteaux) / cloches et bols de bronze (Esteban — il etait
 *   accordeur de cloches, la BO est construite sur ce fait) / verres frottes
 *   (la Maree) / voix humaine sans paroles (une seule chanteuse, mezzo,
 *   souffle audible, JAMAIS de choeur) / contrebasse archet (le danger) /
 *   guitare acoustique nylon, uniquement pour Sol (3 pistes).
 *   INTERDIT : synth pads, batterie moderne, orchestre hollywoodien, taiko.
 *
 * Tout est SYNTHETISE a l'execution (ADR-002 : aucun asset audio binaire).
 * Chaque instrument est un modele physique simplifie, pas un echantillonneur :
 *   - cloche : FM additive a 5 partiels inharmoniques + decay long
 *   - piano prepare : 3 partiels inharmoniques + bruit de marteau + feutre
 *   - violoncelle : dent de scie filtree + vibrato 5,5 Hz + bruit d'archet
 *   - verre frotte : sinus a vibrato lent + friction (bruit filtre module)
 *   - guitare nylon : Karplus-Strong (corde pincee reellement simulee)
 *   - voix mezzo : 4 formants sur dent de scie + souffle
 *   - contrebasse : sinus + harmonique 2, attaque d'archet lente
 */
package com.velmora.lohen.sim.audio;

import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.core.Rng;

public final class Synth {

    public static final int SAMPLE_RATE = 44100;

    /** Une voix de synthese : produit des echantillons, se termine seule. */
    public interface Voice {
        /** Remplit `out` a partir de `offset` avec `frames` echantillons mono. */
        void render(float[] out, int offset, int frames);

        boolean done();

        float gain();

        void setGain(float g);
    }

    /* ------------------------------------------------------------------ */
    /* Enveloppes et filtres                                               */
    /* ------------------------------------------------------------------ */

    /** Enveloppe ADSR sans allocation. */
    public static final class Envelope {
        public float attack = 0.01f;
        public float decay = 0.1f;
        public float sustain = 0.7f;
        public float release = 0.2f;
        private float t;
        private boolean released;
        private float value;

        public Envelope() { }

        public Envelope(float a, float d, float s, float r) {
            attack = a;
            decay = d;
            sustain = s;
            release = r;
        }

        public void release() {
            released = true;
        }

        public float next(float dt) {
            t += dt;
            if (released) {
                value = Maths.damp(value, 0f, 1f / Math.max(0.01f, release), dt);
                return value;
            }
            if (t < attack) {
                value = attack <= 0f ? 1f : t / attack;
            } else if (t < attack + decay) {
                float k = (t - attack) / Math.max(1e-5f, decay);
                value = 1f + (sustain - 1f) * k;
            } else {
                value = sustain;
            }
            return value;
        }

        public boolean finished() {
            return released && value < 0.0005f;
        }

        public void reset() {
            t = 0f;
            value = 0f;
            released = false;
        }
    }

    /** Filtre passe-bas a un pole (economique, suffisant pour le timbre). */
    public static final class LowPass {
        private float z;

        public float process(float in, float cutoffHz) {
            float rc = 1f / (Maths.TWO_PI * Math.max(20f, cutoffHz));
            float dt = 1f / SAMPLE_RATE;
            float a = dt / (rc + dt);
            z += a * (in - z);
            return z;
        }

        public void reset() {
            z = 0f;
        }
    }

    /** Filtre passe-haut a un pole (retire le continu, souffle). */
    public static final class HighPass {
        private float z;
        private float last;

        public float process(float in, float cutoffHz) {
            float rc = 1f / (Maths.TWO_PI * Math.max(5f, cutoffHz));
            float dt = 1f / SAMPLE_RATE;
            float a = rc / (rc + dt);
            float out = a * (z + in - last);
            z = out;
            last = in;
            return out;
        }

        public void reset() {
            z = 0f;
            last = 0f;
        }
    }

    /** Filtre de formant (bande passante) pour la voix. */
    public static final class Formant {
        private float x1, x2, y1, y2;
        private final float center;
        private final float q;

        public Formant(float centerHz, float q) {
            this.center = centerHz;
            this.q = q;
        }

        public float process(float in) {
            float w = Maths.TWO_PI * center / SAMPLE_RATE;
            float r = (float) Math.exp(-w / (2f * q));
            float a1 = 2f * r * (float) Math.cos(w);
            float a2 = -r * r;
            float out = (1f - r) * (in - x1) + a1 * y1 + a2 * y2;
            x2 = x1;
            x1 = in;
            y2 = y1;
            y1 = out;
            return out;
        }

        public void reset() {
            x1 = x2 = y1 = y2 = 0f;
        }
    }

    /** Reverb de Schroeder : 4 comb + 2 allpass (05.19, 13.27). */
    public static final class Reverb {
        private static final int[] COMB = {1557, 1617, 1491, 1422};
        private static final int[] ALLPASS = {225, 556};
        private final float[][] combBuf = new float[COMB.length][];
        private final int[] combIdx = new int[COMB.length];
        private final float[][] apBuf = new float[ALLPASS.length][];
        private final int[] apIdx = new int[ALLPASS.length];
        private float feedback = 0.78f;
        private float damp = 0.35f;
        private float wet = 0.28f;

        public Reverb() {
            for (int i = 0; i < COMB.length; i++) {
                combBuf[i] = new float[COMB[i]];
            }
            for (int i = 0; i < ALLPASS.length; i++) {
                apBuf[i] = new float[ALLPASS[i]];
            }
        }

        /** `size` 0..1 (petite piece -> grande salle), `wet` 0..1. */
        public void configure(float size, float wetAmount) {
            feedback = 0.55f + 0.35f * Maths.clamp01(size);
            damp = 0.2f + 0.4f * Maths.clamp01(size);
            wet = Maths.clamp01(wetAmount);
        }

        public float process(float in) {
            float out = 0f;
            for (int i = 0; i < COMB.length; i++) {
                float[] buf = combBuf[i];
                int idx = combIdx[i];
                float v = buf[idx];
                /* filtrage passe-bas dans la boucle (amortissement) */
                float filtered = v * (1f - damp) + (idx > 0 ? buf[idx - 1] : 0f) * damp;
                buf[idx] = in + filtered * feedback;
                combIdx[i] = (idx + 1) % buf.length;
                out += v;
            }
            out *= 0.25f;
            for (int i = 0; i < ALLPASS.length; i++) {
                float[] buf = apBuf[i];
                int idx = apIdx[i];
                float buffered = buf[idx];
                float temp = out + buffered * 0.5f;
                buf[idx] = temp;
                out = buffered - temp * 0.5f;
                apIdx[i] = (idx + 1) % buf.length;
            }
            return in * (1f - wet) + out * wet;
        }

        public void reset() {
            for (float[] b : combBuf) {
                java.util.Arrays.fill(b, 0f);
            }
            for (float[] b : apBuf) {
                java.util.Arrays.fill(b, 0f);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Voix instrumentales                                                 */
    /* ------------------------------------------------------------------ */

    /** Cloche / bol de bronze (l'instrument d'Esteban — 13.02). */
    public static final class BellVoice implements Voice {
        /* partiels inharmoniques d'une cloche : hum, fundamental, tierce,
           quinte, nominal (modele simplifie de Fletcher) */
        private static final float[] RATIOS = {0.5f, 1f, 1.19f, 1.56f, 2f, 2.66f};
        private static final float[] AMPS = {0.35f, 1f, 0.55f, 0.42f, 0.6f, 0.22f};
        private static final float[] DECAYS = {3.2f, 2.6f, 2.1f, 1.7f, 1.3f, 0.9f};
        private final float freq;
        private final float[] phase = new float[RATIOS.length];
        private float t;
        private float gain;
        private float duration;

        public BellVoice(float freq, float gain, float duration) {
            this.freq = freq;
            this.gain = gain;
            this.duration = duration;
        }

        public void render(float[] out, int offset, int frames) {
            float dt = 1f / SAMPLE_RATE;
            for (int i = 0; i < frames; i++) {
                float s = 0f;
                for (int p = 0; p < RATIOS.length; p++) {
                    phase[p] += freq * RATIOS[p] * dt;
                    if (phase[p] > 1f) {
                        phase[p] -= 1f;
                    }
                    float env = (float) Math.exp(-t / DECAYS[p]);
                    s += (float) Math.sin(phase[p] * Maths.TWO_PI) * AMPS[p] * env;
                }
                /* frappe initiale : le marteau de bronze */
                float strike = t < 0.012f ? (1f - t / 0.012f) * 0.5f : 0f;
                out[offset + i] += s * 0.32f * gain + strike * gain;
                t += dt;
            }
        }

        public boolean done() {
            return t >= duration;
        }

        public float gain() {
            return gain;
        }

        public void setGain(float g) {
            gain = g;
        }
    }

    /** Piano prepare, feutre, enregistre tres pres : on entend les marteaux. */
    public static final class PreparedPianoVoice implements Voice {
        private static final float[] RATIOS = {1f, 2.005f, 3.01f, 4.03f};
        private static final float[] AMPS = {1f, 0.42f, 0.18f, 0.07f};
        private final float freq;
        private float gain;
        private float t;
        private float duration;
        private final float[] phase = new float[RATIOS.length];
        private final LowPass felt = new LowPass();
        private final Rng rng;
        private final float feltAmount;

        public PreparedPianoVoice(float freq, float gain, float duration, float feltAmount, Rng rng) {
            this.freq = freq;
            this.gain = gain;
            this.duration = duration;
            this.feltAmount = feltAmount;
            this.rng = rng;
        }

        public void render(float[] out, int offset, int frames) {
            float dt = 1f / SAMPLE_RATE;
            for (int i = 0; i < frames; i++) {
                /* bruit de marteau : 8 ms, spectre large filtre */
                float hammer = 0f;
                if (t < 0.008f) {
                    hammer = (rng.nextFloat() * 2f - 1f) * (1f - t / 0.008f) * 0.55f;
                }
                float s = 0f;
                for (int p = 0; p < RATIOS.length; p++) {
                    phase[p] += freq * RATIOS[p] * dt;
                    if (phase[p] > 1f) {
                        phase[p] -= 1f;
                    }
                    float decay = (float) Math.exp(-t * (1.4f + p * 0.9f));
                    s += (float) Math.sin(phase[p] * Maths.TWO_PI) * AMPS[p] * decay;
                }
                /* le feutre coupe les aigus : plus il est present, plus c'est sourd */
                float cutoff = 6500f - feltAmount * 5200f;
                s = felt.process(s + hammer, cutoff);
                out[offset + i] += s * gain;
                t += dt;
            }
        }

        public boolean done() {
            return t >= duration;
        }

        public float gain() {
            return gain;
        }

        public void setGain(float g) {
            gain = g;
        }
    }

    /** Violoncelle solo : l'instrument de Lohen (13.02, 13.04). */
    public static final class CelloVoice implements Voice {
        private final float freq;
        private float gain;
        private float t;
        private float duration;
        private float phase;
        private float vibratoPhase;
        private final LowPass lp = new LowPass();
        private final HighPass hp = new HighPass();
        private final Envelope env = new Envelope(0.16f, 0.2f, 0.85f, 0.35f);
        private final Rng rng;
        private final boolean sustained;

        public CelloVoice(float freq, float gain, float duration, boolean sustained, Rng rng) {
            this.freq = freq;
            this.gain = gain;
            this.duration = duration;
            this.sustained = sustained;
            this.rng = rng;
        }

        public void render(float[] out, int offset, int frames) {
            float dt = 1f / SAMPLE_RATE;
            for (int i = 0; i < frames; i++) {
                /* vibrato 5,5 Hz, 1,2 % de deviation, qui s'installe apres 0,3 s */
                vibratoPhase += 5.5f * dt;
                float vibAmount = sustained ? Maths.clamp01((t - 0.3f) / 0.4f) * 0.012f : 0.006f;
                float f = freq * (1f + vibAmount * (float) Math.sin(vibratoPhase * Maths.TWO_PI));
                phase += f * dt;
                if (phase > 1f) {
                    phase -= 1f;
                }
                /* dent de scie adoucie (archet) */
                float saw = 2f * phase - 1f;
                float s = saw * 0.55f + (float) Math.sin(phase * Maths.TWO_PI) * 0.45f;
                /* bruit d'archet : frottement filtre */
                float bow = (rng.nextFloat() * 2f - 1f) * 0.05f * (0.4f + 0.6f * Math.abs(saw));
                s = lp.process(s + bow, 2400f);
                s = hp.process(s, 70f);
                float e = env.next(dt);
                out[offset + i] += s * e * gain;
                t += dt;
                if (sustained && t > duration) {
                    env.release();
                }
            }
        }

        public boolean done() {
            return t >= duration + (sustained ? 0.6f : 0f) && env.finished();
        }

        public float gain() {
            return gain;
        }

        public void setGain(float g) {
            gain = g;
        }
    }

    /** Verre frotte / harmonica de verre : la Maree (13.02, 13.29). */
    public static final class GlassVoice implements Voice {
        private final float freq;
        private float gain;
        private float t;
        private float duration;
        private float phase;
        private float vibPhase;
        private final Rng rng;
        private final LowPass frictionFilter = new LowPass();
        private final float friction;

        public GlassVoice(float freq, float gain, float duration, float friction, Rng rng) {
            this.freq = freq;
            this.gain = gain;
            this.duration = duration;
            this.friction = friction;
            this.rng = rng;
        }

        public void render(float[] out, int offset, int frames) {
            float dt = 1f / SAMPLE_RATE;
            for (int i = 0; i < frames; i++) {
                vibPhase += 4.2f * dt;
                /* le doigt mouille fait osciller la hauteur : c'est le cri du verre */
                float f = freq * (1f + 0.006f * (float) Math.sin(vibPhase * Maths.TWO_PI)
                        + 0.002f * (float) Math.sin(vibPhase * Maths.TWO_PI * 2.7f));
                phase += f * dt;
                if (phase > 1f) {
                    phase -= 1f;
                }
                float s = (float) Math.sin(phase * Maths.TWO_PI) * 0.7f
                        + (float) Math.sin(phase * Maths.TWO_PI * 2.01f) * 0.2f
                        + (float) Math.sin(phase * Maths.TWO_PI * 3.02f) * 0.1f;
                float attack = Maths.clamp01(t / 0.6f);
                float release = Maths.clamp01((duration - t) / 0.8f);
                /* friction : bruit module par la pression du doigt */
                float fr = frictionFilter.process((rng.nextFloat() * 2f - 1f), 3200f)
                        * friction * (0.5f + 0.5f * (float) Math.sin(vibPhase * Maths.TWO_PI * 0.5f));
                out[offset + i] += (s * attack * release + fr) * gain;
                t += dt;
            }
        }

        public boolean done() {
            return t >= duration + 0.8f;
        }

        public float gain() {
            return gain;
        }

        public void setGain(float g) {
            gain = g;
        }
    }

    /** Guitare acoustique nylon — uniquement pour Sol (13.02, 13.07). */
    public static final class NylonGuitarVoice implements Voice {
        private final float[] buffer;
        private int idx;
        private float t;
        private float duration;
        private float gain;
        private final LowPass body = new LowPass();

        public NylonGuitarVoice(float freq, float gain, float duration, Rng rng) {
            this.gain = gain;
            this.duration = duration;
            int len = Math.max(2, (int) (SAMPLE_RATE / Math.max(20f, freq)));
            buffer = new float[len];
            /* excitation : bruit filtre, le pince de l'ongle */
            for (int i = 0; i < len; i++) {
                buffer[i] = (rng.nextFloat() * 2f - 1f) * (1f - i / (float) len);
            }
        }

        public void render(float[] out, int offset, int frames) {
            float dt = 1f / SAMPLE_RATE;
            /* Karplus-Strong : la corde est reellement simulee */
            float damping = 0.996f;
            for (int i = 0; i < frames; i++) {
                int next = (idx + 1) % buffer.length;
                float v = (buffer[idx] + buffer[next]) * 0.5f * damping;
                buffer[idx] = v;
                out[offset + i] += body.process(v, 5200f) * gain * 1.6f;
                idx = next;
                t += dt;
            }
        }

        public boolean done() {
            return t >= duration;
        }

        public float gain() {
            return gain;
        }

        public void setGain(float g) {
            gain = g;
        }
    }

    /** Voix humaine sans paroles — une seule chanteuse, mezzo, souffle audible. */
    public static final class VoiceVoice implements Voice {
        /* formants mezzo : A / E / I / O selon la voyelle soufflee */
        private static final float[][] FORMANTS = {
                {800f, 1150f, 2800f, 3500f},
                {400f, 1600f, 2700f, 3300f},
                {350f, 1700f, 2700f, 3200f},
                {450f, 800f, 2830f, 3500f}};
        private final float freq;
        private float gain;
        private float t;
        private float duration;
        private float phase;
        private float vibPhase;
        private final Formant[] formants = new Formant[4];
        private final Rng rng;
        private final Envelope env = new Envelope(0.35f, 0.3f, 0.8f, 0.6f);
        private final int vowel;

        public VoiceVoice(float freq, float gain, float duration, int vowel, Rng rng) {
            this.freq = freq;
            this.gain = gain;
            this.duration = duration;
            this.vowel = vowel % FORMANTS.length;
            this.rng = rng;
            for (int i = 0; i < 4; i++) {
                formants[i] = new Formant(FORMANTS[this.vowel][i], i == 0 ? 6f : 10f);
            }
        }

        public void render(float[] out, int offset, int frames) {
            float dt = 1f / SAMPLE_RATE;
            for (int i = 0; i < frames; i++) {
                vibPhase += 5.1f * dt;
                float f = freq * (1f + 0.008f * (float) Math.sin(vibPhase * Maths.TWO_PI));
                phase += f * dt;
                if (phase > 1f) {
                    phase -= 1f;
                }
                float glottal = (float) Math.sin(phase * Maths.TWO_PI) * 0.6f
                        + (float) Math.sin(phase * Maths.TWO_PI * 2f) * 0.25f
                        + (float) Math.sin(phase * Maths.TWO_PI * 3f) * 0.1f;
                /* souffle audible : c'est une regle de la BO (13.02) */
                float breath = (rng.nextFloat() * 2f - 1f) * 0.06f;
                float s = 0f;
                for (int k = 0; k < 4; k++) {
                    s += formants[k].process(glottal + breath) * (k == 0 ? 1f : 0.55f / (k + 1));
                }
                float e = env.next(dt);
                out[offset + i] += s * e * gain * 1.4f;
                t += dt;
                if (t > duration) {
                    env.release();
                }
            }
        }

        public boolean done() {
            return t > duration + 0.8f && env.finished();
        }

        public float gain() {
            return gain;
        }

        public void setGain(float g) {
            gain = g;
        }
    }

    /** Contrebasse archet — le danger (13.02). */
    public static final class BassVoice implements Voice {
        private final float freq;
        private float gain;
        private float t;
        private float duration;
        private float phase;
        private final Envelope env = new Envelope(0.25f, 0.3f, 0.9f, 0.5f);
        private final LowPass lp = new LowPass();

        public BassVoice(float freq, float gain, float duration) {
            this.freq = freq;
            this.gain = gain;
            this.duration = duration;
        }

        public void render(float[] out, int offset, int frames) {
            float dt = 1f / SAMPLE_RATE;
            for (int i = 0; i < frames; i++) {
                phase += freq * dt;
                if (phase > 1f) {
                    phase -= 1f;
                }
                float s = (float) Math.sin(phase * Maths.TWO_PI) * 0.8f
                        + (float) Math.sin(phase * Maths.TWO_PI * 2f) * 0.18f
                        + (float) Math.sin(phase * Maths.TWO_PI * 3f) * 0.06f;
                s = lp.process(s, 420f);
                float e = env.next(dt);
                out[offset + i] += s * e * gain;
                t += dt;
                if (t > duration) {
                    env.release();
                }
            }
        }

        public boolean done() {
            return t > duration + 0.7f && env.finished();
        }

        public float gain() {
            return gain;
        }

        public void setGain(float g) {
            gain = g;
        }
    }

    /** Bruit filtre : vent, pluie, frottement, pas (13.28, 13.30). */
    public static final class NoiseVoice implements Voice {
        private float gain;
        private float t;
        private float duration;
        private final Rng rng;
        private final LowPass lp = new LowPass();
        private final HighPass hp = new HighPass();
        private final float cutoff;
        private final float highpass;
        private final float[] envelopeShape;
        private float lfoPhase;
        private final float lfoRate;
        private final float lfoDepth;

        public NoiseVoice(float gain, float duration, float cutoff, float highpass,
                          float lfoRate, float lfoDepth, Rng rng) {
            this.gain = gain;
            this.duration = duration;
            this.cutoff = cutoff;
            this.highpass = highpass;
            this.lfoRate = lfoRate;
            this.lfoDepth = lfoDepth;
            this.rng = rng;
            this.envelopeShape = new float[]{0.02f, 0.1f, 0.7f, 0.4f};
        }

        public void render(float[] out, int offset, int frames) {
            float dt = 1f / SAMPLE_RATE;
            for (int i = 0; i < frames; i++) {
                float n = rng.nextFloat() * 2f - 1f;
                n = lp.process(n, cutoff);
                if (highpass > 0f) {
                    n = hp.process(n, highpass);
                }
                lfoPhase += lfoRate * dt;
                float lfo = 1f + lfoDepth * (float) Math.sin(lfoPhase * Maths.TWO_PI);
                float env = shape(t / Math.max(0.001f, duration));
                out[offset + i] += n * env * lfo * gain;
                t += dt;
            }
        }

        private float shape(float p) {
            if (p <= 0f) {
                return 0f;
            }
            if (p >= 1f) {
                return 0f;
            }
            if (p < envelopeShape[0]) {
                return p / envelopeShape[0];
            }
            if (p < envelopeShape[1]) {
                return 1f;
            }
            return 1f - (p - envelopeShape[1]) / (1f - envelopeShape[1]);
        }

        public boolean done() {
            return t >= duration;
        }

        public float gain() {
            return gain;
        }

        public void setGain(float g) {
            gain = g;
        }
    }

    /** Impulsion : impact, coup, bris de verre (07.13, 13.29). */
    public static final class ImpulseVoice implements Voice {
        private float gain;
        private float t;
        private final float duration;
        private final float freq;
        private final float bright;
        private float phase;
        private final Rng rng;
        private final LowPass lp = new LowPass();

        public ImpulseVoice(float freq, float gain, float duration, float bright, Rng rng) {
            this.freq = freq;
            this.gain = gain;
            this.duration = duration;
            this.bright = bright;
            this.rng = rng;
        }

        public void render(float[] out, int offset, int frames) {
            float dt = 1f / SAMPLE_RATE;
            for (int i = 0; i < frames; i++) {
                phase += freq * dt;
                if (phase > 1f) {
                    phase -= 1f;
                }
                float env = (float) Math.exp(-t / (duration * 0.3f));
                float body = (float) Math.sin(phase * Maths.TWO_PI) * (1f - bright);
                float crack = (rng.nextFloat() * 2f - 1f) * bright;
                out[offset + i] += lp.process(body + crack, 9000f) * env * gain;
                t += dt;
            }
        }

        public boolean done() {
            return t >= duration;
        }

        public float gain() {
            return gain;
        }

        public void setGain(float g) {
            gain = g;
        }
    }

    /** Sinus pur : sub-bass, note de reference, bourdon du verre (13.14). */
    public static final class SineVoice implements Voice {
        private float gain;
        private float t;
        private final float freq;
        private final float duration;
        private float phase;
        private final Envelope env;

        public SineVoice(float freq, float gain, float duration, float attack, float release) {
            this.freq = freq;
            this.gain = gain;
            this.duration = duration;
            this.env = new Envelope(attack, 0.1f, 1f, release);
        }

        public void render(float[] out, int offset, int frames) {
            float dt = 1f / SAMPLE_RATE;
            for (int i = 0; i < frames; i++) {
                phase += freq * dt;
                if (phase > 1f) {
                    phase -= 1f;
                }
                float e = env.next(dt);
                out[offset + i] += (float) Math.sin(phase * Maths.TWO_PI) * e * gain;
                t += dt;
                if (t > duration) {
                    env.release();
                }
            }
        }

        public boolean done() {
            return t > duration && env.finished();
        }

        public float gain() {
            return gain;
        }

        public void setGain(float g) {
            gain = g;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Notes : la mineur, le motif d'Esteban (13.03)                       */
    /* ------------------------------------------------------------------ */

    /** LA - DO - MI - RE - LA : le motif d'Esteban, 5 notes, la mineur. */
    public static final float[] ESTEBAN_MOTIF = {220f, 261.626f, 329.628f, 293.665f, 220f};
    /** Les 4 premieres notes : ce qu'on entend toujours (jamais les 5). */
    public static final float[] ESTEBAN_PARTIAL = {220f, 261.626f, 329.628f, 293.665f};
    /** 3 notes : la forme la plus frequente du motif dans le chapitre. */
    public static final float[] ESTEBAN_THREE = {261.626f, 329.628f, 293.665f};

    /** Note depuis un degre de la gamme de la mineur naturel. */
    public static float note(int semitonesFromA2) {
        return 110f * (float) Math.pow(2.0, semitonesFromA2 / 12.0);
    }

    /** Gamme de la mineur naturel (la BO entiere est dedans). */
    public static final int[] A_MINOR = {0, 2, 3, 5, 7, 8, 10};

    public static float scaleNote(int degree, int octave) {
        int idx = ((degree % 7) + 7) % 7;
        int oct = degree / 7 + octave;
        return note(A_MINOR[idx] + oct * 12 + 12);   /* depart LA2 = 110 Hz */
    }

    /** Fabriques d'instruments (utilisees par les directeurs). */
    public static Voice bell(float freq, float gain, float duration) {
        return new BellVoice(freq, gain, duration);
    }

    public static Voice piano(float freq, float gain, float duration, float felt, Rng rng) {
        return new PreparedPianoVoice(freq, gain, duration, felt, rng);
    }

    public static Voice cello(float freq, float gain, float duration, boolean sustained, Rng rng) {
        return new CelloVoice(freq, gain, duration, sustained, rng);
    }

    public static Voice glass(float freq, float gain, float duration, float friction, Rng rng) {
        return new GlassVoice(freq, gain, duration, friction, rng);
    }

    public static Voice guitar(float freq, float gain, float duration, Rng rng) {
        return new NylonGuitarVoice(freq, gain, duration, rng);
    }

    public static Voice voice(float freq, float gain, float duration, int vowel, Rng rng) {
        return new VoiceVoice(freq, gain, duration, vowel, rng);
    }

    public static Voice bass(float freq, float gain, float duration) {
        return new BassVoice(freq, gain, duration);
    }

    public static Voice noise(float gain, float duration, float cutoff, float highpass,
                              float lfoRate, float lfoDepth, Rng rng) {
        return new NoiseVoice(gain, duration, cutoff, highpass, lfoRate, lfoDepth, rng);
    }

    public static Voice impulse(float freq, float gain, float duration, float bright, Rng rng) {
        return new ImpulseVoice(freq, gain, duration, bright, rng);
    }

    public static Voice sine(float freq, float gain, float duration, float attack, float release) {
        return new SineVoice(freq, gain, duration, attack, release);
    }
}
