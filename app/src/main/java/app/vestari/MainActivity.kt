package app.vestari

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import app.vestari.data.Repository
import app.vestari.sync.Notifier
import app.vestari.sync.SyncWorker
import app.vestari.ui.VestariApp

class VestariApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
        Notifier.createChannel(this)
        SyncWorker.schedule(this)
    }
}

class MainActivity : ComponentActivity() {
    /** Письмо, которое нужно открыть (пришло из уведомления). */
    private val pendingOpen = mutableStateOf<String?>(null)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingOpen.value = intent?.getStringExtra(EXTRA_MESSAGE_KEY)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            VestariApp(
                openMessageKey = pendingOpen.value,
                onOpenedFromNotification = { pendingOpen.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_MESSAGE_KEY)?.let { pendingOpen.value = it }
    }

    companion object {
        const val EXTRA_MESSAGE_KEY = "message_key"
    }
}
