package com.woody.booklauncher

import android.content.Context
import android.os.UserManager
import java.io.File

/**
 * 잠금 해제 전에도 읽을 수 있는 저장소(기기 보호 저장소). 홈이 부팅 직후 시스템 임시 홈 없이 바로 뜨도록
 * (directBootAware) 설정·책·앱 목록 캐시·독서 기록을 모두 여기에 둔다. 크레마는 잠금 화면이 없어 보안상 차이는 없다.
 */
object Storage {
    @Volatile private var device: Context? = null
    @Volatile private var migrated = false
    /** 잠금은 한 번 풀리면 다시 잠기지 않으므로 풀린 뒤로는 묻지 않는다. */
    @Volatile private var unlocked = false

    /** 저장소 컨텍스트(한 번 만들어 재사용). 처음 부를 때(잠금이 풀려 있으면) 예전 저장소의 데이터를 옮긴다. */
    fun of(context: Context): Context {
        val app = context.applicationContext ?: context
        val device = device ?: app.createDeviceProtectedStorageContext().also { device = it }
        if (!migrated && isUnlocked(app)) {
            synchronized(this) {
                if (!migrated) { migrate(app, device); migrated = true }
            }
        }
        return device
    }

    fun isUnlocked(context: Context): Boolean {
        if (unlocked) return true
        return (context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true).also { unlocked = it }
    }

    /** 예전 버전은 일반(자격 증명) 저장소에 두었다. 있으면 옮기고 지운다. */
    private fun migrate(app: Context, device: Context) {
        device.moveSharedPreferencesFrom(app, HomePrefs.FILE)
        for (name in listOf("books", "apps.json", "reading.json")) {
            val src = File(app.filesDir, name)
            val dst = File(device.filesDir, name)
            if (!src.exists() || dst.exists()) continue
            // 두 저장소는 암호화 정책이 달라 이름 바꾸기로는 옮길 수 없다. 복사한 뒤 지운다.
            // 복사가 중간에 실패하면 반쪽 복사본을 지워 다음에 다시 옮기게 한다.
            if (runCatching { src.copyRecursively(dst) }.getOrDefault(false)) src.deleteRecursively() else dst.deleteRecursively()
        }
    }
}
