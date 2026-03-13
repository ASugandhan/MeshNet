---
title: "MeshNet — Master Technical Document"
version: "1.0"
project: "MeshNet — India's Community-Powered Offline Data Network"
team: "SKCET · Coimbatore · Ideathon 2025"
---

### MESHNET
India's Community-Powered Offline Data Network

Master Technical Document v1.0

*Protocols · Edge Cases · Technology Stack · Implementation Checklist*

Built for Ideathon 2025 · SKCET · Coimbatore

# 1. What Is MeshNet?
MeshNet is a community-powered offline data network that uses vehicles, roads, and people as communication infrastructure — without requiring mobile towers or internet connectivity. It allows people in zero-signal zones (forests, highways, rural areas) to send messages, share location, receive emergency help, and relay data through nearby vehicles and strangers' phones.

+———————————————————————--+
| **Core Concept in One Line**                                          |
+———————————————————————--+
| Every vehicle on every road = a moving data relay node.               |
|                                                                       |
| Every road = a two-way data cable.                                    |
|                                                                       |
| Every bus route = a scheduled data pipeline.                          |
|                                                                       |
| Every driver = earns passive relay credits for helping.               |
+———————————————————————--+

# 2. Protocols Used — Every Edge Case
MeshNet uses a three-protocol hybrid system. Each protocol handles a different layer of the communication problem. Here is the complete breakdown:

## 2.1 WiFi Direct (P2P) — NSD / Nearby Connections API
### What it is
WiFi Direct allows two Android phones to connect directly to each other without any router, tower, or internet. It creates a direct device-to-device link over 2.4GHz or 5GHz WiFi radio.

### What it handles
-   Group members communicating within the same dead zone (family in forest)

-   Live GPS location sharing between group members

-   Local mesh chat — messages between 4 family phones

-   File transfer within the group (maps, first aid guides)

-   Initial packet spray — Car A sprays copies to nearby vehicles

### How it works — Step by Step
1.  Phone A activates WiFi Direct mode when signal drops below threshold

2.  Phone A broadcasts a discovery beacon on the local WiFi channel

3.  Phone B detects the beacon, responds with its device ID

4.  Handshake completes — both phones are now connected directly

5.  A private socket connection opens between the two phones

6.  Data packets flow through this socket — no internet required

7.  When Phone A moves out of range — connection drops gracefully

### Technical Specs
-   Range: 50 to 200 metres in open space

-   Speed: Up to 250 Mbps (WiFi Direct)

-   Android API: Nearby Connections API (Google) — handles WiFi Direct + Bluetooth automatically

-   Works on: All Android phones with WiFi — including budget Redmi, Realme, Samsung

-   Battery cost: Medium — WiFi Direct radio stays active

## 2.2 DTN — Delay Tolerant Networking (Store and Forward)
### What it is
DTN is a communication protocol designed for environments where a continuous end-to-end path does not exist. Instead of requiring a live connection, DTN stores a message locally and forwards it whenever a path becomes available. Originally developed by NASA for deep-space communication.

### What it handles
-   Messages that cannot be delivered immediately due to no signal

-   Car A wants to reach Person 5 in the city — but there is a gap between vehicles

-   Message waits inside a vehicle, hops to the next vehicle when in range

-   Handles all three message classes — Normal, Urgent, SOS

### How it works — Step by Step
8.  Ravi types a message. No signal. App stores it locally with metadata:

> { messageId, destination, priority, timestamp, sprayLimit, expiryTime, encrypted_payload }

9.  App broadcasts a tiny beacon: 'I have a pending packet for delivery'

10. A nearby vehicle with MeshNet detects the beacon

11. Vehicle scores the sender's packet (priority check) and accepts it

12. Packet now lives inside the vehicle's phone — the vehicle physically drives toward signal

13. When that vehicle comes into range of another MeshNet node — it evaluates: forward or carry?

14. If next node is closer to signal — forward the packet

15. When any node holding the packet reaches signal — it delivers and sends a kill signal

### Technical Specs
-   Protocol: Bundle Protocol v7 (RFC 9171) — the standard DTN specification

-   Storage: Android Room Database — holds packet queue locally

-   Expiry: Configurable per message class (24h normal, 6h urgent, 72h SOS)

-   Works completely offline — no internet needed until final delivery

