package com.medit.daw

/**
 * A track-level sound source. Either a bundled SoundFont piano synth or
 * an AAP (AndroidAudioPlugin) plugin instance. Implemented by both
 * [PianoSynthAdapter] and [AapSynth] so that [DawAudioEngine] can treat
 * them uniformly.
 */
interface TrackSynth {

    /** Start a note. */
    fun noteOn(pitch: Int, velocity: Int, channel: Int)

    /** Stop a note. */
    fun noteOff(pitch: Int, channel: Int)

    /** Set sustain pedal state. */
    fun setSustain(on: Boolean)

    /** All notes off. */
    fun allNotesOff(fast: Boolean)

    /** Set master volume (0 = silent, 1 = neutral, >1 = boost). */
    fun setMasterVolume(volume: Float)

    /** Release native resources. */
    fun close()
}
