package com.medit.midi.data

import com.medit.midi.model.GridUnit
import com.medit.midi.model.MidiMeta
import com.medit.midi.model.Note
import com.medit.midi.model.Project
import com.medit.midi.model.RawEvent
import com.medit.midi.model.TimeSignature
import com.medit.midi.model.TimeSignaturePoint
import com.medit.midi.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectStoreTest {

    private fun projectWithMixerFields(): Project {
        return Project(
            title = "Mixer Test",
            ppq = 480,
            tracks = listOf(
                Track(
                    name = "Piano",
                    channel = 0,
                    notes = listOf(Note(id = 1, pitch = 60, startTick = 0, durationTicks = 480, velocity = 100)),
                    soundId = "bundled",
                    presetIndex = 0,
                    volume = 1.0f,
                    muted = false,
                    solo = false,
                ),
                Track(
                    name = "Strings",
                    channel = 1,
                    notes = listOf(Note(id = 2, pitch = 64, startTick = 480, durationTicks = 480, velocity = 80)),
                    soundId = "sf-abc123",
                    presetIndex = 3,
                    volume = 0.75f,
                    muted = true,
                    solo = false,
                ),
                Track(
                    name = "Bass",
                    channel = 2,
                    notes = emptyList(),
                    soundId = "sf-def456",
                    presetIndex = 7,
                    volume = 1.25f,
                    muted = false,
                    solo = true,
                ),
            ),
            activeTrack = 1,
            bpm = 132.0,
            timeSignature = TimeSignature(4, 4),
            timeSignatures = listOf(TimeSignaturePoint(0, TimeSignature(4, 4))),
            loopStartTick = 0,
            loopEndTick = 960,
            loopEnabled = true,
            nextNoteId = 3,
            meta = MidiMeta(copyright = "© 2026", comment = "mixer persistence test"),
        )
    }

    private fun roundTrip(data: AutosaveData): AutosaveData {
        val json = ProjectStore.encode(data).toString()
        return ProjectStore.decode(json)
    }

    @Test
    fun trackMixerFieldsRoundTripThroughJson() {
        val original = projectWithMixerFields()
        val data = AutosaveData(
            project = original,
            viewport = ViewportState(startTick = 100.0, lowestPitch = 48f),
            prefs = EditorPrefs(grid = GridUnit.SIXTEENTH, inputVelocity = 110, soundFontVolume = 1.25f),
            savedAtMillis = 1_700_000_000_000,
        )
        val restored = roundTrip(data)

        val origTracks = original.tracks
        val restTracks = restored.project.tracks
        assertEquals("track count must match", origTracks.size, restTracks.size)

        // Track 0: bundled piano, default mixer.
        assertEquals("bundled", restTracks[0].soundId)
        assertEquals(0, restTracks[0].presetIndex)
        assertEquals(1.0f, restTracks[0].volume, 0.001f)
        assertFalse(restTracks[0].muted)
        assertFalse(restTracks[0].solo)

        // Track 1: imported bank, preset 3, muted, reduced volume.
        assertEquals("sf-abc123", restTracks[1].soundId)
        assertEquals(3, restTracks[1].presetIndex)
        assertEquals(0.75f, restTracks[1].volume, 0.001f)
        assertTrue(restTracks[1].muted)
        assertFalse(restTracks[1].solo)

        // Track 2: imported bank, preset 7, solo, boosted volume.
        assertEquals("sf-def456", restTracks[2].soundId)
        assertEquals(7, restTracks[2].presetIndex)
        assertEquals(1.25f, restTracks[2].volume, 0.001f)
        assertFalse(restTracks[2].muted)
        assertTrue(restTracks[2].solo)
    }

    @Test
    fun missingTrackFieldsFallBackToDefaults() {
        // An old-format track object that omits the new mixer fields must decode
        // with the same defaults Medit used before the DAW split.
        val json = """{
          "version": 3,
          "savedAt": 1,
          "summary": {"title": "Old", "noteCount": 0, "savedAt": 1},
          "project": {
            "title": "Old",
            "ppq": 480,
            "activeTrack": 0,
            "bpm": 120.0,
            "timeSignature": [4, 4],
            "timeSignatures": [],
            "loopStart": 0,
            "loopEnd": 480,
            "loopEnabled": false,
            "nextNoteId": 1,
            "meta": {},
            "tracks": [
              {
                "name": "Piano",
                "channel": 0,
                "isDrum": false,
                "notes": [],
                "events": []
              }
            ]
          },
          "viewport": {"startTick": 0, "dpPerTick": 0.12, "lowPitch": 36, "rowHeightDp": 22},
          "prefs": {}
        }"""
        val restored = ProjectStore.decode(json)
        val track = restored.project.tracks[0]
        assertEquals("bundled", track.soundId)
        assertEquals(0, track.presetIndex)
        assertEquals(1.0f, track.volume, 0.001f)
        assertFalse(track.muted)
        assertFalse(track.solo)
    }

    @Test
    fun versionIsRecordedInHeader() {
        val original = projectWithMixerFields()
        val data = AutosaveData(project = original, viewport = ViewportState(), prefs = EditorPrefs(), savedAtMillis = 0)
        val json = ProjectStore.encode(data).toString()
        assertTrue(json.contains("\"version\":3"))
    }

    @Test
    fun trackAapPluginFieldsRoundTripThroughJson() {
        val original = Project(
            title = "AAP Test",
            ppq = 480,
            tracks = listOf(
                Track(
                    name = "Synth",
                    channel = 0,
                    soundId = "bundled",
                    presetIndex = 0,
                    volume = 1.0f,
                    muted = false,
                    solo = false,
                    pluginId = "urn:lv2:example:synth",
                    pluginParams = "{\"cutoff\": 1000.0, \"resonance\": 0.5}",
                ),
                Track(
                    name = "Piano",
                    channel = 1,
                    soundId = "bundled",
                    presetIndex = 0,
                    volume = 1.0f,
                    muted = false,
                    solo = false,
                ),
            ),
            activeTrack = 0,
            bpm = 120.0,
            timeSignature = TimeSignature(4, 4),
        )
        val data = AutosaveData(project = original, viewport = ViewportState(), prefs = EditorPrefs(), savedAtMillis = 0)
        val restored = roundTrip(data)

        val track0 = restored.project.tracks[0]
        assertEquals("urn:lv2:example:synth", track0.pluginId)
        assertEquals("{\"cutoff\": 1000.0, \"resonance\": 0.5}", track0.pluginParams)
        assertTrue(track0.isAapPlugin)

        val track1 = restored.project.tracks[1]
        assertNull(track1.pluginId)
        assertNull(track1.pluginParams)
        assertFalse(track1.isAapPlugin)
    }

    @Test
    fun trackIsAapPluginDefaultsToFalse() {
        val track = Track(name = "Piano", channel = 0)
        assertFalse(track.isAapPlugin)
        assertNull(track.pluginId)
        assertNull(track.pluginParams)

        val aapTrack = Track(
            name = "Synth",
            channel = 0,
            pluginId = "urn:lv2:example:synth",
        )
        assertTrue(aapTrack.isAapPlugin)
        assertEquals("urn:lv2:example:synth", aapTrack.pluginId)
    }
}
