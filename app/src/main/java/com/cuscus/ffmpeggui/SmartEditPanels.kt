package com.cuscus.ffmpeggui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.conversion.BeatsStatus
import com.cuscus.ffmpeggui.conversion.FollowCropStatus
import com.cuscus.ffmpeggui.conversion.PauseCutsStatus
import com.cuscus.ffmpeggui.conversion.SceneSplitsStatus
import com.cuscus.ffmpeggui.core.editing.CutRange
import com.cuscus.ffmpeggui.core.editing.PauseCuts
import com.cuscus.ffmpeggui.ui.expressive.PanelCard
import java.util.Locale

/*
 * The smart-editing tools: pause and filler cuts, scene splits, cutting and
 * snapping on the beat, and the subject-following vertical crop. Each panel
 * drives one search of SmartEditUi and reviews what it found; nothing changes
 * the video until its Apply button.
 */

/** The smart-editing tools' state and actions, collected from the view model for the editor. */
class SmartEditUi(
    val pauseCuts: PauseCutsStatus = PauseCutsStatus.Idle,
    val sceneSplits: SceneSplitsStatus = SceneSplitsStatus.Idle,
    val beats: BeatsStatus = BeatsStatus.Idle,
    val snapToBeats: Boolean = false,
    val followCrop: FollowCropStatus = FollowCropStatus.Idle,
    /** A subject-following reframe is set on the video. */
    val followCropSet: Boolean = false,
    /** A few frames cropped as the export will crop them (null until tracked). */
    val followCropSamples: List<androidx.compose.ui.graphics.ImageBitmap>? = null,
    /** The sequence plays clips of files other than the main one alone: the reframe cannot apply. */
    val followCropNotMainOnly: Boolean = false,
    val onFindPauseCuts: () -> Unit = {},
    val onTogglePauseCut: (Int) -> Unit = {},
    val onApplyPauseCuts: () -> Unit = {},
    val onDismissPauseCuts: () -> Unit = {},
    /** Starts listening to the video (its words are needed for the pause cuts). */
    val onListen: () -> Unit = {},
    val onFindSceneSplits: () -> Unit = {},
    val onToggleSceneSplit: (Int) -> Unit = {},
    val onApplySceneSplits: () -> Unit = {},
    val onDismissSceneSplits: () -> Unit = {},
    val onFindBeats: () -> Unit = {},
    val onToggleBeat: (Int) -> Unit = {},
    val onSnapToBeats: (Boolean) -> Unit = {},
    val onApplyBeatCuts: () -> Unit = {},
    val onDismissBeats: () -> Unit = {},
    val onTrackSubject: () -> Unit = {},
    val onStopFollowCrop: () -> Unit = {},
    val onClearFollowCrop: () -> Unit = {},
    /** Opens the settings page the cut-out model is installed on. */
    val onOpenCutoutModel: () -> Unit = {},
)

private val smartPanelModifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)

private fun smartSeconds(ms: Long): String {
    val total = ms / 1000
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}

/** m:ss.t, tenths kept, for ranges short enough that whole seconds would read the same at both ends. */
private fun smartSecondsTenths(ms: Long): String {
    val tenths = ms / 100
    return String.format(Locale.US, "%d:%02d.%d", tenths / 600, (tenths / 10) % 60, tenths % 10)
}

