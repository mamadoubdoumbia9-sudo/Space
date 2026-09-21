/*
 * LOHEN — sim/audio/AudioEngine.java
 *
 * Le moteur audio natif. Aucun asset binaire : tout est synthetise a la
 * demande (ADR-002) par sim/audio/Synth.java, puis mixe ici.
 *
 * 13.27 : `AudioDirector` gere des stems (4 a 7 par piste) avec crossfade
 * par parametre de gameplay. Transitions musicales TOUJOURS sur temps fort,
 * quantifiees a la mesure.
 * 13.32 : mix final a -16 LUFS, dynamique preservee, PAS de compresseur
 * agressif. Le silence est un materiau (13.01) : on ne remonte jamais le
 * plancher sonore pour "faire vivant".
 * 02.19 : latence audio cible <= 40 ms (44,1 kHz, tampons de 1024).
 */
package com.velmora.lohen.sim.audio;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.core.Options;
import com.velmora.lohen.sim.core.Rng;

public final class AudioEngine {

    public static final int SAMPLE_RATE = Synth.SAMPLE_RATE;
    public static final int BUFFER_FRAMES = 1024;      /* ~23 ms a 44,1 kHz */
    public static final int MAX_VOICES = 48;
    public static final float TARGET_LUFS = -16f;      /* 13.32 */
    /**
     * Calibrage fixe du bus de sortie. Les voix synthetiques sortent autour de
     * -35 LUFS bruts, avec des cretes rares (une note de piano tous les
     * quatre temps) : ce trim de +24 dB pose les notes a hauteur d'ecoute
     * (-7 dBFS environ) et le normalisateur affine vers -16 LUFS sans jamais
     * compressor brutalement (13.32). Le limiteur tanh encaisse les cretes.
     */
    public static final float MIX_TRIM = 16.0f;

    /* bus (14.12) */
    public static final int BUS_MASTER = 0;
    public static final int BUS_MUSIC = 1;
    public static final int BUS_SFX = 2;
    public static final int BUS_VO = 3;
    public static final int BUS_AMBIENCE = 4;
    public static final int BUS_COUNT = 5;

    private final Options options;
    private final EventBus bus;
    private final Rng rng;

    private final Synth.Voice[] voices = new Synth.Voice[MAX_VOICES];
    private final int[] voiceBus = new int[MAX_VOICES];
    private final float[] voicePan = new float[MAX_VOICES];
    private final float[] voiceDistance = new float[MAX_VOICES];
    private int voiceCount;
    private int droppedVoices;

    private final float[] mixL = new float[BUFFER_FRAMES];
    private final float[] mixR = new float[BUFFER_FRAMES];
    private final Synth.Reverb reverb = new Synth.Reverb();
    private final Synth.Reverb reverbMusic = new Synth.Reverb();
    private float reverbSend = 0.2f;
    private float reverbSize = 0.5f;

    private final float[] busGain = new float[BUS_COUNT];
    private final float[] busTarget = new float[BUS_COUNT];
    private float ducking = 1f;
    private float duckTarget = 1f;
    private float masterTrim = 1f;

    /* mesure de sonie (approximation LUFS : K-weighting simplifie) */
    private float momentarySum;
    private float momentaryRaw;
    private int momentaryCount;
    private float momentaryLufs = -70f;
    private float shortTermLufs = -70f;
    private float integratedSum;
    private long integratedFrames;
    private float normalizeGain = 1f;

    private long totalFrames;
    private long blocksRendered;
    private boolean muted;

    public AudioEngine(Options options, EventBus bus, Rng rng) {
        this.options = options;
        this.bus = bus;
        this.rng = rng;
        for (int i = 0; i < BUS_COUNT; i++) {
            busGain[i] = 1f;
            busTarget[i] = 1f;
        }
        applyOptions();
        reverb.configure(reverbSize, reverbSend);
        reverbMusic.configure(0.75f, 0.34f);
    }

    public Rng rng() {
        return rng;
    }

