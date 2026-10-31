package com.woody.booklauncher

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/**
 * 페이지 버튼(설정 › 화면 › 페이지 버튼): 사용자가 눌러 정한 앞 쪽·다음 쪽 키로 쪽을 넘긴다. 기본 키는 없다
 * (기기마다 키가 달라: 메이북 291·292, iReader Ocean·Moaan 은 음량 키. 표준 PAGE_UP·PAGE_DOWN·방향 키도 정해야 받는다).
 * 화면·메뉴마다 넘기는 목록은 하나다([PageKeyHandler]). 넘길 목록이 없으면 키를 먹지 않는다(음량 키는 음량을 바꾼다).
 * 방향 키·PAGE 키는 터치 뒤 첫 누름을 안드로이드가 터치 모드를 벗어나는 데 쓰고 먹는다. 알고 그대로 둔다(이슈 #9).
 */
object PageKeys {
    /** 정할 수 없는 키: 뒤로(메뉴를 닫는다)·홈·전원 등 시스템이 쓰는 키 */
    private val RESERVED = setOf(
        KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_SOFT_SLEEP,
    )

    fun assignable(keyCode: Int) = keyCode !in RESERVED

    /**
     * [event] 가 페이지 버튼이면 다음 쪽(true)·앞 쪽(false), 아니면 null.
     * 음량 키는 소리가 나는 동안에는 페이지 버튼이 아니다(음량을 바꾼다).
     */
    fun direction(context: Context, event: KeyEvent): Boolean? {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_UNKNOWN) return null
        val prefs = HomePrefs(context)
        val next = when (code) {
            prefs.pageKeyNext -> true
            prefs.pageKeyPrev -> false
            else -> return null
        }
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val audio = context.getSystemService(AudioManager::class.java)
            if (audio?.isMusicActive == true) return null
        }
        return next
    }

    /** 설정에 보이는 키 이름: 아는 키는 `음량 위`·`페이지 아래`·`방향 위` 처럼, 그 밖은 `버튼 291` */
    fun name(context: Context, keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> context.getString(R.string.key_volume_up)
        KeyEvent.KEYCODE_VOLUME_DOWN -> context.getString(R.string.key_volume_down)
        KeyEvent.KEYCODE_PAGE_UP -> context.getString(R.string.key_page_up)
        KeyEvent.KEYCODE_PAGE_DOWN -> context.getString(R.string.key_page_down)
        KeyEvent.KEYCODE_DPAD_UP -> context.getString(R.string.key_dpad_up)
        KeyEvent.KEYCODE_DPAD_DOWN -> context.getString(R.string.key_dpad_down)
        KeyEvent.KEYCODE_DPAD_LEFT -> context.getString(R.string.key_dpad_left)
        KeyEvent.KEYCODE_DPAD_RIGHT -> context.getString(R.string.key_dpad_right)
        else -> context.getString(R.string.key_other, keyCode)
    }
}

/**
 * 한 화면(또는 메뉴)의 페이지 버튼 받기: 누를 때 [turn](다음 쪽이면 true)을 불러 넘겼으면(넘길 목록이 있으면 true) 그 키를 먹고,
 * 뗄 때도 먹는다. 길게 눌러 반복되는 누름은 넘기지 않고 먹기만 한다(한 번에 한 쪽).
 */
class PageKeyHandler(private val context: Context, private val turn: (next: Boolean) -> Boolean) {
    /** 넘기는 데 쓴 키(뗄 때까지) */
    private var held = KeyEvent.KEYCODE_UNKNOWN

    fun onKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && held == event.keyCode) {
            held = KeyEvent.KEYCODE_UNKNOWN
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val next = PageKeys.direction(context, event) ?: return false
        if (event.repeatCount > 0) return held == event.keyCode
        val turned = turn(next)
        held = if (turned) event.keyCode else KeyEvent.KEYCODE_UNKNOWN
        return turned
    }
}
