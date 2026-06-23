package dev.scuttle.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.scuttle.inventory.ui.auth.AuthScreen
import dev.scuttle.inventory.ui.auth.AuthViewModel
import dev.scuttle.inventory.ui.theme.InventoryTheme
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InventoryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Root()
                }
            }
        }
    }
}

@Composable
private fun Root(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    if (state.authenticated) {
        // Placeholder home until the inventory screens land.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Signed in")
        }
    } else {
        AuthScreen(viewModel = viewModel)
    }
}
