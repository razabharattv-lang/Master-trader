package com.mastertrader.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mastertrader.app.data.api.*
import com.mastertrader.app.data.db.*
import com.mastertrader.app.data.upstox.UpstoxApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

const val MASTER_TRADER_SYSTEM_PROMPT = """
You are MASTER TRADER AI, an elite institutional hedge fund intraday proprietary trader with 25+ years of market mastery specializing in Multi-Exchange Equities (NSE, BSE, US Stocks) and Derivatives (F&O, Options Chain, Greeks).
OPERATIONAL MANDATE:
1. QUANTITATIVE INFLOW SCREENING: Use Google Search tool to scan for previous-day institutional bulk deals, QIP inflows, high delivery percentages, and breakout patterns before recommending a BUY.
2. PRECISE BUY/SELL TIMING:
   - BUY TRIGGER: Only trigger BUY when price breaks above previous 15-minute high with 1.5x average volume and RSI > 55.
   - SELL / SHORT TRIGGER: Trigger when breakdown below VWAP occurs with heavy red volume.
3. EARLY WARNING SYSTEM (CRITICAL):
   - You must detect trend exhaustion before major drops occur.
   - If a recommended stock shows bearish wick rejection, volume drying up at resistance, or break below trailing support, immediately generate:
   [EXIT_ALERT]
   SYMBOL: <Symbol>
   WARNING: Momentum exhausted / Institutional selling detected.
   ACTION: EXIT IMMEDIATELY / SECURE PROFITS
   CURRENT_PRICE: <Price>
   [/EXIT_ALERT]
4. STRUCTURED TRADE SIGNALS: When asked for a trade, level, or stock suggestion, ALWAYS output a structured block that the UI can parse into a Trading Card:
   [TRADE_SIGNAL]
   SYMBOL: <Symbol (e.g. NIFTY 24500 CE / RELIANCE)>
   EXCHANGE: <NSE F&O / NSE CASH / BSE / US EQUITIES>
   ACTION: <BUY / SELL / WAIT>
   TIMEFRAME: <Intraday 5M / 15M / 0-DTE>
   ENTRY: <Exact Price Range>
   STOP_LOSS: <Strict Price Level>
   TARGET_1: <Conservative Target>
   TARGET_2: <Extended Target>
   RISK_REWARD: <e.g. 1:2.5>
   GREEKS_TAG: <Delta > 0.60 | IV: 14.5% | PCR: 1.15>
   SETUP_LOGIC: <Institutional reason: Options chain OI wall, VWAP cross, volume spike, support level>
   [/TRADE_SIGNAL]
5. TONE: Sharp, decisive, honest, realistic, zero fluff. Speak with absolute authority and disciplined risk management.
"""

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ChatDatabase.getDatabase(application)
    private val dao = db.chatDao()

    val currentSessionId = MutableStateFlow<String>("")
    val messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val isAnalyzing = MutableStateFlow(false)
    val activeModelTag = MutableStateFlow("Dynamic (Auto-Detect)")

    val googleApiKey = MutableStateFlow("")
    val upstoxApiKey = MutableStateFlow("")
    val upstoxApiSecret = MutableStateFlow("")
    val upstoxAccessToken = MutableStateFlow("")
    val isEngineActive = MutableStateFlow(false)
    val activationStatus = MutableStateFlow("STANDBY")

    val activeTrades: StateFlow<List<ActiveTradeEntity>> = dao.getActiveTrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paperTrades: StateFlow<List<PaperTradeEntity>> = dao.getAllPaperTrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        initializeSession()
    }

    private fun initializeSession() {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            val initialSession = ChatSessionEntity(
                id = newId,
                title = "Desk Session",
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis(),
                modelId = "dynamic-resolved"
            )
            dao.insertSession(initialSession)
            currentSessionId.value = newId

            dao.getMessagesForSession(newId).collect {
                messages.value = it
            }
        }
    }

    fun activateAndSaveCredentials() {
        viewModelScope.launch {
            if (googleApiKey.value.isNotBlank()) {
                isEngineActive.value = true
                activationStatus.value = "ACTIVE & CONNECTED"
                resolveLightestModel(googleApiKey.value.trim()) {}
            } else {
                activationStatus.value = "KEY REQUIRED"
                isEngineActive.value = false
            }
        }
    }

    private fun resolveLightestModel(apiKey: String, onResolved: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = GeminiApiClient.service.listModels(apiKey)
                val candidates = response.models?.filter {
                    it.supportedGenerationMethods?.contains("generateContent") == true
                } ?: emptyList()

                val chosen = candidates.firstOrNull { it.name.contains("flash-lite", ignoreCase = true) }
                    ?: candidates.firstOrNull { it.name.contains("flash", ignoreCase = true) }
                    ?: candidates.firstOrNull()

                val cleanModelName = (chosen?.name ?: "models/gemini-2.5-flash").removePrefix("models/")
                withContext(Dispatchers.Main) {
                    activeModelTag.value = cleanModelName
                    onResolved(cleanModelName)
                }
            } catch (e: Exception) {
                Log.e("MasterTrader", "Fallback to default model", e)
                withContext(Dispatchers.Main) {
                    activeModelTag.value = "gemini-2.5-flash"
                    onResolved("gemini-2.5-flash")
                }
            }
        }
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

    fun sendTradingPrompt(userQuery: String) {
        if (userQuery.isBlank() || isAnalyzing.value) return
        val sessionId = currentSessionId.value
        val key = googleApiKey.value.trim()

        viewModelScope.launch {
            val userMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "user",
                text = userQuery,
                timestamp = System.currentTimeMillis()
            )
            dao.insertMessage(userMsg)

            if (key.isBlank()) {
                val errorMsg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "model",
                    text = "⚠️ Google API Key missing! Sidebar Drawer खोलकर API Key दर्ज करें और Activate करें।",
                    timestamp = System.currentTimeMillis(),
                    isError = true
                )
                dao.insertMessage(errorMsg)
                return@launch
            }

            isAnalyzing.value = true

            resolveLightestModel(key) { resolvedModel ->
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val pastMessages = dao.getMessagesForSessionSync(sessionId)
                        val contents = pastMessages.map { msg ->
                            Content(
                                role = if (msg.role == "user") "user" else "model",
                                parts = listOf(Part(text = msg.text))
                            )
                        }

                        val request = GenerateContentRequest(
                            contents = contents,
                            systemInstruction = Content(
                                parts = listOf(Part(text = MASTER_TRADER_SYSTEM_PROMPT))
                            ),
                            generationConfig = GenerationConfig(temperature = 0.3f),
                            tools = listOf(Tool())
                        )

                        val response = GeminiApiClient.service.generateContent(
                            model = resolvedModel,
                            apiKey = key,
                            request = request
                        )

                        val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            ?: response.error?.message
                            ?: "बाज़ार विश्लेषण इंजन से उत्तर प्राप्त नहीं हुआ।"

                        // Automatically log virtual paper trade if trade signal is generated
                        parseAndLogPaperTrade(replyText)

                        val botMsg = ChatMessageEntity(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            role = "model",
                            text = replyText,
                            timestamp = System.currentTimeMillis(),
                            modelUsed = resolvedModel
                        )
                        dao.insertMessage(botMsg)
                    } catch (e: Exception) {
                        dao.insertMessage(
                            ChatMessageEntity(
                                id = UUID.randomUUID().toString(),
                                sessionId = sessionId,
                                role = "model",
                                text = "Engine Error: ${e.localizedMessage}",
                                timestamp = System.currentTimeMillis(),
                                isError = true
                            )
                        )
                    } finally {
                        isAnalyzing.value = false
                    }
                }
            }
        }
    }
}
