package com.medit.daw

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.automirrored.rounded.VolumeUp as AutoMirroredVolumeUp
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medit.midi.audio.AudioExportFormat
import com.medit.midi.audio.SoundFontPreset
import com.medit.midi.data.EditorPrefs
import com.medit.midi.data.ProjectRepository
import com.medit.midi.data.ViewportState
import com.medit.midi.editor.HoverInfo
import com.medit.midi.editor.RollHost
import com.medit.midi.model.GridUnit
import com.medit.midi.model.Note
import com.medit.midi.model.Project
import com.medit.midi.model.TimeSignature
import com.medit.midi.model.Track
import com.medit.midi.ui.Amber
import com.medit.midi.ui.Coral
import com.medit.midi.ui.Ink
import com.medit.midi.ui.MeditTheme
import com.medit.midi.ui.Mist
import com.medit.midi.ui.OutlineSoft
import com.medit.midi.ui.Panel
import com.medit.midi.ui.PanelRaised
import com.medit.midi.ui.Paper
import com.medit.midi.ui.PianoRollView
import com.medit.midi.ui.Teal
import kotlinx.coroutines.delay

private val trackColors = listOf(
    Color(0xFFF2B65A),
    Color(0xFF3ED6C4),
    Color(0xFF9BC7FF),
    Color(0xFFFF9F7A),
    Color(0xFFC4A7FF),
    Color(0xFF8BE28B),
    Color(0xFFFFD36E),
    Color(0xFFFF8FB1),
)

@Composable
fun DawApp(vm: DawViewModel, initialUri: String? = null) {
    MeditTheme {
        val project by vm.project.collectAsStateWithLifecycle()
        val boot by vm.boot.collectAsStateWithLifecycle()
        val snackbarHost = remember { SnackbarHostState() }
        var pendingImport by remember { mutableStateOf(initialUri) }

        LaunchedEffect(vm) {
            vm.messageFlow.collect { snackbarHost.showSnackbar(it) }
        }
        LaunchedEffect(pendingImport, boot) {
            val raw = pendingImport
            if (!raw.isNullOrBlank() && boot is DawBoot.Ready) {
                pendingImport = null
                runCatching { vm.importMidi(Uri.parse(raw)) }
            }
        }

        var showExportMidiDialog by remember { mutableStateOf(false) }
        var exportFormatType by remember { mutableStateOf(1) }
        var exportTrackIndex by remember { mutableStateOf(0) }

        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> uri?.let(vm::importMidi) }
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("audio/midi")
        ) { uri ->
            uri?.let { vm.exportMidi(it, exportFormatType, exportTrackIndex) }
            showExportMidiDialog = false
        }
        val mp3Launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("audio/mpeg")
        ) { uri -> uri?.let { vm.exportAudio(it, AudioExportFormat.MP3) } }
        val aacLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("audio/mp4")
        ) { uri -> uri?.let { vm.exportAudio(it, AudioExportFormat.AAC) } }
        val instrumentLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> uri?.let(vm::importInstrument) }

        val onImport: () -> Unit = {
            importLauncher.launch(
                arrayOf(
                    "audio/midi",
                    "audio/x-midi",
                    "application/x-midi",
                    "application/octet-stream",
                )
            )
        }
        val onShare: () -> Unit = { vm.shareMidi() }
        val onAddInstrument: () -> Unit = { instrumentLauncher.launch(arrayOf("*/*")) }
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        ) {
            if (project != null) {
                DawEditor(
                    vm = vm,
                    project = project!!,
                    onImport = onImport,
                    onExport = { showExportMidiDialog = true },
                    onExportMp3 = { mp3Launcher.launch(vm.suggestedAudioFileName(AudioExportFormat.MP3)) },
                    onExportAac = { aacLauncher.launch(vm.suggestedAudioFileName(AudioExportFormat.AAC)) },
                    onShare = onShare,
                    onAddInstrument = onAddInstrument,
                )
            } else if (boot is DawBoot.Loading) {
                BootingScreen()
            } else {
                WelcomeScreen(
                    onNew = vm::newProject,
                    onImport = onImport,
                    onProjects = vm::openProjectBrowser,
                )
            }

            val browser by vm.projectBrowserVisible.collectAsStateWithLifecycle()
            if (browser) {
                ProjectBrowserDialog(
                    projects = vm.projects.collectAsStateWithLifecycle().value,
                    loading = vm.projectsLoading.collectAsStateWithLifecycle().value,
                    activeId = null,
                    onDismiss = vm::dismissProjectBrowser,
                    onOpen = {
                        vm.openProject(it)
                        vm.dismissProjectBrowser()
                    },
                    onNew = {
                        vm.newProject()
                        vm.dismissProjectBrowser()
                    },
                    onRename = vm::renameStoredProject,
                    onDelete = vm::deleteStoredProject,
                )
            }

            if (showExportMidiDialog && project != null) {
                MidiExportDialog(
                    project = project!!,
                    formatType = exportFormatType,
                    trackIndex = exportTrackIndex,
                    onFormatSelected = { exportFormatType = it },
                    onTrackSelected = { exportTrackIndex = it },
                    onExport = { exportLauncher.launch(vm.suggestedFileName()) },
                    onDismiss = { showExportMidiDialog = false },
                )
            }

            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp),
            )
        }
    }
}

@Composable
private fun BootingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.LibraryMusic, null, tint = Amber, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(14.dp))
            CircularProgressIndicator(color = Amber)
            Spacer(Modifier.height(10.dp))
            Text("プロジェクトを読み込んでいます…", color = Mist)
        }
    }
}

