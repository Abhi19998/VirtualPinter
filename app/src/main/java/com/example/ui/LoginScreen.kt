package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AuthResult
import com.example.ui.components.AppBrandLogo
import kotlinx.coroutines.launch

enum class AuthMode {
    SIGN_IN,
    SIGN_UP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: PrinterViewModel,
    onLoginSuccess: (email: String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }

    val isNameValid = name.trim().isNotBlank()
    val isEmailValid = email.trim().isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val isPasswordValid = password.length >= 6
    val isConfirmPasswordValid = confirmPassword.isNotBlank() && password == confirmPassword

    val isFormValid = if (authMode == AuthMode.SIGN_IN) {
        isEmailValid && isPasswordValid
    } else {
        isNameValid && isEmailValid && isPasswordValid && isConfirmPasswordValid
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            val maxWidth = if (maxWidth > 500.dp) 460.dp else maxWidth

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Brand Logo & Heading
                AppBrandLogo(size = 72.dp, isAnimated = true)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Virtual PDF Printer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = if (authMode == AuthMode.SIGN_IN)
                        "Sign in to sync print jobs & access cloud storage"
                    else
                        "Create your account to start printing & syncing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Mode Selector (Sign In vs Sign Up)
                Surface(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    authMode = AuthMode.SIGN_IN
                                    errorMessage = null
                                    hasAttemptedSubmit = false
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = if (authMode == AuthMode.SIGN_IN)
                                MaterialTheme.colorScheme.surface
                            else
                                Color.Transparent,
                            tonalElevation = if (authMode == AuthMode.SIGN_IN) 2.dp else 0.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sign In",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (authMode == AuthMode.SIGN_IN) FontWeight.Bold else FontWeight.Normal,
                                    color = if (authMode == AuthMode.SIGN_IN)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    authMode = AuthMode.SIGN_UP
                                    errorMessage = null
                                    hasAttemptedSubmit = false
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = if (authMode == AuthMode.SIGN_UP)
                                MaterialTheme.colorScheme.surface
                            else
                                Color.Transparent,
                            tonalElevation = if (authMode == AuthMode.SIGN_UP) 2.dp else 0.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Create Account",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (authMode == AuthMode.SIGN_UP) FontWeight.Bold else FontWeight.Normal,
                                    color = if (authMode == AuthMode.SIGN_UP)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mandatory notice for Sign Up
                AnimatedVisibility(visible = authMode == AuthMode.SIGN_UP) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "All fields marked with (*) are mandatory.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Error Message if any
                AnimatedVisibility(visible = errorMessage != null) {
                    errorMessage?.let { error ->
                        Surface(
                            modifier = Modifier
                                .widthIn(max = maxWidth)
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Form Fields Container
                Card(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Full Name (Sign Up only - Mandatory)
                        AnimatedVisibility(visible = authMode == AuthMode.SIGN_UP) {
                            val isNameError = hasAttemptedSubmit && name.trim().isBlank()
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    errorMessage = null
                                },
                                label = { Text("Full Name *") },
                                placeholder = { Text("e.g. John Doe") },
                                isError = isNameError,
                                supportingText = if (isNameError) {
                                    { Text("Full Name is mandatory", color = MaterialTheme.colorScheme.error) }
                                } else null,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isNameError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("name_input_field"),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                )
                            )
                        }

                        // Email Field (Mandatory)
                        val isEmailError = hasAttemptedSubmit && (!isEmailValid || email.trim().isBlank())
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                errorMessage = null
                            },
                            label = { Text(if (authMode == AuthMode.SIGN_UP) "Email Address *" else "Email Address") },
                            placeholder = { Text("name@example.com") },
                            isError = isEmailError,
                            supportingText = if (isEmailError) {
                                {
                                    Text(
                                        text = if (email.trim().isBlank()) "Email Address is mandatory" else "Please enter a valid email address",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else null,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = if (isEmailError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input_field"),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )

                        // Password Field (Mandatory)
                        val isPasswordError = hasAttemptedSubmit && (!isPasswordValid || password.isBlank())
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMessage = null
                            },
                            label = { Text(if (authMode == AuthMode.SIGN_UP) "Password *" else "Password") },
                            placeholder = { Text("At least 6 characters") },
                            isError = isPasswordError,
                            supportingText = if (isPasswordError) {
                                {
                                    Text(
                                        text = if (password.isBlank()) "Password is mandatory" else "Password must be at least 6 characters",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else if (authMode == AuthMode.SIGN_UP) {
                                { Text("At least 6 characters required", style = MaterialTheme.typography.bodySmall) }
                            } else null,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isPasswordError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input_field"),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (authMode == AuthMode.SIGN_UP) ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                onDone = { focusManager.clearFocus() }
                            )
                        )

                        // Confirm Password Field (Sign Up only - Mandatory)
                        AnimatedVisibility(visible = authMode == AuthMode.SIGN_UP) {
                            val isConfirmError = hasAttemptedSubmit && (!isConfirmPasswordValid || confirmPassword.isBlank())
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    errorMessage = null
                                },
                                label = { Text("Confirm Password *") },
                                placeholder = { Text("Re-enter password") },
                                isError = isConfirmError,
                                supportingText = if (isConfirmError) {
                                    {
                                        Text(
                                            text = if (confirmPassword.isBlank()) "Confirm Password is mandatory" else "Passwords do not match",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else null,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isConfirmError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_password_input_field"),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                )
                            )
                        }

                        // Remember Me & Forgot Password
                        if (authMode == AuthMode.SIGN_IN) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { rememberMe = !rememberMe }
                                ) {
                                    Checkbox(
                                        checked = rememberMe,
                                        onCheckedChange = { rememberMe = it },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Remember me",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TextButton(
                                    onClick = { 
                                        val targetEmail = "abhi741762@gmail.com"
                                        val subject = "Password Reset Request - Virtual PDF Printer"
                                        val userEmailText = if (email.isNotBlank()) email.trim() else "[Enter your registered email ID here]"
                                        val body = "Hello Admin / Support,\n\nI forgot my password for the Virtual PDF Printer app. Please release a new password for me.\n\nAccount Details:\n- User Email: $userEmailText\n- App: Virtual PDF Printer\n\nThank you."

                                        val uriString = "mailto:$targetEmail?subject=${android.net.Uri.encode(subject)}&body=${android.net.Uri.encode(body)}"
                                        val mailUri = android.net.Uri.parse(uriString)

                                        val gmailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO, mailUri).apply {
                                            setPackage("com.google.android.gm")
                                            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(targetEmail))
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                                            putExtra(android.content.Intent.EXTRA_TEXT, body)
                                        }

                                        val genericIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO, mailUri).apply {
                                            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(targetEmail))
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                                            putExtra(android.content.Intent.EXTRA_TEXT, body)
                                        }

                                        try {
                                            context.startActivity(gmailIntent)
                                        } catch (e: Exception) {
                                            try {
                                                context.startActivity(genericIntent)
                                            } catch (e2: Exception) {
                                                try {
                                                    val chooser = android.content.Intent.createChooser(genericIntent, "Send Password Reset Email")
                                                    context.startActivity(chooser)
                                                } catch (e3: Exception) {
                                                    Toast.makeText(context, "No email app found on this device.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = "Forgot password?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Submit Button
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                hasAttemptedSubmit = true

                                if (authMode == AuthMode.SIGN_UP) {
                                    when {
                                        name.trim().isBlank() -> {
                                            errorMessage = "Full Name is mandatory."
                                            return@Button
                                        }
                                        email.trim().isBlank() -> {
                                            errorMessage = "Email Address is mandatory."
                                            return@Button
                                        }
                                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> {
                                            errorMessage = "Please enter a valid email address."
                                            return@Button
                                        }
                                        password.isBlank() -> {
                                            errorMessage = "Password is mandatory."
                                            return@Button
                                        }
                                        password.length < 6 -> {
                                            errorMessage = "Password must be at least 6 characters."
                                            return@Button
                                        }
                                        confirmPassword.isBlank() -> {
                                            errorMessage = "Confirm Password is mandatory."
                                            return@Button
                                        }
                                        password != confirmPassword -> {
                                            errorMessage = "Passwords do not match."
                                            return@Button
                                        }
                                    }
                                } else {
                                    when {
                                        email.trim().isBlank() -> {
                                            errorMessage = "Email Address is mandatory."
                                            return@Button
                                        }
                                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> {
                                            errorMessage = "Please enter a valid email address."
                                            return@Button
                                        }
                                        password.isBlank() -> {
                                            errorMessage = "Password is mandatory."
                                            return@Button
                                        }
                                        password.length < 6 -> {
                                            errorMessage = "Password must be at least 6 characters."
                                            return@Button
                                        }
                                    }
                                }

                                isLoading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val result = if (authMode == AuthMode.SIGN_IN) {
                                        viewModel.signInWithEmail(email.trim(), password)
                                    } else {
                                        viewModel.signUpWithEmail(name.trim(), email.trim(), password)
                                    }
                                    isLoading = false
                                    when (result) {
                                        is AuthResult.Success -> {
                                            Toast.makeText(
                                                context,
                                                if (authMode == AuthMode.SIGN_IN) "Welcome back, ${result.data}!" else "Account created successfully!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onLoginSuccess(result.data)
                                        }
                                        is AuthResult.Error -> {
                                            errorMessage = result.message
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_submit_button"),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    text = if (authMode == AuthMode.SIGN_IN) "Sign In" else "Create Account",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (onBack != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("skip_to_dashboard_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = "Back",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Security & Privacy note
                Row(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Your local print jobs and files remain stored securely on-device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
