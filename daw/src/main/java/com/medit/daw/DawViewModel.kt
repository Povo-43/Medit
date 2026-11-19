package com.medit.daw

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.view.KeyEvent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medit.midi.audio.AudioExportFormat
import com.medit.midi.audio.AudioExporter
import com.medit.midi.audio.SoundFont
import com.medit.midi.audio.SoundFontPreset
import com.medit.midi.data.AutosaveData
import com.medit.midi.data.EditorPrefs
import com.medit.midi.data.ProjectRepository
import com.medit.midi.data.ProjectSummary
import com.medit.midi.data.ViewportState
import com.medit.midi.data.WorkspaceState
import com.medit.midi.editor.HoverInfo
import com.medit.midi.midi.MidiFile
import com.medit.midi.model.GridUnit
import com.medit.midi.model.MidiMeta
import com.medit.midi.model.Note
import com.medit.midi.model.Project
import com.medit.midi.model.TimeSignature
import com.medit.midi.model.TimeSignaturePoint
import com.medit.midi.model.Track
import com.medit.midi.model.noteName
import com.medit.midi.model.snapTick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.androidaudioplugin.PluginInformation
import org.androidaudioplugin.PluginServiceInformation
import org.androidaudioplugin.hosting.AudioPluginHostHelper
import org.androidaudioplugin.hosting.AudioPluginInstance
import org.androidaudioplugin.hosting.AudioPluginServiceConnector
import org.androidaudioplugin.hosting.NativePluginClient
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed interface DawAutosave {
    data object Idle : DawAutosave
    data object Saving : DawAutosave
    data class Saved(val atMillis: Long) : DawAutosave
    data class Failed(val message: String) : DawAutosave
}

sealed interface DawBoot {
    data object Loading : DawBoot
    data object Ready : DawBoot
}

data class DawBusy(
    val importing: Boolean = false,
    val exporting: Boolean = false,
    val instrument: Boolean = false,
)

/**
 * State and editing logic for MeditDAW. The piano-roll editing model is the
 * same as Medit's; the differences are that every track is auditioned at once
 * and each track owns a selectable SoundFont instrument.
 */
class DawViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val library = InstrumentLibrary(application)
    private val aapLibrary = AapInstrumentLibrary(application)
    private val audio = DawAudioEngine(application)
    private val playback = DawPlaybackEngine(audio)

    private val _project = MutableStateFlow<Project?>(null)
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    private val _prefs = MutableStateFlow(EditorPrefs())
    private val _viewport = MutableStateFlow(ViewportState())
    private val _viewportEpoch = MutableStateFlow(0)
    private val _hover = MutableStateFlow<HoverInfo?>(null)
    private val _undoCount = MutableStateFlow(0)
    private val _redoCount = MutableStateFlow(0)
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)
    private val _boot = MutableStateFlow<DawBoot>(DawBoot.Loading)
    private val _autosave = MutableStateFlow<DawAutosave>(DawAutosave.Idle)
    private val _busy = MutableStateFlow(DawBusy())
    private val _audioReady = MutableStateFlow(false)
    private val _audioLoading = MutableStateFlow(false)
    private val _instruments = MutableStateFlow<List<InstrumentLibrary.Entry>>(emptyList())
    private val _aapEntries = MutableStateFlow<List<AapEntry>>(emptyList())
    private val _projectList = MutableStateFlow<List<ProjectSummary>>(emptyList())
    private val _projectsLoading = MutableStateFlow(false)
    private val _projectBrowserVisible = MutableStateFlow(false)
    private val _trackPanelVisible = MutableStateFlow(true)
    private val _inspectorNoteId = MutableStateFlow<Long?>(null)
    private val _masterVolume = MutableStateFlow(1f)
    private val messages = Channel<String>(Channel.BUFFERED)

    // Data class to hold AAP plugin instance, its info, and the connector for lifecycle management
private data class AapPluginInstance(
    val plugin: AudioPluginInstance,
    val info: PluginInformation,
    val connector: AudioPluginServiceConnector
)

