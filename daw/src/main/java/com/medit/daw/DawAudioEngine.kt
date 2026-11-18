package com.medit.daw

import com.medit.midi.audio.PianoSynth
import com.medit.midi.audio.SoundFont
import com.medit.midi.model.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * Realtime mixer for MeditDAW. Each track gets its own [PianoSynth] instance
 * (one AudioTrack per track); Android mixes those outputs. Mute, solo and
 * faders are applied as per-synth gains, so changing the mixer never has to
 * rebuild voices.
 *
 * SoundFont objects are shared between tracks that use the same bank; only the
 * voice state and AudioTrack are per track.
 */
class DawAudioEngine {

    private data class TrackOutput(
        val baseFont: SoundFont,
        val presetIndex: Int,
        val synth: PianoSynth,
    )

    @Volatile private var outputs: Map<Int, TrackOutput> = emptyMap()
    @Volatile private var gains: Map<Int, Float> = emptyMap()
    private val previews = ConcurrentHashMap<Int, Long>()

    @Volatile private var globalVolume: Float = 1f
    @Volatile private var lastError: String? = null

    /**
     * Rebuilds track outputs for [project]. [fonts] maps track index to the
     * bank loaded by the instrument library. Tracks without a font stay silent.
     */
    @Synchronized
    fun sync(project: Project, fonts: Map<Int, SoundFont?>) {
        val anySolo = project.tracks.any { it.solo }
        val next = HashMap<Int, TrackOutput>()
        val nextGains = HashMap<Int, Float>()
        for ((index, track) in project.tracks.withIndex()) {
            val base = fonts[index] ?: continue
            val preset = base.availablePresets.getOrNull(track.presetIndex)?.index ?: 0
            val existing = outputs[index]
            val output = if (
                existing != null &&
                existing.baseFont === base &&
                existing.presetIndex == preset
            ) {
                existing
            } else {
                try {
                    val synth = PianoSynth(base.withPreset(preset))
                    TrackOutput(base, preset, synth)
                } catch (t: Throwable) {
                    lastError = "音源を初期化できません: ${t.message ?: t.javaClass.simpleName}"
                    continue
                }
            }
            val audible = if (anySolo) track.solo && !track.muted else !track.muted
            val gain = if (audible) track.volume else 0f
            output.synth.setMasterVolume((gain * globalVolume).coerceIn(0f, 2f))
            next[index] = output
            nextGains[index] = gain
        }
        val old = outputs
        outputs = next
        gains = nextGains
        // Close only outputs that are no longer referenced by any track.
        old.forEach { (index, output) ->
            val kept = next[index]
            if (kept == null || kept.synth !== output.synth) {
                output.synth.allNotesOff(fast = true)
                output.synth.close()
            }
        }
    }

    fun noteOn(track: Int, pitch: Int, velocity: Int, channel: Int = 0) {
        outputs[track]?.synth?.noteOn(pitch, velocity, channel)
    }

    fun noteOff(track: Int, pitch: Int, channel: Int = 0) {
        outputs[track]?.synth?.noteOff(pitch, channel)
    }

    fun setSustain(track: Int, on: Boolean) {
        outputs[track]?.synth?.setSustain(on)
    }

    fun allNotesOff(fast: Boolean = false) {
        outputs.values.forEach { it.synth.allNotesOff(fast) }
    }

    fun setGlobalVolume(value: Float) {
        globalVolume = value.coerceIn(0f, 2f)
        outputs.forEach { (index, output) ->
            val gain = gains[index] ?: 0f
            output.synth.setMasterVolume((gain * globalVolume).coerceIn(0f, 2f))
        }
    }

    fun currentGlobalVolume(): Float = globalVolume

    fun isReady(): Boolean = outputs.isNotEmpty()

    fun errorMessage(): String? = lastError

    fun preview(track: Int, pitch: Int, velocity: Int, channel: Int) {
        val id = (track + 1) * 1000 + pitch
        previews[id] = System.currentTimeMillis()
        noteOn(track, pitch, velocity, channel)
    }

    fun endPreview(track: Int, pitch: Int, channel: Int) {
        previews.remove((track + 1) * 1000 + pitch)
        noteOff(track, pitch, channel)
    }

    @Synchronized
    fun shutdown() {
        outputs.values.forEach {
            it.synth.allNotesOff(fast = true)
            it.synth.close()
        }
        outputs = emptyMap()
        previews.clear()
    }
}
