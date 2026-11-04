package com.woody.booklauncher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.view.InputDevice
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 상태 줄에 보여 줄 기기 상태 */
data class DeviceStatus(
    val battery: Int,
    val charging: Boolean,
    /** 와이파이에 연결돼 있다(켜져 있기만 하면 아니다) */
    val wifi: Boolean,
    val bluetooth: Boolean,
    /** 블루투스 기기(이어폰·리모컨 등)가 연결돼 있다. 안드로이드 12+ 는 소리 출력·외부 입력 장치로 짐작한다 */
    val bluetoothConnected: Boolean,
    val light: Boolean,
    /** 잔상 제거(전체 새로고침) 주기. 0 이면 표시하지 않는다. */
    val refreshEvery: Int,
) {
    companion object {
        /** [lostWifi]: 끊겼다고 알려 온 와이파이. 시스템 목록에 아직 남아 있어도 연결로 치지 않는다 */
        fun read(context: Context, lostWifi: Set<Network> = emptySet()): DeviceStatus {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            return DeviceStatus(
                battery = if (level >= 0 && scale > 0) level * 100 / scale else -1,
                charging = plugged,
                wifi = wifiConnected(context, lostWifi),
                bluetooth = bluetoothOn(),
                bluetoothConnected = bluetoothConnected(context),
                light = Device.lightOn(context),
                refreshEvery = refreshEvery(),
            )
        }

        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        private fun bluetoothOn() = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }
            .getOrDefault(false)

        @Suppress("DEPRECATION")
        private fun wifiConnected(context: Context, lost: Set<Network> = emptySet()) = runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm.allNetworks.any { it !in lost && cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
        }.getOrDefault(false)

        /** 이어폰(A2DP·헤드셋)·키보드·리모컨(HID, 숨은 상수 4) 중 하나라도 연결돼 있으면 */
        private fun bluetoothConnected(context: Context) = profileStates(context).any { it.second == BluetoothProfile.STATE_CONNECTED }

        /**
         * [BT_PROFILES] 마다 (이름, 연결 상태 0 끊김·1 연결 중·2 연결됨·3 끊는 중). 꺼져 있으면 빈 목록, 물을 수 없으면 -1.
         * 안드로이드 12+ 는 프로필 상태를 물으려면 '근처 기기'(BLUETOOTH_CONNECT) 권한이 있어야 하는데 이 앱은 선언하지 않아 묻지 않는다.
         * 그래서 권한 없이 보이는 것으로 0·2 만 정한다: A2DP·HFP 는 블루투스 소리 출력 장치가 있는지, HID 는 외부 키 입력 장치
         * (USB·블루투스로 붙은 키보드·리모컨. USB 와 가르지 못한다. 기기 안 터치·펜 패널이 외부로 잡혀도 세지 않게 키를 내는 것만)가
         * 있는지. 1.7.5 까지는 12+ 에서 늘 몰라 연결 아이콘이 나오지 않았다(안드로이드 14 팬톤, 2026-11-03 댓글).
         */
        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        private fun profileStates(context: Context): List<Pair<String, Int>> {
            val bt = runCatching { BluetoothAdapter.getDefaultAdapter()?.takeIf { it.isEnabled } }.getOrNull() ?: return emptyList()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return BT_PROFILES.map { (name, profile) ->
                name to runCatching { bt.getProfileConnectionState(profile) }.getOrDefault(-1)
            }
            val outputs = runCatching {
                context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
            }.getOrNull()
            val external = runCatching {
                InputDevice.getDeviceIds().any { id ->
                    InputDevice.getDevice(id)?.let { it.isExternal && it.supportsSource(InputDevice.SOURCE_KEYBOARD) } == true
                }
            }.getOrNull()
            fun state(connected: Boolean?) = when (connected) {
                null -> -1
                true -> BluetoothProfile.STATE_CONNECTED
                false -> BluetoothProfile.STATE_DISCONNECTED
            }
            return listOf(
                "A2DP" to state(outputs?.any { it in BT_MEDIA_OUTPUTS }),
                "HFP" to state(outputs?.contains(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)),
                "HID" to state(external),
            )
        }

        /** 이어폰·스피커로 소리를 내는 블루투스 출력(예전 A2DP 와 LE 오디오) */
        private val BT_MEDIA_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
        )

        private val BT_PROFILES = listOf("A2DP" to BluetoothProfile.A2DP, "HFP" to BluetoothProfile.HEADSET, "HID" to 4)

        /**
         * 기기 정보에 보이는 와이파이·블루투스 지금 값: 와이파이 켜짐·연결, 블루투스 켜짐, [BT_PROFILES] 연결 상태.
         * 상태 줄이 기기에서 맞게 보이는지 사진으로 보려고 둔다(크레마 팬톤에서 연결 없는 와이파이가 보인다는 댓글, 2026-10-28).
         */
        fun radios(context: Context) = Radios(
            wifiOn = runCatching {
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
            }.getOrDefault(false),
            wifiLinked = wifiConnected(context),
            btOn = bluetoothOn(),
            profiles = profileStates(context),
        )

        /** 크레마 상단바와 같은 값(persist.vendor.fullmode_cnt). */
        private fun refreshEvery(): Int =
            Device.prop("persist.vendor.fullmode_cnt").toIntOrNull()?.takeIf { it in 1..99 } ?: 0
    }
}

