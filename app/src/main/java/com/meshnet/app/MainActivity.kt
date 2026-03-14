package com.meshnet.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.meshnet.app.crypto.CryptoManager
import com.meshnet.app.data.WalletManager
import com.meshnet.app.mesh.MeshService
import com.meshnet.app.mesh.MeshViewModel
import com.meshnet.app.models.MessagePriority
import com.meshnet.app.ui.screens.ComposeMessageScreen
import com.meshnet.app.ui.screens.HomeScreen
import com.meshnet.app.ui.screens.LoginScreen
import com.meshnet.app.ui.screens.MeshChatScreen
import com.meshnet.app.ui.theme.MeshNetTheme

class MainActivity : ComponentActivity() {

    private lateinit var walletManager: WalletManager
    private val meshViewModel: MeshViewModel by viewModels()
    
    private var meshService: MeshService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MeshService.MeshBinder
            meshService = binder.getService()
            meshViewModel.setMeshService(meshService)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
            meshViewModel.setMeshService(null)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startAndBindMeshService()
        } else {
            Toast.makeText(this, "Permissions required for MeshNet to function", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        walletManager = WalletManager(this)
        
        val prefs = getSharedPreferences("meshnet_prefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("display_name", null)

        setContent {
            MeshNetTheme {
                var displayName by remember { mutableStateOf(savedName) }
                var currentScreen by remember { mutableStateOf("home") }
                
                val balance by remember { mutableStateOf(walletManager.getBalance()) }
                val peers by meshViewModel.connectedPeers.collectAsState()
                val receivedMessages by meshViewModel.receivedMessages.collectAsState()
                val relayLog by meshViewModel.relayLog.collectAsState()
                var myMeshNetId by remember { mutableStateOf(CryptoManager.getMeshNetId(this@MainActivity)) }

                if (displayName == null || !CryptoManager.hasIdentity(this@MainActivity)) {
                    LoginScreen(onLoginSuccess = { name ->
                        val newId = CryptoManager.generateIdentity(this@MainActivity)
                        prefs.edit().putString("display_name", name).apply()
                        displayName = name
                        myMeshNetId = newId
                        checkAndRequestPermissions()
                    })
                } else {
                    LaunchedEffect(Unit) {
                        checkAndRequestPermissions()
                    }

                    when (currentScreen) {
                        "home" -> HomeScreen(
                            meshActive = peers.isNotEmpty(),
                            peerCount = peers.size,
                            balance = balance,
                            myMeshNetId = myMeshNetId ?: "Unknown",
                            peers = peers,
                            receivedMessages = receivedMessages,
                            relayLog = relayLog,
                            onComposeClick = { currentScreen = "compose" },
                            onSOSClick = {
                                val loc = getLastLocation()
                                meshViewModel.sendMessage("", "EMERGENCY SOS: Help Required!", MessagePriority.SOS, loc?.latitude, loc?.longitude)
                                Toast.makeText(this@MainActivity, "SOS Broadcasted!", Toast.LENGTH_SHORT).show()
                            },
                            onChatSend = { message ->
                                meshViewModel.sendMessage("MESH_CHAT", message, MessagePriority.NORMAL)
                            },
                            onOpenFullChat = { currentScreen = "chat" }
                        )
                        "compose" -> ComposeMessageScreen(
                            onBack = { currentScreen = "home" },
                            onSend = { recipientId, message, priority ->
                                handleSendMessage(recipientId, message, priority)
                                currentScreen = "home"
                            }
                        )
                        "chat" -> MeshChatScreen(
                            myId = myMeshNetId ?: "Unknown",
                            messages = receivedMessages,
                            onBack = { currentScreen = "home" },
                            onSendMessage = { message ->
                                meshViewModel.sendMessage("MESH_CHAT", message, MessagePriority.NORMAL)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun handleSendMessage(recipientId: String, message: String, priority: MessagePriority) {
        if (walletManager.deductForMessage(priority)) {
            val loc = if (priority == MessagePriority.SOS) getLastLocation() else null
            meshViewModel.sendMessage(recipientId, message, priority, loc?.latitude, loc?.longitude)
            Toast.makeText(this, "Message queued for delivery", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Insufficient credits!", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation(): Location? {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isEmpty()) {
            startAndBindMeshService()
        } else {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    private fun startAndBindMeshService() {
        MeshService.start(this)
        Intent(this, MeshService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        if (meshService != null) {
            unbindService(serviceConnection)
            meshService = null
        }
        super.onDestroy()
    }
}
