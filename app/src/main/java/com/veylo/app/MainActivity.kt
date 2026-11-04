package com.veylo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veylo.app.ui.AppViewModel
import com.veylo.app.ui.navigation.VeyloNavHost
import com.veylo.app.ui.theme.VeyloTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val profile by vm.profile.collectAsStateWithLifecycle()
            VeyloTheme(theme = profile.theme) {
                VeyloNavHost(vm)
            }
        }
    }
}