@Composable
private fun SmartNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CutRow(text: String, chosen: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = chosen, onCheckedChange = { onToggle() })
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The pause-and-filler cut tool: finds the quiet and the hesitations, cuts the ticked ones. */
@Composable
fun PauseCutsPanel(ui: SmartEditUi) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = smartPanelModifier) {
        when (val status = ui.pauseCuts) {
            PauseCutsStatus.Idle -> PanelCard {
                SmartNote(stringResource(R.string.smart_cuts_intro))
                Button(onClick = ui.onFindPauseCuts, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.smart_cuts_find))
                }
            }
            PauseCutsStatus.NeedsWords -> PanelCard {
                SmartNote(stringResource(R.string.smart_cuts_need_words))
                Button(onClick = ui.onListen, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.smart_cuts_listen))
                }
            }
            PauseCutsStatus.Working -> PanelCard {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                SmartNote(stringResource(R.string.smart_cuts_working))
            }
            PauseCutsStatus.Failed -> PanelCard {
                SmartNote(stringResource(R.string.smart_cuts_failed))
                OutlinedButton(onClick = ui.onFindPauseCuts, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.smart_retry))
                }
            }
            is PauseCutsStatus.Ready -> {
                if (status.cuts.isEmpty()) {
                    PanelCard { SmartNote(stringResource(R.string.smart_cuts_none)) }
                } else {
                    // A range found both ways lists with the pauses.
                    val pauses = status.cuts.withIndex().filter { PauseCuts.REASON_PAUSE in it.value.reason }
                    val fillers = status.cuts.withIndex().filter { PauseCuts.REASON_PAUSE !in it.value.reason }
                    if (pauses.isNotEmpty()) PanelCard {
                        Text(
                            text = stringResource(R.string.smart_cuts_pauses, pauses.count { it.index in status.chosen }),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        pauses.forEach { cut ->
                            CutRow(cutLabel(cut.value), cut.index in status.chosen) { ui.onTogglePauseCut(cut.index) }
                        }
                    }
                    if (fillers.isNotEmpty()) PanelCard {
                        Text(
                            text = stringResource(R.string.smart_cuts_fillers, fillers.count { it.index in status.chosen }),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        fillers.forEach { cut ->
                            CutRow(cutLabel(cut.value), cut.index in status.chosen) { ui.onTogglePauseCut(cut.index) }
                        }
                    }
                    PanelCard {
                        Button(
                            onClick = ui.onApplyPauseCuts,
                            enabled = status.chosen.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.smart_cuts_apply, status.chosen.size))
                        }
                        OutlinedButton(onClick = ui.onDismissPauseCuts, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.smart_cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun cutLabel(cut: CutRange): String = stringResource(
    R.string.smart_cut_range,
    smartSecondsTenths(cut.startMs),
    smartSecondsTenths(cut.endMs),
    decimal((cut.endMs - cut.startMs) / 1000f, 1),
)

/** The scene-split tool: a cut wherever the picture changes completely. */
@Composable
fun SceneSplitsPanel(ui: SmartEditUi) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = smartPanelModifier) {
        when (val status = ui.sceneSplits) {
            SceneSplitsStatus.Idle -> PanelCard {
                SmartNote(stringResource(R.string.smart_scenes_intro))
                Button(onClick = ui.onFindSceneSplits, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.smart_scenes_find))
                }
            }
            SceneSplitsStatus.Working -> PanelCard {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                SmartNote(stringResource(R.string.smart_scenes_working))
            }
            SceneSplitsStatus.Failed -> PanelCard {
                SmartNote(stringResource(R.string.smart_scenes_failed))
                OutlinedButton(onClick = ui.onFindSceneSplits, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.smart_retry))
                }
            }
            is SceneSplitsStatus.Ready -> {
                if (status.points.isEmpty()) {
                    PanelCard { SmartNote(stringResource(R.string.smart_scenes_none)) }
                } else {
                    PanelCard {
                        Text(
                            text = stringResource(R.string.smart_scenes_list, status.points.size),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        status.points.forEachIndexed { index, at ->
                            CutRow(
                                stringResource(R.string.smart_split_at, smartSeconds(at)),
                                index in status.chosen,
                            ) { ui.onToggleSceneSplit(index) }
                        }
                    }
                    PanelCard {
                        Button(
                            onClick = ui.onApplySceneSplits,
                            enabled = status.chosen.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.smart_scenes_apply, status.chosen.size))
                        }
                        OutlinedButton(onClick = ui.onDismissSceneSplits, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.smart_cancel))
                        }
                    }
                }
            }
        }
    }
}

