package com.mastertrader.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val lastUpdated: Long,
    val modelId: String
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val text: String,
    val imageBase64: String? = null,
    val timestamp: Long,
    val isError: Boolean = false,
    val modelUsed: String? = null
)

@Entity(tableName = "active_trades")
data class ActiveTradeEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val buyPrice: Double,
    val stopLoss: Double,
    val targetPrice: Double,
    val quantity: Int,
    val trailingStopLoss: Double,
    val status: String, // "OPEN", "PROFIT_EXIT", "STOPPED_OUT"
    val timestamp: Long
)

@Entity(tableName = "paper_trades")
data class PaperTradeEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val entryTime: Long,
    val entryPrice: Double,
    val stopLoss: Double,
    val targetPrice: Double,
    val exitTime: Long? = null,
    val exitPrice: Double? = null,
    val status: String, // "RUNNING", "PROFIT_TARGET_HIT", "STOP_LOSS_HIT", "EARLY_EXIT_ALERT"
    val pnlPercent: Double = 0.0,
    val rationale: String
)
