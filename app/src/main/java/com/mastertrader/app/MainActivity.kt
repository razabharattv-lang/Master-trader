package com.mastertrader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mastertrader.app.ui.ChatScreen
import com.mastertrader.app.ui.theme.PurePitchBlack
import com.mastertrader.app.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PurePitchBlack),
                color = PurePitchBlack
            ) {
                ChatScreen(viewModel = viewModel)
            }
        }
    }
}
