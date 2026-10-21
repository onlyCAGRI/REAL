package com.cagri.real.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.DesignStyle
import com.cagri.real.ui.theme.RealType
import java.time.LocalDate
import java.util.Locale

enum class DateSuggestionType(val label: String) {
    TODAY("Bugün'e ekle"),
    TOMORROW("Yarına ekle");

    fun targetDate(today: LocalDate = LocalDate.now()): LocalDate = when (this) {
        TODAY -> today
        TOMORROW -> today.plusDays(1)
    }
}

private val TODAY_REGEX = Regex(
    """(?:^|[^\p{L}\p{N}])(?:bugün|bugun|bu\s+akşam|bu\s+aksam|bu\s+gece)(?:$|[^\p{L}\p{N}])""",
    RegexOption.IGNORE_CASE
)

private val TOMORROW_REGEX = Regex(
    """(?:^|[^\p{L}\p{N}])(?:yarın|yarin)(?:$|[^\p{L}\p{N}])""",
    RegexOption.IGNORE_CASE
)

fun detectDateSuggestion(text: String): DateSuggestionType? {
    if (text.isBlank()) return null
    val trLocale = Locale.forLanguageTag("tr-TR")
    val lower = text.lowercase(trLocale)

    val todayMatch = TODAY_REGEX.find(lower)
    val tomorrowMatch = TOMORROW_REGEX.find(lower)

    return when {
        todayMatch != null && tomorrowMatch != null -> {
            if (todayMatch.range.first <= tomorrowMatch.range.first) DateSuggestionType.TODAY
            else DateSuggestionType.TOMORROW
        }
        todayMatch != null -> DateSuggestionType.TODAY
        tomorrowMatch != null -> DateSuggestionType.TOMORROW
        else -> null
    }
}

@Composable
fun ComposerDateSuggestionChip(
    suggestion: DateSuggestionType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    designStyle: DesignStyle = DesignStyle.Glass,
    reduceAnimations: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .then(
                if (designStyle == DesignStyle.LiquidGlass) {
                    Modifier
                        .pressScale(interactionSource, reduceAnimations = reduceAnimations, targetScale = 0.96f)
                        .liquidChrome(corners = 12.dp)
                } else {
                    Modifier
                        .background(Color(0xFF263247).copy(alpha = 0.70f), shape)
                        .border(0.75.dp, Color(0xFF5A7BB5).copy(alpha = 0.35f), shape)
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = suggestion.label }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ActionIcon(
            ActionGlyph.Calendar,
            Modifier.size(13.dp),
            Color(0xFF91B9FF)
        )
        Text(
            text = suggestion.label,
            color = Color(0xFFDCE7FA),
            style = RealType.composerSuggestion
        )
    }
}
