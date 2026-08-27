package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppBrandLogo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MpinMode {
    UNLOCK,
    SETUP
}

@Composable
fun MpinScreen(
    mode: MpinMode = MpinMode.UNLOCK,
    userName: String? = null,
    userEmail: String? = null,
    isBiometricAvailable: Boolean = true,
    onVerifyPin: (pin: String) -> Boolean,
    onSaveNewPin: (pin: String, enableBiometric: Boolean) -> Unit,
    onRequestBiometric: () -> Unit = {},
    onSwitchToPasswordLogin: () -> Unit,
    onUnlockSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var setupFirstPin by remember { mutableStateOf("") }
    var isConfirmingSetup by remember { mutableStateOf(false) }
    var enableBiometricInSetup by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isShaking by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }

    // Auto-prompt biometric when in UNLOCK mode on initial open
    LaunchedEffect(Unit) {
        if (mode == MpinMode.UNLOCK && isBiometricAvailable) {
            delay(350)
            onRequestBiometric()
        }
    }

    val shakeOffset by animateFloatAsState(
        targetValue = if (isShaking) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh),
        finishedListener = { isShaking = false },
        label = "shake"
    )

    fun handleKeyClick(digit: String) {
        if (isProcessing) return
        if (enteredPin.length < 4) {
            errorMessage = null
            val newPin = enteredPin + digit
            enteredPin = newPin

            if (newPin.length == 4) {
                isProcessing = true
                coroutineScope.launch {
                    // Delay slightly so the user visually sees the 4th dot animate to filled
                    delay(220)

                    if (mode == MpinMode.UNLOCK) {
                        val isValid = onVerifyPin(newPin)
                        if (isValid) {
                            onUnlockSuccess()
                        } else {
                            errorMessage = "Incorrect MPIN. Please try again."
                            isShaking = true
                            delay(350)
                            enteredPin = ""
                            isProcessing = false
                        }
                    } else {
                        // SETUP MODE
                        if (!isConfirmingSetup) {
                            setupFirstPin = newPin
                            isConfirmingSetup = true
                            enteredPin = ""
                            isProcessing = false
                        } else {
                            if (newPin == setupFirstPin) {
                                onSaveNewPin(newPin, enableBiometricInSetup)
                                Toast.makeText(context, "MPIN set successfully!", Toast.LENGTH_SHORT).show()
                                onUnlockSuccess()
                            } else {
                                errorMessage = "MPINs do not match. Try again."
                                isShaking = true
                                delay(400)
                                isConfirmingSetup = false
                                setupFirstPin = ""
                                enteredPin = ""
                                isProcessing = false
                            }
                        }
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (isProcessing) return
        if (enteredPin.isNotEmpty()) {
            errorMessage = null
            enteredPin = enteredPin.dropLast(1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val maxContentWidth = if (maxWidth > 500.dp) 400.dp else maxWidth

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = maxContentWidth)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    AppBrandLogo(size = 64.dp, isAnimated = true)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (mode == MpinMode.SETUP) {
                            if (isConfirmingSetup) "Confirm 4-Digit MPIN" else "Set Quick Access MPIN"
                        } else {
                            "Welcome Back${if (!userName.isNullOrBlank()) ", $userName" else ""}"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    if (!userEmail.isNullOrBlank() && mode == MpinMode.UNLOCK) {
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (mode == MpinMode.SETUP) {
                            if (isConfirmingSetup) "Re-enter the same 4 digits to confirm" else "Choose a 4-digit MPIN for instant unlock"
                        } else {
                            "Enter your 4-digit MPIN or use fingerprint"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // PIN Dot Indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .offset(x = if (isShaking) (shakeOffset * 10).dp else 0.dp)
                            .padding(vertical = 14.dp)
                    ) {
                        for (i in 0 until 4) {
                            val isFilled = i < enteredPin.length
                            val dotScale by animateFloatAsState(
                                targetValue = if (isFilled) 1.25f else 1.0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "dot_$i"
                            )

                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .scale(dotScale)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFilled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .border(
                                        width = if (isFilled) 0.dp else 2.dp,
                                        color = if (isFilled) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    // Error message
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    if (mode == MpinMode.SETUP && !isConfirmingSetup) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { enableBiometricInSetup = !enableBiometricInSetup }
                        ) {
                            Checkbox(
                                checked = enableBiometricInSetup,
                                onCheckedChange = { enableBiometricInSetup = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Enable Fingerprint / Biometric Unlock",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Keypad Section
                Column(
                    modifier = Modifier
                        .widthIn(max = maxContentWidth)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val keyRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    )

                    for (row in keyRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (digit in row) {
                                KeypadButton(
                                    text = digit,
                                    onClick = { handleKeyClick(digit) }
                                )
                            }
                        }
                    }

                    // Bottom Row: Biometric / Action | 0 | Backspace
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Key: Biometric in UNLOCK mode
                        if (mode == MpinMode.UNLOCK && isBiometricAvailable) {
                            KeypadIconButton(
                                icon = Icons.Default.Fingerprint,
                                contentDescription = "Fingerprint Unlock",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = onRequestBiometric
                            )
                        } else {
                            Spacer(modifier = Modifier.size(72.dp))
                        }

                        // Center: 0
                        KeypadButton(
                            text = "0",
                            onClick = { handleKeyClick("0") }
                        )

                        // Right Key: Backspace
                        KeypadIconButton(
                            icon = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = ::handleBackspace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Fallback Action (Switch to Password / Logout)
                TextButton(
                    onClick = onSwitchToPasswordLogin,
                    modifier = Modifier
                        .testTag("switch_to_password_button")
                        .padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockReset,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (mode == MpinMode.SETUP) "Sign Out & Return to Login" else "Use Password or Switch Account",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .testTag("keypad_button_$text"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun KeypadIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .testTag("keypad_action_${contentDescription.lowercase().replace(" ", "_")}"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
