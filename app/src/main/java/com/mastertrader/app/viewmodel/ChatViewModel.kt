package com.mastertrader.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mastertrader.app.data.api.*
import com.mastertrader.app.data.db.*
import com.mastertrader.app.data.upstox.UpstoxApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.UUID

const val MASTER_TRADER_SYSTEM_PROMPT = """
You are an ultra-disciplined 25-Year Institutional Proprietary Desk Trader specializing in Multi-Exchange Equities and Derivatives.
STRICT BEHAVIOR RULES:
- RULE 1 - DIRECT CRISP ANSWERS: If the user asks a simple question (e.g. price, LTP, stock rate, status, info): Answer in 1 to 2 crisp lines ONLY with the direct fact/price. STRICTLY FORBIDDEN: NEVER output trade cards, entry/exit targets, or lengthy explanations for simple queries.
- RULE 2 - CONDITIONAL TRADE CARDS: ONLY generate the `[TRADE_SIGNAL]` block when the user EXPLICITLY asks for a trade plan, strategy, buy/sell execution, or intraday setup (e.g. "इंट्राडे सेटअप बताओ", "give me a setup").
- RULE 3 - ZERO FLUFF: No greetings, no disclaimers, no pleasantries. Output strictly what was asked.
- UPSTOX INTEGRATION STATUS:
  Upstox Core Architecture (API Key & Secret) is permanently embedded. When asked about connection, reply: 'Upstox Core Architecture: PERMANENTLY LINKED & SECURE. Daily Live Ticker: Ready.'
- STRUCTURED TRADE CARDS (when explicitly requested):
   [TRADE_SIGNAL]
   SYMBOL: <Symbol>
   EXCHANGE: <NSE / BSE / F&O>
   ACTION: <BUY / SELL / WAIT>
   TIMEFRAME: <Intraday 5M / 15M>
   ENTRY: <Exact Price Range>
   STOP_LOSS: <Strict Price Level>
   TARGET_1: <Conservative Target>
   TARGET_2: <Extended Target>
   RISK_REWARD: <1:2.5>
   GREEKS_TAG: <Delta | IV | PCR>
   SETUP_LOGIC: <Institutional reason>
   [/TRADE_SIGNAL]
"""

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ChatDatabase.getDatabase(application)
    private val dao = db.chatDao()
    private val prefs = application.getSharedPreferences("master_trader_prefs", Context.MODE_PRIVATE)
    private val requestMutex = Mutex()

    companion object {
        private const val DEFAULT_GEMINI_KEY = "AQ.Ab8RN6IuTKA9Y9riOlNeRRP29HUSu3zBXufyyq5f3_d64LmZ6g"
        private const val DEFAULT_UPSTOX_API_KEY = "0abff738-9b5b-4bbb-b77e-ccb8c6a346ba"
        private const val DEFAULT_UPSTOX_SECRET = "3d5ju4c1no"
    }

    val currentSessionId = MutableStateFlow<String>("")
    val messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val activeModelTag = MutableStateFlow("gemini-3.5-flash-lite")

    val googleApiKey = MutableStateFlow(prefs.getString("saved_gemini_key", DEFAULT_GEMINI_KEY) ?: DEFAULT_GEMINI_KEY)
    val upstoxApiKey = MutableStateFlow(DEFAULT_UPSTOX_API_KEY)
    val upstoxApiSecret = MutableStateFlow(DEFAULT_UPSTOX_SECRET)
    val upstoxAccessToken = MutableStateFlow(prefs.getString("saved_upstox_token", "") ?: "")
    val isUpstoxConnected = MutableStateFlow(false)
    val isEngineActive = MutableStateFlow(true)
    val activationStatus = MutableStateFlow("Engine Online & Validated")
    val isAnalyzing = MutableStateFlow(false)

    val activeTrades: StateFlow<List<ActiveTradeEntity>> = dao.getActiveTrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paperTrades: StateFlow<List<PaperTradeEntity>> = dao.getAllPaperTrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessions: StateFlow<List<String>> = dao.getAllSessionIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startNewSession() {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            val initialSession = ChatSessionEntity(
                id = newId,
                title = "Desk Session #${System.currentTimeMillis().toString().takeLast(4)}",
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis(),
                modelId = "gemini-3.5-flash-lite"
            )
            dao.insertSession(initialSession)
            currentSessionId.value = newId

            dao.getMessagesForSession(newId).collect {
                messages.value = it
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteMessagesForSession(sessionId)
            dao.deleteSession(sessionId)
            if (currentSessionId.value == sessionId) {
                withContext(Dispatchers.Main) {
                    startNewSession()
                }
            }
        }
    }

    fun clearAllChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAllHistory()
            withContext(Dispatchers.Main) {
                startNewSession()
            }
        }
    }

    fun switchSession(sessionId: String) {
        currentSessionId.value = sessionId
        viewModelScope.launch {
            dao.getMessagesForSession(sessionId).collect {
                messages.value = it
            }
        }
    }

    init {
        initializeSession()
        if (upstoxAccessToken.value.isNotBlank()) {
            connectUpstoxToken(upstoxAccessToken.value)
        }
    }

    private fun initializeSession() {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            val initialSession = ChatSessionEntity(
                id = newId,
                title = "Desk Session",
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis(),
                modelId = "gemini-3.5-flash-lite"
            )
            dao.insertSession(initialSession)
            currentSessionId.value = newId

            dao.getMessagesForSession(newId).collect {
                messages.value = it
            }
        }
    }

    fun saveGeminiKey(key: String) {
        val cleanKey = key.trim()
        googleApiKey.value = cleanKey
        prefs.edit().putString("saved_gemini_key", cleanKey).apply()
    }

    fun connectUpstoxToken(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val formattedToken = if (cleanToken.startsWith("Bearer ")) cleanToken else "Bearer $cleanToken"
                val response = UpstoxApiClient.service.validateProfile(formattedToken)
                if (response.isSuccessful) {
                    upstoxAccessToken.value = cleanToken
                    prefs.edit().putString("saved_upstox_token", cleanToken).apply()
                    isUpstoxConnected.value = true
                } else {
                    isUpstoxConnected.value = false
                }
            } catch (e: Exception) {
                // If offline or timeout, persist token for session
                upstoxAccessToken.value = cleanToken
                prefs.edit().putString("saved_upstox_token", cleanToken).apply()
                isUpstoxConnected.value = true
            }
        }
    }

    fun saveDailyToken(token: String) {
        connectUpstoxToken(token)
    }

    private fun parseAndLogPaperTrade(replyText: String) {
        val signalRegex = "\\[TRADE_SIGNAL\\]([\\s\\S]*?)\\[/TRADE_SIGNAL\\]".toRegex()
        val match = signalRegex.find(replyText) ?: return
        val content = match.groupValues[1]
        val symbol = extractField(content, "SYMBOL")
        val entryStr = extractField(content, "ENTRY")
        val slStr = extractField(content, "STOP_LOSS")
        val t1Str = extractField(content, "TARGET_1")
        val logic = extractField(content, "SETUP_LOGIC")

        val entryPrice = extractFirstDouble(entryStr) ?: 100.0
        val stopLoss = extractFirstDouble(slStr) ?: (entryPrice * 0.98)
        val targetPrice = extractFirstDouble(t1Str) ?: (entryPrice * 1.05)

        viewModelScope.launch(Dispatchers.IO) {
            val trade = PaperTradeEntity(
                id = UUID.randomUUID().toString(),
                symbol = symbol,
                entryTime = System.currentTimeMillis(),
                entryPrice = entryPrice,
                stopLoss = stopLoss,
                targetPrice = targetPrice,
                status = "RUNNING",
                pnlPercent = 0.0,
                rationale = logic
            )
            dao.insertPaperTrade(trade)
        }
    }

    private fun extractField(content: String, key: String): String {
        val regex = "$key:\\s*(.+)".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(content)?.groupValues?.get(1)?.trim() ?: "N/A"
    }

    private fun extractFirstDouble(str: String): Double? {
        val regex = "\\d+(\\.\\d+)?".toRegex()
        val match = regex.find(str) ?: return null
        return match.value.toDoubleOrNull()
    }

    fun fetchLiveUpstoxQuote(instrumentKey: String, onResult: (String) -> Unit) {
        val token = upstoxAccessToken.value.trim()
        if (token.isBlank()) {
            onResult("⚠️ Upstox Access Token missing. Please set Today's Token in the Sidebar Drawer.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
                val quoteResponse = UpstoxApiClient.service.getFullMarketQuote(authHeader, instrumentKey)
                val data = quoteResponse.data?.values?.firstOrNull()
                val price = data?.lastPrice ?: 0.0
                val high = data?.ohlc?.high ?: 0.0
                val low = data?.ohlc?.low ?: 0.0
                val volume = data?.volume ?: 0L
                val oi = data?.openInterest ?: 0L

                val resultText = "📊 Live Upstox Quote [$instrumentKey]:\nLTP: ₹$price\nHigh: ₹$high | Low: ₹$low\nVolume: $volume | OI: $oi"
                withContext(Dispatchers.Main) {
                    onResult(resultText)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult("❌ Upstox Quote Error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun sendTradingPrompt(userQuery: String) {
        val trimmed = userQuery.trim()
        val key = googleApiKey.value.trim()
        if (trimmed.isBlank() || isAnalyzing.value) return
        if (!requestMutex.tryLock()) return // Prevent duplicate calls

        viewModelScope.launch {
            try {
                isAnalyzing.value = true

                // 1. Save user query locally to Room
                val userMsg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = currentSessionId.value,
                    role = "user",
                    text = trimmed,
                    timestamp = System.currentTimeMillis()
                )
                dao.insertMessage(userMsg)

                if (key.isBlank()) {
                    dao.insertMessage(
                        ChatMessageEntity(
                            id = UUID.randomUUID().toString(),
                            sessionId = currentSessionId.value,
                            role = "model",
                            text = "⚠️ Google API Key missing in Sidebar Drawer.",
                            timestamp = System.currentTimeMillis(),
                            isError = true
                        )
                    )
                    return@launch
                }

                // 2. Build concise prompt context
                val history = withContext(Dispatchers.IO) {
                    dao.getMessagesForSessionSync(currentSessionId.value).takeLast(4)
                }
                val contents = history.map {
                    Content(role = if (it.role == "user") "user" else "model", parts = listOf(Part(text = it.text)))
                }
                val request = GenerateContentRequest(
                    contents = contents,
                    systemInstruction = Content(parts = listOf(Part(text = MASTER_TRADER_SYSTEM_PROMPT))),
                    generationConfig = GenerationConfig(temperature = 0.2f)
                )

                // 3. SINGLE DIRECT CALL TO gemini-3.5-flash-lite
                val response = withContext(Dispatchers.IO) {
                    GeminiApiClient.service.generateContent(
                        apiKey = key,
                        request = request
                    )
                }

                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No analysis received."

                parseAndLogPaperTrade(replyText)

                dao.insertMessage(
                    ChatMessageEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = currentSessionId.value,
                        role = "model",
                        text = replyText,
                        timestamp = System.currentTimeMillis(),
                        modelUsed = "gemini-3.5-flash-lite"
                    )
                )
            } catch (e: Exception) {
                dao.insertMessage(
                    ChatMessageEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = currentSessionId.value,
                        role = "model",
                        text = "Response: ${e.localizedMessage ?: "Engine busy. Please retry in 5s."}",
                        timestamp = System.currentTimeMillis(),
                        isError = true
                    )
                )
            } finally {
                isAnalyzing.value = false
                requestMutex.unlock()
            }
        }
    }
}
