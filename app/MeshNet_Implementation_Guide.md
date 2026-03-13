# MeshNet — Complete Android Implementation Guide
### For AI-assisted development (Gemini / Cursor / Copilot)

---

## CONTEXT — READ THIS FIRST

You are building **MeshNet** — an Android app that creates an offline mesh communication network using vehicles and phones as relay nodes. No internet required for core functionality.

**Core concept:**
- Phones connect directly via WiFi Direct (no tower, no internet)
- Messages are duplicated and sprayed to nearby vehicles
- Vehicles physically carry packets toward signal zones
- When signal is found — packet delivered, kill signal sent to delete all copies
- Users earn/spend data credits for sending and relaying

**Tech Stack:**
- Language: Kotlin
- UI: Jetpack Compose
- P2P: Google Nearby Connections API
- Local DB: Room Database
- Background: WorkManager
- Maps: OSMDroid (offline)
- Encryption: AES-256-GCM + Android Keystore
- Cloud sync: Firebase (wallet reconciliation only)

**Package: com.meshnet.app**

---

## ALREADY COMPLETED — DO NOT REGENERATE

### 1. models/MeshPacket.kt ✅

```kotlin
package com.meshnet.app.models

import java.util.UUID

enum class MessagePriority {
    NORMAL,   // 5 copies, 1 hop, 24h expiry, 1x cost
    URGENT,   // 15 copies, 3 hops, 6h expiry, 3x cost
    SOS       // 25 copies, unlimited, 72h expiry, FREE
}

enum class PacketStatus {
    QUEUED, IN_TRANSIT, DELIVERED, EXPIRED, FAILED
}

data class MeshPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val originalSenderId: String = "",
    val destinationHash: String = "",
    val priority: MessagePriority = MessagePriority.NORMAL,
    val sprayLimit: Int = 5,
    val sprayRemaining: Int = 5,
    val hopCount: Int = 0,
    val maxHops: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000L),
    val encryptedPayload: String = "",
    val isSOSMessage: Boolean = false,
    val status: PacketStatus = PacketStatus.QUEUED,
    val deliveredAt: Long? = null,
    val senderLatitude: Double? = null,
    val senderLongitude: Double? = null,
) {
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    fun canForward(): Boolean = sprayRemaining > 0 && hopCount < maxHops
    fun createRelayCopy(): MeshPacket = this.copy(
        sprayRemaining = sprayRemaining - 1,
        hopCount = hopCount + 1,
        status = PacketStatus.IN_TRANSIT
    )
    fun markDelivered(): MeshPacket = this.copy(
        status = PacketStatus.DELIVERED,
        deliveredAt = System.currentTimeMillis()
    )

    companion object {
        fun createNormal(senderId: String, destinationHash: String, encryptedPayload: String) = MeshPacket(
            originalSenderId = senderId, destinationHash = destinationHash,
            priority = MessagePriority.NORMAL, sprayLimit = 5, sprayRemaining = 5,
            maxHops = 1, expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000L),
            encryptedPayload = encryptedPayload, isSOSMessage = false
        )
        fun createUrgent(senderId: String, destinationHash: String, encryptedPayload: String) = MeshPacket(
            originalSenderId = senderId, destinationHash = destinationHash,
            priority = MessagePriority.URGENT, sprayLimit = 15, sprayRemaining = 15,
            maxHops = 3, expiresAt = System.currentTimeMillis() + (6 * 60 * 60 * 1000L),
            encryptedPayload = encryptedPayload, isSOSMessage = false
        )
        fun createSOS(senderId: String, destinationHash: String, encryptedPayload: String, latitude: Double, longitude: Double) = MeshPacket(
            originalSenderId = senderId, destinationHash = destinationHash,
            priority = MessagePriority.SOS, sprayLimit = 25, sprayRemaining = 25,
            maxHops = Int.MAX_VALUE, expiresAt = System.currentTimeMillis() + (72 * 60 * 60 * 1000L),
            encryptedPayload = encryptedPayload, isSOSMessage = true,
            senderLatitude = latitude, senderLongitude = longitude
        )
    }
}
```