@Composable
private fun WelcomeScreen(onNew: () -> Unit, onImport: () -> Unit, onProjects: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 620.dp).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.GraphicEq, null, tint = Amber, modifier = Modifier.size(74.dp))
            Spacer(Modifier.height(10.dp))
            Text("MeditDAW", style = MaterialTheme.typography.displaySmall, color = Paper)
            Spacer(Modifier.height(4.dp))
            Text(
                "トラックごとに音源を選べるマルチトラックMIDIエディタ",
                style = MaterialTheme.typography.titleMedium,
                color = Mist,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            WideButton("新しいプロジェクト", Icons.Rounded.Add, true, onNew)
            Spacer(Modifier.height(10.dp))
            WideButton("MIDIファイルを読み込む", Icons.Rounded.FolderOpen, false, onImport)
            Spacer(Modifier.height(10.dp))
            WideButton("保存済みプロジェクトを開く", Icons.Rounded.LibraryMusic, false, onProjects)
            Spacer(Modifier.height(22.dp))
            Text(
                "・各トラックにSoundFont（.sf2）を割り当てて同時に再生\n" +
                    "・内蔵ピアノと追加した音源のプリセットをトラックごとに選択\n" +
                    "・ミックスした音声をMP3 / AACで書き出し",
                style = MaterialTheme.typography.bodyMedium,
                color = Mist,
            )
        }
    }
}

