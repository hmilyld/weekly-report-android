package com.hmilyld.weeklyreport.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A user-visible message produced by a ViewModel.
 *
 * ViewModels never hold Chinese text: [Text] carries a string resource so the
 * copy lives in `res/values/strings.xml`, while [Raw] passes through text that
 * the domain layer already produced (API `detail`, URL validation, sync
 * failures). Resolve it in the UI, never in the ViewModel.
 */
sealed interface UiMessage {
    data class Text(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiMessage
    data class Raw(val value: String) : UiMessage
}

@Composable
fun UiMessage.asText(): String = resolve(LocalContext.current)

fun UiMessage.resolve(context: Context): String = when (this) {
    is UiMessage.Text -> if (args.isEmpty()) {
        context.getString(id)
    } else {
        context.getString(id, *args.toTypedArray())
    }

    is UiMessage.Raw -> value
}