### 2. routing/CarrierScore.kt ✅

```kotlin
package com.meshnet.app.routing

import com.meshnet.app.models.MeshPacket
import com.meshnet.app.models.MessagePriority

data class NearbyPeer(
    val endpointId: String,
    val deviceName: String,
    val batteryLevel: Int,
    val isMovingTowardSignal: Boolean,
    val speedKmh: Float,
    val existingRelayCount: Int,
    val isScheduledBus: Boolean,
)

object CarrierScorer {
    fun score(peer: NearbyPeer): Int {
        var score = 0
        if (peer.isMovingTowardSignal) score += 50 else score -= 50
        when {
            peer.speedKmh > 60 -> score += 20
            peer.speedKmh > 30 -> score += 10
            peer.speedKmh > 0  -> score += 5
            else               -> score -= 10
        }
        when {
            peer.batteryLevel > 70 -> score += 15
            peer.batteryLevel > 40 -> score += 8
            peer.batteryLevel > 20 -> score += 2
            else                   -> score -= 40
        }
        when {
            peer.existingRelayCount == 0  -> score += 10
            peer.existingRelayCount < 5   -> score += 5
            peer.existingRelayCount < 10  -> score += 0
            peer.existingRelayCount < 20  -> score -= 10
            else                          -> score -= 30
        }
        if (peer.isScheduledBus) score += 80
        return score
    }

    fun selectCarriers(peers: List<NearbyPeer>, packet: MeshPacket): List<NearbyPeer> {
        val minScore = when (packet.priority) {
            MessagePriority.NORMAL -> 70
            MessagePriority.URGENT -> 40
            MessagePriority.SOS    -> Int.MIN_VALUE
        }
        val maxCarriers = when (packet.priority) {
            MessagePriority.NORMAL -> 3
            MessagePriority.URGENT -> 10
            MessagePriority.SOS    -> Int.MAX_VALUE
        }
        return peers
            .map { peer -> Pair(peer, score(peer)) }
            .filter { (_, score) -> score >= minScore }
            .sortedByDescending { (_, score) -> score }
            .take(maxCarriers)
            .map { (peer, _) -> peer }
    }
}
```

---

## FILES TO GENERATE — IN ORDER

Generate each file completely. Do not skip any. Each file depends on the previous ones.

---

### FILE 3: data/MessageQueueEntity.kt

**Purpose:** Room Database entity. Stores packets locally when no relay is available. Persists across app restarts.

**Requirements:**
- Room `@Entity` annotation, table name `message_queue`
- Store all MeshPacket fields as columns
- `packetId` is the primary key
- Store `priority` and `status` as String (enum name)
- Include `isRelayPacket: Boolean` — true if we are carrying someone else's packet
- Include `@Dao` interface `MessageQueueDao` in the same file with:
  - `insertPacket(packet: MessageQueueEntity)`
  - `getAllPendingPackets(): Flow<List<MessageQueueEntity>>`
  - `getPacketById(packetId: String): MessageQueueEntity?`
  - `updateStatus(packetId: String, status: String)`
  - `deletePacket(packetId: String)`
  - `deleteExpiredPackets(currentTime: Long)`
  - `getRelayPackets(): List<MessageQueueEntity>` — packets we are carrying for others
- Include `@Database` class `MeshNetDatabase` with singleton pattern using `companion object`

---

### FILE 4: crypto/CryptoManager.kt

**Purpose:** Handles all encryption and decryption. Messages are encrypted before leaving the device. Relay nodes cannot read content.

**Requirements:**
- Singleton `object CryptoManager`
- `generateOrLoadDeviceId(context: Context): String`
  - Uses `SharedPreferences` to store a persistent UUID as device ID
- `hashDestination(deviceId: String): String`
  - Returns SHA-256 hash of deviceId as hex string
  - This is what gets stored in `destinationHash` — NOT the real phone number
- `encryptMessage(plaintext: String, recipientPublicKey: String): String`
  - Encrypts using AES-256-GCM
  - Returns Base64-encoded encrypted string
  - For prototype: use a simplified symmetric key derived from recipient hash
