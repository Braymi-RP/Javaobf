package com.my.newproject7

import java.nio.charset.StandardCharsets

class XorStringMasker {

    val key: Int = (10..250).random()

    private val STRING_LIT = Regex(""""((?:[^"\\]|\\.)*)"""")
    private val SKIP_LINE  = Regex("""^\s*(package |import |@|//.*${'$'}|/\*|\*|${'$'})""")

    fun maskSource(source: String): String =
        source.lines().joinToString("\n") { line ->
            if (SKIP_LINE.containsMatchIn(line)) line
            else replaceStringsInLine(line)
        }

    fun decStub(indent: String = "    "): String = buildString {
        appendLine("${indent}private static final int _k = $key;")
        appendLine("${indent}private static String _dec(byte[] b) {")
        appendLine("${indent}    byte[] r = new byte[b.length];")
        appendLine("${indent}    for (int i = 0; i < b.length; i++)")
        appendLine("${indent}        r[i] = (byte)(b[i] ^ _k);")
        appendLine("${indent}    return new String(r, java.nio.charset.StandardCharsets.UTF_8);")
        append    ("${indent}}")
    }

    private fun replaceStringsInLine(line: String): String =
        STRING_LIT.replace(line) { m ->
            val raw       = m.groupValues[1]
            val unescaped = unescape(raw)
            encryptToCall(unescaped)
        }

    private fun encryptToCall(s: String): String {
        val bytes   = s.toByteArray(StandardCharsets.UTF_8)
        val enc     = bytes.map { b -> (b.toInt() and 0xFF) xor key }
        val literal = enc.joinToString(", ") { "(byte)$it" }
        return "_dec(new byte[]{$literal})"
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i  = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    '"'  -> { sb.append('"');  i += 2 }
                    '\'' -> { sb.append('\''); i += 2 }
                    'n'  -> { sb.append('\n'); i += 2 }
                    'r'  -> { sb.append('\r'); i += 2 }
                    't'  -> { sb.append('\t'); i += 2 }
                    'b'  -> { sb.append('\b'); i += 2 }
                    'f'  -> { sb.append(12.toChar()); i += 2 }
                    'u'  -> {
                        val hex = s.substring(i + 2, minOf(i + 6, s.length))
                        sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                        i += 2 + hex.length
                    }
                    else -> { sb.append(s[i + 1]); i += 2 }
                }
            } else {
                sb.append(s[i++])
            }
        }
        return sb.toString()
    }
}