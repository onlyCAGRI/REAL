package com.cagri.bigboscagri.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CategoryConceptDemoScreen(onClose: () -> Unit) {
    // The old Collections / Layered category experiments are intentionally removed.
    // This screen now hosts only the two card concepts that are still being evaluated.
    var mode by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) {
                Text("‹ Geri", color = Color.White)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    "REAL / Konsept",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp
                )
                Text(
                    "Demo · gerçek veriler değişmez",
                    color = Color(0xFF99999F),
                    fontSize = 11.sp
                )
            }
        }

        DemoSegments(
            labels = listOf("Note Card", "Kart Yığını"),
            selected = mode,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            onSelect = { mode = it }
        )

        when (mode) {
            0 -> DemoNoteCard(Modifier.weight(1f))
            else -> DemoStackCards(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun DemoSegments(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .selectableGroup()
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color(0xFF242426), Color(0xFF111113))))
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                Modifier
                    .weight(1f)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (index == selected) Color(0xFF37373B) else Color.Transparent)
                    .border(
                        0.75.dp,
                        if (index == selected) Color(0xFF66666B) else Color.Transparent,
                        RoundedCornerShape(50)
                    )
                    .selectable(index == selected, role = Role.Tab, onClick = { onSelect(index) })
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (index == selected) Color.White else Color(0xFFA4A4AC),
                    fontSize = 12.sp,
                    fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
