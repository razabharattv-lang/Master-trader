package com.mastertrader.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mastertrader.app.data.db.ChatMessageEntity
import com.mastertrader.app.data.db.PaperTradeEntity
import com.mastertrader.app.ui.theme.*
import com.mastertrader.app.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

sealed class MessageContentBlock {
    data class Text(val content: String) : MessageContentBlock()
    data class TradeSignal(
        val symbol: String,
        val exchange: String,
        val action: String,
        val timeframe: String,
        val entry: String,
        val stopLoss: String,
        val target1: String,
        val target2: String,
        val riskReward: String,
        val greeksTag: String,
        val logic: String
    ) : MessageContentBlock()
    data class ExitAlert(
        val symbol: String,
        val warning: String,
        val action: String,
        val currentPrice: String
    ) : MessageContentBlock()
}

fun parseMessageText(rawText: String): List<MessageContentBlock> {
    val blocks = mutableListOf<MessageContentBlock>()
    val signalRegex = "\\[TRADE_SIGNAL\\]([\\s\\S]*?)\\[/TRADE_SIGNAL\\]".toRegex()
    val alertRegex = "\\[EXIT_ALERT\\]([\\s\\S]*?)\\[/EXIT_ALERT\\]".toRegex()

    var lastIndex = 0
    val matchResults = (signalRegex.findAll(rawText) + alertRegex.findAll(rawText)).sortedBy { it.range.first }

    for (match in matchResults) {
        if (match.range.first > lastIndex) {
            val plainText = rawText.substring(lastIndex, match.range.first).trim()
            if (plainText.isNotEmpty()) {
                blocks.add(MessageContentBlock.Text(plainText))
            }
        }
        val inner = match.value
        if (inner.startsWith("[TRADE_SIGNAL]")) {
            val content = match.groupValues[1]
            blocks.add(
                MessageContentBlock.TradeSignal(
                    symbol = extractField(content, "SYMBOL"),
                    exchange = extractField(content, "EXCHANGE"),
                    action = extractField(content, "ACTION"),
                    timeframe = extractField(content, "TIMEFRAME"),
                    entry = extractField(content, "ENTRY"),
                    stopLoss = extractField(content, "STOP_LOSS"),
                    target1 = extractField(content, "TARGET_1"),
                    target2 = extractField(content, "TARGET_2"),
                    riskReward = extractField(content, "RISK_REWARD"),
                    greeksTag = extractField(content, "GREEKS_TAG"),
                    logic = extractField(content, "SETUP_LOGIC")
                )
            )
        } else if (inner.startsWith("[EXIT_ALERT]")) {
            val content = match.groupValues[1]
            blocks.add(
                MessageContentBlock.ExitAlert(
                    symbol = extractField(content, "SYMBOL"),
                    warning = extractField(content, "WARNING"),
                    action = extractField(content, "ACTION"),
                    currentPrice = extractField(content, "CURRENT_PRICE")
                )
            )
        }
        lastIndex = match.range.last + 1
    }

    if (lastIndex < rawText.length) {
        val remaining = rawText.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            blocks.add(MessageContentBlock.Text(remaining))
        }
    }

    if (blocks.isEmpty()) {
        blocks.add(MessageContentBlock.Text(rawText))
    }

    return blocks
}