## 2.3 Epidemic Routing — Spray and Wait
### What it is
Epidemic Routing is a DTN routing strategy where a message is duplicated and spread to multiple carriers simultaneously — like a virus spreading. Multiple copies race toward the destination through different paths. The fastest copy delivers. All others self-destruct via a kill signal.

### What it handles
-   Car A needs to escape the dead zone — one vehicle alone might not make it

-   Multiple copies ensure at least one reaches signal

-   Fault-tolerant — if Car C breaks down, Car D's copy still delivers

-   Bidirectional — copies spray to vehicles going both directions

### How it works — Step by Step
16. Car A detects multiple nearby vehicles within WiFi Direct range

17. App scores each vehicle based on direction, battery, speed, existing load

18. App creates N copies of the encrypted message (N = spray limit based on message class)

19. Each copy is stamped with: sprayLimit remaining, unique packetID, hopCount

20. Copies spray simultaneously to top-scored vehicles

21. Each copy travels independently through different vehicles

22. When a copy reaches signal — it delivers and broadcasts kill signal #packetID

23. All other copies that receive the kill signal delete themselves immediately

### Spray Limits by Message Class
  ———————-- —————-- —————-- ———————
  **Dimension**           **Normal**        **Urgent**        **SOS / Emergency**

  **Spray Count**         5 copies          15 copies         25 copies

  **Max Hops**            1 hop             3 hops            Unlimited

  **Expiry Time**         24 hours          6 hours           72 hours

  **Min Carrier Score**   70+               40+               No filter

  **Queue Priority**      Low               High              Immediate

  **Credit Cost**         1x base           3x base           FREE

  **Kill Signal**         Yes               Yes               Yes

  **Encryption**          E2E               E2E               E2E
  ———————-- —————-- —————-- ———————

### The Broadcast Storm Problem and Solution
If every vehicle forwards to 25 more, and each of those forwards to 25 more — the mesh floods. This is called a Broadcast Storm.

Solution: Spray and Wait — each copy can only be forwarded ONE more time (for Normal), THREE more times (for Urgent), or unlimited times (for SOS). After that, the copy is held and not forwarded further.

## 2.4 VANET — Vehicular Ad-hoc Network
### What it is
VANET is a network formed by vehicles as nodes. Each vehicle is simultaneously a sender, receiver, and relay. The road becomes a dynamic cable that changes shape as vehicles move. India's highways and forest roads are natural VANET infrastructure.

### What it handles
-   The highway scenario — line of cars and buses passing through forest

-   Bidirectional data flow — city to forest AND forest to city simultaneously

-   Bus routes as scheduled data pipelines — same route multiple times a day

-   Long-range relay — message travels 40km through 15 vehicle hops

### How it works — Step by Step
24. Car A enters dead zone on forest highway

25. Car A has a message packet to deliver to city

26. Car B approaches from opposite direction (coming from city)

27. When both cars are within 200m — WiFi Direct auto-connects

28. Car A sprays encrypted copy to Car B

29. Car B is physically moving toward the city — it carries the packet

30. Car B meets Car D which is also moving cityward

31. Car B hands off the packet to Car D (carry and forward)

32. Car D exits the forest, hits 4G signal

33. Packet fires to destination — delivered

### Carrier Scoring Algorithm
Not all vehicles are equal. The app scores each potential carrier before deciding who receives a copy:

-   Traveling toward signal zone: +50 points

-   High speed (reaches signal faster): +20 points

