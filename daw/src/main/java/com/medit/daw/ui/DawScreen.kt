package com.medit.daw.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.medit.daw.R
import com.medit.shared.audio.TrackAudioStatus
import com.medit.shared.editor.EditorUiState
import com.medit.shared.editor.EditorViewModel
import com.medit.shared.ui.PianoRollView
import com.medit.shared.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DawScreen(viewModel: EditorViewModel) {
    val context = LocalContext.current
    val uiState: EditorUiState by viewModel.uiState.collectAsState()
    val project = uiState.project
    val activeIdx = viewModel.activeTrackIndex()
    val tracks = project?.tracks ?: emptyList()

    var pickTargetTrack by remember { mutableIntStateOf(-1) }
    var presetMenuOpen by remember { mutableIntStateOf(-1) }
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            val indices = project?.tracks?.indices
            if (uri != null && indices != null && pickTargetTrack in indices) {
                viewModel.loadExternalSoundFont(uri)
                pickTargetTrack = -1
            }
        }
    )

    val playLabel = if (uiState.isPlaying) "停止" else "再生"

    Column(modifier = Modifier.fillMaxSize()) {
        // ---------------- Top bar (classic 相当) ----------------
        androidx.compose.material3.TopAppBar(
            title = {
                Text(
                    text = project?.title ?: context.getString(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            colors = androidx.compose.material3.TopAppBarDefaults.mediumTopAppBarColors(containerColor = Color(0xFF141A21)),
            actions = {
                Row {
                    androidx.compose.material3.IconButton(onClick = { viewModel.newProject() }) {
                        Text(text = "+", style = MaterialTheme.typography.bodyLarge)
                    }
                    androidx.compose.material3.IconButton(onClick = { viewModel.togglePlayback() }) {
                        Text(text = playLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
        )

        // ---------------- Track list ----------------
        Text(
            text = "トラック",
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
        ) {
            itemsIndexed(tracks) { index, track ->
                val isActive = index == activeIdx
                val status = uiState.audio[index]
                val statusText = when (status) {
                    is TrackAudioStatus.Ready -> "OK"
                    is TrackAudioStatus.Failed -> "エラー: ${status.message}"
                    is TrackAudioStatus.Loading -> "読込中…"
                    else -> "—"
                }
                val sf2Label = track.soundFontPath.takeIf { it.isNotBlank() }
                    ?.let { it.substringAfterLast('/') } ?: "内蔵SF2"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(
                            if (isActive) Color(0xFF2A3442) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp),
                        ),
                    ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${index + 1}. ${track.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isActive) Color(0xFFF2B65A) else Color(0xFFE7EDF4),
                        )
                        Text(
                            text = "SF2: $sf2Label (プリセット ${track.presetIndex})  $statusText",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF93A1B1),
                        )
                    }
                    Row {
                        androidx.compose.material3.Button(
                            onClick = { viewModel.selectTrack(index) },
                            enabled = !isActive,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF11443F)),
                        ) {
                            Text(text = "選択", style = MaterialTheme.typography.labelSmall)
                        }
                        androidx.compose.material3.Button(
                            onClick = {
                                pickTargetTrack = index
                                getContentLauncher.launch("audio/*")
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF05233D)),
                        ) {
                            Text(text = "SF2…", style = MaterialTheme.typography.labelSmall)
                        }
                        androidx.compose.material3.Button(
                            onClick = {
                                viewModel.selectTrack(index)
                                viewModel.reloadBundledSoundFont()
                                pickTargetTrack = -1
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF201708)),
                        ) {
                            Text(text = "内蔵", style = MaterialTheme.typography.labelSmall)
                        }
                        androidx.compose.material3.Button(
                            onClick = { presetMenuOpen = if (presetMenuOpen == index) -1 else index },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF05233D)),
                        ) {
                            Text(text = "プリセット ${track.presetIndex + 1}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = presetMenuOpen == index,
                    onDismissRequest = { presetMenuOpen = -1 },
                ) {
                    viewModel.trackPresets(index).forEachIndexed { i, p ->
                        androidx.compose.material3.DropdownMenuItem(
                            onClick = {
                                viewModel.setTrackPreset(index, i)
                                presetMenuOpen = -1
                            },
                            text = { Text(text = "${i + 1}. ${p.name}") },
                        )
                    }
                }
            }
        }

        // ---------------- Piano roll (classic 相当の共有 UI) ----------------
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> PianoRollView(context).apply { host = viewModel } },
            update = { roll ->
                roll.syncState(
                    project = uiState.project,
                    prefs = uiState.prefs,
                    selection = uiState.selection,
                    viewport = uiState.viewport,
                    epoch = uiState.viewportEpoch,
                    playing = uiState.isPlaying,
                )
            },
        )

        // ---------------- Bottom bar (classic 相当) ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = project?.let { "トラック ${it.tracks.size} / ノート ${it.notes.size}" } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF93A1B1),
            )
            Row {
                androidx.compose.material3.Button(
                    onClick = {
                        project?.let { p ->
                            val newTrack = Track(
                                name = "Track ${p.tracks.size + 1}",
                                channel = p.tracks.size % 16,
                                notes = emptyList(),
                                rawEvents = emptyList(),
                                isDrum = false,
                                soundFontPath = "",
                                presetIndex = 0,
                            )
                            val updated = p.copy(tracks = p.tracks + newTrack)
                            viewModel.replaceProject(updated)
                            viewModel.selectTrack(p.tracks.size)
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF11443F)),
                ) {
                    Text(text = "トラック追加", style = MaterialTheme.typography.labelSmall)
                }
                androidx.compose.material3.Button(
                    onClick = { viewModel.togglePlayback() },
                ) {
                    Text(text = playLabel, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
