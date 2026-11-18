package com.medit.daw

import com.medit.midi.audio.PianoSynth
import com.medit.midi.audio.SoundFont

/**
 * Adapts the shared [PianoSynth] (SoundFont-based) to the [TrackSynth]
 * interface used by [DawAudioEngine].
 */
class PianoSynthAdapter(private val synth: PianoSynth) : TrackSynth {

    override fun noteOn(pitch: Int, velocity: Int, channel: Int) {
        synth.noteOn(pitch, velocity, channel)
    }

    override fun noteOff(pitch: Int, channel: Int) {
        synth.noteOff(pitch, channel)
    }

    override fun setSustain(on: Boolean) {
        synth.setSustain(on)
    }

    override fun allNotesOff(fast: Boolean) {
        synth.allNotesOff(fast)
    }

    override fun setMasterVolume(volume: Float) {
        synth.setMasterVolume(volume)
    }

    override fun close() {
        synth.close()
    }

    companion object {
        fun create(soundFont: SoundFont, presetIndex: Int): PianoSynthAdapter {
            val font = if (presetIndex == 0) soundFont else soundFont.withPreset(presetIndex)
            return PianoSynthAdapter(PianoSynth(font))
        }
    }
}
