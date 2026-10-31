package com.woody.booklauncher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.net.URL
import java.security.MessageDigest

/**
 * 설정 › 업데이트(홈도 띄엄띄엄 확인해 새 판을 알린다): GitHub 의 최신 릴리스를 보고, 새 판이면 APK 를 받아 확인한 뒤 안드로이드 설치 창을 띄운다(스토어 없이 배포).
 * 받은 APK 는 크기·SHA-256(릴리스가 알려 주는 값, 없으면 받지 않는다)과, 지금 앱의 서명을 이어 받는지(교체 이력)를 확인한다.
 * 상태는 앱이 떠 있는 동안만 기억하고, 바뀌면 [onChange] 로 알린다(앞에 있는 화면이 건다. 설정은 그 줄만 다시 그리고, 홈은 새 판 창을 띄운다).
 */
object Update {
    // 포크의 릴리스 저장소. GitHub 에 이 이름으로 저장소·릴리스를 만들면 앱 안 업데이트가 동작한다.
    private const val LATEST = "https://api.github.com/repos/abhifx/Booklauncher/releases/latest"
    /** 설정을 열 때 이보다 오래됐으면 다시 확인한다(누르면 언제든 다시). */
    private const val RECHECK_MS = 6 * 60 * 60 * 1000L
    /** 받는 중 창을 이만큼은 보인다(금방 받아도 무슨 일이 있었는지 알 수 있게) */
    const val MIN_SHOW_MS = 5_000L
    private const val ACTION_STATUS = "com.woody.booklauncher.UPDATE_STATUS"
    /** 설정에서 설치를 맡긴 때와 그때의 versionCode(새 판이 깔리면 홈을 다시 띄울지 가른다. 앱이 다시 시작되므로 파일에 둔다) */
    private const val KEY_STARTED = "started_at"
    private const val KEY_FROM_CODE = "from_code"
    /** 지금 맡긴 설치 세션. 결과가 이 세션 것일 때만 따른다(앱이 다시 시작돼도 알게 파일에 둔다. 새로 `설치`하면 지운다) */
    private const val KEY_SESSION = "session"
    private const val RESTART_WITHIN_MS = 10 * 60 * 1000L
    /** 받는 크기 상한: 릴리스 정보 응답, APK(서버가 끝없이 보내도 저장소를 다 쓰지 않게) */
    private const val MAX_JSON_BYTES = 1L shl 20
    private const val MAX_APK_BYTES = 50L shl 20
    private val SHA256 = Regex("sha256:([0-9a-fA-F]{64})")
    private val TAG = Regex("v(\\d+(?:\\.\\d+)*)")

    private fun prefs(context: Context) = Storage.of(context).getSharedPreferences("update", Context.MODE_PRIVATE)

    class Release(val version: String, val notes: String, val apkUrl: String, val size: Long, val sha256: String)

    sealed class State {
        object Unknown : State()
        object Checking : State()
        object Latest : State()
        class Available(val release: Release) : State()
        class Downloading(val release: Release) : State()
        /** 설치 창을 띄웠다(설치되면 앱이 다시 시작된다. 창을 그냥 벗어나면 결과가 오지 않아, 다시 누르면 그 창을 다시 띄운다) */
        class Installing(val release: Release) : State()
        /** 인터넷이 없다(연결이 없거나, 있어도 이름을 못 찾거나 연결하지 못함). 설정을 열 때마다 다시 확인한다. */
        object Offline : State()
        /** GitHub 이 응답하지 않았다(오류·시간 초과·알 수 없는 응답). 설정을 열 때마다 다시 확인한다. */
        object Failed : State()
    }

    @Volatile var state: State = State.Unknown
        private set
    private var checkedAt = 0L
    /** 마지막으로 GitHub 에 물어본 때(결과와 상관없이). 홈은 이것으로 띄엄띄엄 확인한다([checkFromHome]). */
    private var askedAt = 0L
    /** 확인·설치를 시작할 때마다 하나씩 올린다. 그 사이 바뀌었으면 늦게 온 확인 결과는 버린다(받는 중을 덮지 않게). */
    private var generation = 0
    var onChange: (() -> Unit)? = null
    /** `설치` 뒤 받기·확인(크기·SHA-256·서명)·설치에 실패했다. 설정 화면이 알림 창을 띄우고 지운다([takeInstallFailure]). */
    @Volatile private var installFailed = false