    public void applyOptions() {
        if (options == null) {
            return;
        }
        busTarget[BUS_MASTER] = options.volMaster;
        busTarget[BUS_MUSIC] = options.volMusic;
        busTarget[BUS_SFX] = options.volSfx;
        /* 13.33 : la VO est la seule piste qui ne baisse jamais avec le reste.
           Ici il n'y a PAS de voix enregistree (ADR-004) : le bus VO existe,
           il porte le souffle de Lohen et la chanteuse sans paroles. */
        busTarget[BUS_VO] = options.volVo;
        busTarget[BUS_AMBIENCE] = options.volAmb;
        ducking = 1f;
        duckTarget = 1f - Maths.clamp01(options.volDucking) * 0.35f;
        /* RETOUR JOUEUR : le jeu est livre muet — « muet » veut dire MUET,
           immediatement, sans attendre le fondu du bus. */
        if (options.volMaster <= 0.001f) {
            busGain[BUS_MASTER] = 0f;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Voix                                                                */
    /* ------------------------------------------------------------------ */

    /** Ajoute une voix sur un bus, avec panoramique et distance. */
    public boolean play(Synth.Voice voice, int busId, float pan, float distance) {
        if (muted || voice == null) {
            return false;
        }
        int slot = -1;
        for (int i = 0; i < MAX_VOICES; i++) {
            if (voices[i] == null) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            /* on vole la voix la plus silencieuse et la plus lointaine */
            float worst = Float.MAX_VALUE;
            for (int i = 0; i < MAX_VOICES; i++) {
                float score = voices[i].gain() / (1f + voiceDistance[i]);
                if (score < worst) {
                    worst = score;
                    slot = i;
                }
            }
            droppedVoices++;
        }
        voices[slot] = voice;
        voiceBus[slot] = busId;
        voicePan[slot] = Maths.clamp(pan, -1f, 1f);
        voiceDistance[slot] = Math.max(0f, distance);
        voiceCount++;
        return true;
    }

    public boolean play(Synth.Voice voice, int busId) {
        return play(voice, busId, 0f, 0f);
    }

    public int activeVoices() {
        int n = 0;
        for (int i = 0; i < MAX_VOICES; i++) {
            if (voices[i] != null) {
                n++;
            }
        }
        return n;
    }

    public int droppedVoices() {
        return droppedVoices;
    }

    public void stopBus(int busId) {
        for (int i = 0; i < MAX_VOICES; i++) {
            if (voices[i] != null && voiceBus[i] == busId) {
                voices[i] = null;
            }
        }
    }

    public void stopAll() {
        for (int i = 0; i < MAX_VOICES; i++) {
            voices[i] = null;
        }
    }

    public void setMuted(boolean m) {
        muted = m;
        if (m) {
            stopAll();
        }
    }

    public boolean muted() {
        return muted;
    }

    /* ------------------------------------------------------------------ */
    /* Reverb / ducking                                                    */
    /* ------------------------------------------------------------------ */

    /** Presets de reverb par lieu (13.27) : falaise, marche, bibliotheque… */
    public void setReverb(String preset) {
        if (preset == null) {
            return;
        }
        if ("falaise".equals(preset)) {
            reverbSize = 0.55f;
            reverbSend = 0.22f;
        } else if ("marche".equals(preset)) {
            reverbSize = 0.35f;
            reverbSend = 0.16f;
        } else if ("bibliotheque".equals(preset)) {
            reverbSize = 0.85f;
            reverbSend = 0.30f;
        } else if ("puits".equals(preset)) {
            reverbSize = 1f;
            reverbSend = 0.42f;
        } else if ("salle_de_bal".equals(preset)) {
            reverbSize = 0.95f;
            reverbSend = 0.38f;
        } else if ("chambre".equals(preset) || "phare".equals(preset)) {
            reverbSize = 0.4f;
            reverbSend = 0.18f;
        } else if ("echo".equals(preset)) {
            /* 11.06 : reverb LONGUE + coupure au-dela de 8 kHz + leger flanger */
            reverbSize = 1f;
            reverbSend = 0.52f;
        } else {
            reverbSize = 0.5f;
            reverbSend = 0.2f;
        }
        reverb.configure(reverbSize, reverbSend);
    }

    /** Ducking : l'ambiance baisse quand un dialogue ou un Echo parle. */
    public void duck(float amount, float seconds) {
        duckTarget = 1f - Maths.clamp01(amount) * 0.55f;
    }

    public void unduck() {
        duckTarget = 1f - (options == null ? 0.35f : Maths.clamp01(options.volDucking) * 0.35f);
    }

    /* ------------------------------------------------------------------ */
    /* Rendu                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * Rend un bloc de BUFFER_FRAMES echantillons stereo en 16 bits.
     * Appelé par l'AudioTrack Android (02.19 : latence <= 40 ms).
     */
    public int render(short[] out) {
        return render(out, BUFFER_FRAMES);
    }

    public int render(short[] out, int frames) {
        frames = Math.min(frames, BUFFER_FRAMES);
        for (int i = 0; i < frames; i++) {
            mixL[i] = 0f;
            mixR[i] = 0f;
        }
        if (muted) {
            for (int i = 0; i < frames * 2; i++) {
                out[i] = 0;
            }
            return frames;
        }
        /* lissage des bus */
        for (int b = 0; b < BUS_COUNT; b++) {
            busGain[b] = Maths.damp(busGain[b], busTarget[b], 12f, frames / (float) SAMPLE_RATE);
        }
        ducking = Maths.damp(ducking, duckTarget, 6f, frames / (float) SAMPLE_RATE);

        float[] scratch = SCRATCH;
        for (int v = 0; v < MAX_VOICES; v++) {
            Synth.Voice voice = voices[v];
            if (voice == null) {
                continue;
            }
            for (int i = 0; i < frames; i++) {
                scratch[i] = 0f;
            }
            voice.render(scratch, 0, frames);
            if (voice.done()) {
                voices[v] = null;
                continue;
            }
            /* attenuation par la distance (loi en 1/d, jamais un couperet) */
            float att = 1f / (1f + voiceDistance[v] * 0.16f);
            float pan = voicePan[v];
            float gl = (float) Math.cos((pan + 1f) * 0.25f * Maths.PI) * att;
            float gr = (float) Math.sin((pan + 1f) * 0.25f * Maths.PI) * att;
            int busId = voiceBus[v];
            float g = busGain[BUS_MASTER] * busGain[busId];
            if (busId == BUS_AMBIENCE || busId == BUS_SFX) {
                g *= ducking;
            }
            if (busId == BUS_MUSIC) {
                for (int i = 0; i < frames; i++) {
                    float s = scratch[i] * g;
                    mixL[i] += reverbMusic.process(s) * gl;
                    mixR[i] += s * gr;
                }
            } else {
                for (int i = 0; i < frames; i++) {
                    float s = scratch[i] * g;
                    float wet = reverb.process(s);
                    mixL[i] += wet * gl;
                    mixR[i] += wet * gr;
                }
            }
        }
        /* normalisation vers -16 LUFS, sans compresseur agressif (13.32) */
        float sum = 0f;
        float peak = 0f;
        for (int i = 0; i < frames; i++) {
            float l = mixL[i];
            float r = mixR[i];
            sum += l * l + r * r;
            float a = Math.abs(l);
            float b = Math.abs(r);
            if (a > peak) {
                peak = a;
            }
            if (b > peak) {
                peak = b;
            }
        }
        float rms = (float) Math.sqrt(sum / Math.max(1, frames * 2));
        float rawLufs = rms < 1e-6f ? -70f : (float) (20f * Math.log10(rms) - 0.691f);
        /* 13.32 : le metre regarde ce qui SORT du bus, trim et normalisation
         * compris — mesurer le mix brut ferait croire que la cible est ratee
         * alors que le joueur entend bien -16 LUFS. */
        float outDb = (float) (20f * Math.log10(Math.max(1e-9f,
                normalizeGain * masterTrim * MIX_TRIM)));
        float lufs = rawLufs + outDb;
        momentarySum += lufs;
        momentaryRaw += rawLufs;
        momentaryCount++;
        if (momentaryCount >= 10) {
            momentaryLufs = momentarySum / momentaryCount;
            float rawAvg = momentaryRaw / momentaryCount;
            shortTermLufs = Maths.damp(shortTermLufs, momentaryLufs, 0.4f, 1f);
            momentarySum = 0f;
            momentaryRaw = 0f;
            momentaryCount = 0;
            /* cible -16 LUFS : on ajuste doucement, jamais plus de 0,5 dB par
             * pas. Dans le silence numerique on ne chasse rien : le gain se
             * fige, sinon il pomperait a la premiere note. */
            float error = TARGET_LUFS - momentaryLufs;
            if (rawAvg > -65f) {
                normalizeGain *= (float) Math.pow(10f, Maths.clamp(error * 0.06f, -0.5f, 0.5f) / 20f);
            }
            normalizeGain = Maths.clamp(normalizeGain, 0.4f, 4.0f);
        }
        float g2 = normalizeGain * masterTrim * MIX_TRIM;
        integratedSum += sum * g2 * g2;
        integratedFrames += frames * 2;
        /* limiteur doux : tanh, pas de clipping numerique */
        for (int i = 0; i < frames; i++) {
            float l = softClip(mixL[i] * normalizeGain * masterTrim * MIX_TRIM);
            float r = softClip(mixR[i] * normalizeGain * masterTrim * MIX_TRIM);
            out[i * 2] = (short) Maths.clamp((int) (l * 32767f), -32768, 32767);
            out[i * 2 + 1] = (short) Maths.clamp((int) (r * 32767f), -32768, 32767);
        }
        totalFrames += frames;
        blocksRendered++;
        return frames;
    }

    private static final float[] SCRATCH = new float[BUFFER_FRAMES];

    private static float softClip(float x) {
        if (x > 0.85f) {
            return 0.85f + (float) Math.tanh((x - 0.85f) * 2f) * 0.14f;
        }
        if (x < -0.85f) {
            return -0.85f - (float) Math.tanh((-x - 0.85f) * 2f) * 0.14f;
        }
        return x;
    }

    /* ------------------------------------------------------------------ */
    /* Mesures                                                             */
    /* ------------------------------------------------------------------ */

    public float momentaryLufs() {
        return momentaryLufs;
    }

    public float shortTermLufs() {
        return shortTermLufs;
    }

    public float integratedLufs() {
        float rms = integratedFrames == 0 ? 0f
                : (float) Math.sqrt(integratedSum / integratedFrames);
        return rms < 1e-6f ? -70f : (float) (20f * Math.log10(rms) - 0.691f);
    }

    public float normalizeGain() {
        return normalizeGain;
    }

    public long totalFrames() {
        return totalFrames;
    }

    public float secondsRendered() {
        return totalFrames / (float) SAMPLE_RATE;
    }

    public long blocksRendered() {
        return blocksRendered;
    }

    public float latencyMs() {
        return BUFFER_FRAMES * 1000f / SAMPLE_RATE;
    }

    public float busGain(int b) {
        return busGain[b];
    }

    public float ducking() {
        return ducking;
    }

    public void setMasterTrim(float t) {
        masterTrim = Maths.clamp(t, 0f, 2f);
    }

    public void reset() {
        stopAll();
        reverb.reset();
        reverbMusic.reset();
        momentarySum = 0f;
        momentaryCount = 0;
        integratedSum = 0f;
        integratedFrames = 0;
        normalizeGain = 1f;
        totalFrames = 0;
        blocksRendered = 0;
        droppedVoices = 0;
    }

    /** Garde CI : le silence doit rester du silence (13.01). */
    public boolean silenceIsSilent() {
        if (activeVoices() != 0) {
            return false;
        }
        short[] buf = new short[BUFFER_FRAMES * 2];
        render(buf, BUFFER_FRAMES);
        for (short s : buf) {
            if (s != 0) {
                return false;
            }
        }
        return true;
    }

    public EventBus bus() {
        return bus;
    }
}
