package com.woody.booklauncher.readers

import java.io.ByteArrayOutputStream

/**
 * KOReader 가 남기는 Lua 값 파일(`return { ["file"] = "…", … }`, KOReader `dump.lua` 꼴)을 읽는다. 표는 [Map](키는 글자·수),
 * 글자는 [String], 수는 [Double], 참거짓은 [Boolean], nil 은 null. 함수 같은 다른 Lua 식은 읽지 않는다(못 읽으면 null).
 * 글자는 `%q` 꼴(`\"`·`\\`·`\` 줄바꿈·`\ddd`)이고 그 밖의 바이트(UTF-8)는 그대로다.
 */
internal object LuaData {
    /** 파일 내용 → `return` 뒤의 값(못 읽으면 null) */
    fun parse(bytes: ByteArray): Any? = runCatching { Reader(bytes).file() }.getOrNull()

    /** 표에서 글자 키 [key] 의 값 */
    fun Map<*, *>.str(key: String): String? = this[key] as? String
    fun Map<*, *>.num(key: String): Double? = this[key] as? Double
    fun Map<*, *>.table(key: String): Map<*, *>? = this[key] as? Map<*, *>

    private class Reader(val b: ByteArray) {
        var i = 0

        fun file(): Any? {
            skip()
            if (word() != "return") throw IllegalArgumentException("no return")
            return value()
        }

        fun value(): Any? {
            skip()
            val c = ch(i)
            return when {
                c == '{' -> table()
                c == '"' || c == '\'' -> string()
                c == '-' || c == '.' || c.isDigit() -> number()
                else -> when (val w = word()) {
                    "true" -> true
                    "false" -> false
                    "nil" -> null
                    "inf" -> Double.POSITIVE_INFINITY
                    "nan" -> Double.NaN
                    else -> throw IllegalArgumentException("unknown $w")
                }
            }
        }

        fun table(): Map<Any, Any?> {
            i++ // {
            val out = LinkedHashMap<Any, Any?>()
            var n = 1.0
            while (true) {
                skip()
                when (ch(i)) {
                    '}' -> { i++; return out }
                    ',', ';' -> { i++; continue }
                    '[' -> {
                        i++
                        val key = value() ?: throw IllegalArgumentException("nil key")
                        skip(); expect(']'); skip(); expect('=')
                        out[key] = value()
                    }
                    else -> {
                        // `이름 = 값` 또는 이름 없는 값(배열)
                        val start = i
                        val w = if (isNameStart(b[i])) word() else null
                        skip()
                        if (w != null && i < b.size && b[i] == '='.code.toByte() && b.getOrNull(i + 1) != '='.code.toByte()) {
                            i++
                            out[w] = value()
                        } else {
                            i = start
                            out[n] = value()
                            n++
                        }
                    }
                }
            }
        }

        fun string(): String {
            val quote = b[i++]
            val out = ByteArrayOutputStream()
            while (true) {
                val c = b[i++]
                when {
                    c == quote -> return out.toString(Charsets.UTF_8.name())
                    c == '\\'.code.toByte() -> {
                        val e = ch(i++)
                        when {
                            e.isDigit() -> {
                                var v = e - '0'
                                var k = 1
                                while (k < 3 && i < b.size && ch(i).isDigit()) { v = v * 10 + (b[i++] - '0'.code.toByte()); k++ }
                                out.write(v)
                            }
                            e == 'n' || e == '\n' -> out.write('\n'.code)
                            e == '\r' -> { out.write('\n'.code); if (i < b.size && b[i] == '\n'.code.toByte()) i++ }
                            e == 'r' -> out.write('\r'.code)
                            e == 't' -> out.write('\t'.code)
                            e == 'a' -> out.write(7)
                            e == 'b' -> out.write(8)
                            e == 'f' -> out.write(12)
                            e == 'v' -> out.write(11)
                            else -> out.write(e.code)
                        }
                    }
                    else -> out.write(c.toInt())
                }
            }
        }

        fun number(): Double {
            val start = i
            while (i < b.size && ch(i).let { it.isLetterOrDigit() || it == '.' || it == '-' || it == '+' }) i++
            val t = String(b, start, i - start, Charsets.US_ASCII)
            return when (t) {
                "-inf" -> Double.NEGATIVE_INFINITY
                "-nan" -> Double.NaN
                else -> if (t.startsWith("0x") || t.startsWith("0X")) t.substring(2).toLong(16).toDouble() else t.toDouble()
            }
        }

        fun word(): String {
            val start = i
            while (i < b.size && (isNameStart(b[i]) || ch(i).isDigit())) i++
            return String(b, start, i - start, Charsets.US_ASCII)
        }

        fun isNameStart(c: Byte) = (c.toInt() and 0xFF).toChar().let { it == '_' || it in 'a'..'z' || it in 'A'..'Z' }

        /** [at] 의 바이트를 글자로(0x80 이상인 UTF-8 바이트가 음수라 `\uFF80` 이후 글자가 되지 않게) */
        fun ch(at: Int) = (b[at].toInt() and 0xFF).toChar()

        fun expect(c: Char) {
            if (ch(i) != c) throw IllegalArgumentException("expected $c")
            i++
        }

        /** 빈칸과 주석(`-- …`, `--[[ … ]]`)을 건너뛴다 */
        fun skip() {
            while (i < b.size) {
                val c = ch(i)
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { i++; continue }
                if (c == '-' && b.getOrNull(i + 1) == '-'.code.toByte()) {
                    i += 2
                    if (b.getOrNull(i) == '['.code.toByte() && b.getOrNull(i + 1) == '['.code.toByte()) {
                        val end = String(b, i, b.size - i, Charsets.ISO_8859_1).indexOf("]]")
                        i = if (end < 0) b.size else i + end + 2
                    } else {
                        while (i < b.size && b[i] != '\n'.code.toByte()) i++
                    }
                    continue
                }
                return
            }
        }
    }
}
