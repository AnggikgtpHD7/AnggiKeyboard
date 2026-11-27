package com.anggik.keyboard.engine

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.LinearLayout
import android.widget.inline.InlineContentView
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.R)
object InlineAutofillHelper {

    fun buildRequest(heightPx: Int): InlineSuggestionsRequest {
        val h = heightPx.coerceAtLeast(36)
        val spec = InlinePresentationSpec.Builder(
            Size(64, h),
            Size(640, h)
        ).setStyle(Bundle.EMPTY).build()

        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(4)
            .build()
    }

    fun populateSuggestions(
        context: Context,
        response: InlineSuggestionsResponse,
        heightPx: Int,
        container: LinearLayout,
        onVisibilityChanged: (Boolean) -> Unit
    ) {
        val suggestions = response.inlineSuggestions
        container.removeAllViews()
        if (suggestions.isEmpty()) {
            onVisibilityChanged(false)
            return
        }

        val h = heightPx.coerceAtLeast(36)
        val marginPx = (4 * context.resources.displayMetrics.density).toInt()
        var inflatedCount = 0

        for (suggestion in suggestions) {
            suggestion.inflate(
                context,
                Size(ViewGroup.LayoutParams.WRAP_CONTENT, h),
                context.mainExecutor
            ) { contentView: InlineContentView? ->
                if (contentView != null) {
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        h
                    ).apply {
                        marginStart = marginPx
                        marginEnd = marginPx
                    }
                    container.addView(contentView, lp)
                    inflatedCount++
                    if (inflatedCount == 1) {
                        onVisibilityChanged(true)
                    }
                }
            }
        }
        if (suggestions.isEmpty()) {
            onVisibilityChanged(false)
        }
    }
}
