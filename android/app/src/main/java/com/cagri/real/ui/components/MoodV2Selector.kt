package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.cagri.real.data.JournalMood
import com.cagri.real.data.MoodDetails
import com.cagri.real.data.MoodScope
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class MoodFamilyV2(
    private val trTitle: String,
    val tint: Color,
    val defaultMood: JournalMood,
    val moods: List<JournalMood>
) {
    val title: String get() = t(trTitle)
}
internal val MoodFamiliesV2 get() = listOf(
    MoodFamilyV2(
        trTitle = t("Kötü"),
        tint = Color(0xFFF05B5B),
        defaultMood = JournalMood.BAD,
        moods = listOf(JournalMood.BAD, JournalMood.SAD, JournalMood.ANGRY)
    ),
    MoodFamilyV2(
        trTitle = t("Zor"),
        tint = Color(0xFFF1A13D),
        defaultMood = JournalMood.HARD,
        moods = listOf(JournalMood.HARD, JournalMood.TIRED, JournalMood.STRESSED, JournalMood.LOW)
    ),
    MoodFamilyV2(
        trTitle = t("Normal"),
        tint = Color(0xFFAEB8CC),
        defaultMood = JournalMood.NEUTRAL,
        moods = listOf(JournalMood.NEUTRAL, JournalMood.BORED)
    ),
    MoodFamilyV2(
        trTitle = t("İyi"),
        tint = Color(0xFF55D3B4),
        defaultMood = JournalMood.GOOD,
        moods = listOf(JournalMood.GOOD, JournalMood.CALM)
    ),
    MoodFamilyV2(
        trTitle = t("Çok iyi"),
        tint = Color(0xFFB8E85C),
        defaultMood = JournalMood.VERY_GOOD,
        moods = listOf(JournalMood.VERY_GOOD, JournalMood.HAPPY, JournalMood.EXCITED)
    )
)


internal data class MoodDescriptorOption(val key: String, val label: String)
internal data class MoodFactorOption(val key: String, val label: String)
internal data class MoodFactorGroup(val title: String, val options: List<MoodFactorOption>)

internal val MoodDescriptorsV2: Map<Int, List<MoodDescriptorOption>> get() = mapOf(
    0 to listOf(
        MoodDescriptorOption("sad", t("Üzgün")),
        MoodDescriptorOption("angry", t("Sinirli")),
        MoodDescriptorOption("anxious", t("Kaygılı")),
        MoodDescriptorOption("scared", t("Korkmuş")),
        MoodDescriptorOption("overwhelmed", t("Bunalmış")),
        MoodDescriptorOption("ashamed", t("Utanmış")),
        MoodDescriptorOption("disgusted", t("İğrenmiş")),
        MoodDescriptorOption("frustrated", t("Hayal kırıklığı")),
        MoodDescriptorOption("lonely", t("Yalnız")),
        MoodDescriptorOption("hopeless", t("Umutsuz")),
        MoodDescriptorOption("guilty", t("Suçlu")),
        MoodDescriptorOption("drained", t("Bitkin")),
        MoodDescriptorOption("stressed", t("Stresli")),
        MoodDescriptorOption("fuck_off", "F*ck off")
    ),
    1 to listOf(
        MoodDescriptorOption("tired", t("Yorgun")),
        MoodDescriptorOption("stressed", t("Stresli")),
        MoodDescriptorOption("low", t("Keyifsiz")),
        MoodDescriptorOption("tense", t("Gergin")),
        MoodDescriptorOption("worried", t("Endişeli")),
        MoodDescriptorOption("pressured", t("Baskı altında")),
        MoodDescriptorOption("restless", t("Huzursuz")),
        MoodDescriptorOption("impatient", t("Sabırsız")),
        MoodDescriptorOption("distracted", t("Dalgın")),
        MoodDescriptorOption("overwhelmed", t("Bunalmış")),
        MoodDescriptorOption("anxious", t("Kaygılı")),
        MoodDescriptorOption("drained", t("Bitkin")),
        MoodDescriptorOption("sad", t("Üzgün")),
        MoodDescriptorOption("uncertain", t("Kararsız"))
    ),
    2 to listOf(
        MoodDescriptorOption("neutral", t("Normal")),
        MoodDescriptorOption("bored", t("Sıkılmış")),
        MoodDescriptorOption("balanced", t("Dengeli")),
        MoodDescriptorOption("blank", t("Boş")),
        MoodDescriptorOption("distracted", t("Dalgın")),
        MoodDescriptorOption("steady", t("Sakin")),
        MoodDescriptorOption("uncertain", t("Kararsız")),
        MoodDescriptorOption("tired", t("Yorgun")),
        MoodDescriptorOption("low", t("Keyifsiz")),
        MoodDescriptorOption("focused", t("Odaklı")),
        MoodDescriptorOption("content", t("Memnun"))
    ),
    3 to listOf(
        MoodDescriptorOption("calm", t("Sakin")),
        MoodDescriptorOption("relieved", t("Rahatlamış")),
        MoodDescriptorOption("hopeful", t("Umutlu")),
        MoodDescriptorOption("content", t("Memnun")),
        MoodDescriptorOption("focused", t("Odaklı")),
        MoodDescriptorOption("safe", t("Güvende")),
        MoodDescriptorOption("grateful", t("Minnettar")),
        MoodDescriptorOption("energized", t("Enerjik")),
        MoodDescriptorOption("happy", t("Mutlu")),
        MoodDescriptorOption("motivated", t("Motive")),
        MoodDescriptorOption("excited", t("Heyecanlı")),
        MoodDescriptorOption("balanced", t("Dengeli"))
    ),
    4 to listOf(
        MoodDescriptorOption("happy", t("Mutlu")),
        MoodDescriptorOption("excited", t("Heyecanlı")),
        MoodDescriptorOption("joyful", t("Neşeli")),
        MoodDescriptorOption("proud", t("Gururlu")),
        MoodDescriptorOption("motivated", t("Motive")),
        MoodDescriptorOption("grateful", t("Minnettar")),
        MoodDescriptorOption("loved", t("Sevgi dolu")),
        MoodDescriptorOption("energetic", t("Enerjik")),
        MoodDescriptorOption("hopeful", t("Umutlu")),
        MoodDescriptorOption("relieved", t("Rahatlamış"))
    )
)

