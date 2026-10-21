package com.cagri.real.ui.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cagri.real.R
import com.cagri.real.ui.connections.ConnectionPill
import com.cagri.real.ui.theme.RealType

const val RealPrivacyUrl = "https://real-privacy-staging.realapp.workers.dev/privacy"
const val RealKvkkUrl = "https://real-privacy-staging.realapp.workers.dev/kvkk"
val RealDataUseSummary: String
    @Composable get() = stringResource(R.string.real_privacy_summary)

private val DataUseSections = listOf(
    R.string.real_privacy_section_0_title to R.string.real_privacy_section_0_body,
    R.string.real_privacy_section_1_title to R.string.real_privacy_section_1_body,
    R.string.real_privacy_section_2_title to R.string.real_privacy_section_2_body,
    R.string.real_privacy_section_3_title to R.string.real_privacy_section_3_body,
    R.string.real_privacy_section_4_title to R.string.real_privacy_section_4_body,
    R.string.real_privacy_section_5_title to R.string.real_privacy_section_5_body,
    R.string.real_privacy_section_6_title to R.string.real_privacy_section_6_body,
    R.string.real_privacy_section_7_title to R.string.real_privacy_section_7_body,
    R.string.real_privacy_section_8_title to R.string.real_privacy_section_8_body
)

/** Reading and language selection never grant or revoke feature permission. */
@Composable
fun RealDataUseDialog(onClose: () -> Unit) {
    var open by remember { mutableStateOf<Int?>(null) }
    var rights by remember { mutableStateOf(false) }
    if (rights) {
        PrivacyRightsDialog { rights = false }
        return
    }
    PrivacyDetailsDialog(title = stringResource(R.string.real_privacy_title),
        subtitle = stringResource(R.string.real_privacy_subtitle), onBack = onClose) {
        DataUseSections.forEachIndexed { index, (title, body) ->
            PrivacyDisclosureRow(title = stringResource(title), expanded = open == index,
                onToggle = { open = if (open == index) null else index }) {
                Text(stringResource(body), style = RealType.cardBody, color = Color.White.copy(alpha = .72f))
            }
        }
        ConnectionPill(stringResource(R.string.real_privacy_rights), { rights = true })
        PrivacyDocumentLinks(includeDeviceSettings = true)
    }
}