- `decryptMessage(encryptedBase64: String, myPrivateKey: String): String`
  - Decrypts AES-256-GCM
  - Returns original plaintext
- `isMyPacket(destinationHash: String, myDeviceId: String): Boolean`
  - Returns true if SHA-256(myDeviceId) == destinationHash
  - Used by relay logic to decide: deliver to me OR forward to next node
- Use Android Keystore for key storage where possible
- Add try-catch on all crypto operations — never crash the app on crypto failure

---

### FILE 5: mesh/NearbyConnectionsManager.kt

**Purpose:** Core mesh networking. Handles peer discovery, connection, and packet transfer using Google Nearby Connections API.

**Requirements:**
- Class `NearbyConnectionsManager(context: Context)`
- Use `ConnectionsClient` from Nearby API
- `SERVICE_ID = "com.meshnet.app.mesh"` — unique identifier for MeshNet peers
- `startAdvertising(deviceName: String, onPacketReceived: (String) -> Unit)`
  - Advertises this device to nearby MeshNet peers
  - `onPacketReceived` callback fires when a packet arrives as JSON string
- `startDiscovery(onPeerFound: (endpointId: String, name: String) -> Unit)`
  - Scans for nearby MeshNet devices
  - `onPeerFound` fires when a peer is discovered
- `connectToPeer(endpointId: String, onConnected: (String) -> Unit, onDisconnected: (String) -> Unit)`
  - Initiates connection to a discovered peer
- `sendPacket(endpointId: String, packet: MeshPacket): Boolean`
  - Serializes MeshPacket to JSON
  - Sends as `Payload.fromBytes()`
  - Returns true if send was initiated successfully
- `disconnectFromPeer(endpointId: String)`
- `stopAll()` — stops advertising and discovery
- Internal: `connectedPeers: MutableMap<String, String>` — endpointId to deviceName
- Use `STRATEGY_CLUSTER` for the connection strategy
- Handle `onPayloadReceived` → deserialize JSON → call `onPacketReceived`
- Use Gson for JSON serialization: `implementation("com.google.code.gson:gson:2.10.1")` — add this to build.gradle too

---

### FILE 6: routing/SprayAndWaitRouter.kt

**Purpose:** Core routing logic. Decides when to spray copies, who gets them, when to forward vs carry, and when to delete via kill signal.

**Requirements:**
- Class `SprayAndWaitRouter(private val nearbyManager: NearbyConnectionsManager, private val db: MeshNetDatabase)`
- `suspend fun handleIncomingPacket(packet: MeshPacket, myDeviceId: String)`
  - Check `CryptoManager.isMyPacket(packet.destinationHash, myDeviceId)`
  - If YES → deliver locally, trigger kill signal broadcast
  - If NO → check `canForward()` → if yes store and relay, if no just store and carry
  - Check for duplicate: if packetId already in DB → ignore (already have it)
  - Check expiry: if `isExpired()` → delete immediately
- `suspend fun sprayToCarriers(packet: MeshPacket, availablePeers: List<NearbyPeer>)`
  - Call `CarrierScorer.selectCarriers(peers, packet)`
  - For each selected carrier: call `packet.createRelayCopy()` then `nearbyManager.sendPacket()`
  - Log how many copies were sprayed
- `suspend fun broadcastKillSignal(packetId: String, connectedPeers: List<String>)`
  - Send a special kill packet: `{"type":"KILL","packetId":"..."}`
  - All recipients must delete that packetId from their DB
- `suspend fun processKillSignal(packetId: String)`
  - Delete packet from DB
  - Log: "Packet $packetId killed — delivery confirmed elsewhere"
- `suspend fun cleanExpiredPackets()`
  - Call `db.messageQueueDao().deleteExpiredPackets(System.currentTimeMillis())`
  - Schedule this via WorkManager every 30 minutes

---

### FILE 7: mesh/MeshService.kt

