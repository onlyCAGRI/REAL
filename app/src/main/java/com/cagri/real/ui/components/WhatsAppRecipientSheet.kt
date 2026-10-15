package com.cagri.real.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.data.normalizePhoneNumber
import kotlinx.coroutines.delay

@Composable
fun WhatsAppRecipientSheet(
    visible: Boolean,
    initialPhoneNumber: String? = null,
    onSaveRecipient: (String) -> Unit,
    onDismiss: () -> Unit,
    reduceAnimations: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    var phoneInput by remember(visible, initialPhoneNumber) {
        mutableStateOf(initialPhoneNumber ?: "")
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    LaunchedEffect(visible) {
        if (visible) {
            delay(150)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    val handleSave = {
        val normalized = normalizePhoneNumber(phoneInput)
        if (normalized != null) {
            onSaveRecipient(normalized)
        } else {
            com.cagri.real.ui.components.RealToast.show(context, "Lütfen geçerli bir telefon numarası girin (örn. 05xx xxx xx xx)")
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (reduceAnimations) EnterTransition.None else fadeIn(tween(250)),
        exit = if (reduceAnimations) ExitTransition.None else fadeOut(tween(200)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

            AnimatedVisibility(
                visible = visible,
                enter = if (reduceAnimations) EnterTransition.None
                else slideInVertically(
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = RealMotion.Emphasized
                    )
                ) { fullHeight -> fullHeight },
                exit = if (reduceAnimations) ExitTransition.None
                else slideOutVertically(
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = RealMotion.Exit
                    )
                ) { fullHeight -> fullHeight },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(sheetShape)
                        .background(Color(0xFF18181B))
                        .border(0.75.dp, Color.White.copy(alpha = 0.12f), sheetShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} 
                        )
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .navigationBarsPadding()
                ) {
                    
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .background(Color(0xFF76767D), RoundedCornerShape(2.dp))
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(Modifier.height(20.dp))

                    
                    Text(
                        text = "WhatsApp numaran",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )

                    Spacer(Modifier.height(8.dp))

                    
                    Text(
                        text = "Hatırlatıcılarını hangi WhatsApp numarasına göndereyim?",
                        color = Color(0xFFA4A4AD),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(18.dp))

                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF222226))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (phoneInput.isEmpty()) {
                            Text(
                                text = "+90 5xx xxx xx xx",
                                color = Color(0xFF6E6E78),
                                fontSize = 16.sp
                            )
                        }
                        BasicTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(Color(0xFF34D399)),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { handleSave() }
                            )
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(25.dp))
                            .background(Color.White)
                            .clickable(onClick = handleSave),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Kaydet",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Vazgeç",
                            color = Color(0xFFA4A4AD),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
