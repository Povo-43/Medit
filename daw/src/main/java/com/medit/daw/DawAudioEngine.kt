package com.medit.daw

import android.content.Context
import com.medit.midi.audio.PianoSynth
import com.medit.midi.audio.SoundFont
import com.medit.midi.model.Project
import org.androidaudioplugin.PluginInformation
import org.androidaudioplugin.hosting.AudioPluginInstance
import org.androidaudioplugin.hosting.AudioPluginServiceConnector
import java.util.concurrent.ConcurrentHashMap

/**
 * Realtime mixer for MeditDAW. Each track gets its own [TrackSynth] instance
 * (one AudioTrack per track); Android mixes those outputs. Mute, solo and
 * faders are applied as per-synth gains, so changing the mixer never has to
 * rebuild voices.
 *
 * Tracks can use either a bundled SoundFont (via [PianoSynthAdapter]) or an
 * AAP (AndroidAudioPlugin) plugin (via [AapSynth]). SoundFont objects are
 * shared between tracks that use the same bank; only the voice state and
 * AudioTrack are per track.
 */
class DawAudioEngine(private val context: Context) {

    private data class TrackOutput(
        val synth: TrackSynth,
        val isAap: Boolean,
    )

    @Volatile private var outputs: Map<Int, TrackOutput> = emptyMap()
    @Volatile private var gains: Map<Int, Float> = emptyMap()
    private val previews = ConcurrentHashMap<Int, Long>()

    @Volatile private var globalVolume: Float = 1f
    @Volatile private var lastError: String? = null

    /**
     * Rebuilds track outputs for [project].
     *
     * [soundFonts] maps track index to the SoundFont loaded by the instrument
     * library. [aapPlugins] maps track index to the AAP plugin instance, its info, and the connector for lifecycle management.
     * Tracks without a usable sound source stay silent.
     */
    @Synchronized
    fun sync(
        project: Project,
        soundFonts: Map<Int, SoundFont?>,
        aapPlugins: Map<Int, Triple<AudioPluginInstance, PluginInformation, AudioPluginServiceConnector>?>,
    ) {
        val anySolo = project.tracks.any { it.solo }
        val next = HashMap<Int, TrackOutput>()
        val nextGains = HashMap<Int, Float>()
        for ((index, track) in project.tracks.withIndex()) {
            val output = if (track.isAapPlugin) {
                val pluginTriple = aapPlugins[index]
                if (pluginTriple == null) continue
                val (plugin, info, connector) = pluginTriple
                try {
                    AapSynth(plugin, info, connector)
                } catch (t: Throwable) {
                    lastError = "AAPプラグインを初期化できません: ${t.message ?: t.javaClass.simpleName}"
                    continue
                }
            } else {
                val base = soundFonts[index] ?: continue
                val preset = base.availablePresets.getOrNull(track.presetIndex)?.index ?: 0
                try {
                    PianoSynthAdapter.create(base, preset)
                } catch (t: Throwable) {
                    lastError = "音源を初期化できません: ${t.message ?: t.javaClass.simpleName}"
                    continue
                }
            }
            val existing = outputs[index]
            // Reuse existing synth if it's the same type and configuration
            val finalOutput = if (
                existing != null &&
                existing.isAap == track.isAapPlugin &&
                (!track.isAapPlugin || existing.synth is AapSynth)
            ) {
                existing.synth
            } else {
                output
            }
            val audible = if (anySolo) track.solo && !track.muted else !track.muted
            val gain = if (audible) track.volume else 0f
            finalOutput.setMasterVolume((gain * globalVolume).coerceIn(0f, 2f))
            next[index] = TrackOutput(finalOutput, track.isAapPlugin)
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

    fun noteOn(track: Int, pitch: Int, velocity: Int, channel: Int = 0, sampleOffset: Int = 0) {
        outputs[track]?.synth?.noteOn(pitch, velocity, channel, sampleOffset)
    }

    fun noteOff(track: Int, pitch: Int, channel: Int = 0, sampleOffset: Int = 0) {
        outputs[track]?.synth?.noteOff(pitch, channel, sampleOffset)
    }

    fun setSustain(track: Int, on: Boolean, sampleOffset: Int = 0) {
        outputs[track]?.synth?.setSustain(on, sampleOffset)
    }

    fun allNotesOff(fast: Boolean = false, sampleOffset: Int = 0) {
        outputs.values.forEach { it.synth.allNotesOff(fast, sampleOffset) }
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
        // First tell all synths to stop (AapSynth sets running=false which
        // causes the render thread to exit its loop).
        outputs.values.forEach { it.synth.allNotesOff(fast = true) }
        // Then close each synth (this joins the render thread and releases
        // native resources only after the thread has stopped).
        outputs.values.forEach { it.synth.close() }
        outputs = emptyMap()
        previews.clear()
    }
}
