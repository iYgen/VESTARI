package app.vestari.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.vestari.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Автообновление через GitHub Releases.
 * GitHub Actions на каждый тег vX.Y.Z собирает подписанный APK и прикладывает к релизу (см. .github/workflows/release.yml),
 * а приложение сверяет свою версию с последним релизом, скачивает APK и открывает системный установщик.
 */
object Updater {
    data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Ready(val release: Release, val file: File) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    private val repo get() = BuildConfig.UPDATE_REPO
    val configured: Boolean get() = repo.isNotBlank() && !repo.startsWith("OWNER/")

    private val json = Json { ignoreUnknownKeys = true }

    /** Запрос к GitHub. Возвращает релиз, только если он новее установленной версии. */
    suspend fun fetchNewer(): Release? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        val conn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Vestari-Android")
        conn.connectTimeout = 15000; conn.readTimeout = 20000
        try {
            if (conn.responseCode == 404) return@withContext null // релизов ещё нет
            if (conn.responseCode != 200) throw IllegalStateException("GitHub ответил ${conn.responseCode}")
            val root = json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
            val version = root["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return@withContext null
            val asset = root["assets"]?.jsonArray?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                ?: return@withContext null
            if (compareVersions(version, currentVersion) <= 0) return@withContext null
            Release(
                version = version,
                notes = root["body"]?.jsonPrimitive?.content.orEmpty().trim(),
                apkUrl = asset["browser_download_url"]!!.jsonPrimitive.content,
                size = asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun check(silent: Boolean = false) {
        if (_state.value is State.Downloading) return
        if (!silent) _state.value = State.Checking
        _state.value = runCatching { fetchNewer() }.fold(
            onSuccess = { r -> if (r != null) State.Available(r) else if (silent) State.Idle else State.UpToDate },
            onFailure = { e -> if (silent) State.Idle else State.Failed("Не удалось проверить: ${e.message}") },
        )
    }

    suspend fun download(context: Context, r: Release) = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "vestari-${r.version}.apk")
        _state.value = State.Downloading(r, 0f)
        runCatching {
            var url = URL(r.apkUrl)
            var conn: HttpURLConnection
            // GitHub отдаёт файл через редирект на свой CDN — идём по редиректам вручную (https→https).
            while (true) {
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Vestari-Android")
                conn.connectTimeout = 15000; conn.readTimeout = 30000
                if (conn.responseCode in 300..399) {
                    url = URL(conn.getHeaderField("Location")); conn.disconnect(); continue
                }
                break
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.size
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (total > 0) _state.value = State.Downloading(r, done.toFloat() / total)
                    }
                }
            }
            conn.disconnect()
        }.onSuccess { _state.value = State.Ready(r, file) }
            .onFailure { _state.value = State.Failed("Загрузка не удалась: ${it.message}") }
    }

    /** true — установщик открыт; false — сначала нужно разрешить установку (открыты настройки). */
    fun install(context: Context, file: File): Boolean {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    fun dismiss() { _state.value = State.Idle }

    /** "1.10.0" > "1.9.3". Нечисловые хвосты вроде "-beta" игнорируются. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }
}
