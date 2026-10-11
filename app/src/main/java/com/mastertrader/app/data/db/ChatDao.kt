package com.mastertrader.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY lastUpdated DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionSync(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllHistory()

    @Query("SELECT DISTINCT sessionId FROM chat_messages ORDER BY timestamp DESC")
    fun getAllSessionIds(): Flow<List<String>>

    @Query("SELECT * FROM active_trades ORDER BY timestamp DESC")
    fun getActiveTrades(): Flow<List<ActiveTradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: ActiveTradeEntity)

    @Update
    suspend fun updateTrade(trade: ActiveTradeEntity)

    @Query("DELETE FROM active_trades WHERE id = :tradeId")
    suspend fun deleteTrade(tradeId: String)

    @Query("SELECT * FROM paper_trades ORDER BY entryTime DESC")
    fun getAllPaperTrades(): Flow<List<PaperTradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaperTrade(trade: PaperTradeEntity)

    @Query("UPDATE paper_trades SET exitPrice = :exitPrice, status = :status, pnlPercent = :pnlPercent, exitTime = :exitTime WHERE id = :id")
    suspend fun updatePaperTradeExit(id: String, exitPrice: Double, status: String, pnlPercent: Double, exitTime: Long)
}