private fun extractField(content: String, key: String): String {
    val regex = "$key:\\s*(.+)".toRegex(RegexOption.IGNORE_CASE)
    return regex.find(content)?.groupValues?.get(1)?.trim() ?: "N/A"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val messages by viewModel.messages.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val activeModel by viewModel.activeModelTag.collectAsState()
    val paperTrades by viewModel.paperTrades.collectAsState()

    val googleKey by viewModel.googleApiKey.collectAsState()
    val upstoxKey by viewModel.upstoxApiKey.collectAsState()
    val upstoxSecret by viewModel.upstoxApiSecret.collectAsState()
    val upstoxToken by viewModel.upstoxAccessToken.collectAsState()
    val isEngineActive by viewModel.isEngineActive.collectAsState()
    val activationStatus by viewModel.activationStatus.collectAsState()

    var secretVisible by remember { mutableStateOf(false) }
    var showScorecardModal by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = PurePitchBlack,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(340.dp)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "MASTER TRADER AI",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = TradingGreen,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Release-Ready Quant Desk",
                                fontSize = 11.sp,
                                color = TextMediumEmphasis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isEngineActive) TradingGreen.copy(alpha = 0.2f) else DarkSurfaceElevated,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(1.dp, if (isEngineActive) TradingGreen else BorderSubtle, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isEngineActive) "STATUS: ACTIVE" else "STATUS: STANDBY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isEngineActive) TradingGreen else TextMediumEmphasis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("GOOGLE GEMINI API KEY", fontSize = 10.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = googleKey,
                        onValueChange = { viewModel.googleApiKey.value = it },
                        placeholder = { Text("AI Studio Key डालें...", color = TextDisabled, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated,
                            focusedBorderColor = TradingGreen,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextHighEmphasis,
                            unfocusedTextColor = TextHighEmphasis
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("UPSTOX API KEY", fontSize = 10.sp, color = ElectricGold, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = upstoxKey,
                        onValueChange = { viewModel.upstoxApiKey.value = it },
                        placeholder = { Text("Upstox API Key...", color = TextDisabled, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated,
                            focusedBorderColor = ElectricGold,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextHighEmphasis,
                            unfocusedTextColor = TextHighEmphasis
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("UPSTOX API SECRET", fontSize = 10.sp, color = ElectricGold, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = upstoxSecret,
                        onValueChange = { viewModel.upstoxApiSecret.value = it },
                        placeholder = { Text("Upstox API Secret...", color = TextDisabled, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { secretVisible = !secretVisible }) {
                                Icon(
                                    imageVector = if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Secret",
                                    tint = TextMediumEmphasis
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated,
                            focusedBorderColor = ElectricGold,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextHighEmphasis,
                            unfocusedTextColor = TextHighEmphasis
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("UPSTOX ACCESS TOKEN", fontSize = 10.sp, color = ElectricGold, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = upstoxToken,
                        onValueChange = { viewModel.upstoxAccessToken.value = it },
                        placeholder = { Text("Bearer Token डालें...", color = TextDisabled, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated,
                            focusedBorderColor = ElectricGold,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextHighEmphasis,
                            unfocusedTextColor = TextHighEmphasis
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassmorphismCard, RoundedCornerShape(10.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("ACTIVE ENGINE MODEL", fontSize = 10.sp, color = TextMediumEmphasis)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(activeModel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TradingGreen)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Activation: $activationStatus", fontSize = 11.sp, color = CyberCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            viewModel.activateAndSaveCredentials()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TradingGreen,
                            contentColor = PurePitchBlack
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "SAVE & ACTIVATE ENGINE",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Build: Release-Ready Quant 3.5",
                        fontSize = 10.sp,
                        color = TextDisabled
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = PurePitchBlack,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PurePitchBlack),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isEngineActive) TradingGreen else ElectricGold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MASTER TRADER AI",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextHighEmphasis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextHighEmphasis)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showScorecardModal = true }) {
                            Icon(Icons.Default.Assessment, contentDescription = "Scorecard", tint = TradingGreen)
                        }
                    }
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PurePitchBlack)
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    // Quick-trigger chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        val quickPrompts = listOf(
                            "⚡ 0-DTE Expiry Setup & Option Chain",
                            "🚨 NIFTY Intraday Levels",
                            "⚡ High-Volume Breakouts",
                            "📊 Penny Stock Quant Scan",
                            "🛡️ Track My Open Position"
                        )
                        items(quickPrompts) { prompt ->
                            OutlinedButton(
                                onClick = { viewModel.sendTradingPrompt(prompt) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceElevated, contentColor = TextHighEmphasis),
                                border = BorderStroke(1.dp, BorderSubtle),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(prompt, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Gemini-Style Floating Glassmorphic Chat Dock with IME padding and explicit typing colors
                    var text by remember { mutableStateOf("") }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF101216))
                            .border(BorderStroke(1.dp, Color(0xFF1E222B)), RoundedCornerShape(28.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.sendTradingPrompt("Scan previous-day institutional bulk deals & delivery spikes") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.AddCircleOutline,
                                    contentDescription = "Scan Chart / Attach",
                                    tint = Color(0xFF00E5FF)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = { Text("Ask Master Trader (e.g. Bought RELIANCE at 2850)...", color = Color(0xFF6B7280), fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF16181D),
                                    unfocusedContainerColor = Color(0xFF16181D),
                                    disabledContainerColor = Color(0xFF16181D),
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color(0xFFFFFFFF),
                                    unfocusedTextColor = Color(0xFFFFFFFF),
                                    cursorColor = Color(0xFF00E676)
                                ),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = { viewModel.sendTradingPrompt("Voice / Mic quant status check") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Voice Input",
                                    tint = Color(0xFF9E9E9E)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    if (text.isNotBlank() && !isAnalyzing) {
                                        viewModel.sendTradingPrompt(text)
                                        text = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = PurePitchBlack
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            val listState = rememberLazyListState()

            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("MASTER TRADER AI", fontWeight = FontWeight.Black, fontSize = 24.sp, color = TradingGreen)
                                Text("Release-Ready Institutional Quant Engine", color = TextMediumEmphasis, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(24.dp))
                                listOf(
                                    "⚡ 0-DTE Expiry Setup & Option Chain",
                                    "Analyze NIFTY 50 & BankNifty Derivatives",
                                    "Evaluate High-Volume Breakout Setups",
                                    "Options Delta & Risk-Reward Calculation"
                                ).forEach { prompt ->
                                    OutlinedButton(
                                        onClick = { viewModel.sendTradingPrompt(prompt) },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceElevated, contentColor = TextHighEmphasis),
                                        border = BorderStroke(1.dp, BorderSubtle),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(prompt, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    items(messages, key = { it.id }) { msg ->
                        val isUser = msg.role == "user"
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                        ) {
                            if (isUser) {
                                // User Message: Right-aligned sleek dark card (Color(0xFF161A22)) with subtle cyber-cyan trim
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 320.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF161A22))
                                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = msg.text,
                                        color = TextHighEmphasis,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            } else {
                                // Model / Master Trader Card: Left-aligned institutional card (Color(0xFF0D0F12)) with neon-emerald avatar badge ("MT 25Y QUANT")
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(TradingGreen, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("MT 25Y QUANT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = PurePitchBlack)
                                        }
                                    }

                                    val blocks = parseMessageText(msg.text)
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (block in blocks) {
                                            when (block) {
                                                is MessageContentBlock.Text -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(16.dp))
                                                            .background(Color(0xFF0D0F12))
                                                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                                            .padding(14.dp)
                                                    ) {
                                                        Text(
                                                            text = block.content,
                                                            color = if (msg.isError) TradingRed else TextHighEmphasis,
                                                            fontSize = 14.sp,
                                                            lineHeight = 20.sp
                                                        )
                                                    }
                                                }
                                                is MessageContentBlock.TradeSignal -> {
                                                    val isBuy = block.action.contains("BUY", ignoreCase = true)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(16.dp))
                                                            .background(GlassmorphismCard)
                                                            .border(2.dp, if (isBuy) TradingGreen else TradingRed, RoundedCornerShape(16.dp))
                                                            .padding(16.dp)
                                                    ) {
                                                        Column {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = block.symbol,
                                                                    fontWeight = FontWeight.Black,
                                                                    fontSize = 18.sp,
                                                                    color = TextHighEmphasis
                                                                )
                                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .background(CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                                            .border(1.dp, CyberCyan, RoundedCornerShape(6.dp))
                                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                                    ) {
                                                                        Text(text = block.exchange, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                                                                    }
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .background(if (isBuy) TradingGreen.copy(alpha = 0.2f) else TradingRed.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                                            .border(1.dp, if (isBuy) TradingGreen else TradingRed, RoundedCornerShape(6.dp))
                                                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = block.action.uppercase(),
                                                                            fontWeight = FontWeight.Black,
                                                                            fontSize = 12.sp,
                                                                            color = if (isBuy) TradingGreen else TradingRed
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            Spacer(modifier = Modifier.height(10.dp))
                                                            Text(text = "Timeframe / Expiry: ${block.timeframe}", fontSize = 11.sp, color = TextMediumEmphasis)
                                                            Spacer(modifier = Modifier.height(8.dp))

                                                            // Metrics grid
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                MetricItem("ENTRY", block.entry, CyberCyan)
                                                                MetricItem("STOP LOSS", block.stopLoss, TradingRed)
                                                            }
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                MetricItem("TARGET 1", block.target1, TradingGreen)
                                                                MetricItem("TARGET 2", block.target2, TradingGreen)
                                                            }

                                                            Spacer(modifier = Modifier.height(10.dp))
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(text = "R:R ${block.riskReward}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricGold)
                                                                Text(text = block.greeksTag, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CyberCyan)
                                                            }

                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Divider(color = BorderSubtle, thickness = 1.dp)
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Text(
                                                                text = "Setup Logic: ${block.logic}",
                                                                fontSize = 12.sp,
                                                                color = TextMediumEmphasis,
                                                                lineHeight = 16.sp
                                                            )
                                                        }
                                                    }
                                                }
                                                is MessageContentBlock.ExitAlert -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(16.dp))
                                                            .background(TradingRed)
                                                            .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                                                            .padding(16.dp)
                                                    ) {
                                                        Column {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(Icons.Default.Warning, contentDescription = "Alert", tint = PurePitchBlack)
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = "🚨 EARLY LOSS EXIT SENTINEL",
                                                                    fontWeight = FontWeight.Black,
                                                                    fontSize = 15.sp,
                                                                    color = PurePitchBlack
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                text = "Symbol: ${block.symbol} | LTP: ${block.currentPrice}",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 13.sp,
                                                                color = PurePitchBlack
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = "Warning: ${block.warning}",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 13.sp,
                                                                color = PurePitchBlack,
                                                                lineHeight = 18.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = "Action: ${block.action}",
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 12.sp,
                                                                color = PurePitchBlack
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isAnalyzing) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TradingGreen, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Executing Release-Ready Quant Analysis...", color = TradingGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }

    // 10-Day Paper Trading Scorecard Dialog
    if (showScorecardModal) {
        AlertDialog(
            onDismissRequest = { showScorecardModal = false },
            containerColor = PurePitchBlack,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = TradingGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📊 10-Day Paper Trading Scorecard", color = TextHighEmphasis, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val totalTrades = paperTrades.size
                    val runningTrades = paperTrades.count { it.status == "RUNNING" }
                    val winTrades = paperTrades.count { it.pnlPercent > 0 }
                    val winRate = if (totalTrades > 0) (winTrades * 100) / totalTrades else 0

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassmorphismCard, RoundedCornerShape(10.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("VIRTUAL DESK PERFORMANCE SUMMARY", fontSize = 10.sp, color = TextMediumEmphasis, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                ScoreItem("Total Trades", "$totalTrades")
                                ScoreItem("Active Running", "$runningTrades")
                                ScoreItem("Win Rate", "$winRate%")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("LOGGED PAPER TRADES:", fontSize = 11.sp, color = CyberCyan, fontWeight = FontWeight.Bold)

                    if (paperTrades.isEmpty()) {
                        Text("No paper trades logged yet. Ask Master Trader for a trade signal to auto-log.", fontSize = 12.sp, color = TextDisabled)
                    } else {
                        for (trade in paperTrades) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurfaceElevated, RoundedCornerShape(10.dp))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(trade.symbol, fontWeight = FontWeight.Bold, color = TextHighEmphasis, fontSize = 13.sp)
                                        Text(trade.status, fontWeight = FontWeight.Bold, color = if (trade.status == "RUNNING") ElectricGold else TradingGreen, fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Entry: ₹${trade.entryPrice} | SL: ₹${trade.stopLoss} | Tgt: ₹${trade.targetPrice}", fontSize = 11.sp, color = TextMediumEmphasis)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Rationale: ${trade.rationale}", fontSize = 11.sp, color = TextDisabled, lineHeight = 14.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showScorecardModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = TradingGreen, contentColor = PurePitchBlack),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun MetricItem(label: String, value: String, valueColor: Color) {
    Column(
        modifier = Modifier
            .background(DarkSurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .width(140.dp)
    ) {
        Text(text = label, fontSize = 9.sp, color = TextMediumEmphasis, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ScoreItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = TextMediumEmphasis)
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextHighEmphasis)
    }
}
