package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Clean up any stale or corrupted WebView variations seed files from emulator data
    try {
      val webViewDir = java.io.File(applicationInfo.dataDir, "app_webview")
      val seedNew = java.io.File(webViewDir, "variations_seed_new")
      if (seedNew.exists()) seedNew.delete()
      val seed = java.io.File(webViewDir, "variations_seed")
      if (seed.exists()) seed.delete()
    } catch (_: Exception) {}

    // Initialize Google Mobile Ads SDK on a background thread to prevent UI thread driver delays
    CoroutineScope(Dispatchers.IO).launch {
      try {
        MobileAds.initialize(this@MainActivity) {}
      } catch (e: Exception) {
        Log.w("MainActivity", "MobileAds initialization warning", e)
      }
    }

    setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          AppNavigation()
        }
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme {
    AppNavigation()
  }
}
