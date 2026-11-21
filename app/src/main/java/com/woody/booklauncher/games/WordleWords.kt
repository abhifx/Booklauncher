package com.woody.booklauncher.games

import android.content.Context
import com.woody.booklauncher.R

/** Wordle 단어 목록(raw 자원, 첫 줄부터 한 단어씩). 같은 목록은 한 번만 읽는다.
 *  답은 FrequencyWords(en_50k, MIT, Copyright 2016 Hermit Dave)에서 흔한 다섯 글자만,
 *  허용 목록은 five-letter-words(sgb-words, MIT, Copyright 2017 Charles Reid)를 쓴다. */
internal object WordleWords {
    private var cachedAnswers: List<String>? = null
    private var cachedAllowed: Set<String>? = null

    fun answers(context: Context): List<String> = cachedAnswers ?: read(context, R.raw.wordle_answers).also { cachedAnswers = it }

    fun allowed(context: Context): Set<String> = cachedAllowed ?: (
        read(context, R.raw.wordle_answers) + read(context, R.raw.wordle_allowed)
        ).toSet().also { cachedAllowed = it }

    private fun read(context: Context, id: Int): List<String> =
        context.resources.openRawResource(id).bufferedReader().useLines { lines ->
            lines.map { it.trim().lowercase() }.filter { it.length == 5 && it.all { c -> c in 'a'..'z' } }.toList()
        }
}