/** [DeviceStatus.radios]. [profiles] 는 (A2DP·HFP·HID, 연결 상태 0 끊김·1 연결 중·2 연결됨·3 끊는 중, -1 물을 수 없음) */
class Radios(val wifiOn: Boolean, val wifiLinked: Boolean, val btOn: Boolean, val profiles: List<Pair<String, Int>>)

/**
 * 홈 맨 위 두 줄.
 *   [crema Pebble 로고]                 18:24
 *              10월 3일 토요일 · 100% · 와이파이 · BT · 조명 · ⑮
 * 안드로이드 상단바 대신 쓰므로 시계·배터리·와이파이 등을 직접 그린다.
 */
class TopBarView(context: Context, private val fixedFrame: Boolean = true, private val marginDp: Int = Ui.MARGIN) : View(context) {
    private val logo: Drawable? = Crema.logo(context)
    /**
     * 로고가 없고 폭이 넉넉한 기기(HiBreak 등)는 시계 오른쪽·상태 줄 오른쪽의 두 줄 대신
     * 날짜·배터리·와이파이를 왼쪽에, 시계를 오른쪽에 한 줄로 둔다(왼쪽 위 빈 자리를 채운다).
     */
    private val singleRow = logo == null &&
        context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density >= SINGLE_ROW_MIN_DP
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.regular
        textSize = context.spf(CLOCK_SP)
        color = Ui.BLACK
        letterSpacing = -0.02f
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.regular
        textSize = context.spf(STATUS_SP)
        color = Ui.BLACK
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.BLACK }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.4f)
        color = Ui.BLACK
    }
    private val ringTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.bold
        textSize = context.spf(11f)
        color = Ui.BLACK
        textAlign = Paint.Align.CENTER
    }
    private val icons = mapOf(
        "wifi" to context.getDrawable(R.drawable.ic_wifi)!!,
        "bt" to context.getDrawable(R.drawable.ic_bluetooth)!!,
        "btOn" to context.getDrawable(R.drawable.ic_bluetooth_connected)!!,
        "light" to context.getDrawable(R.drawable.ic_light)!!,
        "bolt" to context.getDrawable(R.drawable.ic_bolt)!!,
    )
    private val time24 = SimpleDateFormat("HH:mm", Locale.KOREA)
    private val time12 = SimpleDateFormat("h:mm", Locale.KOREA)
    private val ampmKo = SimpleDateFormat("a", Locale.KOREA)
    private val ampmEn = SimpleDateFormat("a", Locale.US)
    private val dateKo = SimpleDateFormat("M월 d일 EEEE", Locale.KOREA)
    private val dateEn = SimpleDateFormat("EEE, MMM d", Locale.US)
    private val timeFormat get() = if (use24Hour) time24 else time12
    private val dateFormat get() = if (english) dateEn else dateKo

    /** 24시간(18:24) / 12시간. 12시간이면 작은 오전·오후를 한국어는 시각 앞(오후 6:24), 영어는 뒤(6:24 PM)에 붙인다. */
    var use24Hour = true
        set(v) {
            if (v == field) return
            field = v
            invalidate()
        }

    /** 시계·날짜를 영어로. 언어를 바꾸면 화면을 새로 만들므로 만들 때 적용된 언어로 한 번 정한다. */
    private val english = context.resources.configuration.locales[0].language == Locale.ENGLISH.language

    private var status: DeviceStatus? = null
    private var now = Date()

    private val padTop = context.dp(20)
    /** 로고 없이 한 줄로 쓸 때의 위·아래 여백(두 줄일 때보다 좁게) */
    private val singlePadTop = context.dp(12)
    private val singlePadBottom = context.dp(8)
    /** 아래 여백: 맨 앞 책(컬러면 색면)과 붙어 보이지 않게. 세로가 남는 바형 화면은 더 띄운다. */
    private val padBottom = context.dp(if (Ui.tallScreen) 18 else 14)
    private val lineGap = context.dp(16)
    private val logoHeight = context.dp(32)
    /** 상태 줄 아이콘·원문자는 상태 글자처럼 글자 배율을 따른다 */
    private val iconSize = context.dp(19 * Ui.textScale)
    private val sepGap = context.dpf(5)
    private val clockCap = capHeight(clockPaint)
    private val statusCap = capHeight(statusPaint)

    /**
     * 높이는 글자 100%·기기 글꼴 100% 일 때로 고정한다(홈 틀이 글자 설정에 따라 움직이지 않게).
     * 키운 글자는 위 여백·아래 여백을 먼저, 그다음 시계와 상태 줄 사이를 [MIN_PAD_DP] 까지 줄여 받고(시계와 상태 줄이 붙어 보이지 않게),
     * 그래도 들지 않으면 상태 줄을 그리지 않는다(반쯤 잘린 줄을 보이지 않게).
     * [fixedFrame] 이 false 면(기기가 앱의 밀도를 덮어씀) 고정하지 않고 글자 크기대로 높이를 잡는다(글자를 줄여 홈을 맞출 수 있게).
     */
    private val fixedHeight: Int
    private var drawPadTop = padTop
    private var drawLineGap = lineGap
    private var showStatus = true

    init {
        if (singleRow) {
            // 시계 한 줄(상태는 같은 줄 왼쪽).
            drawPadTop = singlePadTop
            fixedHeight = singlePadTop + clockCap + clockPaint.fontMetricsInt.descent + singlePadBottom
        } else {
            val baseStatus = Paint(statusPaint).apply { textSize = context.dpf(STATUS_SP) }
            val textOf = { clock: Int, paint: Paint -> clock + capHeight(paint) + paint.fontMetricsInt.descent }
            fixedHeight = padTop + lineGap + padBottom +
                if (fixedFrame) textOf(capHeight(Paint(clockPaint).apply { textSize = context.dpf(CLOCK_SP) }), baseStatus)
                else textOf(clockCap, statusPaint)
            val room = fixedHeight - textOf(clockCap, statusPaint)
            val full = padTop + lineGap + padBottom
            val min = context.dp(MIN_PAD_DP)
            if (room < full) {
                if (room >= 3 * min) {
                    // 모자란 만큼 위 여백 → 아래 여백 → 줄 사이 순으로 줄인다(아래 여백은 그리고 남는 자리라 따로 두지 않는다)
                    var lack = full - room
                    val fromTop = minOf(lack, padTop - min).also { lack -= it }
                    lack -= minOf(lack, padBottom - min)
                    drawPadTop = padTop - fromTop
                    drawLineGap = lineGap - minOf(lack, lineGap - min)
                } else {
                    showStatus = false
                    drawPadTop = maxOf(0, (fixedHeight - clockCap) / 2)
                }
            }
        }
    }

    /** 시계·상태가 실제로 바뀌었을 때만 다시 그린다(전자잉크 부분 갱신을 줄이기 위해). */
    fun update(status: DeviceStatus) {
        val now = Date()
        val changed = status != this.status || timeFormat.format(now) != timeFormat.format(this.now) ||
            dateFormat.format(now) != dateFormat.format(this.now)
        this.status = status
        this.now = now
        if (changed) invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), fixedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val left = context.dp(marginDp).toFloat()
        val right = width - context.dp(marginDp).toFloat()
        val clockBase = (drawPadTop + clockCap).toFloat()

        // 로고: 아랫선을 시계 글자 아랫선에 맞춘다.
        logo?.let {
            val w = logoHeight * it.intrinsicWidth / it.intrinsicHeight.coerceAtLeast(1)
            it.setBounds(left.toInt() - context.dp(2), clockBase.toInt() - logoHeight, left.toInt() + w, clockBase.toInt())
            it.draw(canvas)
        }

        val time = timeFormat.format(now)
        val gap = context.dpf(6)
        if (use24Hour) {
            canvas.drawText(time, right - clockPaint.measureText(time), clockBase, clockPaint)
        } else if (english) {
            val ampm = ampmEn.format(now)
            val ampmW = statusPaint.measureText(ampm)
            canvas.drawText(ampm, right - ampmW, clockBase, statusPaint)
            canvas.drawText(time, right - ampmW - gap - clockPaint.measureText(time), clockBase, clockPaint)
        } else {
            val ampm = ampmKo.format(now)
            val timeLeft = right - clockPaint.measureText(time)
            canvas.drawText(time, timeLeft, clockBase, clockPaint)
            canvas.drawText(ampm, timeLeft - gap - statusPaint.measureText(ampm), clockBase, statusPaint)
        }

        // 상태 줄: 보통은 오른쪽부터 왼쪽으로 쌓고, 한 줄 상단(로고 없는 넓은 화면)은 왼쪽부터 채운다.
        val s = status?.takeIf { showStatus } ?: return
        val statusBase = if (singleRow) clockBase else clockBase + drawLineGap + statusCap
        val mid = statusBase - statusCap / 2f
        val date = dateFormat.format(now)
        val dotR = context.dpf(1.6f)
        val sep = 2 * sepGap + 2 * dotR
        val items = buildList<Pair<Float, (Float) -> Float>> {
            add(statusPaint.measureText(date) to { x -> drawText(canvas, date, x, statusBase) })
            if (s.battery >= 0) {
                val battery = "${s.battery}%"
                val width = statusPaint.measureText(battery) + if (s.charging) context.dpf(1) + iconSize * 0.8f else 0f
                add(width to { x ->
                    val textLeft = drawText(canvas, battery, x, statusBase)
                    if (s.charging) drawIcon(canvas, "bolt", textLeft - context.dpf(1), mid, iconSize * 0.8f) else textLeft
                })
            }
            if (s.wifi) add(iconSize.toFloat() to { x -> drawIcon(canvas, "wifi", x, mid) })
            if (s.bluetooth) add(iconSize.toFloat() to { x -> drawIcon(canvas, if (s.bluetoothConnected) "btOn" else "bt", x, mid) })
            if (s.light) add(iconSize.toFloat() to { x -> drawIcon(canvas, "light", x, mid) })
            if (s.refreshEvery > 0) {
                val width = 2 * context.dpf(9.5f * Ui.textScale) + ringPaint.strokeWidth
                add(width to { x -> drawRing(canvas, s.refreshEvery, x, mid) })
            }
        }
        val total = items.sumOf { it.first.toDouble() }.toFloat() + sep * maxOf(0, items.size - 1)
        var x = if (singleRow) left + total else right
        items.asReversed().forEachIndexed { i, item ->
            if (i > 0) {
                x -= sepGap
                canvas.drawCircle(x - dotR, mid, dotR, dotPaint)
                x -= 2 * dotR + sepGap
            }
            x = item.second(x)
        }
    }

    /** 오른쪽 끝을 [right] 에 맞춰 쓰고 왼쪽 끝을 돌려준다. */
    private fun drawText(canvas: Canvas, text: String, right: Float, base: Float): Float {
        val w = statusPaint.measureText(text)
        canvas.drawText(text, right - w, base, statusPaint)
        return right - w
    }

    private fun drawIcon(canvas: Canvas, name: String, right: Float, mid: Float, size: Float = iconSize.toFloat()): Float {
        val d = icons.getValue(name)
        val half = size / 2f
        d.setBounds((right - size).toInt(), (mid - half).toInt(), right.toInt(), (mid + half).toInt())
        d.draw(canvas)
        return right - size
    }

    /** 원문자(⑮): 크레마 상단바처럼 원 안에 숫자 */
    private fun drawRing(canvas: Canvas, n: Int, right: Float, mid: Float): Float {
        val r = context.dpf(9.5f * Ui.textScale)
        val cx = right - r - ringPaint.strokeWidth / 2
        canvas.drawCircle(cx, mid, r, ringPaint)
        val b = Rect().also { ringTextPaint.getTextBounds("0", 0, 1, it) }
        canvas.drawText(n.toString(), cx, mid + b.height() / 2f, ringTextPaint)
        return cx - r - ringPaint.strokeWidth / 2
    }

    private fun capHeight(p: Paint): Int = Rect().also { p.getTextBounds("0", 0, 1, it) }.height()

    private companion object {
        /** 시계·상태 줄 글자 크기(sp, 글자 배율을 곱한다) */
        const val CLOCK_SP = 37f
        const val STATUS_SP = 15.5f
        /** 키운 글자를 받을 때 위 여백·줄 사이·아래 여백의 최소 */
        const val MIN_PAD_DP = 8
        /** 로고가 없을 때 상태 줄을 시계와 같은 줄 왼쪽에 두는 최소 폭(dp) */
        const val SINGLE_ROW_MIN_DP = 360f
    }
}
