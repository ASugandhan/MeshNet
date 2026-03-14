package com.meshnet.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshnet.app.crypto.CryptoManager
import com.meshnet.app.mesh.MapScreen
import com.meshnet.app.models.MeshPacket
import com.meshnet.app.models.MessagePriority
import com.meshnet.app.routing.NearbyPeer
import com.meshnet.app.ui.theme.MeshGreen
import com.meshnet.app.ui.theme.MeshOrange
import com.meshnet.app.ui.theme.MeshRed
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    meshActive: Boolean,
    peerCount: Int,
    balance: Float,
    myMeshNetId: String,
    peers: List<NearbyPeer>,
    receivedMessages: List<MeshPacket>,
    relayLog: List<String>,
    onComposeClick: () -> Unit,
    onSOSClick: () -> Unit,
    onChatSend: (String) -> Unit,
    onOpenFullChat: () -> Unit
) {
    var showSOSDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            MeshStatusBanner(meshActive, peerCount)
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Inbox") },
                    label = { Text("Inbox") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                    label = { Text("Map") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Group, contentDescription = "Network") },
                    label = { Text("Network") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.ListAlt, contentDescription = "Logs") },
                    label = { Text("Log") }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> DashboardTab(balance, myMeshNetId, receivedMessages, onComposeClick, { showSOSDialog = true }, onChatSend, onOpenFullChat)
                1 -> MapScreen(peers = peers, modifier = Modifier.fillMaxSize())
                2 -> NetworkTab(peers)
                3 -> LogsTab(relayLog)
            }
        }
    }

    if (showSOSDialog) {
        AlertDialog(
            onDismissRequest = { showSOSDialog = false },
            title = { Text("Emergency SOS") },
            text = { Text("This will broadcast your GPS location to all nearby devices. Use only in real emergencies.") },
            confirmButton = {
                Button(
                    onClick = { 
                        onSOSClick()
                        showSOSDialog = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeshRed)
                ) {
                    Text("SEND NOW")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSOSDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
fun DashboardTab(
    balance: Float, 
    myId: String, 
    receivedMessages: List<MeshPacket>,
    onComposeClick: () -> Unit, 
    onSOSClick: () -> Unit,
    onChatSend: (String) -> Unit,
    onOpenFullChat: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var chatInput by remember { mutableStateOf("") }
    var isInboxExpanded by remember { mutableStateOf(false) }
    
    // Filter out messages sent by this device for the "Inbox" view
    val incomingMessages = remember(receivedMessages, myId) {
        receivedMessages.filter { it.originalSenderId != myId }
    }
    
    val visibleMessages = if (isInboxExpanded) {
        incomingMessages
    } else {
        incomingMessages.take(5)
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Wallet Balance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "DATA CREDITS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f MB", balance),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Surface(
                            color = Color.Black.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                clipboardManager.setText(AnnotatedString(myId))
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ID: ${myId.take(12)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            // MESH CHAT Quick Reply Box
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenFullChat() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Local Mesh Chat", 
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Message to everyone...", fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { 
                                if (chatInput.isNotBlank()) {
                                    onChatSend(chatInput)
                                    chatInput = ""
                                }
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Inbox",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { isInboxExpanded = !isInboxExpanded }) {
                        Icon(
                            if (isInboxExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isInboxExpanded) "Collapse" else "Expand"
                        )
                    }
                }
                Text(
                    text = "${incomingMessages.size} Incoming",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (incomingMessages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No incoming messages", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(visibleMessages) { packet ->
                MessageItem(packet, myId)
            }
            
            if (incomingMessages.size > 5 && !isInboxExpanded) {
                item {
                    TextButton(
                        onClick = { isInboxExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show More (${incomingMessages.size - 5} others)")
                    }
                }
            } else if (isInboxExpanded && incomingMessages.size > 5) {
                item {
                    TextButton(
                        onClick = { isInboxExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show Less")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onComposeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text("Private Message (Direct)", style = MaterialTheme.typography.titleMedium)
            }
        }

        item {
            Button(
                onClick = onSOSClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MeshRed),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text(
                    "SEND SOS 🚨",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun MessageItem(packet: MeshPacket, myId: String) {
    val decryptedMessage = remember(packet.packetId) {
        CryptoManager.decryptMessage(packet.encryptedPayload, myId)
    }
    
    val timeString = remember(packet.createdAt) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(packet.createdAt))
    }

    val isGroupChat = packet.destinationHash == "MESH_CHAT"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGroupChat) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                            else MaterialTheme.colorScheme.surface
        ),
        border = if (isGroupChat) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) 
                 else null
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            packet.priority == MessagePriority.SOS -> MeshRed
                            isGroupChat -> MeshGreen
                            packet.priority == MessagePriority.URGENT -> MeshOrange
                            else -> Color.Gray
                        }
                    )
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isGroupChat) "Group Chat" else "From: ${packet.originalSenderId.take(8)}...",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isGroupChat) MeshGreen else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = decryptedMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (isGroupChat) {
                    Text(
                        text = "Sent by: ${packet.originalSenderId.take(6)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NetworkTab(peers: List<NearbyPeer>) {
    val clipboardManager = LocalClipboardManager.current
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Active Peers",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Devices currently in your mesh reach",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (peers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.WifiOff, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Searching for peers...", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(peers) { peer ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    peer.deviceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = MeshGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        "Connected",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MeshGreen
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Peer MeshNet ID (Hash)
                            val displayHash = if (peer.meshNetId == "unknown_hash") "Hash hidden or legacy peer" else peer.meshNetId
                            
                            Surface(
                                color = Color.Black.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    clipboardManager.setText(AnnotatedString(peer.meshNetId))
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = displayHash,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BatteryChargingFull, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${peer.batteryLevel}%", style = MaterialTheme.typography.labelSmall)
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Icon(Icons.Default.Speed, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${peer.speedKmh.toInt()} km/h", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogsTab(logs: List<String>) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Technical Logs",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Real-time mesh networking events",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No technical events logged yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                items(logs) { log ->
                    Text(
                        text = log,
                        color = Color(0xFF00FF00), // Matrix Green
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MeshStatusBanner(active: Boolean, peerCount: Int) {
    Surface(
        color = if (active) MeshGreen.copy(alpha = 0.1f) else MeshRed.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(vertical = 14.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (active) MeshGreen else MeshRed, RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (active) "Mesh Active" else "Mesh Offline",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) MeshGreen else MeshRed
                )
            }
            
            if (active) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MeshGreen
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$peerCount connected",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}
