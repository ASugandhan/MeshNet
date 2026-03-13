package com.meshnet.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.meshnet.app.crypto.CryptoManager
import com.meshnet.app.data.WalletManager
import com.meshnet.app.mesh.MeshService
import com.meshnet.app.models.MessagePriority
import com.meshnet.app.ui.screens.ComposeMessageScreen
import com.meshnet.app.ui.screens.HomeScreen
import com.meshnet.app.ui.screens.LoginScreen
import com.meshnet.app.ui.theme.MeshNetTheme

class MainActivity : ComponentActivity() {

    private lateinit var walletManager: WalletManager
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
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
                var balance by remember { mutableStateOf(walletManager.getBalance()) }

                if (displayName == null) {
                    LoginScreen(onLoginSuccess = { name ->
                        prefs.edit().putString("display_name", name).apply()
                        displayName = name
                        checkAndRequestPermissions()
                    })
                } else {
                    // Start mesh if we have name and permissions
                    LaunchedEffect(Unit) {
                        checkAndRequestPermissions()
                    }

                    when (currentScreen) {
                        "home" -> HomeScreen(
                            meshActive = true, // Placeholder
                            peerCount = 0,    // Placeholder
                            balance = balance,
                            onComposeClick = { currentScreen = "compose" },
                            onSOSClick = {
                                Toast.makeText(this@MainActivity, "SOS Sent!", Toast.LENGTH_SHORT).show()
                            }
                        )
                        "compose" -> ComposeMessageScreen(
                            onBack = { currentScreen = "home" },
                            onSend = { recipientId, message, priority ->
                                handleSendMessage(recipientId, message, priority)
                                balance = walletManager.getBalance()
                                currentScreen = "home"
                            }
                        )
                    }
                }
            }
        }
    }

    private fun handleSendMessage(recipientId: String, message: String, priority: MessagePriority) {
        if (walletManager.deductForMessage(priority)) {
            val deviceId = CryptoManager.generateOrLoadDeviceId(this)
            val recipientHash = CryptoManager.hashDestination(recipientId)
            val encrypted = CryptoManager.encryptMessage(message, recipientHash)
            
            // In a real implementation, we'd insert into DB and the Router/Service would pick it up
            Toast.makeText(this, "Message queued for delivery", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Insufficient credits!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
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
            MeshService.start(this)
        } else {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }
}