internal val MoodFactorGroupsV2 get() = listOf(
    MoodFactorGroup(
        t("Kendim"),
        listOf(
            MoodFactorOption("health", t("Sağlık")),
            MoodFactorOption("sleep", t("Uyku")),
            MoodFactorOption("fitness", t("Spor")),
            MoodFactorOption("self_care", t("Öz bakım")),
            MoodFactorOption("hobbies", t("Hobiler")),
            MoodFactorOption("goals", t("Hedefler")),
            MoodFactorOption("appearance", t("Görünüş")),
            MoodFactorOption("spirituality", t("Maneviyat"))
        )
    ),
    MoodFactorGroup(
        t("İnsanlar"),
        listOf(
            MoodFactorOption("family", t("Aile")),
            MoodFactorOption("friends", t("Arkadaşlar")),
            MoodFactorOption("partner", t("Partner")),
            MoodFactorOption("dating", t("Flört")),
            MoodFactorOption("communication", t("İletişim")),
            MoodFactorOption("conflict", t("Tartışma")),
            MoodFactorOption("closeness", t("Yakınlık")),
            MoodFactorOption("crowds", t("Kalabalık"))
        )
    ),
    MoodFactorGroup(
        t("Hayat"),
        listOf(
            MoodFactorOption("work", t("İş")),
            MoodFactorOption("education", t("Eğitim")),
            MoodFactorOption("money", t("Para")),
            MoodFactorOption("tasks", t("Görevler")),
            MoodFactorOption("weather", t("Hava")),
            MoodFactorOption("travel", t("Seyahat")),
            MoodFactorOption("current_events", t("Güncel olaylar")),
            MoodFactorOption("routine", t("Rutin")),
            MoodFactorOption("future", t("Gelecek"))
        )
    )
)

/* Labels for factors saved by earlier 0.7.8 builds. They stay readable in history/detail,
 * but are intentionally not shown as choices anymore because most duplicated the mood
 * descriptors above (for example lonely, bored, restless, hopeful, grateful). */