@Composable
private fun WideButton(label: String, icon: ImageVector, filled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (filled) Amber else PanelRaised,
        contentColor = if (filled) Ink else Paper,
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DawEditor(
    vm: DawViewModel,
    project: Project,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onExportMp3: () -> Unit,
    onExportAac: () -> Unit,
    onShare: () -> Unit,
    onAddInstrument: () -> Unit,
) {
    val context = LocalContext.current
    val rollView = remember { PianoRollView(context) }
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val viewport by vm.viewport.collectAsStateWithLifecycle()
    val epoch by vm.viewportEpoch.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val aapEntries by vm.aapEntries.collectAsStateWithLifecycle()
    val hover by vm.hover.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val autosave by vm.autosave.collectAsStateWithLifecycle()
    val audioLoading by vm.audioLoading.collectAsStateWithLifecycle()
    val instruments by vm.instruments.collectAsStateWithLifecycle()
    val masterVolume by vm.masterVolume.collectAsStateWithLifecycle()
    val inspectorId by vm.inspectorNoteId.collectAsStateWithLifecycle()
    val trackPanelVisible by vm.trackPanelVisible.collectAsStateWithLifecycle()

    val rollHost = remember(vm) {
        object : RollHost {
            override fun project() = vm.projectNow()
            override fun prefs() = vm.prefsNow()
            override fun selection() = vm.selectionNow()
            override fun playheadTick() = vm.playheadTick()
            override fun isPlaying() = vm.isPlayingNow()
            override fun isAudioReady() = vm.isAudioReady()
            override fun noteById(id: Long) = vm.noteById(id)
            override fun previewNote(pitch: Int, velocity: Int) = vm.previewNote(pitch, velocity)
            override fun addNoteAt(pitch: Int, startTick: Int, durationTicks: Int) =
                vm.addNoteAt(pitch, startTick, durationTicks)
            override fun commitRollNotes(newNotes: List<Note>) = vm.commitRollNotes(newNotes)
            override fun setRollSelection(ids: Set<Long>) = vm.setRollSelection(ids)
            override fun requestNoteInspector(id: Long) = vm.requestNoteInspector(id)
            override fun deleteNoteByDoubleTap(id: Long) = vm.deleteNoteByDoubleTap(id)
            override fun deleteNotes(ids: Set<Long>) = vm.deleteNotes(ids)
            override fun seekTo(tick: Double) = vm.seekTo(tick)
            override fun setLoopRange(startTick: Int, endTick: Int) = vm.setLoopRange(startTick, endTick)
            override fun onViewportSettled(viewport: ViewportState) = vm.onViewportSettled(viewport)
            override fun onHoverInfo(info: HoverInfo?) = vm.onHoverInfo(info)
        }
    }
    DisposableEffect(rollView, rollHost) {
        rollView.host = rollHost
        onDispose { rollView.host = null }
    }
    LaunchedEffect(hover) { if (hover == null) rollView.dismissHover() }

    var showTracks by remember { mutableStateOf(false) }
    var instrumentTrack by remember { mutableStateOf<Int?>(null) }
    var renameTrack by remember { mutableStateOf<Int?>(null) }
    var showBpm by remember { mutableStateOf(false) }
    var showMeter by remember { mutableStateOf(false) }
    var showRenameProject by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showVelocity by remember { mutableStateOf(false) }
    var showLength by remember { mutableStateOf(false) }
    var showInputVelocity by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMaster by remember { mutableStateOf(false) }
    val wide = LocalConfiguration.current.screenWidthDp >= 900

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DawTopBar(
                vm = vm,
                project = project,
                compact = !wide,
                canUndo = canUndo,
                canRedo = canRedo,
                playing = playing,
                autosave = autosave,
                showMenu = showMenu,
                onShowMenu = { showMenu = it },
                onBpm = { showBpm = true },
                onMeter = { showMeter = true },
                onRenameProject = { showRenameProject = true },
                onImport = onImport,
                onExport = onExport,
                onExportMp3 = onExportMp3,
                onExportAac = onExportAac,
                onShare = onShare,
                onTracks = { if (wide) vm.toggleTrackPanel() else showTracks = true },
                onProjects = vm::openProjectBrowser,
                onNew = { showNew = true },
                onHelp = { showHelp = true },
                onMaster = { showMaster = true },
            )
            if (audioLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (wide && trackPanelVisible) {
                    TrackPanel(
                        vm = vm,
                        project = project,
                        instruments = instruments,
                        masterVolume = masterVolume,
                        modifier = Modifier.width(318.dp).fillMaxHeight(),
                        onInstrument = { instrumentTrack = it },
                        onRename = { renameTrack = it },
                        onAddInstrument = onAddInstrument,
                        onClose = null,
                        aapEntries = aapEntries,
                    )
                    VerticalDividerLine()
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AndroidView(
                            factory = { rollView },
                            update = { view ->
                                view.syncState(
                                    project = project,
                                    prefs = prefs,
                                    selection = selection,
                                    viewport = viewport,
                                    epoch = epoch,
                                    playing = playing,
                                )
                            },
                            modifier = Modifier.fillMaxSize().clipToBounds(),
                        )
                        HoverReadout(hover, Modifier.align(Alignment.TopEnd))
                    }
                    DawDock(
                        vm = vm,
                        project = project,
                        prefs = prefs,
                        selectionSize = selection.size,
                        onSettings = { /* 設定ダイアログは未実装（トラックパネル等で代替） */ },
                        onVelocity = { showVelocity = true },
                        onLength = { showLength = true },
                        onInputVelocity = { showInputVelocity = true },
                        onZoomIn = {
                            rollView.zoomHorizontal(1.22f)
                            rollView.zoomVertical(1.12f)
                        },
                        onZoomOut = {
                            rollView.zoomHorizontal(0.82f)
                            rollView.zoomVertical(0.9f)
                        },
                        onFit = { rollView.fitToSong() },
                        onScrollBars = { delta ->
                            val vp = viewport
                            rollView.syncState(
                                project = project,
                                prefs = prefs,
                                selection = selection,
                                viewport = viewport.copy(startTick = vp.startTick + delta * project.ppq * 4),
                                epoch = epoch,
                                playing = playing,
                            )
                        },
                        onInspector = { /* 数値編集ダイアログは未実装 */ },
                    )
                }
            }
        }
    }

    if (!wide && showTracks) {
        ModalBottomSheet(
            onDismissRequest = { showTracks = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            TrackPanel(
                vm = vm,
                project = project,
                instruments = instruments,
                masterVolume = masterVolume,
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                onInstrument = { instrumentTrack = it; showTracks = false },
                onRename = { renameTrack = it },
                onAddInstrument = onAddInstrument,
                onClose = { showTracks = false },
                aapEntries = aapEntries,
            )
        }
    }

    if (showBpm) {
        NumberDialog(
            title = "テンポ (BPM)",
            initial = project.bpm,
            onDismiss = { showBpm = false },
            onApply = { vm.setBpm(it); showBpm = false },
            decimals = true,
        )
    }
    if (showMeter) {
        MeterDialog(
            initial = project.timeSignature,
            onDismiss = { showMeter = false },
            onApply = { n, d -> vm.setTimeSignature(n, d); showMeter = false },
        )
    }
    if (showRenameProject) {
        TextDialog(
            title = "プロジェクト名",
            initial = project.title,
            onDismiss = { showRenameProject = false },
            onApply = { vm.renameProject(it); showRenameProject = false },
        )
    }
    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("新しいプロジェクト") },
            text = { Text("現在のプロジェクトは自動保存されています。新規作成しますか？") },
            confirmButton = { TextButton(onClick = { showNew = false; vm.newProject() }) { Text("作成") } },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("キャンセル") } },
        )
    }
    if (showVelocity) {
        NumberDialog(
            title = "選択ノートのベロシティ",
            initial = project.noteById(selection.firstOrNull() ?: -1L)?.velocity?.toDouble() ?: 96.0,
            onDismiss = { showVelocity = false },
            onApply = { vm.setSelectionVelocity(it.toInt()); showVelocity = false },
        )
    }
    if (showLength) {
        NumberDialog(
            title = "選択ノートの長さ (tick)",
            initial = project.noteById(selection.firstOrNull() ?: -1L)?.durationTicks?.toDouble() ?: 480.0,
            onDismiss = { showLength = false },
            onApply = { vm.setSelectionDuration(it.toInt()); showLength = false },
        )
    }
    if (showInputVelocity) {
        NumberDialog(
            title = "入力ベロシティ",
            initial = prefs.inputVelocity.toDouble(),
            onDismiss = { showInputVelocity = false },
            onApply = { vm.setInputVelocity(it.toInt()); showInputVelocity = false },
        )
    }
    if (showMaster) {
        MasterVolumeDialog(
            initial = masterVolume,
            onDismiss = { showMaster = false },
            onApply = { vm.setMasterVolume(it); showMaster = false },
        )
    }
    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }

    instrumentTrack?.let { index ->
        val track = project.tracks.getOrNull(index)
        if (track != null) {
            InstrumentPickerDialog(
                vm = vm,
                trackIndex = index,
                track = track,
                instruments = instruments,
                aapEntries = aapEntries,
                onAddInstrument = onAddInstrument,
                onDismiss = { instrumentTrack = null },
            )
        }
    }
    renameTrack?.let { index ->
        val track = project.tracks.getOrNull(index)
        if (track != null) {
            TextDialog(
                title = "トラック名",
                initial = track.name,
                onDismiss = { renameTrack = null },
                onApply = {
                    vm.renameTrack(index, it)
                    renameTrack = null
                },
            )
        }
    }
    if (inspectorId != null) {
        val note = project.noteById(inspectorId!!)
        if (note != null) {
            NoteDialog(
                note = note,
                onDismiss = vm::dismissNoteInspector,
                onApply = { pitch, start, duration, velocity ->
                    vm.updateNote(note.id, pitch, start, duration, velocity)
                    vm.dismissNoteInspector()
                },
                onDelete = {
                    vm.deleteNotes(setOf(note.id))
                    vm.dismissNoteInspector()
                },
            )
        } else {
            LaunchedEffect(inspectorId) { vm.dismissNoteInspector() }
        }
    }
}

