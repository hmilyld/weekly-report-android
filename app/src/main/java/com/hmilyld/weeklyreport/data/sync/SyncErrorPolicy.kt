package com.hmilyld.weeklyreport.data.sync

import com.hmilyld.weeklyreport.network.ApiErrorKind
import com.hmilyld.weeklyreport.network.ApiException

/**
 * A single rejected item must not stop unrelated offline writes forever.
 * Authentication and transient failures still stop the drain; permanent
 * request errors park only that item.
 */
object SyncErrorPolicy {
    enum class Action { NEEDS_LOGIN, RETRY_LATER, FAIL_ITEM }

    fun action(error: ApiException): Action {
        if (error.requiresLogin) return Action.NEEDS_LOGIN
        return when {
            error.status == 429 -> Action.RETRY_LATER
            error.status != null && error.status in 500..599 -> Action.RETRY_LATER
            error.kind == ApiErrorKind.NETWORK -> Action.RETRY_LATER
            error.status != null && error.status in 400..499 -> Action.FAIL_ITEM
            error.kind == ApiErrorKind.PARSE -> Action.FAIL_ITEM
            else -> Action.RETRY_LATER
        }
    }
}