private val LegacyMoodFactorLabelsV2 get() = mapOf(
    "self" to t("Kendim"),
    "community" to t("Topluluk"),
    "self_blank" to t("Boş hissediyorum"),
    "mind_full" to t("Kafam dolu"),
    "self_overwhelmed" to t("Bunaldım"),
    "fed_up" to t("Bıktım"),
    "self_restless" to t("Huzursuzum"),
    "cant_sit_still" to t("Yerimde duramıyorum"),
    "no_energy" to t("Enerjim yok"),
    "high_energy" to t("Enerjim yüksek"),
    "unfocused" to t("Odaksızım"),
    "indecisive" to t("Kararsızım"),
    "angry_at_self" to t("Kendime kızgınım"),
    "good_with_self" to t("Kendimle iyiyim"),
    "proud_of_self" to t("Gururluyum"),
    "confident" to t("Özgüvenliyim"),
    "curious" to t("Meraklıyım"),
    "want_to_do_something" to t("Bir şey yapmak istiyorum"),
    "want_to_do_nothing" to t("Hiçbir şey yapmak istemiyorum"),
    "stuck" to t("Sıkışmış hissediyorum"),
    "want_change" to t("Değişiklik istiyorum"),
    "feel_lonely" to t("Yalnız hissediyorum"),
    "tired_of_people" to t("İnsanlardan yoruldum"),
    "want_solitude" to t("Biraz yalnız kalmak istiyorum"),
    "want_to_talk" to t("Konuşmak istiyorum"),
    "miss_someone" to t("Birini özledim"),
    "angry_at_someone" to t("Birine kızgınım"),
    "hurt_by_people" to t("Kırıldım"),
    "feel_unseen" to t("Görülmüyorum"),
    "feel_misunderstood" to t("Anlaşılmıyorum"),
    "feel_excluded" to t("Dışlanmış hissediyorum"),
    "feel_close_to_someone" to t("Birine yakın hissediyorum"),
    "feel_social" to t("Sosyal hissediyorum"),
    "want_people" to t("İnsan görmek istiyorum"),
    "too_many_people" to t("Fazla insan geldi"),
    "want_distance" to t("Mesafe istiyorum"),
    "feel_disconnected" to t("Bağlantım kopmuş gibi"),
    "life_routine" to t("Her şey çok rutin"),
    "life_bored" to t("Sıkıldım"),
    "everything_piling_up" to t("Her şey üst üste geliyor"),
    "chaotic" to t("Kaotik"),
    "uncertain_life" to t("Belirsiz"),
    "not_in_control" to t("Kontrol bende değil"),
    "life_fast" to t("Hayat hızlı akıyor"),
    "something_missing" to t("Bir şey eksik"),
    "want_something_new" to t("Yeni bir şey istiyorum"),
    "going_well" to t("Yolunda gidiyor"),
    "hopeful_life" to t("Umutluyum"),
    "excited_life" to t("Heyecanlıyım"),
    "feel_lucky" to t("Şanslı hissediyorum"),
    "grateful_life" to t("Minnettarım"),
    "same_things" to t("Aynı şeyleri yapıyorum"),
    "want_life_change" to t("Bir şey değişsin istiyorum"),
    "nothing_happened_today" to t("Bugün hiçbir şey olmadı"),
    "too_much_happening" to t("Çok şey oluyor")
)

internal fun moodDescriptorLabelV2(key: String): String =
    MoodDescriptorsV2.values.flatten().firstOrNull { it.key == key }?.label ?: key

internal fun moodFactorLabelV2(key: String): String =
    MoodFactorGroupsV2.flatMap { it.options }.firstOrNull { it.key == key }?.label
        ?: LegacyMoodFactorLabelsV2[key]
        ?: key

internal fun JournalMood.familyIndexV2(): Int {
    // FUCK_OFF is kept in the enum only for backward compatibility with older saved data.
    // New entries represent it as the "fuck_off" descriptor under BAD.
    if (this == JournalMood.FUCK_OFF) return 0
    val index = MoodFamiliesV2.indexOfFirst { this in it.moods }
    return if (index >= 0) index else 2
}

fun moodV2Tint(mood: JournalMood): Color = MoodFamiliesV2[mood.familyIndexV2()].tint

internal fun moodV2FamilyTitle(mood: JournalMood): String = MoodFamiliesV2[mood.familyIndexV2()].title

private fun moodV2LerpColor(progress: Float): Color {
    val clamped = progress.coerceIn(0f, 4f)
    val left = floor(clamped).toInt().coerceIn(0, 4)
    val right = (left + 1).coerceAtMost(4)
    val fraction = clamped - left
    return androidx.compose.ui.graphics.lerp(MoodFamiliesV2[left].tint, MoodFamiliesV2[right].tint, fraction)
}

internal data class FaceV2Params(
    val eyeWidth: Float,
    val eyeHeight: Float,
    val eyeY: Float,
    val eyeSpacing: Float,
    val mouthWidth: Float,
    val mouthY: Float,
    val mouthCurve: Float,
    val mouthStrokeScale: Float,
    val browStrength: Float
)

private val FaceAnchorsV2 = listOf(
    // Kötü: keep the eyes restrained, but make the lower mouth shape much larger
    // and visibly heavier so the expression reads immediately.
    FaceV2Params(0.105f, 0.105f, 0.385f, 0.225f, 0.54f, 0.690f, -0.94f, 1.72f, 0.08f),
    // Zor: slightly tired / compressed.
    FaceV2Params(0.19f, 0.064f, 0.400f, 0.245f, 0.36f, 0.660f, -0.38f, 1.18f, 0.18f),
    // Normal: preserve the familiar neutral REAL face.
    FaceV2Params(0.25f, 0.052f, 0.390f, 0.260f, 0.30f, 0.640f, 0.00f, 1.00f, 0.00f),
    // İyi: eyes open noticeably and the smile gets wider.
    FaceV2Params(0.185f, 0.185f, 0.365f, 0.270f, 0.39f, 0.615f, 0.56f, 1.00f, 0.00f),
    // Çok iyi: biggest, roundest eyes and the broadest smile in the five-anchor set.
    FaceV2Params(0.235f, 0.235f, 0.350f, 0.285f, 0.46f, 0.600f, 0.90f, 1.00f, 0.00f)
)