@Composable
private fun HoverReadout(hover: HoverInfo?, modifier: Modifier = Modifier) {
    if (hover == null) return
    Surface(
        modifier = modifier.padding(10.dp),
        shape = RoundedCornerShape(8.dp),
        color = PanelRaised.copy(alpha = 0.9f),
    ) {
        Text(
            "${hover.barBeatLabel}  ${com.medit.midi.model.noteName(hover.pitch)}",
            style = MaterialTheme.typography.labelMedium,
            color = Paper,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun VerticalDividerLine() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(OutlineSoft))
}

@Composable
private fun DawTopBar(
    vm: DawViewModel,
    project: Project,
    compact: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    playing: Boolean,
    autosave: DawAutosave,
    showMenu: Boolean,
    onShowMenu: (Boolean) -> Unit,
    onBpm: () -> Unit,
    onMeter: () -> Unit,
    onRenameProject: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onExportMp3: () -> Unit,
    onExportAac: () -> Unit,
    onShare: () -> Unit,
    onTracks: () -> Unit,
    onProjects: () -> Unit,
    onNew: () -> Unit,
    onHelp: () -> Unit,
    onMaster: () -> Unit,
) {
    Surface(color = Panel, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(Modifier.padding(horizontal = 6.dp)) {
                Text("MeditDAW", color = Amber, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    project.title,
                    color = Paper,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp).clickable { onRenameProject() },
                )
            }
            IconButton(onClick = onTracks) {
                Icon(Icons.Rounded.Tune, "トラック", tint = Teal)
            }
            IconButton(onClick = { vm.seekTo(0.0) }) {
                Icon(Icons.Rounded.Stop, "先頭へ", tint = Paper)
            }
            IconButton(onClick = vm::togglePlayback) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) "一時停止" else "再生",
                    tint = if (playing) Coral else Amber,
                )
            }
            IconButton(onClick = { vm.toggleLoop() }) {
                Icon(
                    Icons.Rounded.Loop,
                    "ループ",
                    tint = if (project.loopEnabled) Teal else Mist,
                )
            }
            Chip("${"%.1f".format(project.bpm)} BPM", onBpm)
            Chip(project.timeSignature.label(), onMeter)
            IconButton(onClick = vm::undo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, "元に戻す", tint = if (canUndo) Paper else Mist)
            }
            IconButton(onClick = vm::redo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Rounded.Redo, "やり直す", tint = if (canRedo) Paper else Mist)
            }
            IconButton(onClick = vm::saveNow) {
                Icon(Icons.Rounded.Check, "保存", tint = Teal)
            }
            Text(
                autosaveText(autosave),
                color = Mist,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            IconButton(onClick = onMaster) {
                Icon(Icons.Rounded.VolumeUp, "マスター音量", tint = Paper)
            }
            Box {
                IconButton(onClick = { onShowMenu(true) }) {
                    Icon(Icons.Rounded.MoreVert, "メニュー", tint = Paper)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { onShowMenu(false) }) {
                    DropdownMenuItem(
                        text = { Text("MIDIを読み込む") },
                        leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                        onClick = { onShowMenu(false); onImport() },
                    )
                    DropdownMenuItem(
                        text = { Text("MIDIを書き出す") },
                        leadingIcon = { Icon(Icons.Rounded.FileDownload, null) },
                        onClick = { onShowMenu(false); onExport() },
                    )
                    DropdownMenuItem(
                        text = { Text("MP3を書き出す") },
                        leadingIcon = { Icon(Icons.Rounded.AudioFile, null) },
                        onClick = { onShowMenu(false); onExportMp3() },
                    )
                    DropdownMenuItem(
                        text = { Text("AAC (.m4a)を書き出す") },
                        leadingIcon = { Icon(Icons.Rounded.AudioFile, null) },
                        onClick = { onShowMenu(false); onExportAac() },
                    )
                    DropdownMenuItem(
                        text = { Text("MIDIを共有") },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = { onShowMenu(false); onShare() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("プロジェクトを開く") },
                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
                        onClick = { onShowMenu(false); onProjects() },
                    )
                    DropdownMenuItem(
                        text = { Text("新規プロジェクト") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null) },
                        onClick = { onShowMenu(false); onNew() },
                    )
                    DropdownMenuItem(
                        text = { Text("ヘルプ") },
                        leadingIcon = { Icon(Icons.Rounded.HelpOutline, null) },
                        onClick = { onShowMenu(false); onHelp() },
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = PanelRaised,
        contentColor = Paper,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
    }
}

@Composable
private fun TrackPanel(
    vm: DawViewModel,
    project: Project,
    instruments: List<InstrumentLibrary.Entry>,
    masterVolume: Float,
    modifier: Modifier = Modifier,
    onInstrument: (Int) -> Unit,
    onRename: (Int) -> Unit,
    onAddInstrument: () -> Unit,
    onClose: (() -> Unit)?,
    aapEntries: List<AapEntry> = emptyList(),
) {
    Surface(modifier = modifier, color = Panel) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("トラック", color = Paper, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::addTrack) {
                    Icon(Icons.Rounded.Add, "トラックを追加", tint = Amber)
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "閉じる", tint = Mist) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VolumeDown, null, tint = Mist, modifier = Modifier.size(16.dp))
                Slider(
                    value = masterVolume,
                    onValueChange = vm::setMasterVolume,
                    valueRange = 0f..1.5f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("${(masterVolume * 100).toInt()}%", color = Mist, style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider(color = OutlineSoft)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                itemsIndexed(project.tracks) { index, track ->
                    TrackCard(
                        index = index,
                        track = track,
                        active = index == project.activeTrack,
                        instrument = if (track.isAapPlugin) {
                            aapEntries.firstOrNull { it.pluginId == track.pluginId }?.displayName ?: track.pluginId ?: "AAP"
                        } else {
                            instruments.firstOrNull { it.id == track.soundId }?.name ?: "Medit Piano"
                        },
                        onSelect = { vm.selectTrack(index) },
                        onInstrument = { onInstrument(index) },
                        onRename = { onRename(index) },
                        onMute = { vm.toggleMute(index) },
                        onSolo = { vm.toggleSolo(index) },
                        onVolume = { vm.setTrackVolume(index, it) },
                        onDuplicate = { vm.duplicateTrack(index) },
                        onDelete = { vm.deleteTrack(index) },
                        canDelete = project.tracks.size > 1,
                    )
                }
                item {
                    OutlinedButton(
                        onClick = onAddInstrument,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        Icon(Icons.Rounded.LibraryMusic, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SoundFontを追加")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    index: Int,
    track: Track,
    active: Boolean,
    instrument: String,
    onSelect: () -> Unit,
    onInstrument: () -> Unit,
    onRename: () -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    onVolume: (Float) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
) {
    val color = trackColors[index % trackColors.size]
    var localVolume by remember(track.volume) { mutableFloatStateOf(track.volume) }
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (active) PanelRaised else Panel.copy(alpha = 0.55f),
        border = if (active) androidx.compose.foundation.BorderStroke(1.dp, color) else null,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(
                    track.name,
                    color = Paper,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable { onRename() },
                )
                Text("${track.noteCount}", color = Mist, style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = onDuplicate, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.ContentCopy, "複製", tint = Mist, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Delete, "削除", tint = if (canDelete) Mist else OutlineSoft, modifier = Modifier.size(16.dp))
                }
            }
            Surface(
                onClick = onInstrument,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(7.dp),
                color = Ink.copy(alpha = 0.45f),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, null, tint = color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(instrument, color = Paper, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Edit, null, tint = Mist, modifier = Modifier.size(13.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                SmallToggle("M", track.muted, Coral, onMute)
                Spacer(Modifier.width(5.dp))
                SmallToggle("S", track.solo, Teal, onSolo)
                Slider(
                    value = localVolume,
                    onValueChange = { localVolume = it },
                    onValueChangeFinished = { onVolume(localVolume) },
                    valueRange = 0f..1.5f,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp).height(28.dp),
                )
                Text("${(localVolume * 100).toInt()}", color = Mist, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun SmallToggle(label: String, on: Boolean, activeColor: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (on) activeColor else Ink.copy(alpha = 0.6f),
        contentColor = if (on) Ink else Mist,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DawDock(
    vm: DawViewModel,
    project: Project,
    prefs: EditorPrefs,
    selectionSize: Int,
    onSettings: () -> Unit,
    onVelocity: () -> Unit,
    onLength: () -> Unit,
    onInputVelocity: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
    onScrollBars: (Int) -> Unit,
    onInspector: () -> Unit,
) {
    val selectionCount = selectionSize

    @Composable
    fun dockBtn(
        icon: ImageVector,
        label: String,
        active: Boolean = false,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        val tint = when {
            !enabled -> Mist.copy(alpha = 0.3f)
            active -> MaterialTheme.colorScheme.primary
            else -> Paper
        }
        Column(
            modifier = Modifier
                .width(64.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                    else Color.Transparent
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                color = tint,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    // 未選択時: 入力コントロール＋常時アイテム
    val unselectedActions: MutableList<@Composable () -> Unit> = mutableListOf()
    unselectedActions += { dockBtn(
        icon = if (prefs.grid == GridUnit.OFF) Icons.Rounded.GridOff else Icons.Rounded.GridOn,
        label = "グリッド ${prefs.grid.label}",
        active = prefs.grid != GridUnit.OFF,
        onClick = { vm.cycleGrid() },
    ) }
    unselectedActions += { dockBtn(
        icon = Icons.Rounded.Straighten,
        label = "長さ ${prefs.inputDuration.label}",
        onClick = {
            val values = GridUnit.entries.filter { it != GridUnit.OFF }
            val idx = values.indexOf(prefs.inputDuration)
            vm.setInputDuration(values[(idx + 1) % values.size])
        },
    ) }
    unselectedActions += { dockBtn(
        icon = if (prefs.snapEnabled) Icons.Rounded.Check else Icons.Rounded.Close,
        label = "スナップ ${if (prefs.snapEnabled) "ON" else "OFF"}",
        active = prefs.snapEnabled,
        onClick = { vm.setSnapEnabled(!prefs.snapEnabled) },
    ) }
    unselectedActions += { dockBtn(
        icon = Icons.Rounded.VolumeUp,
        label = "入力 v${prefs.inputVelocity}",
        onClick = onInputVelocity,
    ) }
    unselectedActions += { dockBtn(
        icon = Icons.Rounded.BarChart,
        label = "ベロシティ編集",
        active = prefs.velocityEditor,
        onClick = { vm.toggleVelocityEditor() },
    ) }
    unselectedActions += { dockBtn(
        icon = Icons.Rounded.HighlightAlt,
        label = if (prefs.multiSelectMode) "複数選択 ON" else "複数選択",
        active = prefs.multiSelectMode,
        onClick = { vm.toggleMultiSelectMode() },
    ) }
    unselectedActions += { dockBtn(
        icon = Icons.Rounded.AutoFixHigh,
        label = "全クオンタイズ",
        enabled = project.notes.isNotEmpty(),
        onClick = { vm.quantizeAll() },
    ) }
    unselectedActions += { dockBtn(
        icon = Icons.Rounded.SelectAll,
        label = "全選択",
        enabled = project.notes.isNotEmpty(),
        onClick = { vm.selectAll() },
    ) }
    unselectedActions += { dockBtn(
        icon = Icons.Rounded.ContentPaste,
        label = "貼り付け",
        onClick = { vm.pasteAtPlayhead() },
    ) }
    // 選択時: 上段に重なるノート編集メニュー
    val selectionActions: MutableList<@Composable () -> Unit> = mutableListOf()
    if (selectionCount > 0) {
        selectionActions += { dockBtn(
            icon = Icons.Rounded.Close,
            label = "選択解除",
            onClick = { vm.clearSelection() },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.Straighten,
            label = "長さ",
            onClick = onLength,
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.BarChart,
            label = "ベロシティ編集",
            active = prefs.velocityEditor,
            onClick = { vm.toggleVelocityEditor() },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.GraphicEq,
            label = "v を揃える",
            onClick = onVelocity,
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.HighlightAlt,
            label = if (prefs.multiSelectMode) "複数選択 ON" else "複数選択",
            active = prefs.multiSelectMode,
            onClick = { vm.toggleMultiSelectMode() },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.ArrowDownward,
            label = "−12",
            onClick = { vm.transposeSelection(-12) },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.ArrowDownward,
            label = "−1",
            onClick = { vm.transposeSelection(-1) },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.ArrowUpward,
            label = "+1",
            onClick = { vm.transposeSelection(1) },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.ArrowUpward,
            label = "+12",
            onClick = { vm.transposeSelection(12) },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.FileCopy,
            label = "複製",
            onClick = { vm.duplicateSelection() },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.ContentCopy,
            label = "コピー",
            onClick = { vm.copySelection() },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.ContentCut,
            label = "カット",
            onClick = { vm.cutSelection() },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.ContentPaste,
            label = "貼り付け",
            onClick = { vm.pasteAtPlayhead() },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.Delete,
            label = "削除",
            onClick = { vm.deleteSelection() },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.AutoFixHigh,
            label = "クオンタイズ",
            onClick = { vm.quantizeSelection() },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.Loop,
            label = "ループ範囲",
            onClick = { /* rollView.setLoopRange from selection handled by roll host */ },
        ) }
        selectionActions += { dockBtn(
            icon = Icons.Rounded.SelectAll,
            label = "全選択",
            onClick = { vm.selectAll() },
        ) }
        if (selectionCount == 1) {
            selectionActions += { dockBtn(
                icon = Icons.Rounded.Edit,
                label = "数値編集",
                onClick = onInspector,
            ) }
        }
    }
    // 常時: 下段
    val alwaysActions: MutableList<@Composable () -> Unit> = mutableListOf()
    alwaysActions += { dockBtn(
        icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
        label = "前の小節",
        onClick = { onScrollBars(-1) },
    ) }
    alwaysActions += { dockBtn(
        icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        label = "次の小節",
        onClick = { onScrollBars(1) },
    ) }
    alwaysActions += { dockBtn(
        icon = Icons.Rounded.ZoomOut,
        label = "縮小",
        onClick = onZoomOut,
    ) }
    alwaysActions += { dockBtn(
        icon = Icons.Rounded.ZoomIn,
        label = "拡大",
        onClick = onZoomIn,
    ) }
    alwaysActions += { dockBtn(
        icon = Icons.Rounded.Tune,
        label = "全体表示",
        onClick = onFit,
    ) }
    alwaysActions += { dockBtn(
        icon = Icons.Rounded.Loop,
        label = "ループ ${if (project.loopEnabled) "ON" else "OFF"}",
        active = project.loopEnabled,
        onClick = { vm.toggleLoop() },
    ) }
    alwaysActions += { dockBtn(
        icon = Icons.Rounded.Settings,
        label = "設定",
        onClick = onSettings,
    ) }

    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        prefs.multiSelectMode && selectionCount == 0 ->
                            "複数選択 ON ・ ドラッグで範囲選択 / ノートをタップで追加"
                        prefs.multiSelectMode ->
                            "複数選択 ON ・ $selectionCount / ${project.notes.size} ノートを選択"
                        selectionCount == 0 ->
                            "入力 ${prefs.inputDuration.label} ・ グリッド ${prefs.grid.label} ・ スナップ ${if (prefs.snapEnabled) "ON" else "OFF"}"
                        else ->
                            "$selectionCount / ${project.notes.size} ノートを選択"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectionCount == 0) Mist else MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Mist.copy(alpha = 0.7f),
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "${project.notes.size} notes",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mist.copy(alpha = 0.7f),
                )
            }
            if (selectionCount > 0) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(selectionActions) { action ->
                        action()
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(unselectedActions + alwaysActions) { action ->
                    action()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------

@Composable
private fun InstrumentPickerDialog(
    vm: DawViewModel,
    trackIndex: Int,
    track: Track,
    instruments: List<InstrumentLibrary.Entry>,
    aapEntries: List<AapEntry>,
    onAddInstrument: () -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(track.soundId) }
    var presets by remember { mutableStateOf<List<SoundFontPreset>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        loading = true
        presets = vm.presetsFor(expanded)
        loading = false
    }
    val aapSelected = track.isAapPlugin && track.pluginId != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${track.name} の音源") },
        text = {
            Column(Modifier.heightIn(max = 520.dp)) {
                Text("SoundFont", color = Mist, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 0.dp))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    itemsIndexed(instruments) { _, entry ->
                        val selected = entry.id == track.soundId
                        Surface(
                            onClick = { expanded = entry.id },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (entry.id == expanded) PanelRaised else Color.Transparent,
                            border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Teal) else null,
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.LibraryMusic, null, tint = if (selected) Teal else Mist, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(entry.name, color = Paper, modifier = Modifier.weight(1f))
                                if (entry.builtIn) {
                                    Text("内蔵", color = Mist, style = MaterialTheme.typography.labelSmall)
                                } else {
                                    IconButton(onClick = { vm.deleteInstrument(entry) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.Delete, "削除", tint = Mist, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        if (entry.id == expanded) {
                            if (loading) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), color = Teal)
                                    Spacer(Modifier.width(8.dp))
                                    Text("プリセットを読み込み中…", color = Mist, style = MaterialTheme.typography.labelMedium)
                                }
                            } else if (presets.isEmpty()) {
                                Text("再生できるプリセットがありません", color = Coral, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(10.dp))
                            } else {
                                Column(Modifier.padding(start = 26.dp, bottom = 6.dp)) {
                                    presets.forEach { preset ->
                                        val isCurrent = selected && preset.index == track.presetIndex
                                        Surface(
                                            onClick = {
                                                vm.setTrackInstrument(trackIndex, entry.id, preset.index)
                                                onDismiss()
                                            },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isCurrent) Teal.copy(alpha = 0.16f) else Color.Transparent,
                                        ) {
                                            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "${preset.name}  (bank ${preset.bank} / ${preset.program})",
                                                    color = if (isCurrent) Teal else Paper,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                if (isCurrent) {
                                                    Icon(Icons.Rounded.Check, null, tint = Teal, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("AAP プラグイン", color = Mist, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 0.dp))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    itemsIndexed(aapEntries) { _, entry ->
                        val selected = track.pluginId == entry.pluginId
                        Surface(
                            onClick = {
                                vm.setTrackPlugin(trackIndex, entry.pluginId)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) Teal.copy(alpha = 0.16f) else Color.Transparent,
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Settings, null, tint = if (selected) Teal else Mist, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.displayName, color = Paper, modifier = Modifier.weight(1f))
                                    Text("${entry.parameterCount}パラメータ", color = Mist, style = MaterialTheme.typography.labelSmall)
                                }
                                if (selected) {
                                    Icon(Icons.Rounded.Check, null, tint = Teal, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    if (aapEntries.isEmpty()) {
                        item {
                            Text("インストール済みのAAPプラグインはありません", color = Mist, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onAddInstrument, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SoundFont (.sf2) を追加")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

@Composable
private fun ProjectBrowserDialog(
    projects: List<com.medit.midi.data.ProjectSummary>,
    loading: Boolean,
    activeId: String?,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var renameId by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("プロジェクト") },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                if (loading) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Teal)
                        Spacer(Modifier.width(8.dp))
                        Text("読み込み中…", color = Mist)
                    }
                } else if (projects.isEmpty()) {
                    Text("保存済みプロジェクトはありません", color = Mist, modifier = Modifier.padding(12.dp))
                } else {
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        itemsIndexed(projects) { _, item ->
                            Surface(
                                onClick = { onOpen(item.id) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = if (item.id == activeId) PanelRaised else Color.Transparent,
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, color = Paper)
                                        Text("${item.noteCount} notes", color = Mist, style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(
                                        onClick = {
                                            renameId = item.id
                                            renameValue = item.title
                                        },
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(Icons.Rounded.Edit, "名前変更", tint = Mist, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { deleteId = item.id }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Rounded.Delete, "削除", tint = Coral, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                OutlinedButton(onClick = onDismiss) { Text("閉じる") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onNew) { Text("新規作成") }
            }
        },
    )
    renameId?.let { id ->
        TextDialog(
            title = "プロジェクト名",
            initial = renameValue,
            onDismiss = { renameId = null },
            onApply = {
                onRename(id, it)
                renameId = null
            },
        )
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("プロジェクトを削除") },
            text = { Text("このプロジェクトを削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(id)
                    deleteId = null
                }) { Text("削除", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun NumberDialog(
    title: String,
    initial: Double,
    onDismiss: () -> Unit,
    onApply: (Double) -> Unit,
    decimals: Boolean = false,
) {
    var text by remember(initial) { mutableStateOf(if (decimals) "%.2f".format(initial) else initial.toInt().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toDoubleOrNull()?.let(onApply) }) { Text("適用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onApply(text) }) { Text("適用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun MeterDialog(
    initial: TimeSignature,
    onDismiss: () -> Unit,
    onApply: (Int, Int) -> Unit,
) {
    var numerator by remember { mutableStateOf(initial.numerator.toString()) }
    var denominator by remember { mutableStateOf(initial.denominator.toString()) }
    val invalid = numerator.toIntOrNull() !in 1..32 ||
        denominator.toIntOrNull() !in listOf(1, 2, 4, 8, 16, 32)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("拍子") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = numerator,
                    onValueChange = { numerator = it.filter(Char::isDigit).take(2) },
                    label = { Text("分子") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                )
                Text(" / ", color = Paper)
                OutlinedTextField(
                    value = denominator,
                    onValueChange = { denominator = it.filter(Char::isDigit).take(2) },
                    label = { Text("分母") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !invalid,
                onClick = { onApply(numerator.toInt(), denominator.toInt()) },
            ) { Text("適用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun MasterVolumeDialog(initial: Float, onDismiss: () -> Unit, onApply: (Float) -> Unit) {
    var value by remember { mutableFloatStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("マスター音量") },
        text = {
            Column {
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1.5f)
                Text("${(value * 100).toInt()}%", color = Mist)
            }
        },
        confirmButton = { TextButton(onClick = { onApply(value) }) { Text("適用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun NoteDialog(
    note: Note,
    onDismiss: () -> Unit,
    onApply: (pitch: Int, start: Int, duration: Int, velocity: Int) -> Unit,
    onDelete: () -> Unit,
) {
    var pitch by remember { mutableStateOf(note.pitch.toString()) }
    var start by remember { mutableStateOf(note.startTick.toString()) }
    var duration by remember { mutableStateOf(note.durationTicks.toString()) }
    var velocity by remember { mutableStateOf(note.velocity.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ノートを編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(pitch, { pitch = it.filter(Char::isDigit) }, label = { Text("音高") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(velocity, { velocity = it.filter(Char::isDigit) }, label = { Text("強さ") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(start, { start = it.filter(Char::isDigit) }, label = { Text("開始 tick") }, singleLine = true)
                OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text("長さ tick") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    pitch.toIntOrNull() ?: note.pitch,
                    start.toIntOrNull() ?: note.startTick,
                    duration.toIntOrNull() ?: note.durationTicks,
                    velocity.toIntOrNull() ?: note.velocity,
                )
            }) { Text("適用") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("削除", color = Coral) }
                TextButton(onClick = onDismiss) { Text("キャンセル") }
            }
        },
    )
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MeditDAW の使い方") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("・左のトラック一覧でトラックを選ぶと、ピアノロールでそのトラックを編集できます。", color = Paper)
                Text("・トラック名をタップすると名前を変更できます。", color = Paper)
                Text("・音源名をタップすると、内蔵ピアノや追加したSoundFontのプリセットをトラックごとに選べます。", color = Paper)
                Text("・M / S はミュート / ソロ、スライダーはトラック音量です。", color = Paper)
                Text("・再生は全トラック同時です。ループ範囲はピアノロール上部のループレーンで指定できます。", color = Paper)
                Text("・書き出しメニューからMIDI / MP3 / AACを保存できます。", color = Paper)
                Text("・SoundFont (.sf2) は「SoundFontを追加」から読み込めます (SF3/Ogg形式は非対応)。", color = Mist, style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

private fun autosaveText(state: DawAutosave): String = when (state) {
    DawAutosave.Idle -> "未保存"
    DawAutosave.Saving -> "保存中…"
    is DawAutosave.Saved -> "保存済み"
    is DawAutosave.Failed -> "保存エラー"
}

@Composable
private fun MidiExportDialog(
    project: Project,
    formatType: Int,
    trackIndex: Int,
    onFormatSelected: (Int) -> Unit,
    onTrackSelected: (Int) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tracks = project.tracks
    val isType0 = formatType == 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MIDI書き出し形式", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(modifier = Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("書き出すMIDIの形式を選択してください。", color = Paper, style = MaterialTheme.typography.bodyMedium)

                Row(modifier = Modifier.fillMaxWidth()) {
                    ExportFormatTile(
                        modifier = Modifier.weight(1f),
                        label = "形式1 (SMF Type 1)",
                        desc = "全トラックを別トラックチャンクで書き出し",
                        selected = !isType0,
                        onClick = { onFormatSelected(1) },
                    )
                    ExportFormatTile(
                        modifier = Modifier.weight(1f),
                        label = "形式0 (SMF Type 0)",
                        desc = "選択トラックを1トラックチャンクで書き出し",
                        selected = isType0,
                        onClick = { onFormatSelected(0) },
                    )
                }

                if (isType0) {
                    Text("トラックを選択:", color = Mist, style = MaterialTheme.typography.labelSmall)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    ) {
                        itemsIndexed(tracks) { index, track ->
                            val selected = index == trackIndex
                            Surface(
                                onClick = { onTrackSelected(index) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) Teal.copy(alpha = 0.16f) else Color.Transparent,
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}. ${track.name}", color = Paper, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                    if (selected) {
                                        Icon(Icons.Rounded.Check, null, tint = Teal, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onExport) { Text("書き出し") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun ExportFormatTile(
    modifier: Modifier = Modifier,
    label: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Teal.copy(alpha = 0.16f) else Panel.copy(alpha = 0.5f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = if (selected) Teal else Paper, style = MaterialTheme.typography.titleMedium)
            Text(desc, color = Mist, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}
