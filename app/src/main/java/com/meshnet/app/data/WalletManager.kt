package com.meshnet.app.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.meshnet.app.models.MessagePriority

/**
 * Manages the data credit wallet. Implementation of Section 8.
 */
class WalletManager(context: Context) {

    private val prefs = context.getSharedPreferences("meshnet_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_BALANCE = "meshnet_wallet_balance"
        private const val KEY_TRANSACTIONS = "meshnet_wallet_transactions"
    }

    fun getBalance(): Float {
        return prefs.getFloat(KEY_BALANCE, 10.0f) // Start with 10MB for testing
    }

    fun deductCredits(amount: Float, reason: String): Boolean {
        val current = getBalance()
        if (current < amount) return false
        
        val newBalance = current - amount
        prefs.edit { putFloat(KEY_BALANCE, newBalance) }
        addTransactionLog("-$amount MB: $reason")
        return true
    }

    fun addCredits(amount: Float, reason: String) {
        val newBalance = getBalance() + amount
        prefs.edit { putFloat(KEY_BALANCE, newBalance) }
        addTransactionLog("+$amount MB: $reason")
    }

    fun deductForMessage(priority: MessagePriority): Boolean {
        return when (priority) {
            MessagePriority.NORMAL -> deductCredits(1.2f, "Sent Normal Message")
            MessagePriority.URGENT -> deductCredits(3.6f, "Sent Urgent Message")
            MessagePriority.SOS -> true // SOS is free
        }
    }

    fun rewardForRelay(delivered: Boolean): Float {
        val reward = if (delivered) 0.8f else 0.1f
        addCredits(reward, if (delivered) "Relay Delivered" else "Relay Carried")
        return reward
    }

    fun getTransactionLog(): List<String> {
        val json = prefs.getString(KEY_TRANSACTIONS, "[]")
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun addTransactionLog(entry: String) {
        val logs = getTransactionLog().toMutableList()
        logs.add(0, "${System.currentTimeMillis()}: $entry")
        if (logs.size > 50) logs.removeAt(50)
        prefs.edit { putString(KEY_TRANSACTIONS, gson.toJson(logs)) }
    }
}
