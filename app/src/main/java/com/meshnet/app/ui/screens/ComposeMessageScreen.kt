package com.meshnet.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshnet.app.models.MessagePriority
import com.meshnet.app.ui.theme.MeshGreen
import com.meshnet.app.ui.theme.MeshOrange
import com.meshnet.app.ui.theme.MeshRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeMessageScreen(
    onBack: () -> Unit,
    onSend: (recipientId: String, message: String, priority: MessagePriority) -> Unit
) {
    var recipientId by remember { mutableStateOf("") }
    var messageContent by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(MessagePriority.NORMAL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "New Message", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            OutlinedTextField(
                value = recipientId,
                onValueChange = { recipientId = it },
                label = { Text("Recipient Device ID") },
                placeholder = { Text("Paste hash or enter ID") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = messageContent,
                onValueChange = { messageContent = it },
                label = { Text("Message Content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = 5,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Delivery Priority", 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PriorityButton(
                    label = "Normal",
                    selected = selectedPriority == MessagePriority.NORMAL,
                    color = Color.Gray,
                    onClick = { selectedPriority = MessagePriority.NORMAL },
                    modifier = Modifier.weight(1f)
                )
                PriorityButton(
                    label = "Urgent",
                    selected = selectedPriority == MessagePriority.URGENT,
                    color = MeshOrange,
                    onClick = { selectedPriority = MessagePriority.URGENT },
                    modifier = Modifier.weight(1f)
                )
                PriorityButton(
                    label = "SOS",
                    selected = selectedPriority == MessagePriority.SOS,
                    color = MeshRed,
                    onClick = { selectedPriority = MessagePriority.SOS },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Estimated Cost",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val costText = when (selectedPriority) {
                        MessagePriority.NORMAL -> "1.2 MB"
                        MessagePriority.URGENT -> "3.6 MB"
                        MessagePriority.SOS -> "FREE ✅"
                    }
                    Text(
                        text = costText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedPriority == MessagePriority.SOS) MeshGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onSend(recipientId, messageContent, selectedPriority) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = recipientId.isNotBlank() && messageContent.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text("Send Securely", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun PriorityButton(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) color else color.copy(alpha = 0.1f),
            contentColor = if (selected) Color.White else color
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            label, 
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