/** The beat tool: finds the music's beat, snaps splits to it and cuts on it. */
@Composable
fun BeatsPanel(ui: SmartEditUi) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = smartPanelModifier) {
        when (val status = ui.beats) {
            BeatsStatus.Idle -> PanelCard {
                SmartNote(stringResource(R.string.smart_beats_intro))
                Button(onClick = ui.onFindBeats, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.smart_beats_find))
                }
            }
            BeatsStatus.Working -> PanelCard {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                SmartNote(stringResource(R.string.smart_beats_working))
            }
            BeatsStatus.Failed -> PanelCard {
                SmartNote(stringResource(R.string.smart_beats_failed))
                OutlinedButton(onClick = ui.onFindBeats, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.smart_retry))
                }
            }
            is BeatsStatus.Ready -> {
                PanelCard {
                    if (status.tempoBpm != null) {
                        Text(
                            text = stringResource(R.string.smart_beats_tempo, decimal(status.tempoBpm.toFloat(), 0), status.beatsMs.size),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.smart_beats_snap),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = ui.snapToBeats, onCheckedChange = ui.onSnapToBeats)
                    }
                }
                PanelCard {
                    Text(
                        text = stringResource(R.string.smart_beats_list, status.chosen.size, status.beatsMs.size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.heightIn(max = 260.dp),
                    ) {
                        itemsIndexed(status.beatsMs) { index, at ->
                            CutRow(smartSeconds(at), index in status.chosen) { ui.onToggleBeat(index) }
                        }
                    }
                    Button(
                        onClick = ui.onApplyBeatCuts,
                        enabled = status.chosen.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.smart_beats_apply, status.chosen.size))
                    }
                    OutlinedButton(onClick = ui.onDismissBeats, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.smart_cancel))
                    }
                }
            }
        }
    }
}

/** The follow-crop tool: a vertical cut-out that follows the video's subject. */
@Composable
fun FollowCropPanel(ui: SmartEditUi) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = smartPanelModifier) {
        when (val status = ui.followCrop) {
            is FollowCropStatus.Working -> PanelCard {
                LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth())
                SmartNote(stringResource(R.string.smart_follow_working))
                OutlinedButton(onClick = ui.onStopFollowCrop, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.smart_follow_stop))
                }
            }
            FollowCropStatus.NoModel -> PanelCard {
                SmartNote(stringResource(R.string.smart_follow_no_model))
                Button(onClick = ui.onOpenCutoutModel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.smart_follow_get_model))
                }
            }
            FollowCropStatus.Failed -> PanelCard {
                SmartNote(stringResource(R.string.smart_follow_failed))
                OutlinedButton(onClick = ui.onTrackSubject, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.smart_retry))
                }
            }
            FollowCropStatus.Idle -> PanelCard {
                if (ui.followCropSet) {
                    val samples = ui.followCropSamples
                    if (!samples.isNullOrEmpty()) {
                        Text(
                            text = stringResource(R.string.smart_follow_samples),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            samples.forEach { frame ->
                                androidx.compose.foundation.Image(
                                    bitmap = frame,
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
                                )
                            }
                        }
                    }
                    SmartNote(stringResource(R.string.smart_follow_set))
                    if (ui.followCropNotMainOnly) {
                        SmartNote(stringResource(R.string.smart_follow_not_applied))
                    }
                    OutlinedButton(onClick = ui.onTrackSubject, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.smart_follow_retrack))
                    }
                    OutlinedButton(onClick = ui.onClearFollowCrop, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.smart_follow_remove))
                    }
                } else if (ui.followCropNotMainOnly) {
                    SmartNote(stringResource(R.string.smart_follow_multi))
                } else {
                    SmartNote(stringResource(R.string.smart_follow_intro))
                    Button(onClick = ui.onTrackSubject, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.smart_follow_start))
                    }
                }
            }
            FollowCropStatus.MultiSource -> PanelCard {
                SmartNote(stringResource(R.string.smart_follow_multi))
            }
        }
    }
}
