package com.cuscus.ffmpeggui.conversion

import com.cuscus.ffmpeggui.core.editing.CutRange

/** Where the pause-and-filler cut search is. */
sealed interface PauseCutsStatus {
    data object Idle : PauseCutsStatus

    /** The file has not been listened to yet; its words are needed first. */
    data object NeedsWords : PauseCutsStatus

    data object Working : PauseCutsStatus

    /** The suggestions, on the timeline clock; [chosen] are ticked. */
    data class Ready(val cuts: List<CutRange>, val chosen: Set<Int>) : PauseCutsStatus

    data object Failed : PauseCutsStatus
}

/** Where the scene-split search is. */
sealed interface SceneSplitsStatus {
    data object Idle : SceneSplitsStatus
    data object Working : SceneSplitsStatus

    /** The split points, on the timeline clock; [chosen] are ticked. */
    data class Ready(val points: List<Long>, val chosen: Set<Int>) : SceneSplitsStatus

    data object Failed : SceneSplitsStatus
}

/** Where the beat search is. */
sealed interface BeatsStatus {
    data object Idle : BeatsStatus
    data object Working : BeatsStatus

    /**
     * The beats (and the onsets they came from), on the timeline clock;
     * [chosen] are ticked for cutting (snapping uses every beat regardless).
     */
    data class Ready(
        val onsetsMs: List<Long>,
        val beatsMs: List<Long>,
        val tempoBpm: Double?,
        val chosen: Set<Int> = beatsMs.indices.toSet(),
    ) : BeatsStatus

    data object Failed : BeatsStatus
}

/** Where following the subject for a vertical reframe is. */
sealed interface FollowCropStatus {
    data object Idle : FollowCropStatus

    /** [fraction] of the frames looked at (0..1). */
    data class Working(val fraction: Float) : FollowCropStatus

    /** No cut-out model is installed (Settings > Smart features has it). */
    data object NoModel : FollowCropStatus

    /** The sequence holds more than one file: the reframe is made for one. */
    data object MultiSource : FollowCropStatus

    data object Failed : FollowCropStatus
}
