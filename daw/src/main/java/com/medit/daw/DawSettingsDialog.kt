package com.medit.daw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import com.medit.midi.data.EditorPrefs
import com.medit.midi.model.GridUnit
import com.medit.midi.model.Project
import com.medit.midi.ui.Mist
import com.medit.midi.ui.OutlineSoft
import com.medit.midi.ui.Panel
import com.medit.midi.ui.Paper
import com.medit.midi.ui.Teal
import kotlin.math.roundToInt

// ---------------------------------------------------------------------
// Dialog helper composables
// ---------------------------------------------------------------------

@Composable
private fun DawDialogPrimaryButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun DawDialogSecondaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DawToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = { onCheckedChange(!checked) })
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DawSectionTitle(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DawSettingLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = Mist, modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
fun DawGridUnitChips(
    selected: GridUnit?,
    includeOff: Boolean,
    onSelect: (GridUnit) -> Unit,
) {
    val units = listOf(
        GridUnit.WHOLE, GridUnit.HALF, GridUnit.QUARTER,
        GridUnit.EIGHTH, GridUnit.SIXTEENTH, GridUnit.THIRTY_SECOND,
    )
    val displayUnits = if (includeOff) listOf(null) + units else units
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(displayUnits) { unit ->
            val isSelected = unit == selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    val u = unit ?: GridUnit.QUARTER
                    onSelect(u)
                },
                label = {
                    Text(
                        unit?.label ?: "OFF",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                modifier = Modifier.heightIn(min = 32.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------
// DawAlertDialog (port of classic AppDialog pattern)
// Uses ColumnScope content to avoid receiver scope conflict with Icons.Rounded
// ---------------------------------------------------------------------

@Composable
fun DawAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        val surfaceInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismissRequest,
                ),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                val maxDialogHeight = maxHeight.coerceAtMost(700.dp)
                Surface(
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxDialogHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 24.dp,
                ) {
                    Column(Modifier.padding(22.dp)) {
                        // Title row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                title?.invoke()
                            }
                            IconButton(onClick = onDismissRequest, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "閉じる")
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = (maxDialogHeight - 150.dp).coerceAtLeast(140.dp)),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            text?.invoke()
                            content()
                        }
                        Spacer(Modifier.height(16.dp))
                        // Buttons
                        if (confirmButton != null || dismissButton != null) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                dismissButton?.invoke()
                                Spacer(Modifier.width(8.dp))
                                confirmButton?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Settings Dialog (ported from Medit Classic)
// ---------------------------------------------------------------------

@Composable
fun DawSettingsDialog(
    vm: DawViewModel,
    project: Project?,
    prefs: EditorPrefs,
    onDismiss: () -> Unit,
    onLoadSoundFont: () -> Unit,
    onHelp: () -> Unit,
    versionName: String,
) {
    DawAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定") },
        confirmButton = { DawDialogPrimaryButton("閉じる", Icons.Rounded.Check, onClick = onDismiss) },
        dismissButton = { DawDialogSecondaryButton("操作ガイド", Icons.Rounded.HelpOutline, onHelp) },
        content = {
            // Capture icons in local variables to avoid receiver scope issues
            val icTune = Icons.Rounded.Tune
            val icGridOn = Icons.Rounded.GridOn
            val icTouchApp = Icons.Rounded.TouchApp
            val icSwipeLeft = Icons.Rounded.SwipeLeft
            val icVolumeUp = Icons.Rounded.VolumeUp
            val icGraphicEq = Icons.Rounded.GraphicEq
            val icSave = Icons.Rounded.Save
            val icAudiotrack = Icons.Rounded.Audiotrack
            val icLibraryMusic = Icons.Rounded.LibraryMusic
            val icCheck = Icons.Rounded.Check
            val icFolderOpen = Icons.Rounded.FolderOpen
            val icVolumeUpRegular = Icons.Rounded.VolumeUp

            Column(Modifier.heightIn(max = 600.dp)) {
                DawSectionTitle("入力", Icons.Rounded.Tune)
                DawSettingLabel("グリッド / スナップ")
                DawGridUnitChips(selected = prefs.grid, includeOff = true, onSelect = vm::setGrid)
                Spacer(Modifier.height(10.dp))
                DawSettingLabel("新規音符の長さ")
                DawGridUnitChips(selected = prefs.inputDuration, includeOff = false, onSelect = vm::setInputDuration)
                Spacer(Modifier.height(8.dp))
                DawToggleRow(
                    title = "スナップ",
                    description = "音符の開始・終了をグリッドに合わせる",
                    icon = Icons.Rounded.GridOn,
                    checked = prefs.snapEnabled,
                    onCheckedChange = vm::setSnapEnabled,
                )
                DawToggleRow(
                    title = "指で音符を編集",
                    description = "オフにすると指はスクロールと選択だけに使います",
                    icon = Icons.Rounded.TouchApp,
                    checked = prefs.fingerEditing,
                    onCheckedChange = vm::setFingerEditing,
                )
                DawToggleRow(
                    title = "再生中の自動スクロール",
                    description = "再生位置が画面外に出たら追従します",
                    icon = Icons.Rounded.SwipeLeft,
                    checked = prefs.autoScroll,
                    onCheckedChange = vm::setAutoScroll,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("入力ベロシティ", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text(prefs.inputVelocity.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                }
                Slider(value = prefs.inputVelocity.toFloat(), onValueChange = { vm.setInputVelocity(it.roundToInt()) }, valueRange = 1f..127f)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = OutlineSoft)
                Spacer(Modifier.height(12.dp))
                DawSectionTitle("音源", Icons.Rounded.GraphicEq)
                SoundFontSettingsCard(vm, project, prefs, onLoadSoundFont)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = OutlineSoft)
                Spacer(Modifier.height(12.dp))
                DawSectionTitle("保存について", Icons.Rounded.Save)
                Text(
                    "編集内容はアプリ内へ自動保存されます。MIDIの書き出し先とは別なので、元のファイルが勝手に上書きされることはありません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("MeditDAW $versionName \u00B7 トラック対応DAW版", style = MaterialTheme.typography.labelSmall, color = Mist)
            }
        }
    )
}

// ---------------------------------------------------------------------
// SoundFont Settings Card
// ---------------------------------------------------------------------

@Composable
fun SoundFontSettingsCard(
    vm: DawViewModel,
    project: Project?,
    prefs: EditorPrefs,
    onLoadSoundFont: () -> Unit,
) {
    val activeTrack = project?.tracks?.getOrNull(project.activeTrack ?: 0)
    val isAap = activeTrack?.isAapPlugin == true
    val trackName = activeTrack?.name ?: "トラック"

    if (isAap) {
        Text(
            "トラック\u201C$trackName\u201Dは AAP プラグインを使用しています。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "AAP プラグインの音色\u00B7パラメータはトラックパネルで設定します。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    // Collect instruments StateFlow as state to avoid lint error
    val instruments: List<InstrumentLibrary.Entry> by vm.instruments.collectAsStateWithLifecycle()

    val baseSf = project?.tracks?.getOrNull(project.activeTrack ?: 0)?.soundId
        ?.let { soundId -> instruments.firstOrNull { it.id == soundId } }
        ?: instruments.firstOrNull()

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                "トラック\u201C$trackName\u201D\u306E\u97F3\u6E90",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            instruments.forEach { inst ->
                val isSelected = baseSf?.id == inst.id
                Surface(
                    onClick = { vm.setSoundFont(inst.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Teal.copy(alpha = 0.16f) else Panel.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = if (isSelected) Teal else Mist, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(inst.name, style = MaterialTheme.typography.titleMedium, color = if (isSelected) Teal else Paper)
                            Text(
                                "内蔵ピアノ",
                                style = MaterialTheme.typography.labelSmall,
                                color = Mist,
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Rounded.Check, contentDescription = "選択中", tint = Teal)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = onLoadSoundFont,
                shape = RoundedCornerShape(12.dp),
                color = Panel.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("SoundFont (.sf2) を追加", style = MaterialTheme.typography.titleMedium)
                        Text("端末から .sf2 ファイルを選択", style = MaterialTheme.typography.labelSmall, color = Mist)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = OutlineSoft)
    Spacer(Modifier.height(12.dp))
    Text("音量", style = MaterialTheme.typography.labelMedium, color = Mist, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("マスター音量", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text("${(vm.currentMasterVolume() * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
    }
    Slider(value = vm.currentMasterVolume(), onValueChange = { vm.setMasterVolume(it) }, valueRange = 0f..2f)
}