-   High battery (won't die mid-journey): +15 points

-   Low existing relay load (has storage capacity): +10 points

-   Bus on scheduled route (guaranteed arrival): +80 points

-   Low battery (under 20%): -40 points

-   Traveling away from signal: -50 points

-   Already overloaded with relay packets: -30 points

The app picks the top 3 scored vehicles for Normal messages, top 10 for Urgent, all available for SOS.

## 2.5 SMS Compression Fallback — 2G Punch Through
### What it is
Even in near-zero signal areas where mobile data is unavailable, most Indian towers still maintain a 2G control channel. This channel can carry tiny SMS-sized packets. MeshNet compresses critical messages to under 160 bytes and punches them through this channel as SMS-type packets.

### What it handles
-   SOS messages when no vehicles are nearby

-   Location coordinates when all other paths fail

-   Ultra-compressed text messages through 1-bar 2G signal

### How it works — Step by Step
34. All WiFi Direct and DTN relay paths have failed or are unavailable

35. App detects faint 2G signal on control channel (even 1 bar)

36. App compresses message to minimum size:

> SOS|10.3456|77.1234|INJURY|RAVI → 38 bytes

37. App encodes this as a binary SMS payload

38. Sends via Android SmsManager using data SMS (not text SMS)

39. Receiver's MeshNet app decodes and reconstructs the message

40. Full formatted message displayed to recipient

### Technical Specs
-   Max payload: 140 bytes (SMS standard limit)

-   Android API: SmsManager — built into all Android versions

-   Compression: LZ4 algorithm — fastest compression, good ratio

-   Encoding: Base64 for binary safety in SMS

-   Works on: 2G, EDGE, even GSM voice channel in extreme cases

## 2.6 End-to-End Encryption
### What it is
Every message in MeshNet is encrypted before leaving the sender's phone. Relay vehicles carry only encrypted blobs — they cannot read the content. Only the intended recipient can decrypt.

### How it works
41. On app install — each phone generates an asymmetric key pair (public + private)

42. Public keys are shared to the MeshNet cloud server when online

43. Sender fetches recipient's public key from server (cached locally)

44. Message encrypted with recipient's public key using AES-256-GCM

45. Encrypted blob travels through relay vehicles — they see only random bytes

46. Recipient decrypts using their private key stored only on their device

### Technical Specs
-   Algorithm: AES-256-GCM (symmetric) + RSA-2048 (key exchange)

-   Key storage: Android Keystore — hardware-backed on modern phones

-   Library: Bouncy Castle or Android's built-in Cipher API

-   Relay nodes: see only packetID + destination hash + encrypted blob

## 2.7 Credit System — Data Wallet Economy
### What it is
Every MeshNet user has a data wallet. Unused mobile data credits accumulate in the wallet. Credits are spent when sending messages through the mesh. Credits are earned when your phone relays other people's packets. The wallet reconciles with the cloud server when signal is available.

### How credits flow
47. Telecom plan has 2GB/day — user uses 1.3GB — 700MB goes to wallet

48. Ravi sends a message — costs 1.2MB from his wallet

49. Selvi's phone relays the message — earns 0.8MB into her wallet

50. Anbu carried but didn't deliver — earns 0.1MB partial reward

51. When signal restores — all wallets sync to cloud and reconcile

### Credit Cost by Message Class
-   Normal message: 1x base rate (approx 1-2MB depending on hops)

-   Urgent message: 3x base rate (more copies, more hops)

-   SOS message: FREE — always. No credit check. No wallet deduction.

### Technical Specs
-   Local storage: Room DB — stores wallet balance, transaction log

-   Cloud sync: Firebase Realtime Database — reconciles on signal restore

-   Conflict resolution: CRDT (Conflict-free Replicated Data Type) — handles offline divergence

-   Anti-fraud: Each transaction signed with device key — cannot be spoofed

# 3. Edge Cases — Every Scenario Handled
### Edge Case 1: No vehicles pass for hours
Ravi's message is queued. No vehicles come. What happens?

52. Message stays in queue — DTN store and wait

53. App keeps retrying beacon broadcast every 5 minutes

54. App attempts 2G SMS compression if any signal detected

55. If SOS — app tries satellite fallback (Jio Satellite / Starlink)

56. Message expires after class-defined expiry time

57. User notified: 'Message could not be delivered — retry?'

### Edge Case 2: Vehicle carrying packet loses battery and turns off
Car C has Ravi's packet. Car C's battery dies at kilometer 20.

58. Car C's phone saves all pending relay packets to persistent storage before shutdown

59. When Car C restarts — packets reload from storage and resume

60. Meanwhile — other copies (spray duplicates) continue their routes

61. If all copies die — sender's app retries spray when new vehicles are detected

### Edge Case 3: Same message delivered multiple times
Both Selvi and Anbu reach signal at the same time and both try to deliver.

62. First delivery fires — server marks packetID #RV2024-001 as DELIVERED

63. Second delivery attempt hits server — server sees duplicate packetID

64. Server rejects second delivery — returns 'already delivered' response

65. No duplicate message shown to recipient

66. Both carriers notified — kill signal sent to all remaining copies

### Edge Case 4: Malicious user tries to read relay packets
Someone reverse-engineers the app and tries to read packets their phone is relaying.

67. All packets are AES-256-GCM encrypted — random bytes without private key

68. Packet contains only: destination hash, packetID, encrypted blob

69. Destination is a hash — not a phone number — not identifiable

70. Even Anthropic or MeshNet servers cannot read the content

71. Relay node gets no information about sender, recipient, or message content

### Edge Case 5: Broadcast storm from too many SOS sprays
100 people in a forest all hit SOS simultaneously. 100 x 25 = 2500 copies flooding the mesh.

72. Each packet has a unique packetID — no duplicates stored on same device

73. Each device caps relay storage at 50MB — rejects packets when full

74. Priority queue: SOS packets evict Normal packets from storage if needed

75. Kill signals clean up copies aggressively once delivered

76. In practice — 100 SOS at 5KB each = 500KB total — well within 50MB cap

### Edge Case 6: Opposite-direction vehicle is also in dead zone
Car C is coming from the other side — but it is ALSO in a dead zone. It cannot deliver.

77. Car C carries the packet and keeps driving

78. Car C exits the dead zone on its side — hits signal

79. Delivers from the opposite city — same result, message delivered

80. If Car C also enters a dead zone on its side — it sprays to vehicles on that side

81. DTN ensures eventual delivery regardless of which direction signal is found

### Edge Case 7: Recipient also has no signal
Murugan (recipient) is also in a dead zone when the message arrives at server.

82. Server stores the message — push notification queued

83. When Murugan's phone reconnects — push notification fires

84. MeshNet uses Firebase Cloud Messaging (FCM) for push delivery

85. If Murugan is also in a dead zone permanently — message waits on server for 7 days

### Edge Case 8: App is in background / phone is locked
Selvi's phone is locked and screen is off when a relay packet needs to be accepted.

86. Android Nearby Connections API works in background — no screen needed

87. Android WorkManager handles background relay tasks

88. Foreground service runs silently — shown as persistent notification

89. Packet accepted and stored without any user interaction

90. User sees a subtle notification: 'Relayed 1 packet. Earned 0.8MB credits'

# 4. Complete Message Flow — Ravi to Murugan
This is the end-to-end flow of a single Normal message traveling from a zero-signal forest to a city recipient.

+————————————————————————————————————--+
| **Step 1 — Message Creation**                                                                              |
+————————————————————————————————————--+
| Ravi types: 'Murugan anna, rendu naal munnaadi va'                                                         |
|                                                                                                              |
| App classifies: Normal (no urgency keywords detected)                                                        |
|                                                                                                              |
| App suggests: Normal | Urgent | SOS — Ravi confirms Normal                                               |
|                                                                                                              |
| App encrypts message with Murugan's public key (fetched from cache)                                         |
|                                                                                                              |
| Creates packet: { id: RV001, dest: HASH(Murugan), priority: NORMAL, spray: 5, ttl: 24h, payload: ENCRYPTED } |
+————————————————————————————————————--+

+———————————————————————--+
| **Step 2 — No Signal Detection**                                    |
+———————————————————————--+
| App checks: Internet available? NO                                    |
|                                                                       |
| App checks: Any WiFi Direct peers? NO (no one nearby yet)             |
|                                                                       |
| App stores packet in Room DB queue                                    |
|                                                                       |
| App starts broadcasting beacon every 60 seconds                       |
|                                                                       |
| App monitors for new nearby devices continuously                      |
+———————————————————————--+

+———————————————————————--+
| **Step 3 — Vehicle Detected**                                       |
+———————————————————————--+
| KSRTC bus enters 200m range — 3 passengers have MeshNet             |
|                                                                       |
| App detects 3 new peer beacons via Nearby Connections API             |
|                                                                       |
| App scores each passenger: Selvi=95, Kumar=45, Anbu=95                |
|                                                                       |
| App selects: Selvi and Anbu (score > 70 threshold for Normal)        |
|                                                                       |
| App creates 2 copies of packet RV001                                  |
+———————————————————————--+

+———————————————————————--+
| **Step 4 — Spray**                                                  |
+———————————————————————--+
| Copy 1 transferred to Selvi's phone via WiFi Direct — 0.3 seconds  |
|                                                                       |
| Copy 2 transferred to Anbu's phone via WiFi Direct — 0.3 seconds   |
|                                                                       |
| Both copies stamped: sprayRemaining=1 (can forward once more)         |
|                                                                       |
| Ravi's app shows: 'Message handed to 2 relay carriers'             |
|                                                                       |
| Bus drives on toward Pollachi                                         |
+———————————————————————--+

+——————————————————————————————+
| **Step 5 — Carry and Forward**                                                         |
+——————————————————————————————+
| Bus travels through forest — still no signal                                           |
|                                                                                          |
| At km 18: opposite-direction car detected — scored negative (wrong direction) — SKIP |
|                                                                                          |
| At km 28: another MeshNet user detected — Selvi evaluates: forward or hold?            |
|                                                                                          |
| Selvi's copy: sprayRemaining=1, new user scored 80 — FORWARD                          |
|                                                                                          |
| Copy handed to new carrier — sprayRemaining now 0 (no more forwarding)                 |
+——————————————————————————————+

+————————————————————————--+
| **Step 6 — Signal Detected and Delivery**                              |
+————————————————————————--+
| New carrier reaches Aliyar — 1 bar 4G detected                         |
|                                                                          |
| App wakes up — connects to internet                                    |
|                                                                          |
| Resolves Murugan's device address from MeshNet server                   |
|                                                                          |
| Delivers encrypted packet to server — server pushes to Murugan via FCM |
|                                                                          |
| Murugan receives message — decrypts with private key                   |
|                                                                          |
| Server sends delivery confirmation back                                  |
+————————————————————————--+

+——————————————————————————————+
| **Step 7 — Kill Signal and Credit Settlement**                                         |
+——————————————————————————————+
| Server broadcasts kill signal for packetID RV001 into mesh                               |
|                                                                                          |
| Anbu's copy receives kill signal — deletes packet — frees storage                   |
|                                                                                          |
| Selvi's copy (via new carrier) also receives kill — deleted                           |
|                                                                                          |
| Credit reconciliation when Ravi gets signal: -1.2MB from Ravi                            |
|                                                                                          |
| Selvi: +0.8MB (delivered). New carrier: +0.6MB (delivered). Anbu: +0.1MB (partial carry) |
+——————————————————————————————+

# 5. Technology Stack — Complete
  ————————-- ————————————-- ——————————————- ———————————--
  **Protocol**               **Purpose**                            **Edge Case**                               **Works Offline?**

  Nearby Connections API     P2P device discovery + data transfer   Family mesh in forest / vehicle spray       YES — no internet

  Room Database              Local message queue + wallet storage   Message persistence when no signal          YES — fully local

  DTN Bundle Protocol v7     Store and forward message routing      No vehicles nearby for hours                YES — core offline protocol

  Epidemic Spray and Wait    Multi-copy parallel delivery           Single vehicle gets stuck or dies           YES — no coordination needed

  VANET scoring algorithm    Smart carrier selection                Choosing best vehicle to relay              YES — local computation

  AES-256-GCM Encryption     End-to-end message encryption          Malicious relay node trying to read         YES — encrypt before sending

  SmsManager + LZ4           SMS fallback for SOS                   Zero WiFi Direct peers, faint 2G only       YES — uses 2G control channel

  Firebase FCM               Push notification on delivery          Recipient offline when message arrives      NO — needs internet at delivery

  Firebase Realtime DB       Wallet credit reconciliation           Offline credit divergence between devices   NO — syncs when signal returns

  OpenStreetMap + OSMDroid   Offline map display                    GPS location sharing with no internet       YES — fully offline maps

  Android LocationManager    GPS coordinates                        Live location in dead zone                  YES — GPS is satellite based

  Android WorkManager        Background relay tasks                 Phone locked while relaying packets         YES — background execution

  CRDT data structures       Conflict-free wallet sync              Two devices diverge during offline period   YES — resolves on sync
  ————————-- ————————————-- ——————————————- ———————————--

# 6. Is It Vibe Codable? (Cursor / Windsurf / Copilot)
+—————————————————————————————--+
| **Short Answer**                                                                        |
+—————————————————————————————--+
| YES — 70% of this is vibe-codable in Cursor or Windsurf.                              |
|                                                                                         |
| The remaining 30% needs your understanding to guide the AI correctly.                   |
|                                                                                         |
| The AI cannot design the routing logic — but it CAN implement it once you explain it. |
+—————————————————————————————--+

### What AI IDEs Can Do For You
-   Scaffold the entire Android project structure in one prompt

-   Write boilerplate Nearby Connections API code (discovery, connection, payload transfer)

-   Generate Room Database schema and DAO classes

-   Build the UI screens (Jetpack Compose or XML layouts)

-   Write Firebase integration code

-   Implement AES-256 encryption/decryption functions

-   Generate the carrier scoring algorithm once you describe the logic

-   Write the SmsManager fallback code

-   Create the credit tracking logic

### What AI IDEs CANNOT Do — You Must Guide
-   Design the routing decision logic — you must explain Spray and Wait clearly

-   Implement DTN hop counting — AI needs exact specification

-   Design the kill signal propagation — AI will get this wrong without guidance

-   Architect the message class system — you must specify all dimensions

-   Handle edge cases — AI will miss them unless you describe each one

### Recommended Workflow in Cursor
91. Open Cursor. Create new Android project (Kotlin).

92. Paste this entire master document into Cursor as context.

93. Prompt: 'Read the master document. Scaffold the Android project structure based on it.'

94. Prompt: 'Implement the Nearby Connections API peer discovery and connection logic.'

95. Prompt: 'Implement the Room Database schema for the message queue as described in Section 4.'

96. Prompt: 'Implement the carrier scoring algorithm using the point system in Section 2.4.'

97. Prompt: 'Implement the Spray and Wait routing with the spray limits from the message class table.'

98. Test each component against the checklist in Section 7 before moving to next.

### Cursor Prompt — Project Scaffold
Copy this exact prompt into Cursor to get started:

+———————————————————————————--+
| **Cursor Starter Prompt**                                                         |
+———————————————————————————--+
| I am building MeshNet — an Android app for offline mesh messaging in India.     |
|                                                                                   |
| Tech stack: Kotlin, Jetpack Compose, Room DB, Nearby Connections API, Firebase.   |
|                                                                                   |
| Create the following package structure:                                           |
|                                                                                   |
| com.meshnet/                                                                      |
|                                                                                   |
| mesh/ — Nearby Connections logic, peer discovery, packet transfer               |
|                                                                                   |
| routing/ — Spray and Wait, DTN, carrier scoring algorithm                       |
|                                                                                   |
| data/ — Room DB, message queue, wallet, packet storage                          |
|                                                                                   |
| crypto/ — AES-256-GCM encryption, key management                                |
|                                                                                   |
| fallback/ — SMS compression, 2G fallback                                        |
|                                                                                   |
| ui/ — Jetpack Compose screens                                                   |
|                                                                                   |
| sync/ — Firebase wallet reconciliation                                          |
|                                                                                   |
| models/ — Message, Packet, Peer, Wallet data classes                            |
|                                                                                   |
| Create empty files for each module. Add placeholder TODOs matching each function. |
+———————————————————————————--+

# 7. Implementation Checklist
Use this checklist after implementing each step. If the verification test fails — do not proceed to the next step. Fix first.

## Phase 1 — Project Setup
  ——-- ———————————————————————-- ———————————————-- ————
  **\#**   **Task**                                                                **Verification Test**                           **Status**

  1        Create Android project with Kotlin and Jetpack Compose                  Project builds without errors                   ☐ Pending

  2        Add Nearby Connections API dependency to build.gradle                   import com.google.android.gms.nearby resolves   ☐ Pending

  3        Add Room Database dependency                                            import androidx.room resolves                   ☐ Pending

  4        Add Firebase dependencies (FCM, Realtime DB)                            Firebase initializes without crash              ☐ Pending

  5        Add OSMDroid dependency for offline maps                                Map tile renders offline                        ☐ Pending

  6        Configure AndroidManifest permissions: WIFI, BLUETOOTH, LOCATION, SMS   App requests all permissions on first launch    ☐ Pending

  7        Create package structure as per Section 6 scaffold                      All packages visible in project tree            ☐ Pending
  ——-- ———————————————————————-- ———————————————-- ————

## Phase 2 — Peer Discovery (Nearby Connections)
  ——-- ———————————————————— ——————————————————-- ————
  **\#**   **Task**                                                     **Verification Test**                                    **Status**

  1        Implement NearbyConnectionsManager class                     Class instantiates without error                         ☐ Pending

  2        Implement startAdvertising() — phone broadcasts presence   Logcat shows 'Advertising started'                     ☐ Pending

  3        Implement startDiscovery() — phone scans for peers         Logcat shows 'Discovery started'                       ☐ Pending

  4        Implement onEndpointFound callback — peer detected         When 2nd phone is nearby — peer detected in logs       ☐ Pending

  5        Implement connection request and acceptance logic            Two phones connect — both show 'Connected to peer'   ☐ Pending

  6        Implement onDisconnected graceful handling                   Moving phones apart — no crash, disconnect logged      ☐ Pending

  7        Test: 3 phones all discover each other simultaneously        All 3 phones show 2 peers connected each                 ☐ Pending
  ——-- ———————————————————— ——————————————————-- ————

## Phase 3 — Message Packet System
  ——-- ————————————————————- ———————————————————- ————
  **\#**   **Task**                                                      **Verification Test**                                      **Status**

  1        Create MessagePacket data class with all fields               Packet serializes to JSON correctly                        ☐ Pending

  2        Implement Room DB schema: messages, packets, peers tables     DB creates on first launch — inspect with DB Inspector   ☐ Pending

  3        Implement MessageDAO: insert, query pending, mark delivered   Insert message → query → returns same message              ☐ Pending

  4        Implement message classification: Normal / Urgent / SOS       Keyword 'help' → suggests SOS classification             ☐ Pending

  5        Implement AES-256-GCM encryption for message payload          Encrypt 'hello' → decrypt → returns 'hello'            ☐ Pending

  6        Implement key pair generation on first install                Keys generated — stored in Android Keystore              ☐ Pending

  7        Implement packet expiry cleanup job (WorkManager)             Expired packets deleted after TTL — check DB             ☐ Pending
  ——-- ————————————————————- ———————————————————- ————

## Phase 4 — Core Mesh Messaging
  ——-- ——————————————————-- ————————————————— ————
  **\#**   **Task**                                                 **Verification Test**                               **Status**

  1        Implement send message via Nearby Connections payload    Phone A sends string → Phone B receives it          ☐ Pending

  2        Implement message queue — store if no peer available   Send with no peer → message in DB queue             ☐ Pending

  3        Implement auto-send from queue when peer connects        Queue message → connect peer → auto-sends           ☐ Pending

  4        Implement relay logic: am I the destination?             Phone B receives packet for Phone C → relays to C   ☐ Pending

  5        Implement hop counter decrement on each relay            Packet hops: 3 → 2 → 1 → 0 → stops forwarding       ☐ Pending

  6        Test: A → B → C message hop with WiFi OFF, data OFF      Phone C receives message. Zero internet used.       ☐ Pending

  7        Implement delivery confirmation back to sender           Sender sees 'Delivered' status after hop chain    ☐ Pending
  ——-- ——————————————————-- ————————————————— ————

## Phase 5 — Spray and Wait Routing
  ——-- ———————————————————————- —————————————————— ————
  **\#**   **Task**                                                               **Verification Test**                                  **Status**

  1        Implement carrier scoring algorithm (Section 2.4 formula)              Bus scored higher than slow car in unit test           ☐ Pending

  2        Implement spray: create N copies based on message class                Normal=5 copies, Urgent=15, SOS=25 verified in logs    ☐ Pending

  3        Implement spray to top-N scored peers simultaneously                   3 peers available, Normal: top 2 receive copies        ☐ Pending

  4        Implement unique packetID for each original message                    Two sends generate two different packetIDs             ☐ Pending

  5        Implement kill signal broadcast on delivery confirmation               After delivery, all copies on other phones deleted     ☐ Pending

  6        Implement duplicate delivery rejection on server                       Same packetID delivered twice — second rejected      ☐ Pending

  7        Test full spray scenario: A sprays to B and C, C delivers, B deletes   Only one delivery. B's copy gone after kill signal.   ☐ Pending
  ——-- ———————————————————————- —————————————————— ————

## Phase 6 — GPS and Offline Maps
  ——-- ————————————————— ———————————————————-- ————
  **\#**   **Task**                                            **Verification Test**                                       **Status**

  1        Implement LocationManager — get GPS coordinates   Coordinates update every 30 seconds in logs                 ☐ Pending

  2        Implement OSMDroid map in Compose/XML layout        Map renders with no internet — offline tiles visible      ☐ Pending

  3        Implement pre-download of map tiles for a route     Download tiles for route → disable WiFi → map still shows   ☐ Pending

  4        Broadcast GPS coordinates to mesh peers             Phone B sees Phone A's location dot on map, no internet    ☐ Pending

  5        Update peer location dots on map in real time       Move Phone A → dot moves on Phone B's map                  ☐ Pending
  ——-- ————————————————— ———————————————————-- ————

## Phase 7 — SOS and SMS Fallback
  ——-- ——————————————————— ——————————————————— ————
  **\#**   **Task**                                                  **Verification Test**                                     **Status**

  1        Implement SOS button in UI                                Button visible and tappable                               ☐ Pending

  2        Implement SOS message creation with GPS auto-attach       SOS packet contains accurate GPS coordinates              ☐ Pending

  3        Implement LZ4 message compression                         Message compressed to under 140 bytes                     ☐ Pending

  4        Implement SmsManager data SMS send                        Compressed SOS received as SMS on test device             ☐ Pending

  5        Implement SOS spray with limit 25 and no carrier filter   All 5 nearby peers receive SOS copy regardless of score   ☐ Pending

  6        Implement SOS as free — skip credit deduction           SOS sent — wallet balance unchanged                     ☐ Pending
  ——-- ——————————————————— ——————————————————— ————

## Phase 8 — Credit Wallet System
  ——-- ——————————————————— ——————————————————————- ————
  **\#**   **Task**                                                  **Verification Test**                                               **Status**

  1        Implement local wallet balance in Room DB                 Balance persists after app restart                                  ☐ Pending

  2        Implement credit deduction on message send                Send Normal message → balance decreases by 1.2MB                    ☐ Pending

  3        Implement credit reward on successful relay               Relay and deliver → balance increases by 0.8MB                      ☐ Pending

  4        Implement Firebase wallet sync on signal restore          Go offline, spend credits, restore signal — cloud matches local   ☐ Pending

  5        Implement CRDT conflict resolution for diverged wallets   Two offline devices merge wallets correctly on sync                 ☐ Pending

  6        Implement wallet display UI                               Balance shown correctly on home screen                              ☐ Pending
  ——-- ——————————————————— ——————————————————————- ————

## Phase 9 — Demo Readiness
  ——-- ——————————————————————-- —————————————————— ————
  **\#**   **Task**                                                             **Verification Test**                                  **Status**

  1        3 phones demo: A sends, B relays, C receives — all airplane mode   Message delivered. Zero internet. Judges can verify.   ☐ Pending

  2        GPS demo: 3 phones show each other's location dots, no internet     Move phones — dots update on all 3 screens           ☐ Pending

  3        SOS demo: SOS sends via spray to all 3 phones instantly              All 3 phones alarm within 2 seconds of SOS press       ☐ Pending

  4        Credit demo: send message → show wallet deduction → relay reward     Credits change as expected visually on screen          ☐ Pending

  5        Rehearse 30-second demo script                                       Demo completes in under 30 seconds, no fumbles         ☐ Pending

  6        Prepare fallback: screen recording of demo if phones fail on stage   Recording ready on laptop as backup                    ☐ Pending
  ——-- ——————————————————————-- —————————————————— ————

# 8. Team Structure and Timeline
  ————- ———————-- ———————————————————————————
  **Person**    **Role**                **Owns**

  Person 1      Android Dev — Core    Nearby Connections API, mesh logic, Spray and Wait, DTN hop count, kill signal

  Person 2      Android Dev — UI/UX   Jetpack Compose screens, OSMDroid maps, GPS sharing, SOS button, credit display

  Person 3      Backend + Security      Room DB schema, Firebase sync, AES encryption, credit wallet, SMS fallback

  Person 4      Pitch + Demo            Ideathon slides, business model, demo script rehearsal, backup recording
  ————- ———————-- ———————————————————————————

  ———- —————- —————————————————————————-
  **Week**   **Focus**        **Deliverable**

  Week 1     Foundation       Project scaffold, Nearby Connections working, 2 phones discover each other

  Week 2     Core Mesh        A→B→C message hop working, Room DB queue, basic encryption

  Week 3     Routing + GPS    Spray and Wait working, carrier scoring, GPS sharing on offline map

  Week 4     Polish + Demo    SOS button, credit display, 3-phone demo rehearsed and ready
  ———- —————- —————————————————————————-

# 9. Final Note
This document is the single source of truth for MeshNet. Paste it into Cursor or Windsurf at the start of every session. Every protocol, every edge case, every checklist item is here.

The demo goal is simple: three phones, airplane mode, one message hops from Phone A through Phone B to reach Phone C. If that works on stage — everything else is detail.

+———————————————————————--+
| **Remember**                                                          |
+———————————————————————--+
| The idea is valid. The protocols are real. The tech exists.           |
|                                                                       |
| No consumer app in India does all of this together.                   |
|                                                                       |
| Build the demo. Win the ideathon. Then build the real thing.          |
+———————————————————————--+