    /**
     * 띄운 설치 창과 그 판. 창을 그냥 벗어났으면 다시 띄운다(새 세션을 만들면 남은 앞 창이 대신 떠 버린다).
     * 다시 확인해 상태가 바뀌어도 같은 판이면 쓴다. 취소·실패하면 지운다.
     */
    private var confirm: Pair<String, Intent>? = null

    private fun session(context: Context) = prefs(context).getInt(KEY_SESSION, -1)

    /** 설치 표시(세션·홈 다시 띄우기)를 모두 지운다: 새로 `설치`하거나 설치가 끝나지 못했을 때 */
    private fun clearInstallMarks(context: Context) =
        prefs(context).edit().remove(KEY_SESSION).remove(KEY_STARTED).remove(KEY_FROM_CODE).apply()

    /** 지금 세션을 버리고 표시를 지운다(설치 창을 띄우지 못할 때) */
    private fun dropSession(context: Context) {
        runCatching { context.packageManager.packageInstaller.abandonSession(session(context)) }
        clearInstallMarks(context)
        confirm = null
    }

    /** [version] 의 설치 창을 띄워 둔 적이 있고 그 세션이 아직 살아 있으면 다시 띄운다. 아니면 false(새로 받는다) */
    fun resumeInstall(context: Context, version: String): Boolean {
        val intent = confirm?.takeIf { it.first == version }?.second ?: return false
        if (context.packageManager.packageInstaller.getSessionInfo(session(context)) == null) {
            confirm = null
            clearInstallMarks(context)
            return false
        }
        return runCatching { context.startActivity(confirmWindow(intent)) }.isSuccess
    }

    /**
     * 설치 창을 새 작업으로 띄운다. 앞서 벗어난 설치 창이 남아 있으면 안드로이드가 그 창(다른 세션)을 앞으로 가져와
     * 눌러도 설치되지 않으므로, 남은 창을 지우고 새로 띄운다(CLEAR_TASK).
     */
    private fun confirmWindow(intent: Intent) =
        Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    /** 받은 만큼(0~100, 받는 동안 백그라운드에서 바뀐다) */
    @Volatile var downloaded = 0
        private set

    /** 설치 실패를 알리고, 릴리스가 바뀌었을 수 있으니 다시 확인한다. */
    private fun failInstall(context: Context) {
        installFailed = true
        check(context)
    }

    /** 알리지 않은 설치 실패가 있으면 true(한 번만) */
    fun takeInstallFailure(): Boolean = installFailed.also { installFailed = false }

    private fun set(s: State) {
        state = s
        onChange?.invoke()
    }

    /** 설정을 열 때: 확인한 지 오래됐거나 확인한 적이 없으면 확인한다. */
    fun checkIfStale(context: Context) {
        val s = state
        if (s is State.Checking || s is State.Downloading) return
        if ((s == State.Latest || s is State.Available || s is State.Installing) && SystemClock.elapsedRealtime() - checkedAt < RECHECK_MS) return
        check(context)
    }

    /**
     * 홈에 돌아올 때: 앱이 뜬 뒤 처음이거나 마지막으로 물어본 지 [RECHECK_MS] 가 지났으면 확인한다(새 판이면 홈이 알린다).
     * 인터넷이 없으면 조용히 넘기고 다음에 돌아올 때 다시 본다. 받는 중·설치 중에는 하지 않는다.
     */
    fun checkFromHome(context: Context) {
        val s = state
        if (s is State.Checking || s is State.Downloading || s is State.Installing) return
        if (askedAt != 0L && SystemClock.elapsedRealtime() - askedAt < RECHECK_MS) return
        if (online(context)) check(context)
    }