**Purpose:** Android Foreground Service. Keeps mesh networking alive when app is in background or phone is locked. This is what makes passive relay work — user doesn't need to have the app open.

**Requirements:**
- Class `MeshService : Service()`
- Start as Foreground Service with persistent notification:
  - Title: "MeshNet Active"
  - Content: "Relay mode on — earning credits"
  - Icon: use default app icon for now
- On `onStartCommand`:
  - Initialize `NearbyConnectionsManager`
  - Start advertising and discovery
  - Start periodic expired packet cleanup (every 30 min via Handler)
- On incoming packet:
  - Call `SprayAndWaitRouter.handleIncomingPacket()`
  - If packet is for this device — show notification: "New message received"
  - If packet is relay — show subtle notification: "Relayed 1 packet. +0.8MB credits"
- `companion object` with:
  - `fun start(context: Context)` — starts the service
  - `fun stop(context: Context)` — stops the service
- Register in AndroidManifest.xml:
```xml
<service
    android:name=".mesh.MeshService"
    android:foregroundServiceType="connectedDevice"
    android:exported="false"/>
```
- Add to AndroidManifest permissions:
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"/>
```

---

### FILE 8: data/WalletManager.kt

**Purpose:** Manages the data credit wallet. Tracks credits earned (relay) and spent (send). Persists locally. Syncs to Firebase when signal available.

**Requirements:**
- Class `WalletManager(context: Context)`
- Use `SharedPreferences` for local balance storage (key: `"meshnet_wallet_balance"`)
- `fun getBalance(): Float` — returns current MB balance
- `fun deductCredits(amount: Float, reason: String): Boolean`
  - Returns false if insufficient balance (never deduct below 0)
  - Log the transaction
- `fun addCredits(amount: Float, reason: String)`
  - Called when relay is successful
- `fun deductForMessage(priority: MessagePriority): Boolean`
  - NORMAL: deduct 1.2f MB
  - URGENT: deduct 3.6f MB
  - SOS: return true without deducting (SOS is always free)
- `fun rewardForRelay(delivered: Boolean): Float`
  - If delivered: add 0.8f MB, return 0.8f
  - If carried but not delivered: add 0.1f MB, return 0.1f
- `fun getTransactionLog(): List<String>` — returns last 50 transactions as strings
- Transaction log stored in SharedPreferences as JSON array

---

### FILE 9: ui/screens/HomeScreen.kt

**Purpose:** Main screen the user sees. Shows mesh status, wallet balance, message compose button, and SOS button.

**Requirements:**
- Jetpack Compose `@Composable fun HomeScreen()`
- Top section: Mesh status card
  - Show: "Mesh Active 🟢" or "Mesh Offline 🔴"
  - Show: number of peers currently connected
  - Show: wallet balance "Credits: 487.3 MB"
- Middle section: Recent messages list (just UI, data comes later)
  - Placeholder: "No messages yet"
- Bottom section: Two buttons
  - "New Message" button → navigates to compose (placeholder for now)
  - "SOS 🚨" button → RED, large, prominent
    - On click: show confirmation dialog "Send SOS to all contacts?"
    - On confirm: call SOS send logic
- SOS button must be visually distinct — red background, white text, slightly larger
- Use Material3 components
- Add a `MeshStatusBanner` composable at top — green when peers > 0, red when no peers

---

### FILE 10: ui/screens/ComposeMessageScreen.kt

**Purpose:** Screen where user types a message, selects priority, and sends.

**Requirements:**
- `@Composable fun ComposeMessageScreen(onSend: (message: String, priority: MessagePriority) -> Unit)`
- Text field for recipient (placeholder: "Enter recipient ID or scan QR")
- Text field for message content (multiline)
- Priority selector — three buttons: Normal / Urgent / SOS
  - Normal: grey/neutral color
  - Urgent: amber/orange color
  - SOS: red color
  - Selected one should be highlighted
- Below priority selector: show cost
  - Normal: "Cost: ~1.2 MB credits"
  - Urgent: "Cost: ~3.6 MB credits"
  - SOS: "Cost: FREE ✅"
- Send button — disabled if message is empty
- On send: call `onSend(message, selectedPriority)`

---

### FILE 11: MainActivity.kt (REPLACE EXISTING)

**Purpose:** Entry point. Handles permission requests, starts MeshService, sets up navigation.

**Requirements:**
- Request all permissions on launch:
  - `ACCESS_FINE_LOCATION`
  - `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`
  - `NEARBY_WIFI_DEVICES` (Android 13+)
  - `READ_PHONE_STATE`
- Only start MeshService AFTER permissions are granted
- Use `rememberPermissionState` or `ActivityResultContracts.RequestMultiplePermissions`
- Show a permission rationale screen if permissions denied:
  - "MeshNet needs these permissions to connect to nearby phones"
  - "Without these, the mesh network cannot function"
- Once permissions granted: call `MeshService.start(this)`
- Set content to `HomeScreen()`
- On app destroy: call `MeshService.stop(this)`

---

### FILE 12: routing/PacketExpiryWorker.kt

**Purpose:** WorkManager background job. Runs every 30 minutes to clean expired packets from DB.

**Requirements:**
- Class `PacketExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker`
- In `doWork()`:
  - Get DB instance
  - Call `deleteExpiredPackets(System.currentTimeMillis())`
  - Return `Result.success()`
- `companion object`:
  - `fun schedule(context: Context)` — schedules periodic work:
    ```kotlin
    PeriodicWorkRequestBuilder<PacketExpiryWorker>(30, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "packet_expiry_cleanup",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
    ```

---

## IMPLEMENTATION RULES — FOLLOW EXACTLY

1. **Every file must have the correct package declaration at the top**
2. **No file should crash on null — use safe calls (?.) everywhere**
3. **All database operations must be in suspend functions or background threads**
4. **Nearby Connections callbacks run on main thread — launch coroutines for DB ops**
5. **Never log or store decrypted message content — only encrypted payloads in DB**
6. **SOS messages skip credit deduction — always check `isSOSMessage` before deducting**
7. **Kill signals must be processed before storing a new relay packet**
8. **Duplicate packetId check must happen first in `handleIncomingPacket`**

---

## VERIFICATION CHECKLIST

After generating each file, verify:

- [ ] File 3 (DB): `MeshNetDatabase.getInstance()` returns non-null
- [ ] File 4 (Crypto): `encryptMessage` then `decryptMessage` returns original string
- [ ] File 5 (Nearby): Two phones discover each other — check Logcat for "Peer found"
- [ ] File 6 (Router): Phone B receives packet meant for Phone C → relays it
- [ ] File 7 (Service): App backgrounded → mesh still active → check notification
- [ ] File 8 (Wallet): Send message → balance decreases. Relay → balance increases.
- [ ] File 9 (Home UI): Home screen renders without crash
- [ ] File 10 (Compose UI): Priority selector changes cost label correctly
- [ ] File 11 (Main): All permissions requested on first launch
- [ ] File 12 (Worker): Expired packets deleted after TTL — check DB Inspector

---

## DEMO GOAL

**Three phones. Airplane mode on all three. Zero internet.**

1. Phone A types message → hits Send
2. Message hops through Phone B (relay)
3. Phone C receives message

If this works on stage — the prototype is complete.

---

## ADDITIONAL CONTEXT — EDGE CASES ALREADY DESIGNED

- **No vehicles for hours:** DTN store-and-wait. Beacon retries every 5 min.
- **Battery dies mid-relay:** Packet persists in Room DB. Reloads on restart.
- **Duplicate delivery:** Server rejects duplicate packetId. Kill signal cleans rest.
- **Malicious relay node:** AES-256-GCM encrypted. Relay sees only random bytes.
- **Broadcast storm:** Spray limit + hop count cap prevents flooding.
- **Opposite direction vehicle also in dead zone:** Carries packet to its own signal exit.
- **Recipient offline:** Firebase queues push notification until they reconnect.
- **Phone locked:** Foreground service + WorkManager handles everything in background.

---

*MeshNet — Built for India. Every road a cable. Every vehicle a node.*
