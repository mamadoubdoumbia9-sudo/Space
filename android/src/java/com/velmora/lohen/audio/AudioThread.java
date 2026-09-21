/*
 * LOHEN — audio/AudioThread.java
 *
 * Le fil audio. Tout le son de LOHEN est synthetise a la volee (13.01) : aucun
 * fichier .ogg, aucune banque d'echantillons. Le moteur rend des blocs de 1024
 * images stereo 16 bits a 44,1 kHz, soit environ 23 ms de musique par bloc, et
 * ce fil les pousse dans un AudioTrack en mode flux.
 *
 * Regles respectees ici :
 *   13.32 — le mix est calibre a -16 LUFS par AudioEngine, ce fil n'y touche
 *           pas : il ne fait que transporter des echantillons.
 *   13.05 — le ducking des voix (-9 dB sur l'ambiance) est decide par la
 *           simulation, pas par ce fil.
 *   00.04 — le rendu audio ne doit jamais bloquer le rendu image : ce fil est
 *           entierement separe du fil GL, et il n'appelle la simulation que
 *           sous un verrou court.
 *
 * Si AudioTrack refuse de demarrer (appareil sans sortie, permission audio
 * retiree), le fil s'arrete proprement et le jeu continue en silence : une
 * panne de son ne doit jamais empecher de jouer.
 */
package com.velmora.lohen.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.velmora.lohen.sim.audio.AudioEngine;
import com.velmora.lohen.sim.core.LohenGame;

public final class AudioThread extends Thread {

    private static final String TAG = "LOHEN.audio";

    private final LohenGame game;
    private final short[] buffer = new short[AudioEngine.BUFFER_FRAMES * 2];
    private final Object lock = new Object();

    private AudioTrack track;
    private volatile boolean running = true;
    private volatile boolean paused;
    private volatile int underruns;
    private volatile boolean failed;

    public AudioThread(LohenGame game) {
        super("lohen-audio");
        this.game = game;
        setPriority(Thread.MAX_PRIORITY - 1);
        setDaemon(true);
    }

    /** Le fil tourne-t-il encore, et le son sort-il vraiment ? */
    public boolean isHealthy() {
        return !failed && running;
    }

    public int underruns() {
        return underruns;
    }

    public void pauseAudio() {
        synchronized (lock) {
            paused = true;
        }
    }

    public void resumeAudio() {
        synchronized (lock) {
            paused = false;
            lock.notifyAll();
        }
    }

    public void shutdown() {
        running = false;
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    @Override
    public void run() {
        try {
            track = createTrack();
        } catch (Throwable t) {
            Log.w(TAG, "AudioTrack indisponible : le jeu continue en silence", t);
            failed = true;
            return;
        }
        if (track == null) {
            failed = true;
            return;
        }
        try {
            track.play();
        } catch (IllegalStateException e) {
            Log.w(TAG, "play() refuse", e);
            failed = true;
            release();
            return;
        }

        while (running) {
            synchronized (lock) {
                while (paused && running) {
                    try {
                        lock.wait(120);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            if (!running) {
                break;
            }
            if (paused) {
                /* silence plutot qu'un bloc fige : la pause ne doit pas gronder */
                java.util.Arrays.fill(buffer, (short) 0);
            } else {
                int frames;
                synchronized (game) {
                    frames = game.renderAudio(buffer);
                }
                if (frames <= 0) {
                    java.util.Arrays.fill(buffer, (short) 0);
                    frames = buffer.length / 2;
                }
            }
            int offset = 0;
            int total = buffer.length;
            while (offset < total && running) {
                int written = track.write(buffer, offset, total - offset);
                if (written <= 0) {
                    if (written == AudioTrack.ERROR_DEAD_OBJECT) {
                        Log.w(TAG, "sortie audio perdue, re-ouverture");
                        release();
                        try {
                            track = createTrack();
                            if (track != null) {
                                track.play();
                            }
                        } catch (Throwable t) {
                            failed = true;
                            return;
                        }
                        if (track == null) {
                            failed = true;
                            return;
                        }
                    } else {
                        underruns++;
                        sleepQuietly(4);
                    }
                    break;
                }
                offset += written;
            }
        }
        release();
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private AudioTrack createTrack() {
        int min = AudioTrack.getMinBufferSize(AudioEngine.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            Log.w(TAG, "minBufferSize invalide : " + min);
            return null;
        }
        /* quatre blocs d'avance : assez pour absorber un pic du fil GL, assez
         * peu pour que la latence reste sous les 100 ms (13.40). */
        int wanted = AudioEngine.BUFFER_FRAMES * 2 * 2 * 4;
        int size = Math.max(min, wanted);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(AudioEngine.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();
        AudioTrack t = new AudioTrack(attrs, format, size, AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE);
        if (t.getState() != AudioTrack.STATE_INITIALIZED) {
            t.release();
            return null;
        }
        return t;
    }

    private void release() {
        AudioTrack t = track;
        track = null;
        if (t != null) {
            try {
                t.pause();
                t.flush();
                t.stop();
            } catch (IllegalStateException ignored) {
                /* deja arretee */
            }
            t.release();
        }
    }
}
