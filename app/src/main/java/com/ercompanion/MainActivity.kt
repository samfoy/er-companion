package com.ercompanion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import com.ercompanion.ui.MainScreen
import com.ercompanion.ui.theme.ERCompanionTheme
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {

    private val crashFile get() = File(filesDir, "last_crash.txt")

    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                val sw = StringWriter()
                ex.printStackTrace(PrintWriter(sw))
                crashFile.writeText("Thread: ${thread.name}\n$sw")
            } catch (_: Exception) {}
            default?.uncaughtException(thread, ex)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashHandler()
        super.onCreate(savedInstanceState)

        // Show last crash if one exists
        val lastCrash = if (crashFile.exists()) crashFile.readText().also { crashFile.delete() } else null

        // Clear Coil disk cache once to bust any stale icon.png entries
        val prefs = getSharedPreferences("er_companion", MODE_PRIVATE)
        if (!prefs.getBoolean("cache_cleared_v2", false)) {
            Coil.imageLoader(this).diskCache?.clear()
            prefs.edit().putBoolean("cache_cleared_v2", true).apply()
        }

        // Request storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1001)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }

        setContent {
            ERCompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (lastCrash != null) {
                        // Show crash log on screen so you can read it without ADB
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text("💥 CRASH LOG (restart to clear)", color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                            Text(lastCrash, color = Color(0xFFFF6B6B), fontSize = 10.sp, lineHeight = 14.sp)
                        }
                    } else {
                        val viewModel: MainViewModel = viewModel()
                        val connectionState by viewModel.connectionState.collectAsState()
                        val partyState by viewModel.partyState.collectAsState()
                        val enemyPartyState by viewModel.enemyPartyState.collectAsState()
                        val activePlayerSlot by viewModel.activePlayerSlot.collectAsState()
                        val scanningState by viewModel.scanningState.collectAsState()
                        val errorMessage by viewModel.errorMessage.collectAsState()
                        val debugLog by viewModel.debugLog.collectAsState()

                        MainScreen(
                            viewModel = viewModel,
                            connectionState = connectionState,
                            partyState = partyState,
                            enemyPartyState = enemyPartyState,
                            activePlayerSlot = activePlayerSlot,
                            scanningState = scanningState,
                            errorMessage = errorMessage,
                            debugLog = debugLog,
                            onRescan = { viewModel.rescan() }
                        )
                    }
                }
            }
        }
    }
}