private val aapPluginInstances = HashMap<Int, AapPluginInstance>()

    val project: StateFlow<Project?> = _project.asStateFlow()
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()
    val prefs: StateFlow<EditorPrefs> = _prefs.asStateFlow()
    val viewport: StateFlow<ViewportState> = _viewport.asStateFlow()
    val viewportEpoch: StateFlow<Int> = _viewportEpoch.asStateFlow()
    val hover: StateFlow<HoverInfo?> = _hover.asStateFlow()
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()
    val boot: StateFlow<DawBoot> = _boot.asStateFlow()
    val autosave: StateFlow<DawAutosave> = _autosave.asStateFlow()
    val busy: StateFlow<DawBusy> = _busy.asStateFlow()
    val audioReady: StateFlow<Boolean> = _audioReady.asStateFlow()
    val audioLoading: StateFlow<Boolean> = _audioLoading.asStateFlow()
    val instruments: StateFlow<List<InstrumentLibrary.Entry>> = _instruments.asStateFlow()
    val aapEntries: StateFlow<List<AapEntry>> = _aapEntries.asStateFlow()
    val aapAvailable: kotlinx.coroutines.flow.Flow<Boolean> = _aapEntries.asStateFlow().map { entries -> entries.isNotEmpty() }
    val projects: StateFlow<List<ProjectSummary>> = _projectList.asStateFlow()
    val projectsLoading: StateFlow<Boolean> = _projectsLoading.asStateFlow()
    val projectBrowserVisible: StateFlow<Boolean> = _projectBrowserVisible.asStateFlow()
    val trackPanelVisible: StateFlow<Boolean> = _trackPanelVisible.asStateFlow()
    val inspectorNoteId: StateFlow<Long?> = _inspectorNoteId.asStateFlow()
    val masterVolume: StateFlow<Float> = _masterVolume.asStateFlow()
    val isPlaying: StateFlow<Boolean> = playback.isPlaying
    val messageFlow = messages.receiveAsFlow()

    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()
    private var clipboard: List<Note> = emptyList()
    private var clipboardBaseTick = 0
    private var projectId: String? = null
    private var autosaveEnabled = false
    private var autosaveJob: Job? = null
    private var audioSyncJob: Job? = null
    private var pendingImport: Uri? = null
    private val previewJobs = HashMap<Int, Job>()

    private data class EditSnapshot(
        val tracks: List<Track>,
        val activeTrack: Int,
        val bpm: Double,
        val timeSignature: TimeSignature,
        val timeSignatures: List<TimeSignaturePoint>,
        val meta: MidiMeta,
    )

    init {
        viewModelScope.launch {
            _instruments.value = withContext(Dispatchers.IO) { library.entries() }
            aapLibrary.refresh()
            _aapEntries.value = aapLibrary.entries.value
            restoreWorkspace()
        }
        viewModelScope.launch {
            combineProjectChanges()
        }
        // Keep AAP entries in sync
        viewModelScope.launch {
            aapLibrary.entries.collect { _aapEntries.value = it }
        }
    }

    private suspend fun combineProjectChanges() {
        // A lightweight debounce: the collectors below only schedule autosave,
        // and scheduleAutosave already coalesces bursts of edits.
        _prefs.drop(1).collect { if (autosaveEnabled) scheduleAutosave() }
    }

    // -----------------------------------------------------------------
    // Host bridge for the shared piano roll
    // -----------------------------------------------------------------

    fun projectNow(): Project? = _project.value
    fun prefsNow(): EditorPrefs = _prefs.value
    fun selectionNow(): Set<Long> = _selection.value
    fun playheadTick(): Double = playback.currentTick()
    fun isPlayingNow(): Boolean = playback.isPlaying.value
    fun isAudioReady(): Boolean = _audioReady.value
    fun noteById(id: Long): Note? = _project.value?.noteById(id)
    fun onHoverInfo(info: HoverInfo?) { _hover.value = info }
    fun onViewportSettled(viewport: ViewportState) {
        if (_viewport.value != viewport) {
            _viewport.value = viewport
            if (autosaveEnabled) scheduleAutosave()
        }
    }

    fun requestNoteInspector(id: Long) {
        if (_project.value?.noteById(id) == null) return
        _selection.value = setOf(id)
        _inspectorNoteId.value = id
    }

    fun dismissNoteInspector() { _inspectorNoteId.value = null }

    fun previewNote(pitch: Int, velocity: Int) {
        val p = _project.value ?: return
        val trackIndex = p.activeTrack
        val channel = p.track.channel
        previewJobs[pitch]?.cancel()
        audio.preview(trackIndex, pitch, velocity.coerceIn(1, 127), channel)
        previewJobs[pitch] = viewModelScope.launch {
            delay(320)
            audio.endPreview(trackIndex, pitch, channel)
            previewJobs.remove(pitch)
        }
    }

    fun addNoteAt(pitch: Int, startTick: Int, durationTicks: Int): Long? {
        val p = _project.value ?: return null
        val id = p.nextNoteId
        val note = Note(
            id = id,
            pitch = pitch.coerceIn(0, 127),
            startTick = startTick.coerceAtLeast(0),
            durationTicks = durationTicks.coerceAtLeast(1),
            velocity = _prefs.value.inputVelocity,
            channel = p.track.channel,
        )
        pushUndo()
        applyNotes(p.notes + note, p.copy(nextNoteId = id + 1))
        _selection.value = setOf(id)
        previewNote(note.pitch, note.velocity)
        return id
    }

    fun commitRollNotes(newNotes: List<Note>) {
        val p = _project.value ?: return
        if (newNotes == p.notes) return
        pushUndo()
        applyNotes(newNotes, p)
    }

    fun setRollSelection(ids: Set<Long>) {
        val available = _project.value?.notes?.mapTo(HashSet()) { it.id } ?: emptySet()
        _selection.value = ids.filterTo(LinkedHashSet()) { it in available }
    }

    fun deleteSelection() {
        val p = _project.value ?: return
        val ids = _selection.value
        if (ids.isEmpty()) {
            post("削除するノートを選択してください")
            return
        }
        pushUndo()
        applyNotes(p.notes.filterNot { it.id in ids }, p)
        _selection.value = emptySet()
    }

    fun deleteNotes(ids: Set<Long>) {
        val p = _project.value ?: return
        if (ids.isEmpty()) return
        pushUndo()
        applyNotes(p.notes.filterNot { it.id in ids }, p)
        _selection.value = _selection.value - ids
    }

    fun deleteNoteByDoubleTap(id: Long) {
        val p = _project.value ?: return
        if (p.noteById(id) == null) return
        deleteNotes(setOf(id))
        post("ノートを削除しました（元に戻すで復元できます）")
    }

    fun selectAll() {
        _selection.value = _project.value?.notes?.mapTo(LinkedHashSet()) { it.id } ?: emptySet()
    }

    fun clearSelection() { _selection.value = emptySet() }

    fun copySelection() {
        val selected = selectedNotes()
        if (selected.isEmpty()) {
            post("コピーするノートを選択してください")
            return
        }
        clipboardBaseTick = selected.minOf { it.startTick }
        clipboard = selected.map { it.copy(startTick = it.startTick - clipboardBaseTick) }
        post("${clipboard.size} 個のノートをコピーしました")
    }

    fun cutSelection() {
        if (_selection.value.isEmpty()) return
        copySelection()
        deleteSelection()
    }

    fun pasteAtPlayhead() {
        val p = _project.value ?: return
        if (clipboard.isEmpty()) {
            post("コピーしたノートがありません")
            return
        }
        val at = snapIfNeeded(playback.currentTick().toInt().coerceAtLeast(0))
        var nextId = p.nextNoteId
        val pasted = clipboard.map { n ->
            n.copy(
                id = nextId++,
                startTick = (at + n.startTick).coerceAtLeast(0),
                channel = p.track.channel,
            )
        }
        pushUndo()
        applyNotes(p.notes + pasted, p.copy(nextNoteId = nextId))
        _selection.value = pasted.mapTo(LinkedHashSet()) { it.id }
    }

    fun duplicateSelection() {
        val p = _project.value ?: return
        val selected = selectedNotes()
        if (selected.isEmpty()) {
            post("複製するノートを選択してください")
            return
        }
        val step = duplicateStepTicks(p)
        var nextId = p.nextNoteId
        val copies = selected.map { n -> n.copy(id = nextId++, startTick = n.startTick + step) }
        pushUndo()
        applyNotes(p.notes + copies, p.copy(nextNoteId = nextId))
        _selection.value = copies.mapTo(LinkedHashSet()) { it.id }
    }

    fun duplicateNote(id: Long) {
        val p = _project.value ?: return
        val note = p.noteById(id) ?: return
        val copy = note.copy(id = p.nextNoteId, startTick = note.startTick + duplicateStepTicks(p))
        pushUndo()
        applyNotes(p.notes + copy, p.copy(nextNoteId = p.nextNoteId + 1))
        _selection.value = setOf(copy.id)
    }

    fun transposeSelection(semitones: Int) {
        val p = _project.value ?: return
        val selected = selectedNotes()
        if (selected.isEmpty()) {
            post("移調するノートを選択してください")
            return
        }
        val ids = selected.mapTo(HashSet()) { it.id }
        var changed = false
        val notes = p.notes.map { n ->
            if (n.id in ids) {
                val pitch = (n.pitch + semitones).coerceIn(0, 127)
                if (pitch != n.pitch) changed = true
                n.copy(pitch = pitch)
            } else n
        }
        if (!changed) return
        pushUndo()
        applyNotes(notes, p)
    }

    fun updateNote(
        id: Long,
        pitch: Int? = null,
        startTick: Int? = null,
        durationTicks: Int? = null,
        velocity: Int? = null,
    ) {
        val p = _project.value ?: return
        val note = p.noteById(id) ?: return
        val updated = note.copy(
            pitch = (pitch ?: note.pitch).coerceIn(0, 127),
            startTick = (startTick ?: note.startTick).coerceAtLeast(0),
            durationTicks = (durationTicks ?: note.durationTicks).coerceAtLeast(1),
            velocity = (velocity ?: note.velocity).coerceIn(1, 127),
        )
        if (updated == note) return
        pushUndo()
        applyNotes(p.notes.map { if (it.id == id) updated else it }, p)
    }

    fun quantizeSelection() {
        val ids = _selection.value
        if (ids.isEmpty()) {
            post("クオンタイズするノートを選択してください")
            return
        }
        quantize(ids)
    }

    fun quantizeAll() {
        quantize(_project.value?.notes?.mapTo(HashSet()) { it.id } ?: emptySet())
    }

    private fun quantize(ids: Set<Long>) {
        val p = _project.value ?: return
        if (ids.isEmpty()) return
        val grid = _prefs.value.grid
        if (grid == GridUnit.OFF) {
            post("クオンタイズにはスナップ間隔が必要です")
            return
        }
        val notes = p.notes.map { n ->
            if (n.id in ids) n.copy(startTick = snapTick(n.startTick, grid, p.ppq).coerceAtLeast(0)) else n
        }
        pushUndo()
        applyNotes(notes, p)
        post("${ids.size} 個のノートをクオンタイズしました")
    }

    fun setSelectionVelocity(velocity: Int) {
        val p = _project.value ?: return
        val ids = selectedNotes().mapTo(HashSet()) { it.id }
        if (ids.isEmpty()) return
        pushUndo()
        applyNotes(p.notes.map { if (it.id in ids) it.copy(velocity = velocity.coerceIn(1, 127)) else it }, p)
    }

    fun setSelectionDuration(durationTicks: Int) {
        val p = _project.value ?: return
        val ids = selectedNotes().mapTo(HashSet()) { it.id }
        if (ids.isEmpty()) return
        pushUndo()
        applyNotes(p.notes.map { if (it.id in ids) it.copy(durationTicks = durationTicks.coerceAtLeast(1)) else it }, p)
    }

    // -----------------------------------------------------------------
    // Tracks and instruments
    // -----------------------------------------------------------------

    fun selectTrack(index: Int) {
        val p = _project.value ?: return
        if (index !in p.tracks.indices || index == p.activeTrack) return
        playback.pause()
        _selection.value = emptySet()
        _inspectorNoteId.value = null
        updateProject(p.copy(activeTrack = index))
        post("${p.tracks[index].name} を編集します")
    }

    fun addTrack() {
        val p = _project.value ?: return
        pushUndo()
        val used = p.tracks.mapTo(HashSet()) { it.channel }
        val channel = (0..15).firstOrNull { it != 9 && it !in used }
            ?: (0..15).firstOrNull { it !in used } ?: 0
        val track = Track(
            name = "Track ${p.tracks.size + 1}",
            channel = channel,
            soundId = InstrumentLibrary.BUNDLED_ID,
        )
        updateProject(p.copy(tracks = p.tracks + track, activeTrack = p.tracks.size))
        _selection.value = emptySet()
        post("トラックを追加しました")
    }

    fun duplicateTrack(index: Int) {
        val p = _project.value ?: return
        val source = p.tracks.getOrNull(index) ?: return
        pushUndo()
        var nextId = p.nextNoteId
        val notes = source.notes.map { it.copy(id = nextId++) }
        val copy = source.copy(name = "${source.name} 2", notes = notes)
        val tracks = p.tracks.toMutableList()
        tracks.add(index + 1, copy)
        updateProject(p.copy(tracks = tracks, activeTrack = index + 1, nextNoteId = nextId))
        _selection.value = emptySet()
        post("トラックを複製しました")
    }

    fun deleteTrack(index: Int) {
        val p = _project.value ?: return
        if (p.tracks.size <= 1) {
            post("最後のトラックは削除できません")
            return
        }
        if (index !in p.tracks.indices) return
        playback.pause()
        pushUndo()
        val tracks = p.tracks.toMutableList().apply { removeAt(index) }
        val active = when {
            p.activeTrack > index -> p.activeTrack - 1
            p.activeTrack == index -> index.coerceAtMost(tracks.lastIndex)
            else -> p.activeTrack
        }
        updateProject(p.copy(tracks = tracks, activeTrack = active))
        _selection.value = emptySet()
        syncAudio()
        post("トラックを削除しました")
    }

    fun renameTrack(index: Int, name: String) {
        val p = _project.value ?: return
        val track = p.tracks.getOrNull(index) ?: return
        val clean = name.trim().take(60).ifBlank { "Track ${index + 1}" }
        if (track.name == clean) return
        pushUndo()
        val tracks = p.tracks.toMutableList()
        tracks[index] = track.copy(name = clean)
        updateProject(p.copy(tracks = tracks))
    }

    fun setTrackVolume(index: Int, volume: Float) {
        val p = _project.value ?: return
        val track = p.tracks.getOrNull(index) ?: return
        val v = volume.coerceIn(0f, 2f)
        if (abs(track.volume - v) < 0.001f) return
        val tracks = p.tracks.toMutableList()
        tracks[index] = track.copy(volume = v)
        updateProject(p.copy(tracks = tracks))
        syncAudio()
        if (autosaveEnabled) scheduleAutosave()
    }

    fun toggleMute(index: Int) {
        val p = _project.value ?: return
        val track = p.tracks.getOrNull(index) ?: return
        val tracks = p.tracks.toMutableList()
        tracks[index] = track.copy(muted = !track.muted)
        updateProject(p.copy(tracks = tracks))
        syncAudio()
    }

    fun toggleSolo(index: Int) {
        val p = _project.value ?: return
        val track = p.tracks.getOrNull(index) ?: return
        val tracks = p.tracks.toMutableList()
        tracks[index] = track.copy(solo = !track.solo)
        updateProject(p.copy(tracks = tracks))
        syncAudio()
    }

    fun setTrackInstrument(index: Int, soundId: String, presetIndex: Int) {
        val p = _project.value ?: return
        val track = p.tracks.getOrNull(index) ?: return
        if (track.soundId == soundId && track.presetIndex == presetIndex) return
        playback.pause()
        pushUndo()
        val tracks = p.tracks.toMutableList()
        // Switching to a SoundFont clears any AAP plugin assignment
        val newTrack = track.copy(soundId = soundId, presetIndex = presetIndex, pluginId = null, pluginParams = null)
        tracks[index] = newTrack
        updateProject(p.copy(tracks = tracks))
        syncAudio()
        post("${track.name} の音源を ${library.displayName(soundId)} にしました")
    }

    /**
     * Sets an AAP plugin as the sound source for [index]. Passing null clears
     * the plugin and falls back to the SoundFont.
     */
    fun setTrackPlugin(index: Int, pluginId: String?) {
        val p = _project.value ?: return
        val track = p.tracks.getOrNull(index) ?: return
        if (track.pluginId == pluginId) return
        playback.pause()
        pushUndo()
        val tracks = p.tracks.toMutableList()
        val newTrack = track.copy(pluginId = pluginId, pluginParams = null)
        tracks[index] = newTrack
        updateProject(p.copy(tracks = tracks))
        syncAudio()
        val name = pluginId?.let { aapLibrary.find(it)?.displayName ?: it } ?: "SoundFont"
        post("${track.name} の音源を $name にしました")
    }

    /** Sets a parameter on the AAP plugin of track [index]. */
    fun setTrackPluginParameter(index: Int, paramId: Int, value: Float) {
        val p = _project.value ?: return
        val track = p.tracks.getOrNull(index) ?: return
        if (!track.isAapPlugin) return
        val jsonObj = track.pluginParams?.let { org.json.JSONObject(it) } ?: org.json.JSONObject()
        jsonObj.put(paramId.toString(), value)
        val json = jsonObj.toString()
        val tracks = p.tracks.toMutableList()
        tracks[index] = track.copy(pluginParams = json)
        updateProject(p.copy(tracks = tracks))
        if (autosaveEnabled) scheduleAutosave()
    }

    fun refreshAapPlugins() {
        viewModelScope.launch {
            aapLibrary.refresh()
            _aapEntries.value = aapLibrary.entries.value
        }
    }

    fun setMasterVolume(value: Float) {
        val v = value.coerceIn(0f, 1.5f)
        _masterVolume.value = v
        _prefs.value = _prefs.value.copy(soundFontVolume = v)
        audio.setGlobalVolume(v)
        if (autosaveEnabled) scheduleAutosave()
    }

    fun currentMasterVolume(): Float = _masterVolume.value

    fun refreshInstruments() {
        viewModelScope.launch {
            _instruments.value = withContext(Dispatchers.IO) { library.entries() }
        }
    }

    fun importInstrument(uri: Uri) {
        if (_busy.value.instrument) return
        _busy.value = _busy.value.copy(instrument = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                library.import(app.contentResolver, uri)
            }
            result.fold(
                onSuccess = { entry ->
                    refreshInstruments()
                    post("音源「${entry.name}」を追加しました")
                },
                onFailure = { t -> post("音源を追加できません: ${t.message ?: t.javaClass.simpleName}") },
            )
            _busy.value = _busy.value.copy(instrument = false)
        }
    }

    fun deleteInstrument(entry: InstrumentLibrary.Entry) {
        if (entry.builtIn) return
        val p = _project.value
        if (p != null) {
            var changed = false
            val tracks = p.tracks.map {
                if (it.soundId == entry.id) {
                    changed = true
                    it.copy(soundId = InstrumentLibrary.BUNDLED_ID, presetIndex = 0)
                } else it
            }
            if (changed) {
                updateProject(p.copy(tracks = tracks))
                syncAudio()
            }
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.delete(entry.id) }
            refreshInstruments()
            post("音源「${entry.name}」を削除しました")
        }
    }

    suspend fun presetsFor(soundId: String): List<SoundFontPreset> =
        withContext(Dispatchers.IO) { library.presets(soundId) }

    fun instrumentName(soundId: String): String =
        _instruments.value.firstOrNull { it.id == soundId }?.name ?: "Medit Piano"

    // -----------------------------------------------------------------
    // Mix and playback
    // -----------------------------------------------------------------

    private fun syncAudio() {
        val p = _project.value ?: return
        audioSyncJob?.cancel()
        _audioLoading.value = true
        audioSyncJob = viewModelScope.launch {
            val fonts = withContext(Dispatchers.IO) {
                val cache = HashMap<String, SoundFont?>()
                p.tracks.map { track ->
                    cache.getOrPut(track.soundId) { library.font(track.soundId) }
                }
            }
            val fontMap = fonts.withIndex().associate { it.index to it.value }

            // Load AAP plugin instances for tracks that use them
            val aapMap = HashMap<Int, Triple<AudioPluginInstance, PluginInformation, AudioPluginServiceConnector>>()
            for ((index, track) in p.tracks.withIndex()) {
                if (track.isAapPlugin) {
                    val existing = aapPluginInstances[index]
                    if (existing != null && existing.info.pluginId == track.pluginId) {
                        aapMap[index] = Triple(existing.plugin, existing.info, existing.connector)
                    } else {
                        // Unload old instance if plugin changed
                        existing?.plugin?.destroy()
                        existing?.connector?.close()
                        val newInstance = loadAapPlugin(track)
                        if (newInstance != null) {
                            aapPluginInstances[index] = newInstance
                            aapMap[index] = Triple(newInstance.plugin, newInstance.info, newInstance.connector)
                        }
                    }
                } else {
                    // Clean up any AAP instance for non-AAP tracks
                    aapPluginInstances[index]?.plugin?.destroy()
                    aapPluginInstances[index]?.connector?.close()
                    aapPluginInstances.remove(index)
                }
            }

            audio.sync(p, fontMap, aapMap)
            _audioReady.value = fontMap.any { it != null } || aapMap.isNotEmpty()
            _audioLoading.value = false
            val missingFonts = p.tracks.filterIndexed { i, _ -> !p.tracks[i].isAapPlugin && fontMap.get(i) == null }
            val missingAaps = p.tracks.filterIndexed { i, _ -> p.tracks[i].isAapPlugin && aapMap.get(i) == null }
            val missing = missingFonts + missingAaps
            if (missing.isNotEmpty()) {
                post("音源を読み込めないトラックがあります: ${missing.joinToString { it.name }}")
            }
        }
    }

    /**
     * Loads and binds an AAP plugin instance for [track]. Returns the plugin
     * instance, its [PluginInformation], and the [AudioPluginServiceConnector] for lifecycle management.
     */
    private suspend fun loadAapPlugin(track: Track): AapPluginInstance? {
        val pluginId = track.pluginId ?: return null

        // Query the plugin service information for the track's plugin ID
        val serviceInfo = try {
            AudioPluginHostHelper.queryAudioPluginService(app, pluginId)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        if (serviceInfo == null) return null

        // Bind to the plugin service and ensure the native binder is connected
        val connector = AudioPluginServiceConnector(app)
        try {
            AudioPluginHostHelper.ensureBinderConnected(serviceInfo, connector)
        } catch (e: Exception) {
            e.printStackTrace()
            connector.close()
            return null
        }

        // Create a NativePluginClient from the connection
        val connectionId = connector.serviceConnectionId
        val nativePluginClient = NativePluginClient.Companion.createFromConnection(connectionId)

        // Create the plugin instance from the existing connection
        // The native method expects the plugin index within the service, not the string pluginId
        val pluginIndex = serviceInfo.plugins.indexOfFirst { it.pluginId == pluginId }
        if (pluginIndex < 0) return null
        val nativeRemotePluginInstance = try {
            nativePluginClient.createInstanceFromExistingConnection(pluginIndex, serviceInfo.packageName)
        } catch (e: Exception) {
            e.printStackTrace()
            nativePluginClient.dispose()
            connector.close()
            return null
        }

        // Wrap the native instance in AudioPluginInstance using the proper factory method
        val pluginInfo: PluginInformation = serviceInfo.plugins.firstOrNull { it.pluginId == pluginId } ?: serviceInfo.plugins.first()
        val audioPluginInstance = AudioPluginInstance.Companion.create(
            nativePluginClient,
            { it.destroy() },
            pluginInfo,
            0
        )

        // Prepare and activate the plugin
        try {
            audioPluginInstance.prepare(44100, 256)
            audioPluginInstance.activate()
        } catch (e: Exception) {
            e.printStackTrace()
            audioPluginInstance.destroy()
            nativePluginClient.dispose()
            connector.close()
            return null
        }

        // Return the plugin instance with its info and connector for lifecycle management
        return AapPluginInstance(
            plugin = audioPluginInstance,
            info = serviceInfo.plugins.firstOrNull { it.pluginId == track.pluginId } ?: serviceInfo.plugins.first(),
            connector = connector
        )
    }

    fun play() {
        val p = _project.value ?: return
        if (!_audioReady.value) {
            post("音源を読み込み中です")
            syncAudio()
            return
        }
        playback.play(p)
    }

    fun pause() = playback.pause()
    fun stopPlayback() = playback.stop()
    fun togglePlayback() {
        if (playback.isPlaying.value) pause() else play()
    }
    fun seekTo(tick: Double) = playback.seek(tick)

    fun setBpm(bpm: Double) {
        val p = _project.value ?: return
        val value = bpm.coerceIn(20.0, 400.0)
        if (abs(p.bpm - value) < 0.0001) return
        pushUndo()
        updateProject(p.copy(bpm = value))
    }

    fun setTimeSignature(numerator: Int, denominator: Int) {
        val p = _project.value ?: return
        val sig = runCatching { TimeSignature(numerator, denominator) }.getOrNull() ?: return
        if (p.timeSignature == sig) return
        pushUndo()
        val sigs = p.timeSignatures.toMutableList()
        val idx = sigs.indexOfFirst { it.tick == 0 }
        val point = TimeSignaturePoint(0, sig)
        if (idx >= 0) sigs[idx] = point else sigs.add(0, point)
        updateProject(p.copy(timeSignature = sig, timeSignatures = sigs.sortedBy { it.tick }))
    }

    fun toggleLoop() {
        val p = _project.value ?: return
        val updated = if (!p.loopEnabled && p.loopEndTick <= p.loopStartTick) {
            p.copy(loopEnabled = true, loopEndTick = p.loopStartTick + p.ppq * 16)
        } else {
            p.copy(loopEnabled = !p.loopEnabled)
        }
        updateProject(updated)
    }

    fun setLoopRange(startTick: Int, endTick: Int) {
        val p = _project.value ?: return
        val a = min(startTick, endTick).coerceAtLeast(0)
        val b = max(startTick, endTick).coerceAtLeast(a + 1)
        updateProject(p.copy(loopStartTick = a, loopEndTick = b, loopEnabled = true))
    }

    fun setMidiMeta(meta: MidiMeta) {
        val p = _project.value ?: return
        if (p.meta == meta) return
        pushUndo()
        updateProject(p.copy(meta = meta))
    }

    // -----------------------------------------------------------------
    // Preferences
    // -----------------------------------------------------------------

    fun setGrid(grid: GridUnit) { _prefs.value = _prefs.value.copy(grid = grid); scheduleAutosave() }
    fun cycleGrid() {
        val values = GridUnit.entries
        val idx = values.indexOf(_prefs.value.grid)
        setGrid(values[(idx + 1) % values.size])
    }
    fun setInputDuration(duration: GridUnit) {
        _prefs.value = _prefs.value.copy(inputDuration = duration)
        scheduleAutosave()
    }
    fun setSnapEnabled(on: Boolean) {
        _prefs.value = _prefs.value.copy(snapEnabled = on)
        scheduleAutosave()
    }
    fun setInputVelocity(velocity: Int) {
        _prefs.value = _prefs.value.copy(inputVelocity = velocity.coerceIn(1, 127))
        scheduleAutosave()
    }
    fun setAutoScroll(on: Boolean) {
        _prefs.value = _prefs.value.copy(autoScroll = on)
        scheduleAutosave()
    }
    fun setFingerEditing(on: Boolean) {
        _prefs.value = _prefs.value.copy(fingerEditing = on)
        scheduleAutosave()
    }
    fun toggleFingerEditing() {
        _prefs.value = _prefs.value.copy(fingerEditing = !_prefs.value.fingerEditing)
        scheduleAutosave()
    }
    fun toggleVelocityEditor() {
        _prefs.value = _prefs.value.copy(velocityEditor = !_prefs.value.velocityEditor)
        scheduleAutosave()
    }
    fun toggleMultiSelectMode() {
        _prefs.value = _prefs.value.copy(multiSelectMode = !_prefs.value.multiSelectMode)
    }
    fun setShowSustainLane(on: Boolean) {
        _prefs.value = _prefs.value.copy(showSustainLane = on)
        scheduleAutosave()
    }
    fun toggleTrackPanel() { _trackPanelVisible.value = !_trackPanelVisible.value }

    // -----------------------------------------------------------------
    // Undo / redo
    // -----------------------------------------------------------------

    fun undo() {
        val p = _project.value ?: return
        val snapshot = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshotOf(p))
        restoreSnapshot(snapshot)
        updateHistory()
        syncAudio()
        post("元に戻しました")
    }

    fun redo() {
        val p = _project.value ?: return
        val snapshot = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshotOf(p))
        restoreSnapshot(snapshot)
        updateHistory()
        syncAudio()
        post("やり直しました")
    }

    private fun pushUndo() {
        val p = _project.value ?: return
        undoStack.addLast(snapshotOf(p))
        while (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        updateHistory()
    }

    private fun updateHistory() {
        _undoCount.value = undoStack.size
        _redoCount.value = redoStack.size
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun snapshotOf(p: Project) = EditSnapshot(
        tracks = p.tracks,
        activeTrack = p.activeTrack,
        bpm = p.bpm,
        timeSignature = p.timeSignature,
        timeSignatures = p.timeSignatures,
        meta = p.meta,
    )

    private fun restoreSnapshot(snapshot: EditSnapshot) {
        val p = _project.value ?: return
        val updated = p.copy(
            tracks = snapshot.tracks,
            activeTrack = snapshot.activeTrack.coerceIn(0, snapshot.tracks.size - 1),
            bpm = snapshot.bpm,
            timeSignature = snapshot.timeSignature,
            timeSignatures = snapshot.timeSignatures,
            meta = snapshot.meta,
        )
        updateProject(updated)
        val ids = updated.notes.mapTo(HashSet()) { it.id }
        _selection.value = _selection.value.filterTo(LinkedHashSet()) { it in ids }
    }

    private fun applyNotes(newNotes: List<Note>, base: Project) {
        val maxId = newNotes.maxOfOrNull { it.id } ?: 0L
        val updated = base.withActiveNotes(newNotes).copy(
            nextNoteId = maxOf(base.nextNoteId, maxId + 1),
        )
        updateProject(updated)
        val ids = updated.notes.mapTo(HashSet()) { it.id }
        _selection.value = _selection.value.filterTo(LinkedHashSet()) { it in ids }
    }

    private fun selectedNotes(): List<Note> {
        val p = _project.value ?: return emptyList()
        val ids = _selection.value
        return p.notes.filter { it.id in ids }
    }

    private fun updateProject(updated: Project) {
        _project.value = updated
        playback.setProject(updated)
        if (autosaveEnabled) scheduleAutosave()
    }

    // -----------------------------------------------------------------
    // Project lifecycle and persistence
    // -----------------------------------------------------------------

    private suspend fun restoreWorkspace() {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val ws = ProjectRepository.loadWorkspace(app)
                val id = ws.openIds.firstOrNull()?.takeIf { ProjectRepository.exists(app, it) }
                val data = id?.let { ProjectRepository.load(app, it).getOrNull() }
                Triple(ws, id, data)
            }
        }
        result.fold(
            onSuccess = { (ws, id, data) ->
                _prefs.value = ws.prefs
                _masterVolume.value = ws.prefs.soundFontVolume.coerceIn(0f, 1.5f)
                audio.setGlobalVolume(_masterVolume.value)
                autosaveEnabled = true
                if (id != null && data != null) {
                    projectId = id
                    _project.value = data.project
                    _viewport.value = data.viewport
                    _viewportEpoch.value++
                    playback.setProject(data.project)
                } else {
                    createProject(save = false)
                }
                _boot.value = DawBoot.Ready
                syncAudio()
                persistWorkspace()
                runPendingImport()
            },
            onFailure = { t ->
                _boot.value = DawBoot.Ready
                autosaveEnabled = true
                post("ワークスペースを復元できませんでした: ${t.message ?: t.javaClass.simpleName}")
                createProject(save = false)
                syncAudio()
            },
        )
    }

    private fun createProject(save: Boolean) {
        val project = Project.empty()
        projectId = ProjectRepository.newId()
        undoStack.clear()
        redoStack.clear()
        clipboard = emptyList()
        _selection.value = emptySet()
        _inspectorNoteId.value = null
        _project.value = project
        _viewport.value = ViewportState()
        _viewportEpoch.value++
        playback.setProject(project)
        updateHistory()
        if (save) saveNow()
    }

    fun newProject() {
        playback.stop()
        audio.allNotesOff()
        createProject(save = true)
        post("新しいプロジェクトを作成しました")
    }

    fun renameProject(title: String) {
        val p = _project.value ?: return
        val clean = title.trim().take(80).ifBlank { "無題" }
        if (clean == p.title) return
        updateProject(p.copy(title = clean))
        saveNow()
    }

    fun saveNow() {
        autosaveJob?.cancel()
        viewModelScope.launch { saveInternal() }
    }

    fun onBackground() {
        playback.pause()
        audio.allNotesOff()
        saveNow()
    }

    private fun scheduleAutosave() {
        if (!autosaveEnabled) return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(650)
            saveInternal()
        }
    }

    private suspend fun saveInternal() {
        if (!autosaveEnabled) return
        val project = _project.value ?: return
        val id = projectId ?: return
        val snapshot = AutosaveData(
            project = project,
            viewport = _viewport.value,
            prefs = _prefs.value,
            savedAtMillis = System.currentTimeMillis(),
        )
        _autosave.value = DawAutosave.Saving
        val result = withContext(Dispatchers.IO) { ProjectRepository.save(app, id, snapshot) }
        _autosave.value = result.fold(
            onSuccess = { DawAutosave.Saved(snapshot.savedAtMillis) },
            onFailure = { DawAutosave.Failed(it.message ?: "自動保存に失敗") },
        )
        persistWorkspace()
    }

    private fun persistWorkspace() {
        val id = projectId ?: return
        val state = WorkspaceState(
            openIds = listOf(id),
            activeIndex = 0,
            prefs = _prefs.value,
        )
        viewModelScope.launch { withContext(Dispatchers.IO) { ProjectRepository.saveWorkspace(app, state) } }
    }

    override fun onCleared() {
        autosaveJob?.cancel()
        audioSyncJob?.cancel()
        previewJobs.values.forEach { it.cancel() }
        playback.shutdown()
        audio.shutdown()
        // Destroy all AAP plugin instances
        aapPluginInstances.values.forEach { (instance, _) ->
            instance.destroy()
        }
        aapPluginInstances.clear()
        aapLibrary.shutdown()
        super.onCleared()
    }

    // -----------------------------------------------------------------
    // Project browser
    // -----------------------------------------------------------------

    fun refreshProjects() {
        _projectsLoading.value = true
        viewModelScope.launch {
            _projectList.value = withContext(Dispatchers.IO) { ProjectRepository.list(app) }
            _projectsLoading.value = false
        }
    }

    fun openProjectBrowser() {
        refreshProjects()
        _projectBrowserVisible.value = true
    }

    fun dismissProjectBrowser() { _projectBrowserVisible.value = false }

    fun openProject(id: String) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { ProjectRepository.load(app, id) }
                .getOrNull() ?: run {
                    post("プロジェクトを開けませんでした")
                    return@launch
                }
            playback.stop()
            audio.allNotesOff()
            projectId = id
            undoStack.clear()
            redoStack.clear()
            clipboard = emptyList()
            _selection.value = emptySet()
            _inspectorNoteId.value = null
            _project.value = data.project
            _viewport.value = data.viewport
            _viewportEpoch.value++
            playback.setProject(data.project)
            updateHistory()
            syncAudio()
            persistWorkspace()
            _projectBrowserVisible.value = false
            post("「${data.project.title}」を開きました")
        }
    }

    fun renameStoredProject(id: String, title: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ProjectRepository.rename(app, id, title) }
                .fold(
                    onSuccess = {
                        if (id == projectId) {
                            _project.value = _project.value?.copy(
                                title = title.trim().take(80).ifBlank { "無題" },
                            )
                        }
                        refreshProjects()
                        post("名前を変更しました")
                    },
                    onFailure = { post("名前を変更できませんでした") },
                )
        }
    }

    fun deleteStoredProject(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ProjectRepository.delete(app, id) }
            if (id == projectId) createProject(save = true)
            refreshProjects()
            post("プロジェクトを削除しました")
        }
    }

    // -----------------------------------------------------------------
    // Import / export
    // -----------------------------------------------------------------

    fun importMidi(uri: Uri) {
        if (_boot.value is DawBoot.Loading) {
            pendingImport = uri
            return
        }
        if (_busy.value.importing) return
        _busy.value = _busy.value.copy(importing = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = app.contentResolver
                    val (name, size) = queryMeta(resolver, uri)
                    if (size != null && size > 32L * 1024 * 1024) {
                        throw IOException("MIDIファイルが大きすぎます (最大32MB)")
                    }
                    val bytes = resolver.openInputStream(uri)?.use { readLimited(it, 32 * 1024 * 1024) }
                        ?: throw IOException("ファイルを開けません")
                    val display = name ?: "import.mid"
                    val title = display.substringBeforeLast('.').ifBlank { "読み込んだ曲" }
                    MidiFile.parse(bytes, title)
                }
            }
            result.fold(
                onSuccess = { parsed ->
                    playback.stop()
                    audio.allNotesOff()
                    projectId = ProjectRepository.newId()
                    undoStack.clear()
                    redoStack.clear()
                    clipboard = emptyList()
                    _selection.value = emptySet()
                    _project.value = parsed.project
                    _viewport.value = ViewportState(
                        lowestPitch = parsed.project.notes.minOfOrNull { it.pitch }
                            ?.minus(4)?.toFloat()?.coerceIn(0f, 108f) ?: 36f,
                    )
                    _viewportEpoch.value++
                    playback.setProject(parsed.project)
                    updateHistory()
                    syncAudio()
                    saveNow()
                    val warn = parsed.warnings.take(3).joinToString(" / ")
                    post(
                        if (warn.isBlank()) "MIDIを読み込みました (${parsed.project.tracks.size}トラック)"
                        else "MIDIを読み込みました: $warn",
                    )
                },
                onFailure = { t -> post("MIDIを読み込めません: ${t.message ?: t.javaClass.simpleName}") },
            )
            _busy.value = _busy.value.copy(importing = false)
        }
    }

    /**
     * Exports the project as MIDI.
     *
     * [formatType] `1` = SMF Type 1 (one track chunk per project track, all tracks).
     * `0` = SMF Type 0 (a single track chunk; only [trackIndex] is written).
     */
    fun exportMidi(uri: Uri, formatType: Int = 1, trackIndex: Int = 0) {
        val p = _project.value ?: return
        if (_busy.value.exporting) return
        if (formatType == 0 && trackIndex !in p.tracks.indices) {
            post("トラック $trackIndex が見つかりません")
            return
        }
        _busy.value = _busy.value.copy(exporting = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = when (formatType) {
                        0 -> MidiFile.writeType0(p, trackIndex)
                        else -> MidiFile.writeType1(p)
                    }
                    app.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(bytes)
                        out.flush()
                    } ?: throw IOException("保存先を開けません")
                }
            }
            val label = when (formatType) {
                0 -> "MIDI（形式0 / トラック ${trackIndex + 1}）を書き出しました"
                else -> "MIDI（形式1 / 全トラック）を書き出しました"
            }
            result.fold(
                onSuccess = { post(label) },
                onFailure = { t -> post("書き出しに失敗しました: ${t.message ?: t.javaClass.simpleName}") },
            )
            _busy.value = _busy.value.copy(exporting = false)
        }
    }

    fun exportAudio(uri: Uri, format: AudioExportFormat) {
        val p = _project.value ?: return
        if (_busy.value.exporting) return
        if (p.tracks.all { it.notes.isEmpty() }) {
            post("ノートがないため音声を書き出せません")
            return
        }
        playback.pause()
        _busy.value = _busy.value.copy(exporting = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fonts = HashMap<String, SoundFont?>()
                    val sources = p.tracks.map { track ->
                        fonts.getOrPut(track.soundId) { library.font(track.soundId) }
                    }
                    if (sources.all { it == null }) throw IOException("音源が準備できていません")
                    val anySolo = p.tracks.any { it.solo }
                    val gains = p.tracks.map { track ->
                        val audible = if (anySolo) track.solo && !track.muted else !track.muted
                        if (audible) track.volume else 0f
                    }
                    val pcm = AudioExporter.renderMixPcm(p, sources, gains, _masterVolume.value)
                    if (pcm.isEmpty()) throw IOException("書き出す音がありません")
                    when (format) {
                        AudioExportFormat.MP3 ->
                            app.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                                AudioExporter.encodeMp3(pcm, out)
                            } ?: throw IOException("保存先を開けません")
                        AudioExportFormat.AAC ->
                            app.contentResolver.openFileDescriptor(uri, "rwt")?.use { fd ->
                                AudioExporter.encodeAac(pcm, fd.fileDescriptor)
                            } ?: throw IOException("保存先を開けません")
                    }
                }
            }
            result.fold(
                onSuccess = { post("${format.label}を書き出しました") },
                onFailure = { t -> post("書き出しに失敗しました: ${t.message ?: t.javaClass.simpleName}") },
            )
            _busy.value = _busy.value.copy(exporting = false)
        }
    }

    fun shareMidi() {
        val p = _project.value ?: return
        if (_busy.value.exporting) return
        _busy.value = _busy.value.copy(exporting = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = MidiFile.write(p)
                    val dir = File(app.cacheDir, "shared").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val name = suggestedFileName()
                    val file = File(dir, name)
                    file.writeBytes(bytes)
                    FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file) to name
                }
            }
            result.fold(
                onSuccess = { (uri, name) ->
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "audio/midi"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, name)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = android.content.Intent.createChooser(send, "MIDIを共有")
                    chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    app.startActivity(chooser)
                },
                onFailure = { t -> post("共有に失敗しました: ${t.message ?: t.javaClass.simpleName}") },
            )
            _busy.value = _busy.value.copy(exporting = false)
        }
    }

    fun suggestedFileName(): String {
        val title = _project.value?.title ?: "daw"
        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "daw" }
        return "$safe.mid"
    }

    fun suggestedAudioFileName(format: AudioExportFormat): String {
        val title = _project.value?.title ?: "daw"
        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "daw" }
        return "$safe.${format.extension}"
    }

    private fun runPendingImport() {
        val uri = pendingImport ?: return
        pendingImport = null
        importMidi(uri)
    }

    // -----------------------------------------------------------------
    // Helpers / keyboard
    // -----------------------------------------------------------------

    fun onShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val ctrl = event.isCtrlPressed || event.isMetaPressed
        val shift = event.isShiftPressed
        if (ctrl) {
            return when (keyCode) {
                KeyEvent.KEYCODE_Z -> { if (shift) redo() else undo(); true }
                KeyEvent.KEYCODE_Y -> { redo(); true }
                KeyEvent.KEYCODE_S -> { saveNow(); true }
                KeyEvent.KEYCODE_C -> { copySelection(); true }
                KeyEvent.KEYCODE_X -> { cutSelection(); true }
                KeyEvent.KEYCODE_V -> { pasteAtPlayhead(); true }
                KeyEvent.KEYCODE_A -> { selectAll(); true }
                KeyEvent.KEYCODE_D -> { duplicateSelection(); true }
                KeyEvent.KEYCODE_N -> { newProject(); true }
                else -> false
            }
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> { if (event.repeatCount == 0) togglePlayback(); true }
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL ->
                _selection.value.isNotEmpty().also { if (it) deleteSelection() }
            KeyEvent.KEYCODE_ESCAPE -> {
                if (playback.isPlaying.value) stopPlayback() else clearSelection()
                true
            }
            else -> false
        }
    }

    fun autosaveLabel(): String = when (val s = _autosave.value) {
        DawAutosave.Idle -> "未保存"
        DawAutosave.Saving -> "自動保存中…"
        is DawAutosave.Saved -> {
            val f = SimpleDateFormat("HH:mm:ss", Locale.JAPAN)
            "自動保存 ${f.format(Date(s.atMillis))}"
        }
        is DawAutosave.Failed -> "自動保存エラー"
    }

    fun trackLabel(index: Int): String {
        val t = _project.value?.tracks?.getOrNull(index) ?: return "Track ${index + 1}"
        return "${index + 1}. ${t.name} (${t.noteCount})"
    }

    fun pitchLabel(pitch: Int): String = noteName(pitch)

    private fun duplicateStepTicks(p: Project): Int {
        val grid = _prefs.value.grid
        return if (grid != GridUnit.OFF) grid.ticks(p.ppq)
        else _prefs.value.inputDuration.ticks(p.ppq)
    }

    private fun snapIfNeeded(tick: Int): Int {
        val p = _project.value ?: return tick
        return if (_prefs.value.snapEnabled) snapTick(tick, _prefs.value.grid, p.ppq) else tick
    }

    private fun post(text: String) { messages.trySend(text) }

    private fun queryMeta(resolver: ContentResolver, uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        if (name == null) name = uri.lastPathSegment?.substringAfterLast('/')
        return name to size
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw IOException("ファイルが大きすぎます")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
