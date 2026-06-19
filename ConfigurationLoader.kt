package com.my.newproject7

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets

class ConfigurationLoader(private val context: Context) {

    private val dict = mutableListOf<String>()
    private var loaded = false

    fun load(): List<String> {
        if (loaded) return dict.toList()
        dict.clear()
        try {
            context.assets.open("0.txt").bufferedReader(StandardCharsets.UTF_8).use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) dict.add(trimmed)
                }
            }
        } catch (e: Exception) {
            loadFallback()
        }
        loaded = true
        return dict.toList()
    }

    fun loadFromFile(file: File): List<String> {
        dict.clear()
        loaded = false
        file.readLines(StandardCharsets.UTF_8).forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) dict.add(trimmed)
        }
        loaded = true
        return dict.toList()
    }

    fun deriveNames(count: Int): List<String> {
        val d = if (loaded) dict else load()
        if (d.isEmpty()) return List(count) { i -> "_n$i" }
        val result = mutableListOf<String>()
        var seed = 0xDEAD_BEEF.toInt()
        repeat(count) {
            seed = seed * 1664525 + 1013904223
            val idx = (seed.toLong() and 0x7FFFFFFFL % d.size).toInt()
            val raw = d[idx].filter { c -> c.isLetterOrDigit() || c == '_' }.take(40)
            val name = if (raw.isEmpty() || raw[0].isDigit()) "_$raw" else raw
            result.add(if (name.isBlank()) "_x${result.size}" else name)
        }
        return result
    }

    fun buildNamingMap(seeds: List<Int>): Map<Int, String> {
        val d = if (loaded) dict else load()
        val map = mutableMapOf<Int, String>()
        seeds.forEach { seed ->
            val idx = (seed.toLong() and 0x7FFFFFFFL % maxOf(d.size, 1)).toInt()
            val raw = if (d.isEmpty()) "s${seed and 0xFFFF}"
                      else d[idx].filter { c -> c.isLetterOrDigit() || c == '_' }.take(40)
            map[seed] = if (raw.isEmpty() || raw[0].isDigit()) "_$raw" else raw
        }
        return map
    }

    fun size(): Int = dict.size

    private fun loadFallback() {
        val fallback = listOf(
            "\u0130", "\u0131", "\u0141", "\u0142", "\u0143", "\u0144",
            "lI", "Il", "II", "ll", "lI1", "I1l",
            "_0x1", "_0x2", "_0x3", "_0x4", "_0x5"
        )
        dict.addAll(fallback)
    }
}