package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * Striking Gold & Amber Gradient Badge for PRO Users
 */
@Composable
fun ProBadge(
    modifier: Modifier = Modifier,
    isSmall: Boolean = false
) {
    Surface(
        modifier = modifier
            .testTag("pro_badge")
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFD700), Color(0xFFF59E0B), Color(0xFFD97706))
                ),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1B4B)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF7C2D12).copy(alpha = 0.8f),
                            Color(0xFFB45309).copy(alpha = 0.8f),
                            Color(0xFFD97706).copy(alpha = 0.9f)
                        )
                    )
                )
                .padding(
                    horizontal = if (isSmall) 6.dp else 10.dp,
                    vertical = if (isSmall) 2.dp else 4.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = "PRO",
                tint = Color(0xFFFFE066),
                modifier = Modifier.size(if (isSmall) 12.dp else 16.dp)
            )
            Text(
                text = "PRO",
                color = Color(0xFFFFFBEB),
                fontWeight = FontWeight.Black,
                fontSize = if (isSmall) 10.sp else 12.sp,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/**
 * Free Plan status badge with quick upgrade affordance
 */
@Composable
fun FreePlanBadge(
    onClickUpgrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClickUpgrade,
        modifier = modifier
            .testTag("free_plan_badge")
            .clip(RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "FREE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "Upgrade to Pro",
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * Limit Banner showing usage for Free users (10 prints / 10 conversions)
 */
@Composable
fun UsageLimitCard(
    isPro: Boolean,
    receivedPrints: Int,
    conversions: Int,
    onActivateProClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isPro) {
        // Pro User Status Banner
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("pro_status_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1B4B)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFD97706))
                ),
                width = 1.5.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFFD97706), Color(0xFFF59E0B))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Virtual PDF Printer PRO",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall
                            )
                            ProBadge(isSmall = true)
                        }
                        Text(
                            text = "Unlimited Prints & Conversions Active",
                            color = Color(0xFFFDE68A),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onActivateProClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF312E81),
                        contentColor = Color(0xFFFDE68A)
                    )
                ) {
                    Text("License", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        // Free User Usage & Limits Banner
        val remainingPrints = (10 - receivedPrints).coerceAtLeast(0)
        val remainingConversions = (10 - conversions).coerceAtLeast(0)
        val isAtLimit = remainingPrints == 0 || remainingConversions == 0

        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("free_usage_limit_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAtLimit) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = if (isAtLimit) Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
                else Brush.horizontalGradient(listOf(Color(0xFFF59E0B).copy(alpha = 0.5f), Color(0xFF38BDF8).copy(alpha = 0.5f))),
                width = 1.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isAtLimit) Icons.Default.Warning else Icons.Default.PieChart,
                            contentDescription = null,
                            tint = if (isAtLimit) MaterialTheme.colorScheme.error else Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Free Plan Limits",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    Button(
                        onClick = onActivateProClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Activate PRO",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Progress indicators for Prints and Conversions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Prints Limit Metric
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Prints Received", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$receivedPrints/10", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { (receivedPrints / 10f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (receivedPrints >= 10) MaterialTheme.colorScheme.error else Color(0xFF38BDF8),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    // Conversions Limit Metric
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Conversions", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$conversions/10", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { (conversions / 10f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (conversions >= 10) MaterialTheme.colorScheme.error else Color(0xFF10B981),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pro Activation & Key Entry Dialog
 */
@Composable
fun ProActivationDialog(
    isPro: Boolean,
    currentKey: String?,
    receivedPrints: Int,
    conversions: Int,
    onActivateWithKey: suspend (String) -> Result<String>,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var inputKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .testTag("pro_activation_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Crown / Star Header
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706), Color(0xFF7C2D12))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "PRO",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Virtual PDF Printer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        ProBadge()
                    }
                    Text(
                        text = if (isPro) "PRO License is Active & Unlimited" else "Unlock Unlimited Prints & Conversions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Features list
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProFeatureRow(
                            icon = Icons.Default.AllInclusive,
                            title = "Unlimited Print Files",
                            subtitle = "Free limit is 10 files. PRO has zero limits."
                        )
                        ProFeatureRow(
                            icon = Icons.Default.Bolt,
                            title = "Unlimited File Conversions",
                            subtitle = "Convert unlimited .ps / .eps to crystal PDF."
                        )
                        ProFeatureRow(
                            icon = Icons.Default.WorkspacePremium,
                            title = "Exclusive PRO Branding",
                            subtitle = "Virtual PDF Printer PRO interface & cloud backup."
                        )
                    }
                }

                if (isPro) {
                    // Already Pro view
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF065F46).copy(alpha = 0.15f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF059669))),
                            width = 1.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "PRO Account Active",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                            if (!currentKey.isNullOrBlank()) {
                                Text(
                                    text = "License Key: $currentKey",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Key Entry Form
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Enter Pro Activation Key",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = inputKey,
                            onValueChange = {
                                inputKey = it.uppercase()
                                errorMessage = null
                                successMessage = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pro_key_input"),
                            placeholder = { Text("e.g. PRO-XXXX-XXXX-XXXX") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrBlank()) {
                                            inputKey = clip.trim().uppercase()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste Key"
                                    )
                                }
                            },
                            singleLine = true,
                            isError = errorMessage != null,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (inputKey.isNotBlank() && !isLoading) {
                                        coroutineScope.launch {
                                            isLoading = true
                                            errorMessage = null
                                            val result = onActivateWithKey(inputKey)
                                            isLoading = false
                                            if (result.isSuccess) {
                                                successMessage = result.getOrNull()
                                            } else {
                                                errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Invalid key"
                                            }
                                        }
                                    }
                                }
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        AnimatedVisibility(visible = errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        AnimatedVisibility(visible = successMessage != null) {
                            Text(
                                text = successMessage ?: "",
                                color = Color(0xFF10B981),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    val result = onActivateWithKey(inputKey)
                                    isLoading = false
                                    if (result.isSuccess) {
                                        successMessage = result.getOrNull()
                                    } else {
                                        errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Invalid key"
                                    }
                                }
                            },
                            enabled = inputKey.isNotBlank() && !isLoading,
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("activate_pro_submit_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD97706),
                                contentColor = Color.White
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Activate PRO", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = "Need a Pro key? Contact your administrator to receive an activation key.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ProFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Color(0xFFD97706).copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(16.dp)
            )
        }
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
