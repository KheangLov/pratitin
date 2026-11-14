package com.pratitin.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import com.pratitin.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Love tab's sound theme: a short-effect [SoundPool] and a looping music-box lullaby
 * that fades in and out. All sounds are original and synthesised by tools/make_love_sounds.py.
 *
 * Music never starts over something the user is already listening to (another app holding
 * music playback), and it stops whenever the tab or the app goes away.
 */
object LoveSound {
    enum class Sfx(val res: Int, val volume: Float) {
        TAP(R.raw.love_tap, 0.9f),
        CHIME(R.raw.love_chime, 0.7f),
        CELEBRATE(R.raw.love_celebrate, 0.75f),
    }

    private const val MUSIC_VOLUME = 0.32f
    private val main = MainScope()
    private var pool: SoundPool? = null
    private val ids = HashMap<Sfx, Int>()
    private val ready = HashSet<Int>()
    private var player: MediaPlayer? = null
    private var fade: Job? = null
    private var level = 0f

    fun preload(ctx: Context) {
        if (pool != null) return
        val app = ctx.applicationContext
        pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build().also { sp ->
                sp.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready += id }
                Sfx.entries.forEach { ids[it] = sp.load(app, it.res, 1) }
            }
    }

    fun play(ctx: Context, sfx: Sfx, enabled: Boolean = true) {
        if (!enabled) return
        preload(ctx)
        val id = ids[sfx] ?: return
        if (id in ready) pool?.play(id, sfx.volume, sfx.volume, 1, 0, 1f)
    }

    fun startMusic(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java)
        if (player?.isPlaying == true) { fadeTo(MUSIC_VOLUME, 1400); return }
        if (am?.isMusicActive == true) return // don't talk over the user's own music
        runCatching {
            val mp = player ?: MediaPlayer.create(
                ctx.applicationContext, R.raw.love_music,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                am?.generateAudioSessionId() ?: 0,
            )?.also { it.isLooping = true; player = it } ?: return
            level = 0f
            mp.setVolume(0f, 0f)
            mp.start()
            fadeTo(MUSIC_VOLUME, 2200)
        }
    }

    fun stopMusic(release: Boolean = false) {
        val mp = player ?: return
        fadeTo(0f, 700) {
            runCatching { if (mp.isPlaying) mp.pause() }
            if (release) { runCatching { mp.release() }; if (player === mp) player = null }
        }
    }

    private fun fadeTo(target: Float, ms: Long, then: (() -> Unit)? = null) {
        fade?.cancel()
        fade = main.launch {
            val from = level
            val steps = (ms / 40).coerceAtLeast(1)
            for (i in 1..steps) {
                level = from + (target - from) * i / steps
                runCatching { player?.setVolume(level, level) }
                delay(40)
            }
            then?.invoke()
        }
    }
}
