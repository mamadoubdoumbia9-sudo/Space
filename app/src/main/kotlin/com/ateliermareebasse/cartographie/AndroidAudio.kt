package com.ateliermareebasse.cartographie

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.ateliermareebasse.cartographie.core.platform.Audio
import java.util.concurrent.ConcurrentHashMap

/**
 * Audio Android : SoundPool pour les effets (chargés à la demande, cache), deux MediaPlayer par couche
 * (musique, ambiance) avec fondus croisés, un lecteur pour les voix. Aucun son n'est indispensable : tout est tolérant.
 */
class AndroidAudio(private val assets: AssetManager) : Audio {
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val pool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
    private val loaded = ConcurrentHashMap<String, Int>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var master = 1f; private var musicVol = 0.8f; private var ambVol = 0.8f; private var sfxVol = 1f
    private val music = Layer("music") { musicVol * master }
    private val ambience = Layer("ambience") { ambVol * master }
    private var voice: MediaPlayer? = null
    private var paused = false
    private val sfxIndex: Set<String> by lazy { try { assets.list("audio/sfx")?.toSet() ?: emptySet() } catch (e: Exception) { emptySet() } }

    init { pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) { val pend = pendingPlay.remove(id); if (pend != null) pool.play(id, pend.first, pend.first, 1, 0, pend.second) } } }
    private val pendingPlay = ConcurrentHashMap<Int, Pair<Float, Float>>()

    override fun sfxExists(id: String) = "$id.ogg" in sfxIndex

    override fun playSfx(id: String, volume: Float, pitch: Float) {
        if (paused) return
        val v = (volume * sfxVol * master).coerceIn(0f, 1f)
        if (v <= 0.001f) return
        val sid = loaded[id]
        if (sid != null) { pool.play(sid, v, v, 1, 0, pitch); return }
        if (id in missing || !sfxExists(id)) { missing.add(id); return }
        if (loading.add(id)) {
            try {
                val afd = assets.openFd("audio/sfx/$id.ogg")
                val nid = pool.load(afd, 1); afd.close()
                loaded[id] = nid; pendingPlay[nid] = v to pitch
            } catch (e: Exception) { missing.add(id) } finally { loading.remove(id) }
        }
    }

    override fun playMusic(id: String?, fadeMs: Int, loop: Boolean) = music.play(id?.let { "audio/music/$it.ogg" }, fadeMs, loop)
    override fun playAmbience(id: String?, fadeMs: Int) = ambience.play(id?.let { "audio/ambience/$it.ogg" }, fadeMs, true)
    override fun playVoice(id: String?) {
        voice?.let { try { it.stop(); it.release() } catch (e: Exception) {} }; voice = null
        if (id == null) return
        try {
            val afd = assets.openFd("audio/voice/$id.ogg")
            val mp = MediaPlayer().apply { setAudioAttributes(attrs); setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); prepare(); setVolume(master, master); start() }
            afd.close(); voice = mp
            mp.setOnCompletionListener { it.release(); if (voice === it) voice = null }
        } catch (e: Exception) { }
    }
    override fun setVolumes(master: Float, music: Float, ambience: Float, sfx: Float) { this.master = master; musicVol = music; ambVol = ambience; sfxVol = sfx; this.music.applyVolume(); this.ambience.applyVolume() }
    override fun pauseAll() { paused = true; music.pause(); ambience.pause(); pool.autoPause(); try { voice?.pause() } catch (e: Exception) {} }
    override fun resumeAll() { paused = false; music.resume(); ambience.resume(); pool.autoResume(); try { voice?.start() } catch (e: Exception) {} }
    override fun stopAll() { music.play(null, 0, true); ambience.play(null, 0, true); playVoice(null) }
    fun release() { stopAll(); pool.release() }

    /** Une couche = deux lecteurs qui se relaient en fondu croisé. */
    private inner class Layer(val name: String, val vol: () -> Float) {
        private var current: MediaPlayer? = null
        private var currentPath: String? = null
        private var fading: MediaPlayer? = null
        private var fadeStart = 0L; private var fadeMs = 1500; private var currentGain = 0f
        private var wasPlaying = false

        fun play(path: String?, ms: Int, loop: Boolean) {
            if (path == currentPath) return
            // l'ancien lecteur part en fondu
            fading?.let { try { it.stop(); it.release() } catch (e: Exception) {} }
            fading = current; current = null; currentPath = path
            fadeStart = System.currentTimeMillis(); fadeMs = ms.coerceAtLeast(50)
            if (path != null) {
                try {
                    val afd = assets.openFd(path)
                    val mp = MediaPlayer().apply { setAudioAttributes(attrs); setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); isLooping = loop; prepare(); setVolume(0f, 0f) }
                    afd.close()
                    if (!paused) mp.start()
                    current = mp
                } catch (e: Exception) { current = null; currentPath = null }
            }
            tick()
        }
        private val ticker = object : Runnable { override fun run() { tick() } }
        private fun tick() {
            handler.removeCallbacks(ticker)
            val t = ((System.currentTimeMillis() - fadeStart).toFloat() / fadeMs).coerceIn(0f, 1f)
            currentGain = t
            try { current?.setVolume(vol() * t, vol() * t) } catch (e: Exception) {}
            try { fading?.setVolume(vol() * (1 - t), vol() * (1 - t)) } catch (e: Exception) {}
            if (t >= 1f) { fading?.let { try { it.stop(); it.release() } catch (e: Exception) {} }; fading = null }
            else handler.postDelayed(ticker, 40)
        }
        fun applyVolume() { try { current?.setVolume(vol() * currentGain, vol() * currentGain) } catch (e: Exception) {} }
        fun pause() { try { wasPlaying = current?.isPlaying == true; current?.pause(); fading?.pause() } catch (e: Exception) {} }
        fun resume() { try { current?.start(); fading?.let { it.stop(); it.release() }; fading = null } catch (e: Exception) {} }
    }
}
