package com.stephen.nativewal.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stephen.nativewal.data.model.WifiConfig
import com.stephen.nativewal.data.repository.WifiConfigRepository
import com.stephen.nativewal.ui.theme.WALTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoLoginWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            WALTheme {
                Surface {
                    ConfigureWidgetScreen(
                        onSelected = { ssid ->
                            val store = AutoLoginWidgetStore(this)
                            store.saveWidgetSsid(appWidgetId, ssid)
                            AutoLoginWidgetProvider.updateAllWidgets(this)

                            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(Activity.RESULT_OK, resultValue)
                            finish()
                        },
                        onCancel = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureWidgetScreen(
    onSelected: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var configs by remember { mutableStateOf<List<WifiConfig>>(emptyList()) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        configs = withContext(Dispatchers.IO) {
            WifiConfigRepository(context.applicationContext).getAllConfigs()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopAppBar(title = { Text("Choose WiFi") })

        if (configs.isEmpty()) {
            Text("No saved SSIDs yet. Add one in the app first.")
            Button(onClick = onCancel) {
                Text("Cancel")
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(configs, key = { it.ssid }) { config ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(config.ssid) }
                        .padding(16.dp)
                ) {
                    Text(config.ssid, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Button(onClick = onCancel) {
            Text("Cancel")
        }
    }
}
