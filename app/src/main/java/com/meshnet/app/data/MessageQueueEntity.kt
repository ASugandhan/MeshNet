package com.meshnet.app.data

import android.content.Context
import androidx.room.*
import com.meshnet.app.models.MeshPacket
import com.meshnet.app.models.MessagePriority
import com.meshnet.app.models.PacketStatus
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "message_queue")
data class MessageQueueEntity(
    @PrimaryKey val packetId: String,
    val originalSenderId: String,
    val destinationHash: String,
    val priority: String, // Store as String (enum name)
    val sprayLimit: Int,
    val sprayRemaining: Int,
    val hopCount: Int,
    val maxHops: Int,
    val createdAt: Long,
    val expiresAt: Long,
    val encryptedPayload: String,
    val isSOSMessage: Boolean,
    val status: String, // Store as String (enum name)
    val deliveredAt: Long?,
    val senderLatitude: Double?,
    val senderLongitude: Double?,
    val isRelayPacket: Boolean
) {
    fun toMeshPacket(): MeshPacket {
        return MeshPacket(
            packetId = packetId,
            originalSenderId = originalSenderId,
            destinationHash = destinationHash,
            priority = MessagePriority.valueOf(priority),
            sprayLimit = sprayLimit,
            sprayRemaining = sprayRemaining,
            hopCount = hopCount,
            maxHops = maxHops,
            createdAt = createdAt,
            expiresAt = expiresAt,
            encryptedPayload = encryptedPayload,
            isSOSMessage = isSOSMessage,
            status = PacketStatus.valueOf(status),
            deliveredAt = deliveredAt,
            senderLatitude = senderLatitude,
            senderLongitude = senderLongitude
        )
    }

    companion object {
        fun fromMeshPacket(packet: MeshPacket, isRelayPacket: Boolean): MessageQueueEntity {
            return MessageQueueEntity(
                packetId = packet.packetId,
                originalSenderId = packet.originalSenderId,
                destinationHash = packet.destinationHash,
                priority = packet.priority.name,
                sprayLimit = packet.sprayLimit,
                sprayRemaining = packet.sprayRemaining,
                hopCount = packet.hopCount,
                maxHops = packet.maxHops,
                createdAt = packet.createdAt,
                expiresAt = packet.expiresAt,
                encryptedPayload = packet.encryptedPayload,
                isSOSMessage = packet.isSOSMessage,
                status = packet.status.name,
                deliveredAt = packet.deliveredAt,
                senderLatitude = packet.senderLatitude,
                senderLongitude = packet.senderLongitude,
                isRelayPacket = isRelayPacket
            )
        }
    }
}

@Dao
interface MessageQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacket(packet: MessageQueueEntity)

    @Query("SELECT * FROM message_queue WHERE status != 'DELIVERED' AND status != 'EXPIRED'")
    fun getAllPendingPackets(): Flow<List<MessageQueueEntity>>

    @Query("SELECT * FROM message_queue WHERE packetId = :packetId")
    suspend fun getPacketById(packetId: String): MessageQueueEntity?

    @Query("UPDATE message_queue SET status = :status WHERE packetId = :packetId")
    suspend fun updateStatus(packetId: String, status: String)

    @Query("DELETE FROM message_queue WHERE packetId = :packetId")
    suspend fun deletePacket(packetId: String)

    @Query("DELETE FROM message_queue WHERE expiresAt < :currentTime")
    suspend fun deleteExpiredPackets(currentTime: Long)

    @Query("SELECT * FROM message_queue WHERE isRelayPacket = 1")
    suspend fun getRelayPackets(): List<MessageQueueEntity>
}

@Database(entities = [MessageQueueEntity::class], version = 1, exportSchema = false)
abstract class MeshNetDatabase : RoomDatabase() {
    abstract fun messageQueueDao(): MessageQueueDao

    companion object {
        @Volatile
        private var INSTANCE: MeshNetDatabase? = null

        fun getDatabase(context: Context): MeshNetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeshNetDatabase::class.java,
                    "meshnet_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