    fun check(context: Context) {
        val current = context.appVersion()
        val gen = ++generation
        if (!online(context)) return set(State.Offline)
        askedAt = SystemClock.elapsedRealtime()
        set(State.Checking)
        Bg.run({ latest() }) { r ->
            if (gen != generation) return@run
            checkedAt = SystemClock.elapsedRealtime()
            set(r.fold(
                { if (newer(it.version, current)) State.Available(it) else State.Latest },
                // 이름을 못 찾거나 연결하지 못하면 인터넷이 안 되는 것(와이파이만 켜져 있고 바깥으로 못 나가는 때)
                { e -> if (e is UnknownHostException || e is ConnectException || e is NoRouteToHostException) State.Offline else State.Failed },
            ))
        }
    }

    /** 인터넷에 나갈 수 있는 연결이 있는가 */
    fun online(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** 받아서 확인하고 설치 창을 띄운다. 알 수 없는 앱 설치를 허용했는지는 부르는 쪽에서 본다. */
    fun install(context: Context, release: Release) {
        val app = context.applicationContext
        val started = SystemClock.elapsedRealtime()
        generation++
        downloaded = 0
        // 앞 세션은 이제 지금 것이 아니다(곧 버리는데, 그 취소 결과를 이 설치의 취소로 받지 않게).
        clearInstallMarks(app)
        confirm = null
        set(State.Downloading(release))
        Bg.run({
            val apk = File(app.cacheDir, "update.apk")
            try {
                download(release, apk)
                verify(app, release, apk)
                // 받는 중 창을 [MIN_SHOW_MS] 는 보이고 나서 설치 창을 띄운다.
                val left = MIN_SHOW_MS - (SystemClock.elapsedRealtime() - started)
                if (left > 0) Thread.sleep(left)
                commit(app, apk)
            } finally {
                apk.delete()
            }
        }) { r ->
            // 맡기면 결과([StatusReceiver])가 설치 창을 띄우며 `설치 중`으로 바꾼다(그때까지 받는 중 창).
            r.onFailure { failInstall(app) }
        }
    }

    /** `1.4.10` > `1.4.9`. 숫자가 아닌 조각은 0 으로 본다. */
    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** 최신 릴리스(태그 `v1.5.1`, 자산 `BookLauncher-1.5.1-release.apk`). 네트워크를 쓰므로 백그라운드에서. */
    private fun latest(): Release {
        val json = JSONObject(String(open(LATEST).use { readLimited(it.inputStream, MAX_JSON_BYTES) }, Charsets.UTF_8))
        // 태그 `v1.5.1` 과 자산 `BookLauncher-1.5.1-release.apk` 가 맞아야 한다(받은 APK 의 versionName 도 [verify] 가 본다).
        val version = TAG.matchEntire(json.getString("tag_name"))?.groupValues?.get(1) ?: throw IOException("bad tag")
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name") == "BookLauncher-$version-release.apk" } ?: throw IOException("no apk asset")
        val size = apk.getLong("size")
        if (size !in 1..MAX_APK_BYTES) throw IOException("bad asset size $size")
        // GitHub 가 자산마다 알려 주는 `sha256:…`. 없거나 형식이 틀리면 받지 않는다(크기만으로는 바뀐 파일을 못 가린다).
        val sha256 = SHA256.matchEntire(apk.optString("digest"))?.groupValues?.get(1) ?: throw IOException("no sha256 digest")
        return Release(
            version = version,
            notes = json.optString("body").trim(),
            apkUrl = apk.getString("browser_download_url"),
            size = size,
            sha256 = sha256,
        )
    }

    /** [limit] 바이트까지 읽는다(넘으면 실패) */
    private fun readLimited(input: java.io.InputStream, limit: Long): ByteArray = input.use {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = it.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > limit) throw IOException("response too large")
        }
        out.toByteArray()
    }

    private class Response(private val conn: HttpURLConnection) : AutoCloseable {
        val inputStream get() = conn.inputStream
        override fun close() = conn.disconnect()
    }

    private fun open(url: String): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "BookLauncher")
        val code = try { conn.responseCode } catch (e: IOException) { conn.disconnect(); throw e }
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IOException("HTTP $code")
        }
        return Response(conn)
    }

    /** [file] 에 받는다(크기·SHA-256 이 다르면 실패. 지우는 것은 부르는 쪽) */
    private fun download(release: Release, file: File) =
        fetch(release.apkUrl, release.size, release.sha256, file) { downloaded = it }

    /**
     * [url] 을 [file] 에 받는다: 크기가 [size] 이고 SHA-256 이 [sha256] 이어야 한다(다르면 실패, 넘치면 바로 멈춘다. 지우는 것은 부르는 쪽).
     * 받은 만큼(0~100)을 [progress] 로 알린다. 네트워크를 쓰므로 백그라운드에서.
     */
    fun fetch(url: String, size: Long, sha256: String, file: File, progress: (Int) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        open(url).use { r ->
            r.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                        total += n
                        // 알려 준 크기를 넘으면 바로 멈춘다(끝없이 보내는 서버)
                        if (total > size) throw IOException("larger than asset size")
                        progress((total * 100 / size).toInt())
                    }
                }
            }
        }
        if (file.length() != size || !digest.digest().hex().equals(sha256, ignoreCase = true)) {
            throw IOException("size or SHA-256 mismatch")
        }
    }

    /**
     * 이 앱의 새 판인지: 같은 패키지, 더 높은 versionCode, 서명을 이어 받는다 — 서명자가 하나면 지금 앱의 교체 이력(처음 키 → 지금 키)이
     * 새 APK 이력의 앞부분과 순서까지 같고, 여럿이면 서명자 묶음이 같다(시스템 설치기가 다시 보지만 앞서 가린다).
     */
    private fun verify(context: Context, release: Release, apk: File) {
        val pm = context.packageManager
        // 안드로이드 9·10 은 받은 APK 의 서명을 GET_SIGNATURES 가 있을 때만 읽는다(없으면 signingInfo 가 null 이라 늘 실패했다).
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES)
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val ok = archive != null && archive.packageName == context.packageName &&
            archive.versionName == release.version && archive.longVersionCode > installed.longVersionCode &&
            continuesSigning(signers(installed), signers(archive))
        if (!ok) throw IOException("not an update of this app")
    }

    /** 서명자들(SHA-256): 여럿이면 그 묶음, 하나면 교체 이력(처음 키부터 지금 키까지 순서대로). 서명을 못 읽으면 null */
    private class Signers(val multiple: Boolean, val certs: List<String>)

    private fun signers(info: PackageInfo): Signers? {
        val signing = info.signingInfo ?: return null
        val multiple = signing.hasMultipleSigners()
        val sigs = (if (multiple) signing.apkContentsSigners else signing.signingCertificateHistory).orEmpty()
        if (sigs.isEmpty()) return null
        return Signers(multiple, sigs.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() })
    }

    private fun continuesSigning(installed: Signers?, update: Signers?): Boolean = when {
        installed == null || update == null -> false
        installed.multiple || update.multiple -> installed.multiple && update.multiple && installed.certs.toSet() == update.certs.toSet()
        else -> update.certs.size >= installed.certs.size && update.certs.subList(0, installed.certs.size) == installed.certs
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    /** 설치 세션에 APK 를 넣고 맡긴다. 결과는 [StatusReceiver] 로 온다(사용자 확인이 필요하면 설치 창을 띄운다). */
    private fun commit(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        // 앞서 맡기고 끝나지 않은 세션은 버린다(설치 창은 늘 새로 띄우므로 남은 창이 대신 뜨지 않는다).
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session -> commitSession(context, session, id, apk) }
        } catch (e: Exception) {
            // 맡기기 전에 실패하면 세션·홈 다시 띄우기 표시를 남기지 않는다.
            runCatching { installer.abandonSession(id) }
            clearInstallMarks(context)
            throw e
        }
    }

    /** 세션에 APK 를 쓰고 맡긴다(홈 다시 띄우기 표시와 지금 세션을 적어 둔 뒤) */
    private fun commitSession(context: Context, session: PackageInstaller.Session, id: Int, apk: File) {
        apk.inputStream().use { input ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                input.copyTo(out)
                session.fsync(out)
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val status = PendingIntent.getBroadcast(
            context, id, Intent(context, StatusReceiver::class.java).setAction(ACTION_STATUS), flags,
        )
        // 세션 번호를 적지 못하면 결과를 모두 버리게 되므로 맡기지 않는다(부르는 쪽이 세션을 버린다).
        val saved = prefs(context).edit().putLong(KEY_STARTED, System.currentTimeMillis())
            .putLong(KEY_FROM_CODE, versionCode(context)).putInt(KEY_SESSION, id).commit()
        if (!saved) throw IOException("could not save install session")
        session.commit(status.intentSender)
    }

    /**
     * 새 판이 깔린 뒤: 설치하는 동안 이 앱이 멈춰 시스템이 다른 홈(크레마 런처 등)을 띄워 두므로, 설정에서 맡긴 설치였고(10분 안)
     * 이 앱이 기본 홈이면 홈을 다시 띄운다(adb 등 다른 길로 깔렸을 때는 읽던 화면을 빼앗지 않게 하지 않는다).
     * 깔린 직후 받는 알림([ReplacedReceiver])에서는 백그라운드에서 화면을 띄우지 못하게 막힐 때가 있어(안드로이드 10+),
     * 읽는 책 자동 추가(접근성 서비스)가 켜져 있으면 서비스가 다시 붙을 때([ReaderWatchService.onServiceConnected]) 한 번 더 띄운다.
     * [fromService] 가 아니고 서비스가 켜져 있으면 표시를 남겨 두어 서비스가 지우게 한다.
     */
    fun reopenHomeAfterUpdate(context: Context, fromService: Boolean) {
        val prefs = prefs(context)
        val started = prefs.getLong(KEY_STARTED, 0)
        if (started == 0L) return
        // 서비스는 설치가 끝나기 전에도 다시 붙을 수 있다: 판이 실제로 올라갔을 때만
        if (versionCode(context) <= prefs.getLong(KEY_FROM_CODE, Long.MAX_VALUE)) return
        val late = System.currentTimeMillis() - started !in 0..RESTART_WITHIN_MS
        if (fromService || late || !ReaderWatchService.isEnabled(context)) prefs.edit().remove(KEY_STARTED).apply()
        if (late) return
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val default = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (default != context.packageName) return
        runCatching { context.startActivity(home.setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun versionCode(context: Context) = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /** 새 판이 깔렸다([reopenHomeAfterUpdate]) */
    class ReplacedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) reopenHomeAfterUpdate(context, fromService = false)
        }
    }

    /** 설치 결과. 사용자 확인이 필요하면 시스템 설치 창을 띄우고, 취소하면 다시 받을 수 있게 두고, 실패면 알린다. */
    class StatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 앱이 다시 시작됐으면 어느 판인지 모른다(설치 창은 그래도 띄운다).
            val release = (state as? State.Installing)?.release ?: (state as? State.Downloading)?.release
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            // 앞서 버린 세션의 결과는 지금 설치와 상관없다.
            if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != session(context)) return
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> Unit
                // 성공: 세션은 끝났다(홈 다시 띄우기 표시는 새 판이 쓴다)
                PackageInstaller.STATUS_SUCCESS -> prefs(context).edit().remove(KEY_SESSION).apply()
                // 취소·실패: 나중에 다른 길로 깔릴 때 홈을 띄우지 않게 표시를 모두 지운다.
                else -> { clearInstallMarks(context); confirm = null }
            }
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val window = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        ?: return run { dropSession(context); failInstall(context) }
                    confirm = release?.let { it.version to window }
                    // 설치 창을 띄울 수 있게 됐으니 `설치 중`(받는 중 창이 닫힌다)
                    if (release != null) set(State.Installing(release))
                    runCatching { context.startActivity(confirmWindow(window)) }
                        .onFailure { dropSession(context); failInstall(context) }
                }
                PackageInstaller.STATUS_SUCCESS -> Unit // 새 판으로 앱이 다시 시작된다
                // 취소: 다시 받을 수 있게 새 판으로(어느 판인지 모르면 다시 확인)
                PackageInstaller.STATUS_FAILURE_ABORTED -> release?.let { set(State.Available(it)) } ?: check(context)
                else -> failInstall(context)
            }
        }
    }
}
