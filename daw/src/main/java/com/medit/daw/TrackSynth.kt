package com.medit.daw

/**
 * A track-level sound source. Either a bundled SoundFont piano synth or
 * an AAP (AndroidAudioPlugin) plugin instance. Implemented by both
 * [PianoSynthAdapter] and [AapSynth] so that [DawAudioEngine] can treat
 * them uniformly.
 *
 * All methods that affect sound output accept an optional [sampleOffset]
 * parameter, which specifies the sample position (0-based, within the
 * current 256-frame render chunk) at which the change should take effect.
 * A value of 0 means "at the start of the next chunk".
 */
interface TrackSynth {

    /** Start a note at [sampleOffset] within the current chunk. */
    fun noteOn(pitch: Int, velocity: Int, channel: Int, sampleOffset: Int = 0)

    /** Stop a note at [sampleOffset] within the current chunk. */
    fun noteOff(pitch: Int, channel: Int, sampleOffset: Int = 0)

    /** Set sustain pedal state at [sampleOffset] within the current chunk. */
    fun setSustain(on: Boolean, sampleOffset: Int = 0)

    /** All notes off at [sampleOffset] within the current chunk. */
    fun allNotesOff(fast: Boolean, sampleOffset: Int = 0)

    /** Set master volume (0 = silent, 1 = neutral, >1 = boost) at [sampleOffset]. */
    fun setMasterVolume(volume: Float, sampleOffset: Int = 0)

    /** Release native resources. Must be called from the UI thread. */
    fun close()
}
