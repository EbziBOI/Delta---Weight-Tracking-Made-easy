package io.github.ebziboi.delta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.ui.DeltaTheme

/**
 * Opened from Health Connect when someone asks why Delta wants access.
 * Health Connect requires every app that uses it to answer that question.
 */
class PermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeltaTheme(AppSettings()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .systemBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "How Delta uses Health Connect",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Delta reads your weight and measured body-fat percentages so it can show your progress from your start " +
                                "date to your target, in the app and on your home-screen widgets."
                        )
                        Text(
                            "Access to past data lets it include weigh-ins from before you installed " +
                                "it. Background access lets widgets stay up to date while the app is closed."
                        )
                        Text("Delta writes weight and body fat only when you log measurements or import a scale export. Each measurement stays independent; missing body fat is never estimated.")
                        Text(
                            "Your measurements stay on your phone. Delta has no account, no server and " +
                                "no analytics."
                        )
                        Button(onClick = { finish() }) { Text("Close") }
                    }
                }
            }
        }
    }
}