private fun Float.mix(other: Float, t: Float): Float = this + (other - this) * t

internal fun faceParamsAtV2(progress: Float, selectedMood: JournalMood?): FaceV2Params {
    val clamped = progress.coerceIn(0f, 4f)
    val left = floor(clamped).toInt().coerceIn(0, 4)
    val right = (left + 1).coerceAtMost(4)
    val t = clamped - left
    val a = FaceAnchorsV2[left]
    val b = FaceAnchorsV2[right]
    var result = FaceV2Params(
        eyeWidth = a.eyeWidth.mix(b.eyeWidth, t),
        eyeHeight = a.eyeHeight.mix(b.eyeHeight, t),
        eyeY = a.eyeY.mix(b.eyeY, t),
        eyeSpacing = a.eyeSpacing.mix(b.eyeSpacing, t),
        mouthWidth = a.mouthWidth.mix(b.mouthWidth, t),
        mouthY = a.mouthY.mix(b.mouthY, t),
        mouthCurve = a.mouthCurve.mix(b.mouthCurve, t),
        mouthStrokeScale = a.mouthStrokeScale.mix(b.mouthStrokeScale, t),
        browStrength = a.browStrength.mix(b.browStrength, t)
    )

    // The five main anchors morph continuously. Specific moods only add a small
    // character detail once the user settles on a family.
    result = when (selectedMood) {
        JournalMood.EXCITED -> result.copy(eyeWidth = result.eyeWidth * 1.10f, eyeHeight = result.eyeHeight * 1.14f, mouthCurve = 0.95f)
        JournalMood.HAPPY -> result.copy(mouthWidth = result.mouthWidth * 1.08f, mouthCurve = maxOf(result.mouthCurve, 0.88f))
        JournalMood.CALM -> result.copy(eyeHeight = minOf(result.eyeHeight, 0.055f), mouthCurve = 0.28f)
        JournalMood.TIRED -> result.copy(eyeHeight = 0.040f, eyeY = result.eyeY + 0.025f, mouthCurve = -0.16f)
        JournalMood.BORED -> result.copy(eyeHeight = 0.045f, mouthCurve = -0.04f)
        JournalMood.STRESSED -> result.copy(eyeHeight = 0.055f, browStrength = 0.45f, mouthCurve = -0.50f)
        JournalMood.ANGRY -> result.copy(eyeHeight = 0.075f, browStrength = 1.0f, mouthCurve = -0.48f)
        JournalMood.SAD -> result.copy(eyeY = result.eyeY + 0.015f, mouthCurve = -0.90f, browStrength = 0.25f)
        JournalMood.LOW -> result.copy(eyeHeight = 0.050f, mouthCurve = -0.32f)
        JournalMood.FUCK_OFF -> result.copy(eyeHeight = 0.052f, browStrength = 1.0f, mouthCurve = -0.12f, mouthWidth = 0.28f)
        else -> result
    }
    return result
}

