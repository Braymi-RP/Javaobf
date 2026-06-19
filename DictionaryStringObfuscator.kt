package com.my.newproject7

import java.io.File
import java.nio.charset.StandardCharsets

class DictionaryStringObfuscator(
    private val log: (String) -> Unit,
    private val packageName: String = "com.runtime.core"
) {

    private val pool     = mutableListOf<ByteArray>()
    private val keyStore = mutableListOf<Int>()
    private val dict     = mutableListOf<String>()

    private val XK1 = 0x661
    private val XK2 = 0x759
    private val XK3 = 0x6dd
    private val XKE = 0x137

    private val CF = 0x0e;  private val CP = 0x31
    private val CW = 0xcc;  private val CD = 0xef

    private val CLC = 0x11;  private val CLB = 0x3b;  private val CLE = 0x6e

    private val CSB = 0x11;  private val CSR = 0x36

    private val SI  = CP  xor XK1
    private val SF  = CD  xor XK1
    private val SW  = CW  xor XK1
    private val L2I = CLC xor XK2
    private val L2B = CLB xor XK2
    private val L2E = CLE xor XK2
    private val SCI = CSB xor XK3
    private val SCB = CSR xor XK3

    private val STRING_LIT = Regex(""""((?:[^"\\]|\\.)*)"""")
    private val SKIP_LINE  = Regex("""^\s*(package |import |@|\*|//)""")

    private fun h(v: Int) = "0x${Integer.toHexString(v)}"
    private fun lit(r: String, v: Int) = when (v) {
        in -8..7         -> "const/4 $r, $v"
        in -32768..32767 -> "const/16 $r, ${h(v and 0xFFFF)}"
        else             -> "const $r, ${h(v)}"
    }
    private fun idx(r: String, i: Int) = when (i) {
        in 0..7     -> "const/4 $r, $i"
        in 8..32767 -> "const/16 $r, $i"
        else        -> "const $r, $i"
    }

    fun loadDictionary(assetFile: File) {
        dict.clear()
        assetFile.readLines(StandardCharsets.UTF_8).forEach { line ->
            val t = line.trim(); if (t.isNotEmpty()) dict.add(t)
        }
        log("  Dictionary loaded: ${dict.size} symbols from '${assetFile.name}'")
        if (dict.isEmpty()) throw IllegalStateException("Dictionary file is empty: ${assetFile.absolutePath}")
    }

    fun obfuscateSource(source: String): String {
        pool.clear(); keyStore.clear()
        return source.lines().joinToString("\n") { line ->
            if (SKIP_LINE.containsMatchIn(line)) line else replaceStringsInLine(line)
        }
    }

    fun generatePoolClass(pkg: String = packageName): String {
        val sb = StringBuilder()
        sb.appendLine("package $pkg;")
        sb.appendLine()
        sb.appendLine("public final class P {")
        sb.appendLine()
        sb.appendLine("    private P() {}")
        sb.appendLine()
        sb.appendLine("    public static final byte[][] a = {")
        pool.forEachIndexed { idx, encrypted ->
            val literal = encrypted.joinToString(", ") { "(byte)${it.toInt() and 0xFF}" }
            if (idx < pool.size - 1) sb.appendLine("        {$literal},")
            else sb.appendLine("        {$literal}")
        }
        sb.appendLine("    };")
        sb.appendLine("}")
        return sb.toString()
    }

    fun generateDecoderClass(pkg: String = packageName): String {
        val sb = StringBuilder()
        sb.appendLine("package $pkg;")
        sb.appendLine()
        sb.appendLine("public final class D {")
        sb.appendLine()
        sb.appendLine("    private D() {}")
        sb.appendLine()
        sb.appendLine("    public static String r(int index, int key) {")
        sb.appendLine("        byte[] src = P.a[index];")
        sb.appendLine("        byte[] out = new byte[src.length];")
        sb.appendLine("        byte b0 = (byte)(key >>> 24);")
        sb.appendLine("        byte b1 = (byte)(key >>> 16);")
        sb.appendLine("        byte b2 = (byte)(key >>> 8);")
        sb.appendLine("        byte b3 = (byte)(key);")
        sb.appendLine("        for (int i = 0; i < src.length; i++) {")
        sb.appendLine("            switch (i & 3) {")
        sb.appendLine("                case 0: out[i] = (byte)(src[i] ^ b0); break;")
        sb.appendLine("                case 1: out[i] = (byte)(src[i] ^ b1); break;")
        sb.appendLine("                case 2: out[i] = (byte)(src[i] ^ b2); break;")
        sb.appendLine("                default: out[i] = (byte)(src[i] ^ b3); break;")
        sb.appendLine("            }")
        sb.appendLine("        }")
        sb.appendLine("        try {")
        sb.appendLine("            return new String(out, java.nio.charset.StandardCharsets.UTF_8);")
        sb.appendLine("        } catch (Exception e) {")
        sb.appendLine("            return \"\";")
        sb.appendLine("        }")
        sb.appendLine("    }")
        sb.appendLine("}")
        return sb.toString()
    }

    fun generatePoolSmali(pkg: String = packageName): String {
        val pkgPath = pkg.replace('.', '/')
        val ec = List(pool.size + 1) { i -> 0x200 + i * 0x37 }

        return buildString {
            appendLine(".class public final L$pkgPath/P;")
            appendLine(".super Ljava/lang/Object;")
            appendLine()
            appendLine(".field public static final a:[[B")
            appendLine()
            appendLine(".method private constructor <init>()V")
            appendLine("    .registers 1")
            appendLine("    invoke-direct {p0}, Ljava/lang/Object;-><init>()V")
            appendLine("    return-void")
            appendLine(".end method")
            appendLine()
            appendLine(".method static constructor <clinit>()V")
            appendLine("    .registers 5")
            appendLine()
            appendLine("    const/4 v1, 0x4")
            appendLine("    add-int/lit8 v4, v1, 0x1")
            appendLine("    mul-int/2addr v1, v4")
            appendLine("    and-int/lit8 v1, v1, 0x1")
            appendLine()
            appendLine("    ${lit("v0", SI)}")
            appendLine("    :goto_P")
            appendLine("    xor-int/lit16 v0, v0, ${h(XK1)}")
            appendLine("    sparse-switch v0, :sw_P_main")
            appendLine("    goto :goto_P")
            appendLine()
            appendLine("    :sw_P_fake")
            appendLine("    ${lit("v0", SF)}")
            appendLine("    goto :goto_P")
            appendLine()
            appendLine("    :sw_P_pred")
            appendLine("    if-gtz v1, :sw_P_fake")
            appendLine("    ${lit("v0", SW)}")
            appendLine("    goto :goto_P")
            appendLine()
            appendLine("    :sw_P_work")
            appendLine("    ${lit("v2", pool.size)}")
            appendLine("    new-array v2, v2, [[B")

            if (pool.isEmpty()) {
                appendLine("    sput-object v2, L$pkgPath/P;->a:[[B")
                appendLine("    ${lit("v0", SF)}")
                appendLine("    goto :goto_P")
            } else {
                appendLine("    ${lit("v0", ec[0] xor XKE)}")
                appendLine()
                appendLine("    :goto_PE")
                appendLine("    xor-int/lit16 v0, v0, ${h(XKE)}")
                appendLine("    sparse-switch v0, :sw_P_entries")
                appendLine("    goto :goto_PE")
                appendLine()
                pool.forEachIndexed { i, bytes ->
                    appendLine("    :sw_PE_$i")
                    appendLine("    ${lit("v3", bytes.size)}")
                    appendLine("    new-array v3, v3, [B")
                    appendLine("    fill-array-data v3, :P_arr_$i")
                    appendLine("    ${idx("v4", i)}")
                    appendLine("    aput-object v3, v2, v4")
                    if (i < pool.size - 1) {
                        appendLine("    ${lit("v0", ec[i + 1] xor XKE)}")
                        appendLine("    goto :goto_PE")
                    }
                    appendLine()
                }
                appendLine("    sput-object v2, L$pkgPath/P;->a:[[B")
                appendLine("    ${lit("v0", SF)}")
                appendLine("    goto :goto_P")
            }
            appendLine()
            appendLine("    :sw_P_dead")
            appendLine("    return-void")
            appendLine()
            appendLine("    :sw_P_main")
            appendLine("    .sparse-switch")
            appendLine("        ${h(CF)} -> :sw_P_fake")
            appendLine("        ${h(CP)} -> :sw_P_pred")
            appendLine("        ${h(CW)} -> :sw_P_work")
            appendLine("        ${h(CD)} -> :sw_P_dead")
            appendLine("    .end sparse-switch")
            appendLine()
            if (pool.isNotEmpty()) {
                appendLine("    :sw_P_entries")
                appendLine("    .sparse-switch")
                pool.indices.forEach { i -> appendLine("        ${h(ec[i])} -> :sw_PE_$i") }
                appendLine("    .end sparse-switch")
                appendLine()
                pool.forEachIndexed { i, bytes ->
                    appendLine("    :P_arr_$i")
                    appendLine("    .array-data 1")
                    bytes.forEach { b -> appendLine("        ${b.toInt()}") }
                    appendLine("    .end array-data")
                    appendLine()
                }
            }
            append(".end method")
        }
    }

    fun generateDecoderSmali(pkg: String = packageName): String {
        val pkgPath = pkg.replace('.', '/')
        return buildString {
            appendLine(".class public final L$pkgPath/D;")
            appendLine(".super Ljava/lang/Object;")
            appendLine()
            appendLine(".method private constructor <init>()V")
            appendLine("    .registers 1")
            appendLine("    invoke-direct {p0}, Ljava/lang/Object;-><init>()V")
            appendLine("    return-void")
            appendLine(".end method")
            appendLine()
            appendLine(".method public static r(II)Ljava/lang/String;")
            appendLine("    .registers 16")
            appendLine()
            appendLine("    const/4 v1, 0x5")
            appendLine("    add-int/lit8 v13, v1, 0x1")
            appendLine("    mul-int/2addr v1, v13")
            appendLine("    and-int/lit8 v1, v1, 0x1")
            appendLine()
            appendLine("    ${lit("v0", SI)}")
            appendLine("    :goto_D")
            appendLine("    xor-int/lit16 v0, v0, ${h(XK1)}")
            appendLine("    sparse-switch v0, :sw_D_main")
            appendLine("    goto :goto_D")
            appendLine()
            appendLine("    :sw_D_fake")
            appendLine("    ${lit("v0", SF)}")
            appendLine("    goto :goto_D")
            appendLine()
            appendLine("    :sw_D_pred")
            appendLine("    if-gtz v1, :sw_D_fake")
            appendLine("    ${lit("v0", SW)}")
            appendLine("    goto :goto_D")
            appendLine()
            appendLine("    :sw_D_work")
            appendLine("    sget-object v3, L$pkgPath/P;->a:[[B")
            appendLine("    aget-object v4, v3, p0")
            appendLine("    array-length v5, v4")
            appendLine("    new-array v6, v5, [B")
            appendLine()
            appendLine("    ushr-int/lit8 v9, p1, 0x18")
            appendLine("    int-to-byte v9, v9")
            appendLine("    ushr-int/lit8 v10, p1, 0x10")
            appendLine("    int-to-byte v10, v10")
            appendLine("    ushr-int/lit8 v11, p1, 0x8")
            appendLine("    int-to-byte v11, v11")
            appendLine("    int-to-byte v12, p1")
            appendLine()
            appendLine("    const/4 v7, 0x0")
            appendLine()
            appendLine("    ${lit("v2", L2I)}")
            appendLine("    :goto_DL")
            appendLine("    xor-int/lit16 v2, v2, ${h(XK2)}")
            appendLine("    sparse-switch v2, :sw_DL_data")
            appendLine("    goto :goto_DL")
            appendLine()
            appendLine("    :sw_DL_chk")
            appendLine("    if-ge v7, v5, :DL_to_exit")
            appendLine("    ${lit("v2", L2B)}")
            appendLine("    goto :goto_DL")
            appendLine()
            appendLine("    :DL_to_exit")
            appendLine("    ${lit("v2", L2E)}")
            appendLine("    goto :goto_DL")
            appendLine()
            appendLine("    :sw_DL_body")
            appendLine("    aget-byte v8, v4, v7")
            appendLine("    and-int/lit8 v13, v7, 0x3")
            appendLine("    packed-switch v13, :psw_D")
            appendLine("    :D_x0")
            appendLine("    xor-int/2addr v8, v9")
            appendLine("    goto :D_xdone")
            appendLine("    :D_x1")
            appendLine("    xor-int/2addr v8, v10")
            appendLine("    goto :D_xdone")
            appendLine("    :D_x2")
            appendLine("    xor-int/2addr v8, v11")
            appendLine("    goto :D_xdone")
            appendLine("    :D_x3")
            appendLine("    xor-int/2addr v8, v12")
            appendLine("    :D_xdone")
            appendLine("    int-to-byte v8, v8")
            appendLine("    aput-byte v8, v6, v7")
            appendLine("    add-int/lit8 v7, v7, 0x1")
            appendLine("    ${lit("v2", L2I)}")
            appendLine("    goto :goto_DL")
            appendLine()
            appendLine("    :sw_DL_exit")
            appendLine("    sget-object v13, Ljava/nio/charset/StandardCharsets;->UTF_8:Ljava/nio/charset/Charset;")
            appendLine("    new-instance v0, Ljava/lang/String;")
            appendLine("    invoke-direct {v0, v6, v13}, Ljava/lang/String;-><init>([BLjava/nio/charset/Charset;)V")
            appendLine("    return-object v0")
            appendLine()
            appendLine("    :sw_D_dead")
            appendLine("    const/4 v0, 0x0")
            appendLine("    ${lit("v2", SCI)}")
            appendLine("    :goto_Dsec")
            appendLine("    xor-int/lit16 v2, v2, ${h(XK3)}")
            appendLine("    sparse-switch v2, :sw_Dsec_data")
            appendLine("    goto :goto_Dsec")
            appendLine()
            appendLine("    :sw_Dsec_bounce")
            appendLine("    ${lit("v2", SCB)}")
            appendLine("    goto :goto_Dsec")
            appendLine()
            appendLine("    :sw_Dsec_ret")
            appendLine("    return-object v0")
            appendLine()
            appendLine("    :psw_D")
            appendLine("    .packed-switch 0x0")
            appendLine("        :D_x0")
            appendLine("        :D_x1")
            appendLine("        :D_x2")
            appendLine("        :D_x3")
            appendLine("    .end packed-switch")
            appendLine()
            appendLine("    :sw_D_main")
            appendLine("    .sparse-switch")
            appendLine("        ${h(CF)} -> :sw_D_fake")
            appendLine("        ${h(CP)} -> :sw_D_pred")
            appendLine("        ${h(CW)} -> :sw_D_work")
            appendLine("        ${h(CD)} -> :sw_D_dead")
            appendLine("    .end sparse-switch")
            appendLine()
            appendLine("    :sw_DL_data")
            appendLine("    .sparse-switch")
            appendLine("        ${h(CLC)} -> :sw_DL_chk")
            appendLine("        ${h(CLB)} -> :sw_DL_body")
            appendLine("        ${h(CLE)} -> :sw_DL_exit")
            appendLine("    .end sparse-switch")
            appendLine()
            appendLine("    :sw_Dsec_data")
            appendLine("    .sparse-switch")
            appendLine("        ${h(CSB)} -> :sw_Dsec_bounce")
            appendLine("        ${h(CSR)} -> :sw_Dsec_ret")
            appendLine("    .end sparse-switch")
            appendLine()
            append(".end method")
        }
    }

    private fun pickSymbols(stringLength: Int): List<String> {
        if (dict.isEmpty()) throw IllegalStateException("Dictionary not loaded.")
        val n = ((stringLength / 4) + 1).coerceIn(1, dict.size)
        val picks = mutableListOf<String>(); val used = mutableSetOf<Int>()
        var seed = stringLength * 31 + pool.size * 17
        repeat(n) {
            seed = (seed * 1664525 + 1013904223) and Int.MAX_VALUE
            var i = seed % dict.size; if (i < 0) i += dict.size
            var att = 0
            while (i in used && att < dict.size) { i = (i + 1) % dict.size; att++ }
            used.add(i); picks.add(dict[i])
        }
        return picks
    }

    private fun computeSaltHash(symbols: List<String>): Int {
        var hash = symbols.first().hashCode()
        for (i in 1 until symbols.size) {
            hash = hash xor (symbols[i].hashCode() shl (i and 31))
            hash = hash xor (hash ushr 16); hash = hash * -2048144789
            hash = hash xor (hash ushr 13); hash = hash * -1028477387
            hash = hash xor (hash ushr 16)
        }
        return hash
    }

    private fun encryptString(raw: String, key: Int): ByteArray {
        val bytes  = raw.toByteArray(StandardCharsets.UTF_8)
        val result = ByteArray(bytes.size)
        val b0 = (key ushr 24).toByte(); val b1 = (key ushr 16).toByte()
        val b2 = (key ushr 8).toByte();  val b3 = key.toByte()
        for (i in bytes.indices) {
            result[i] = when (i and 3) {
                0    -> (bytes[i].toInt() xor b0.toInt()).toByte()
                1    -> (bytes[i].toInt() xor b1.toInt()).toByte()
                2    -> (bytes[i].toInt() xor b2.toInt()).toByte()
                else -> (bytes[i].toInt() xor b3.toInt()).toByte()
            }
        }
        return result
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder(); var i = 0
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
                    'u'  -> {
                        val hex = s.substring(i + 2, minOf(i + 6, s.length))
                        sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                        i += 2 + hex.length
                    }
                    else -> { sb.append(s[i + 1]); i += 2 }
                }
            } else { sb.append(s[i++]) }
        }
        return sb.toString()
    }

    private fun replaceStringsInLine(line: String): String =
        STRING_LIT.replace(line) { m ->
            val raw = m.groupValues[1]; val unescaped = unescape(raw)
            val symbols  = pickSymbols(unescaped.length)
            val saltHash = computeSaltHash(symbols)
            val encrypted = encryptString(unescaped, saltHash)
            val index = pool.size; pool.add(encrypted); keyStore.add(saltHash)
            "$packageName.D.r($index, $saltHash)"
        }
}