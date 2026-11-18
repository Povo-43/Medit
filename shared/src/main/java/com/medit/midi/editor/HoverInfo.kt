package com.medit.midi.editor

/** Which part of a note the pointer is over, if any. */
enum class HoverZone { EMPTY, NOTE_BODY, NOTE_START, NOTE_END }

/**
 * Pointer feedback published by the piano roll and consumed by the editor for
 * the hover readout. Shared by every app that embeds [com.medit.midi.ui.PianoRollView].
 */
data class HoverInfo(
    val xPx: Float,
    val yPx: Float,
    val pitch: Int,
    val tick: Int,
    val snappedTick: Int,
    val barBeatLabel: String,
    val zone: HoverZone,
    val noteId: Long?,
    val notePitch: Int?,
    val noteStart: Int?,
    val noteDuration: Int?,
    val ghostStart: Int,
    val ghostPitch: Int,
    val ghostDurationTicks: Int,
    val stylus: Boolean,
)
