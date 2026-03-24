package com.botkin.zontdatahandler.wear.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.botkin.zontdatahandler.shared.combinedShortText
import com.botkin.zontdatahandler.shared.combinedShortTitle
import com.botkin.zontdatahandler.shared.withComputedStaleness
import com.botkin.zontdatahandler.wear.R
import com.botkin.zontdatahandler.wear.data.WearSnapshotStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val snapshotStore by lazy { WearSnapshotStore(applicationContext) }
    private var snapshotState by mutableStateOf<com.botkin.zontdatahandler.shared.ZontSnapshot?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshSnapshot()
        setContent {
            MaterialTheme {
                Surface {
                    WearStatusScreen(snapshot = snapshotState)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSnapshot()
    }

    private fun refreshSnapshot() {
        snapshotState = snapshotStore.readSnapshot()?.withComputedStaleness(currentEpochSeconds())
    }
}

@androidx.compose.runtime.Composable
private fun WearStatusScreen(
    snapshot: com.botkin.zontdatahandler.shared.ZontSnapshot?,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Watch build ${installedBuildLabel(context)}",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                if (snapshot == null) {
                    Text(
                        text = "Waiting for phone sync.",
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = snapshot.combinedShortText(currentEpochSeconds()),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = snapshot.combinedShortTitle(currentEpochSeconds()),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "${if (snapshot.isStale) "! " else ""}Updated ${formatEpochSeconds(snapshot.updatedAtEpochSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000L

private fun formatEpochSeconds(epochSeconds: Long): String {
    if (epochSeconds <= 0L) {
        return "--"
    }
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(timestampFormatter)
}

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun installedBuildLabel(context: Context): String {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0L),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionName = packageInfo.versionName ?: "--"
    val buildType = if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
        "debug"
    } else {
        "release"
    }
    return "$versionName (${packageInfo.longVersionCode}) $buildType"
}
