package com.ioscastaway.notificationbrain.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    private val vm: BrainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyTabExtra(intent)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                BrainApp(vm)
            }
        }
    }

    /** The summary notification reopens a running activity; the tab extra must work then too. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyTabExtra(intent)
    }

    private fun applyTabExtra(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_TAB) == true) vm.tab = intent.getIntExtra(EXTRA_TAB, TAB_HOME)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshAccess()
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_HOME = 0
        const val TAB_ARCHIVE = 1
        const val TAB_LEARN = 2
        const val TAB_LAB = 3
    }
}