@Composable
fun MoodV2Face(
    mood: JournalMood,
    size: androidx.compose.ui.unit.Dp,
    tint: Color = Color(0xFFF5F5F7),
    modifier: Modifier = Modifier
) {
    val familyIndex = mood.familyIndexV2()
    val params = faceParamsAtV2(familyIndex.toFloat(), mood)
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.052f
        val eyeW = w * params.eyeWidth
        val eyeH = h * params.eyeHeight
        val eyeY = h * params.eyeY
        val spacing = w * params.eyeSpacing
        val centerX = w / 2f
        fun drawEye(cx: Float) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(cx - eyeW / 2f, eyeY - eyeH / 2f),
                size = Size(eyeW, eyeH),
                cornerRadius = CornerRadius(eyeH / 2f, eyeH / 2f)
            )
        }
        drawEye(centerX - spacing / 2f)
        drawEye(centerX + spacing / 2f)
        if (params.browStrength > 0.05f) {
            val browY = eyeY - eyeH * 1.15f
            val browHalf = eyeW * 0.52f
            val tilt = eyeH * 0.75f * params.browStrength
            drawLine(tint, Offset(centerX - spacing / 2f - browHalf, browY - tilt), Offset(centerX - spacing / 2f + browHalf, browY + tilt), strokeWidth = stroke * 0.55f)
            drawLine(tint, Offset(centerX + spacing / 2f - browHalf, browY + tilt), Offset(centerX + spacing / 2f + browHalf, browY - tilt), strokeWidth = stroke * 0.55f)
        }
        val mouthCenterY = h * params.mouthY
        val mouthHalfW = w * params.mouthWidth / 2f
        val curve = h * 0.12f * params.mouthCurve
        val mouth = Path().apply {
            moveTo(centerX - mouthHalfW, mouthCenterY)
            cubicTo(
                centerX - mouthHalfW * 0.45f, mouthCenterY + curve,
                centerX + mouthHalfW * 0.45f, mouthCenterY + curve,
                centerX + mouthHalfW, mouthCenterY
            )
        }
        drawPath(
            path = mouth,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke * params.mouthStrokeScale, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

@Composable
fun MoodV2FullScreen(
    selectedMood: JournalMood,
    onMoodSelected: (JournalMood) -> Unit,
    onDismiss: () -> Unit,
    onSave: (JournalMood, MoodDetails) -> Unit,
    initialDetails: MoodDetails? = null,
    reminderContextLabel: String? = null,
    reduceAnimations: Boolean = false
) {
    val initialFamily = selectedMood.familyIndexV2()
    var progress by remember { mutableFloatStateOf(initialFamily.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    var lastHapticFamily by remember { mutableIntStateOf(initialFamily) }
    val isReminderMoodFlow = reminderContextLabel != null
    var step by remember(initialDetails?.loggedAt, reminderContextLabel) {
        mutableIntStateOf(if (isReminderMoodFlow || initialDetails != null) 1 else 0)
    }
    var scope by remember(initialDetails?.loggedAt, reminderContextLabel) {
        mutableStateOf(initialDetails?.scope ?: if (isReminderMoodFlow) MoodScope.MOMENT else null)
    }
    var descriptorKeys by remember(initialDetails?.loggedAt) {
        val restored = initialDetails?.descriptors?.toSet() ?: emptySet()
        mutableStateOf(if (selectedMood == JournalMood.FUCK_OFF) restored + "fuck_off" else restored)
    }
    var factorKeys by remember(initialDetails?.loggedAt) {
        mutableStateOf(initialDetails?.factors?.toSet() ?: emptySet())
    }
    var expandedFactorGroups by remember { mutableStateOf(emptySet<String>()) }
    val originalLoggedAt = remember(initialDetails?.loggedAt) { initialDetails?.loggedAt ?: System.currentTimeMillis() }
    val view = LocalView.current
    val context = LocalContext.current

    LaunchedEffect(selectedMood) {
        if (!isDragging) {
            val selectedFamily = selectedMood.familyIndexV2()
            progress = selectedFamily.toFloat()
            lastHapticFamily = selectedFamily
        }
    }

    val displayProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress,
        animationSpec = if (isDragging || reduceAnimations) snap() else tween(190),
        label = "moodV2Progress"
    )
    val nearestFamilyIndex = displayProgress.roundToInt().coerceIn(0, 4)
    val family = MoodFamiliesV2[nearestFamilyIndex]
    val ambientTarget = moodV2LerpColor(displayProgress)
    val ambient by animateColorAsState(
        targetValue = ambientTarget,
        animationSpec = if (reduceAnimations) snap() else tween(120),
        label = "moodV2Ambient"
    )
    val faceParams = faceParamsAtV2(displayProgress, if (isDragging) null else selectedMood)
    val opaqueAmbient = androidx.compose.ui.graphics.lerp(Color.Black, ambient, 0.42f)

    fun updateProgressFromX(x: Float, finish: Boolean) {
        val next = ((x / trackWidthPx.coerceAtLeast(1f)) * 4f).coerceIn(0f, 4f)
        progress = next
        val anchor = next.roundToInt().coerceIn(0, 4)
        if (anchor != lastHapticFamily) {
            lastHapticFamily = anchor
            RealHaptics.tick(view)
        }
        if (finish) {
            progress = anchor.toFloat()
            val targetFamily = MoodFamiliesV2[anchor]
            val validKeys = MoodDescriptorsV2[anchor].orEmpty().map { it.key }.toSet()
            descriptorKeys = descriptorKeys.intersect(validKeys)
            if (selectedMood !in targetFamily.moods) {
                onMoodSelected(targetFamily.defaultMood)
            }
        }
    }

    val activity = context as? Activity ?: (view.context as? Activity)
    val window = activity?.window
    val insetsController = remember(window, view) {
        window?.let { WindowCompat.getInsetsController(it, window.decorView) }
    }
    val isLightStatus = remember(opaqueAmbient) {
        val luminance = 0.2126f * opaqueAmbient.red + 0.7152f * opaqueAmbient.green + 0.0722f * opaqueAmbient.blue
        luminance > 0.42f
    }

    DisposableEffect(insetsController) {
        val prevLightStatus = insetsController?.isAppearanceLightStatusBars ?: false
        val prevLightNav = insetsController?.isAppearanceLightNavigationBars ?: false
        onDispose {
            insetsController?.isAppearanceLightStatusBars = prevLightStatus
            insetsController?.isAppearanceLightNavigationBars = prevLightNav
        }
    }
    LaunchedEffect(isLightStatus) {
        insetsController?.isAppearanceLightStatusBars = isLightStatus
        insetsController?.isAppearanceLightNavigationBars = false
    }

    fun moveBack() {
        if (step <= 0 || (isReminderMoodFlow && step <= 1)) onDismiss() else step -= 1
    }

    fun advance() {
        when (step) {
            0 -> if (scope != null) step = 1
            1 -> {
                val validKeys = MoodDescriptorsV2[nearestFamilyIndex].orEmpty().map { it.key }.toSet()
                descriptorKeys = descriptorKeys.intersect(validKeys)
                step = 2
            }
            else -> onSave(
                if (selectedMood == JournalMood.FUCK_OFF) JournalMood.BAD else selectedMood,
                MoodDetails(
                    scope = scope ?: MoodScope.DAILY,
                    descriptors = descriptorKeys.toList(),
                    // Energy is intentionally not asked in the current flow. Preserve an
                    // older value when editing an existing record, but new entries stay null.
                    energy = initialDetails?.energy,
                    factors = factorKeys.toList(),
                    loggedAt = originalLoggedAt
                )
            )
        }
    }

    val ctaEnabled = step != 0 || scope != null
    val ctaText = if (step >= 2) t("Kaydet") else t("İleri")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            opaqueAmbient,
                            Color(0xFF0B0B0D),
                            Color.Black
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(52.dp))

                when (step) {
                    0 -> {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = t("Bir iz bırak"),
                            color = Color(0xFFF4F4F6),
                            fontSize = 30.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = t("Neyi kaydetmek istiyorsun?"),
                            color = Color(0xFF9A9AA3),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(34.dp))

                        MoodScopeCardV2(
                            title = t("Anlık his"),
                            subtitle = t("Şu anda ya da belirli bir anda nasıl hissettiğin"),
                            selected = scope == MoodScope.MOMENT,
                            ambient = ambient,
                            onClick = {
                                scope = MoodScope.MOMENT
                                RealHaptics.tick(view)
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        MoodScopeCardV2(
                            title = t("Günün havası"),
                            subtitle = t("Bugünün genel hissi"),
                            selected = scope == MoodScope.DAILY,
                            ambient = ambient,
                            onClick = {
                                scope = MoodScope.DAILY
                                RealHaptics.tick(view)
                            }
                        )
                    }

                    1 -> {
                        Text(
                            text = when {
                                isReminderMoodFlow -> t("Bu REAL nasıl hissettirdi?")
                                scope == MoodScope.MOMENT -> t("Şu an içeride hava nasıl?")
                                else -> t("Bugünün havası nasıldı?")
                            },
                            color = Color(0xFFE0E0E5),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(22.dp))

                        val showFuckOffHand = nearestFamilyIndex == 0 && "fuck_off" in descriptorKeys
                        Crossfade(
                            targetState = showFuckOffHand,
                            animationSpec = if (reduceAnimations) snap() else tween(220),
                            label = "moodFaceFuckOff"
                        ) { showHand ->
                            if (showHand) {
                                Box(
                                    modifier = Modifier
                                        .size(216.dp)
                                        .semantics { contentDescription = t("F*ck off orta parmak işareti") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    ActionIcon(
                                        glyph = ActionGlyph.MoodFuckOff,
                                        modifier = Modifier.size(158.dp),
                                        color = Color(0xFFF5F5F7)
                                    )
                                }
                            } else {
                                MoodV2FaceCanvas(
                                    faceParams = faceParams,
                                    ambient = ambient,
                                    size = 216.dp,
                                    description = t("{0} mood yüzü", family.title)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = family.title.uppercase(),
                            color = ambient,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp
                        )

                        Spacer(Modifier.height(24.dp))

                        MoodSliderV2(
                            displayProgress = displayProgress,
                            nearestFamilyIndex = nearestFamilyIndex,
                            ambient = ambient,
                            onTrackWidth = { trackWidthPx = it },
                            onTap = { x ->
                                isDragging = false
                                updateProgressFromX(x, finish = true)
                            },
                            onDragStart = { x ->
                                isDragging = true
                                updateProgressFromX(x, finish = false)
                            },
                            onDragCancel = {
                                isDragging = false
                                updateProgressFromX((progress / 4f) * trackWidthPx, finish = true)
                            },
                            onDragEnd = {
                                isDragging = false
                                updateProgressFromX((progress / 4f) * trackWidthPx, finish = true)
                            },
                            onDrag = { dragAmount ->
                                val next = progress + (dragAmount / trackWidthPx.coerceAtLeast(1f)) * 4f
                                progress = next.coerceIn(0f, 4f)
                                val anchor = progress.roundToInt().coerceIn(0, 4)
                                if (anchor != lastHapticFamily) {
                                    lastHapticFamily = anchor
                                    RealHaptics.tick(view)
                                }
                            }
                        )

                        Spacer(Modifier.height(28.dp))

                        Text(
                            text = t("Biraz daha netleştir"),
                            color = Color(0xFFE6E6EB),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = t("Bu hissi en iyi anlatanları seç. En fazla 3 tane."),
                            color = Color(0xFF85858E),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(13.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            MoodDescriptorsV2[nearestFamilyIndex].orEmpty().forEach { option ->
                                val selected = option.key in descriptorKeys
                                MoodFlowChipV2(
                                    label = option.label,
                                    selected = selected,
                                    ambient = ambient,
                                    onClick = {
                                        descriptorKeys = when {
                                            selected -> descriptorKeys - option.key
                                            descriptorKeys.size < 3 -> descriptorKeys + option.key
                                            else -> descriptorKeys
                                        }
                                        RealHaptics.tick(view)
                                    }
                                )
                            }
                        }

                        val summaryParts = descriptorKeys.take(3).map { moodDescriptorLabelV2(it) }
                        if (summaryParts.isNotEmpty()) {
                            Spacer(Modifier.height(13.dp))
                            Text(
                                text = summaryParts.joinToString(" · "),
                                color = ambient.copy(alpha = 0.88f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    else -> {
                        MoodFlowSummaryV2(
                            mood = selectedMood,
                            detailsScope = scope,
                            ambient = ambient,
                            descriptorKeys = descriptorKeys,
                            contextLabel = if (isReminderMoodFlow) reminderContextLabel else null
                        )
                        Spacer(Modifier.height(22.dp))
                        Text(
                            text = t("Bunu en çok ne etkiledi?"),
                            color = Color(0xFFF1F1F4),
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = t("Hissi değil, kaynağını seç. Birden fazla olabilir."),
                            color = Color(0xFF85858E),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(18.dp))

                        MoodFactorGroupsV2.forEachIndexed { index, group ->
                            if (index > 0) Spacer(Modifier.height(20.dp))
                            Text(
                                text = group.title,
                                color = Color(0xFF777780),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            val expanded = group.title in expandedFactorGroups
                            val compactCount = 9
                            val baseOptions = group.options.take(compactCount)
                            val selectedExtras = group.options.drop(compactCount).filter { it.key in factorKeys }
                            val visibleOptions = if (expanded) group.options else (baseOptions + selectedExtras).distinctBy { it.key }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                visibleOptions.forEach { option ->
                                    val selected = option.key in factorKeys
                                    MoodFlowChipV2(
                                        label = option.label,
                                        selected = selected,
                                        ambient = ambient,
                                        onClick = {
                                            factorKeys = if (selected) factorKeys - option.key else factorKeys + option.key
                                            RealHaptics.tick(view)
                                        }
                                    )
                                }
                                if (group.options.size > compactCount) {
                                    MoodExpansionChipV2(
                                        label = if (expanded) t("Daha az") else t("+ {0} daha", group.options.size - compactCount),
                                        ambient = ambient,
                                        onClick = {
                                            expandedFactorGroups = if (expanded) {
                                                expandedFactorGroups - group.title
                                            } else {
                                                expandedFactorGroups + group.title
                                            }
                                            RealHaptics.tick(view)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(108.dp))
            }

            RealBackButton({ moveBack() }, Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 2.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 2.dp)
            ) {
                Text("×", color = Color(0xFFE0E0E5), fontSize = 28.sp, lineHeight = 28.sp)
            }

            val ctaShape = RoundedCornerShape(22.dp)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 18.dp)
                    .clip(ctaShape)
                    .background(if (ctaEnabled) Color(0xFFF3F3F5) else Color(0xFF4A4A50))
                    .clickable(enabled = ctaEnabled, onClick = { advance() })
                    .height(42.dp)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    ctaText,
                    color = if (ctaEnabled) Color(0xFF111114) else Color(0xFF9A9AA2),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MoodScopeCardV2(
    title: String,
    subtitle: String,
    selected: Boolean,
    ambient: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) ambient.copy(alpha = 0.22f) else Color(0xFF18181C))
            .border(1.dp, if (selected) ambient.copy(alpha = 0.72f) else Color(0xFF303036), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) ambient else Color(0xFF777780), CircleShape)
                .padding(4.dp)
                .background(if (selected) ambient else Color.Transparent, CircleShape)
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFFF4F4F6), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color(0xFF92929B), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun MoodFlowChipV2(
    label: String,
    selected: Boolean,
    ambient: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(99.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) ambient.copy(alpha = 0.24f) else Color(0xFF1D1D21))
            .border(0.8.dp, if (selected) ambient.copy(alpha = 0.72f) else Color(0xFF303037), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFFF5F5F7) else Color(0xFFB1B1BA),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun MoodExpansionChipV2(
    label: String,
    ambient: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(99.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Color(0xFF151519))
            .border(0.8.dp, ambient.copy(alpha = 0.28f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ambient.copy(alpha = 0.90f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MoodFlowSummaryV2(
    mood: JournalMood,
    detailsScope: MoodScope?,
    ambient: Color,
    descriptorKeys: Set<String>,
    contextLabel: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if ("fuck_off" in descriptorKeys) {
            ActionIcon(ActionGlyph.MoodFuckOff, Modifier.size(72.dp), Color(0xFFF5F5F7))
        } else {
            MoodV2Face(mood = mood, size = 78.dp, tint = ambient)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = contextLabel?.takeIf { it.isNotBlank() } ?: detailsScope?.label ?: t("Mood"),
                color = Color(0xFF85858E),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = MoodFamiliesV2[mood.familyIndexV2()].title,
                color = Color(0xFFF5F5F7),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
            val summaryParts = descriptorKeys.take(3).map { moodDescriptorLabelV2(it) }
            if (summaryParts.isNotEmpty()) {
                Text(
                    text = summaryParts.joinToString(" · "),
                    color = ambient.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun MoodV2FaceCanvas(
    faceParams: FaceV2Params,
    ambient: Color,
    size: androidx.compose.ui.unit.Dp,
    description: String
) {
    Canvas(
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = description }
    ) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.052f
        val eyeW = w * faceParams.eyeWidth
        val eyeH = h * faceParams.eyeHeight
        val eyeY = h * faceParams.eyeY
        val spacing = w * faceParams.eyeSpacing
        val centerX = w / 2f
        val ink = Color(0xFFF5F5F7)
        val softInk = ambient.copy(alpha = 0.96f)

        fun drawEye(cx: Float) {
            drawRoundRect(
                color = ink,
                topLeft = Offset(cx - eyeW / 2f, eyeY - eyeH / 2f),
                size = Size(eyeW, eyeH),
                cornerRadius = CornerRadius(eyeH / 2f, eyeH / 2f)
            )
        }
        drawEye(centerX - spacing / 2f)
        drawEye(centerX + spacing / 2f)

        if (faceParams.browStrength > 0.05f) {
            val browY = eyeY - eyeH * 1.15f
            val browHalf = eyeW * 0.52f
            val tilt = eyeH * 0.75f * faceParams.browStrength
            drawLine(softInk, Offset(centerX - spacing / 2f - browHalf, browY - tilt), Offset(centerX - spacing / 2f + browHalf, browY + tilt), strokeWidth = stroke * 0.55f)
            drawLine(softInk, Offset(centerX + spacing / 2f - browHalf, browY + tilt), Offset(centerX + spacing / 2f + browHalf, browY - tilt), strokeWidth = stroke * 0.55f)
        }

        val mouthCenterY = h * faceParams.mouthY
        val mouthHalfW = w * faceParams.mouthWidth / 2f
        val curve = h * 0.12f * faceParams.mouthCurve
        val mouth = Path().apply {
            moveTo(centerX - mouthHalfW, mouthCenterY)
            cubicTo(
                centerX - mouthHalfW * 0.45f, mouthCenterY + curve,
                centerX + mouthHalfW * 0.45f, mouthCenterY + curve,
                centerX + mouthHalfW, mouthCenterY
            )
        }
        drawPath(
            path = mouth,
            color = ink,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke * faceParams.mouthStrokeScale, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

@Composable
private fun MoodSliderV2(
    displayProgress: Float,
    nearestFamilyIndex: Int,
    ambient: Color,
    onTrackWidth: (Float) -> Unit,
    onTap: (Float) -> Unit,
    onDragStart: (Float) -> Unit,
    onDragCancel: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (Float) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .onSizeChanged { onTrackWidth(it.width.toFloat().coerceAtLeast(1f)) }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onTap(offset.x) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onDragStart(offset.x) },
                    onDragCancel = onDragCancel,
                    onDragEnd = onDragEnd,
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                )
            }
    ) {
        val thumbSize = 28.dp
        val usableWidth = maxWidth - thumbSize
        val thumbX = usableWidth * (displayProgress / 4f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(99.dp))
                .background(Brush.horizontalGradient(MoodFamiliesV2.map { it.tint.copy(alpha = 0.68f) }))
        )
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MoodFamiliesV2.forEachIndexed { index, anchorFamily ->
                val active = index == nearestFamilyIndex
                Box(
                    modifier = Modifier
                        .size(if (active) 8.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (active) Color.White else anchorFamily.tint.copy(alpha = 0.72f))
                )
            }
        }
        Box(
            modifier = Modifier
                .offset(x = thumbX)
                .size(thumbSize)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(Color(0xFFF4F4F6))
                .border(3.dp, ambient.copy(alpha = 0.80f), CircleShape)
        )
    }
}
