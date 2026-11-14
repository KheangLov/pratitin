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

    /** Original songs, rendered by tools/make_love_songs.py and make_love_sounds.py. */
    enum class Song(val res: Int, val emoji: String, val km: String, val en: String, val kmBlurb: String, val enBlurb: String, val volume: Float) {
        SERENADE(R.raw.love_song_serenade, "🎹", "ភ្លេងព្យ៉ាណូស្នេហា", "Piano Serenade", "វ៉ាល់ព្យ៉ាណូយឺតៗ និងវីយូឡុង", "A slow piano waltz with warm strings", 0.42f),
        MOONLIGHT(R.raw.love_song_moonlight, "🌕", "ពន្លឺចន្ទលើគូទឹក", "Moonlight on the Moat", "ឃឹម ខ្លុយ និងស្គរដូចចង្វាក់បេះដូង", "Khim, bamboo flute and a heartbeat skor", 0.42f),
        FOREVER(R.raw.love_song_forever, "🎻", "ជារៀងរហូត", "Forever", "វីយូឡុង ពិណ និងខ្លុយ", "A strings, harp and flute ballad", 0.38f),
        MUSIC_BOX(R.raw.love_song_musicbox, "🎠", "ប្រអប់តន្ត្រី", "Music Box", "ចម្រៀងបំពេរយប់ទន់ភ្លន់", "A gentle music-box lullaby", 0.36f);

        companion object {
            fun of(key: String) = entries.firstOrNull { it.name == key } ?: SERENADE
        }
    }

    private var current: Song? = null
    private var musicVolume = 0.4f
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

    /** Plays [song], cross-fading if another song is playing. [force] ignores other apps' music (an explicit pick). */
    fun startMusic(ctx: Context, song: Song = current ?: Song.SERENADE, force: Boolean = false) {
        val am = ctx.getSystemService(AudioManager::class.java)
        musicVolume = song.volume
        if (current == song && player?.isPlaying == true) { fadeTo(musicVolume, 1400); return }
        if (current != song && player != null) {
            // swap songs: fade the old one out, then start the new one
            val old = player!!
            player = null
            fade?.cancel()
            fade = main.launch {
                val from = level
                for (i in 1..12) { level = from * (1f - i / 12f); runCatching { old.setVolume(level, level) }; delay(30) }
                runCatching { old.stop(); old.release() }
                current = song
                startMusic(ctx, song, force = true)
            }
            return
        }
        if (!force && am?.isMusicActive == true && player?.isPlaying != true) return // don't talk over the user's own music
        current = song
        runCatching {
            val mp = player ?: MediaPlayer.create(
                ctx.applicationContext, song.res,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                am?.generateAudioSessionId() ?: 0,
            )?.also { it.isLooping = true; player = it } ?: return
            level = 0f
            mp.setVolume(0f, 0f)
            mp.start()
            fadeTo(musicVolume, 2200)
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
