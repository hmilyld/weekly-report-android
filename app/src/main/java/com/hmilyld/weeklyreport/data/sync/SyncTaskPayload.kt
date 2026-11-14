package com.hmilyld.weeklyreport.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncTaskPayload(
    val content: String,
    val deadline: String? = null,
    val includeDeadline: Boolean = false,
    val isCompleted: Boolean = false,
)

/** Pure rule for replaying a locally saved day against the server's current value. */
object ReplayRules {
    sealed interface Decision {
        data object Send : Decision
        data object AlreadySynced : Decision
        data object Conflict : Decision
    }

    fun dailyDecision(serverContent: String?, baseline: String?, localContent: String): Decision {
        if (serverContent == null) return Decision.Send
        if (serverContent == localContent) return Decision.AlreadySynced
        if (serverContent.isEmpty() && baseline.isNullOrEmpty()) return Decision.Send
        if (baseline != null && serverContent == baseline) return Decision.Send
        return Decision.Conflict
    }
}
