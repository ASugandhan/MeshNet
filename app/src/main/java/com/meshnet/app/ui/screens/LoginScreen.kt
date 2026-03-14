package com.meshnet.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: (name: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isGeneratingIdentity by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = isGeneratingIdentity,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }, label = ""
            ) { generating ->
                if (!generating) {
                    SetupView(
                        name = name,
                        onNameChange = { name = it },
                        onStart = {
                            scope.launch {
                                isGeneratingIdentity = true
                                // Simulate identity generation steps
                                currentStep = 1
                                delay(1200)
                                currentStep = 2
                                delay(1500)
                                currentStep = 3
                                delay(800)
                                onLoginSuccess(name)
                            }
                        }
                    )
                } else {
                    GeneratingView(currentStep)
                }
            }
        }
    }
}

@Composable
fun SetupView(
    name: String,
    onNameChange: (String) -> Unit,
    onStart: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Logo Placeholder
        Surface(
            modifier = Modifier.size(90.dp),
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "MeshNet",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "India's Community-Powered Offline Network",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("How should we call you?") },
            placeholder = { Text("e.g. Rahul, Admin, etc.") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            enabled = name.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text("GENERATE IDENTITY", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                IdentityFeature(Icons.Default.Security, "End-to-End Encrypted")
                Spacer(modifier = Modifier.height(8.dp))
                IdentityFeature(Icons.Default.Fingerprint, "Decentralized ID")
            }
        }
    }
}

@Composable
fun GeneratingView(step: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 6.dp,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        val statusText = when (step) {
            1 -> "Generating RSA-2048 Key Pair..."
            2 -> "Securing Identity in Keystore..."
            3 -> "Creating MeshNet ID..."
            else -> "Finalizing..."
        }
        
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "This process creates your unique digital address. No phone number or internet required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun IdentityFeature(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
