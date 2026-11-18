package com.medit.daw

import com.medit.midi.audio.PianoSynth
import com.medit.midi.audio.SoundFont

/**
 * Adapts the shared [PianoSynth] (SoundFont-based) to the [TrackSynth]
 * interface used by [DawAudioEngine].
 */
class PianoSynthAdapter(private val synth: PianoSynth) : TrackSynth {

    override fun noteOn(pitch: Int, velocity: Int, channel: Int, sampleOffset: Int) {
        // PianoSynth processes commands at chunk boundaries; sampleOffset is
        // acknowledged but the SoundFont path applies changes at the next
        // render chunk.
        synth.noteOn(pitch, velocity, channel)
    }

    override fun noteOff(pitch: Int, channel: Int, sampleOffset: Int) {
        synth.noteOff(pitch, channel)
    }

    override fun setSustain(on: Boolean, sampleOffset: Int) {
        synth.setSustain(on)
    }

    override fun allNotesOff(fast: Boolean, sampleOffset: Int) {
        synth.allNotesOff(fast)
    }

    override fun setMasterVolume(volume: Float, sampleOffset: Int) {
        synth.setMasterVolume(volume)
    }

    override fun close() {
        synth.close()
    }

    override fun getLatencyInfo(): TrackSynth.LatencyInfo? = null

    companion object {
        fun create(soundFont: SoundFont, presetIndex: Int): PianoSynthAdapter {
            val font = if (presetIndex == 0) soundFont else soundFont.withPreset(presetIndex)
            return PianoSynthAdapter(PianoSynth(font))
        }
    }
}
