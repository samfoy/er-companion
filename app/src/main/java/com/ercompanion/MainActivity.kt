package com.ercompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ercompanion.ui.MainScreen
import com.ercompanion.ui.theme.ERCompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ERCompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = viewModel()
                    val connectionState by viewModel.connectionState.collectAsState()
                    val partyState by viewModel.partyState.collectAsState()
                    val scanningState by viewModel.scanningState.collectAsState()
                    val errorMessage by viewModel.errorMessage.collectAsState()

                    MainScreen(
                        connectionState = connectionState,
                        partyState = partyState,
                        scanningState = scanningState,
                        errorMessage = errorMessage,
                        onRescan = { viewModel.rescan() }
                    )
                }
            }
        }
    }
}
