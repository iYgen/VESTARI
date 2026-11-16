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
import kotlinx.coroutines.launch
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

    /**
     * Ищет новую версию в двух местах и берёт самую свежую:
     * 1) GitHub Releases (если релизы публикуются через GitHub Actions);
     * 2) файл version.json в корне репозитория рядом с Vestari.apk — работает без релизов и секретов.
     * Возвращает обновление, только если оно новее установленной версии.
     */
    suspend fun fetchNewer(): Release? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        val fromReleases = runCatching { fetchFromReleases() }
        val fromRepo = runCatching { fetchFromRepo() }
        val best = listOfNotNull(fromReleases.getOrNull(), fromRepo.getOrNull())
            .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
        if (best == null) {
            // Ничего не нашли и файл версии недоступен — это ошибка, а не «у вас последняя версия».
            fromRepo.exceptionOrNull()?.let { throw it }
            return@withContext null
        }
        best.takeIf { compareVersions(it.version, currentVersion) > 0 }
    }

    private fun fetchFromRepo(): Release? {
        val conn = URL("https://raw.githubusercontent.com/$repo/main/version.json?t=${System.currentTimeMillis()}")
            .openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Vestari-Android")
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.connectTimeout = 15000; conn.readTimeout = 20000
        try {
            if (conn.responseCode == 404) throw IllegalStateException(PRIVATE_HINT)
            if (conn.responseCode != 200) throw IllegalStateException("GitHub ответил ${conn.responseCode}")
            val root = json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
            val version = root["version"]?.jsonPrimitive?.content ?: return null
            val apk = root["apk"]?.jsonPrimitive?.content ?: "Vestari.apk"
            return Release(
                version = version,
                notes = root["notes"]?.jsonPrimitive?.content.orEmpty().trim(),
                apkUrl = if (apk.startsWith("http")) apk else "https://github.com/$repo/raw/main/$apk",
                size = root["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchFromReleases(): Release? {
        val conn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Vestari-Android")
        conn.connectTimeout = 15000; conn.readTimeout = 20000
        try {
            if (conn.responseCode == 404) return null // релизов ещё нет
            if (conn.responseCode != 200) throw IllegalStateException("GitHub ответил ${conn.responseCode}")
            val root = json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
            val version = root["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return null
            val asset = root["assets"]?.jsonArray?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                ?: return null
            return Release(
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
                    url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue
                }
                break
            }
            // Без этой проверки страница «404» сохранялась бы вместо APK и установщик выдавал бы ошибку разбора.
            when (conn.responseCode) {
                200 -> {}
                404 -> { conn.disconnect(); throw IllegalStateException(PRIVATE_HINT) }
                else -> { conn.disconnect(); throw IllegalStateException("GitHub ответил ${conn.responseCode}") }
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
            // APK — это zip-архив: первые байты «PK». Иначе скачалось что-то другое.
            val head = file.inputStream().use { ByteArray(2).also { b -> it.read(b) } }
            if (head[0] != 'P'.code.toByte() || head[1] != 'K'.code.toByte()) {
                file.delete(); throw IllegalStateException("вместо APK пришла веб-страница")
            }
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

    /** Загрузка в области приложения: не прерывается, если уйти с экрана или закрыть карточку. */
    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    fun startDownload(context: Context, r: Release) {
        if (_state.value is State.Downloading) return
        appScope.launch { download(context.applicationContext, r) }
    }

    private const val PRIVATE_HINT =
        "файл обновления недоступен — репозиторий на GitHub закрыт или файл удалён"

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
