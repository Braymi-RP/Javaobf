package com.my.newproject7

import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.stmt.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import java.util.ArrayDeque

class ControlFlowFlattenerV2(private val dict: List<String>) {

    private val counter = AtomicInteger(0)

    fun flatten(method: MethodDeclaration): String {
        val body = method.body.orElse(null) ?: return method.toString().trim()
        val allStmts = body.statements.toList()

        if (allStmts.size < 3) return method.toString().trim()
        if (hasUnsafe(allStmts)) return method.toString().trim()
        if (isStatic(method)) return method.toString().trim()

        val uid = counter.incrementAndGet()

        val sv = resolveName(uid * 0x6b43a9L)
        val sv2 = resolveName(uid * 0x1337CAFEL)
        val sv3 = resolveName(uid * 0xABCDEF01L)
        val flag = resolveName(uid * 0x9e3779b9L)
        val junk = resolveName(uid * 0xDEADF00DL)
        val decoy = resolveName(uid * 0xABCDEF01L)

        val retStmt = allStmts.lastOrNull()?.takeIf { it is ReturnStmt } as? ReturnStmt
        val workStmts = if (retStmt != null) allStmts.dropLast(1) else allStmts

        val chunks = workStmts.chunked(1)
        val realStates = List(chunks.size + 1) { i -> stateHash(uid, i) }
        val exitState = realStates.last()

        val fakeStates = generateFakeStates(uid, chunks.size * 6 + 12, realStates)

        fun hex(n: Int) = "0x${Integer.toHexString(n).uppercase()}"

        val mods = method.modifiers.joinToString(" ") { it.keyword.asString() }
        val params = method.parameters.joinToString(", ") { "${it.type} ${it.nameAsString}" }
        val throwsClause = method.thrownExceptions
            .takeIf { !it.isEmpty() }
            ?.joinToString(", ") { it.asString() }
            ?.let { " throws $it" } ?: ""

        return buildString {
            method.annotations.forEach { appendLine(it.toString().trim()) }
            val mod = if (mods.isNotBlank()) "$mods " else ""
            
            appendLine("$mod${method.type} ${method.nameAsString}($params)$throwsClause {")

            // مرحلة التهيئة مع IF متداخلة
            appendLine("    int $sv = ${realStates[0]};")
            appendLine("    int $sv2 = $sv ^ ${hex(realStates[0] xor 0x5A5A5A5A)};")
            appendLine("    int $sv3 = ${hex((uid * 0x87654321L).toInt())};")
            appendLine("    int $flag = 1;")
            appendLine("    int $junk = 0;")
            appendLine("    int $decoy = ${hex((uid * 0x87654321L).toInt())};")

            // طبقة IF أولى للتحقق
            appendLine("    if ($sv != ${realStates[0]}) { $sv = ${realStates[0]}; }")
            appendLine("    if ($flag > 0) {")
            appendLine("        if ($sv2 != ($sv ^ ${hex(realStates[0] xor 0x5A5A5A5A)})) {")
            appendLine("            $sv2 = $sv ^ ${hex(realStates[0] xor 0x5A5A5A5A)};")
            appendLine("        }")
            appendLine("    }")

            appendLine("    while ($flag != 0) {")
            
            // SWITCH رئيسي
            appendLine("        switch ($sv) {")

            chunks.forEachIndexed { i, chunk ->
                val current = realStates[i]
                val next = realStates[i + 1]

                val junk1 = fakeStates[i * 4]
                val junk2 = fakeStates[i * 4 + 1]
                val junk3 = fakeStates[i * 4 + 2]
                val junk4 = fakeStates[i * 4 + 3]

                // حالة وهمية #1 مع switch متداخل
                appendLine("            case $junk1: {")
                appendLine("                $decoy = ($decoy * 0x11 ^ $sv2) + $junk1;")
                appendLine("                $junk ^= ($sv >>> 4) ^ $decoy;")
                appendLine("                switch ($sv2 % 5) {")
                appendLine("                    case 0: $sv3 ^= 0xFF; break;")
                appendLine("                    case 1: $sv3 = ($sv3 * 31) & 0xFFFF; break;")
                appendLine("                    case 2: $sv3 ^= $junk1; break;")
                appendLine("                    case 3: $sv3 = ($sv3 << 3) | ($sv3 >> 29); break;")
                appendLine("                    default: $sv3 ^= 0xDEADBEEF; break;")
                appendLine("                }")
                appendLine("                $sv = ${fakeStates.random()};")
                appendLine("                break;")
                appendLine("            }")

                // حالة وهمية #2 مع IF متداخلة
                appendLine("            case $junk2: {")
                appendLine("                if ($sv2 != 0) {")
                appendLine("                    $sv2 ^= ${hex(junk2)};")
                appendLine("                    if (($sv2 & 0xFF) > 100) {")
                appendLine("                        $junk = ($junk << 1) | ($sv2 & 1);")
                appendLine("                    } else {")
                appendLine("                        $junk = ($junk >> 1) ^ $sv2;")
                appendLine("                    }")
                appendLine("                }")
                appendLine("                $sv = $junk1;")
                appendLine("                break;")
                appendLine("            }")

                // حالة وهمية #3 مع nested switch
                appendLine("            case $junk3: {")
                appendLine("                switch ($decoy & 0xF) {")
                appendLine("                    case 0:")
                appendLine("                    case 1:")
                appendLine("                    case 2: $decoy ^= ($sv >> 8); break;")
                appendLine("                    case 3:")
                appendLine("                    case 4: $decoy = ($decoy * 7) ^ $junk3; break;")
                appendLine("                    default: $decoy = ($decoy >>> 2) ^ 0xABCD; break;")
                appendLine("                }")
                appendLine("                $sv = ${fakeStates.random()};")
                appendLine("                break;")
                appendLine("            }")

                // حالة حقيقية مع switch متداخل معقد
                appendLine("            case $current: {")
                
                // IF متداخلة للتحقق من الحالة
                appendLine("                if ($sv == $current) {")
                appendLine("                    if ($flag > 0) {")
                
                // السلوك الفعلي
                chunk.forEach { stmt ->
                    stmt.toString().trim().lines().forEach { line ->
                        appendLine("                        $line")
                    }
                }

                appendLine("                    }")
                appendLine("                }")

                // طبقات IF معقدة للانتقال للحالة التالية
                if (i < chunks.size / 2 && chunks.size > 3) {
                    appendLine("                if (($sv2 ^ ${hex(junk3)}) % ${fakeStates.size + 7} == ${abs(junk3 % 127)}) {")
                    appendLine("                    if ($decoy > 0) {")
                    appendLine("                        $decoy ^= $sv;")
                    appendLine("                        $sv2 = ${nextFakeTransition(junk3, fakeStates)};")
                    appendLine("                    } else if ($decoy < 0) {")
                    appendLine("                        $decoy ^= $sv2;")
                    appendLine("                        $sv2 = ${nextFakeTransition(junk4, fakeStates)};")
                    appendLine("                    } else {")
                    appendLine("                        $sv = ${advancedTransition(current, next, uid)};")
                    appendLine("                    }")
                    appendLine("                } else {")
                    appendLine("                    $sv = ${advancedTransition(current, next, uid)};")
                    appendLine("                }")
                } else {
                    appendLine("                $sv = ${advancedTransition(current, next, uid)};")
                }

                appendLine("                $sv2 ^= ${hex(current xor next)};")
                appendLine("                break;")
                appendLine("            }")

                // حالة وهمية #4 مع switch آخر
                appendLine("            case $junk4: {")
                appendLine("                switch (($junk * 13) & 3) {")
                appendLine("                    case 0: $sv3 ^= 0xAAAA; break;")
                appendLine("                    case 1: $sv3 += $junk; break;")
                appendLine("                    case 2: $sv3 = ($sv3 * 13) % 0x10000; break;")
                appendLine("                    case 3: $sv3 ^= $decoy; break;")
                appendLine("                }")
                appendLine("                $sv = ${fakeStates.random()};")
                appendLine("                break;")
                appendLine("            }")
            }

            // حالة الخروج مع IF متداخلة
            appendLine("            case $exitState: {")
            appendLine("                if ($sv == $exitState) {")
            appendLine("                    $flag = 0;")
            appendLine("                    if ($junk != 0) { $junk = 0; }")
            appendLine("                } else {")
            appendLine("                    $flag = 0;")
            appendLine("                }")
            appendLine("                break;")
            appendLine("            }")

            // حالات وهمية إضافية قبل default
            fakeStates.takeLast(8).forEach { fs ->
                appendLine("            case $fs: {")
                appendLine("                switch (($sv >>> 4) & 0x3) {")
                appendLine("                    case 0: $decoy = ($decoy * 0x13 ^ $sv) & 0xFFFF; break;")
                appendLine("                    case 1: $decoy ^= ($junk << 7); break;")
                appendLine("                    case 2: $decoy = ($decoy + $sv3) & 0xFFFFFF; break;")
                appendLine("                    case 3: $decoy ^= 0xDEADBEEF; break;")
                appendLine("                }")
                appendLine("                $sv = ${fakeStates.random()};")
                appendLine("                break;")
                appendLine("            }")
            }

            // default مع IF متداخلة
            appendLine("            default: {")
            appendLine("                if ($flag > 0) {")
            appendLine("                    if ($sv != $current) {")
            appendLine("                        $flag = 0;")
            appendLine("                    }")
            appendLine("                } else {")
            appendLine("                    $flag = 0;")
            appendLine("                }")
            appendLine("                break;")
            appendLine("            }")

            appendLine("        }")
            appendLine("    }")

            // return statement مع IF
            if (retStmt != null) {
                val expr = retStmt.expression.map { it.toString() }.orElse(null)
                if (expr != null) {
                    appendLine("    if ($sv == $exitState) {")
                    appendLine("        return $expr;")
                    appendLine("    }")
                } else {
                    appendLine("    if ($sv == $exitState) {")
                    appendLine("        return;")
                    appendLine("    }")
                }
            }
            appendLine("}")
        }
    }

    private fun generateFakeStates(uid: Int, count: Int, real: List<Int>): List<Int> {
        val fakes = mutableSetOf<Int>()
        var seed = uid.toLong() * 0xDEAD1337L + 0xBEEFL
        while (fakes.size < count) {
            seed = (seed * 0x6b43a9L + (0x1234567 + fakes.size))
            val c = stateHash(seed.toInt(), fakes.size + 100)
            if (c !in real) fakes.add(c)
        }
        return fakes.toList().shuffled()
    }

    private fun advancedTransition(current: Int, next: Int, uid: Int): String {
        val p1 = 0x1000003
        val p2 = 0x1000033
        val mask = (uid xor 0x5A5A5A5A) * p1
        return "((($current * $p1) ^ $mask) ^ ($next * $p2)) ^ ($current >>> 5)"
    }

    private fun nextFakeTransition(current: Int, fakes: List<Int>): String {
        val idx = abs((current * 0x7f) % fakes.size)
        return "${fakes[idx]}"
    }

    private fun stateHash(uid: Int, idx: Int): Int {
        var h = uid * 0x9e3779b9.toInt() xor (idx * 0x6b43a9b7.toInt())
        h = h xor (h ushr 16)
        h = h * -0x7a143595
        h = h xor (h shl 13)
        return h and 0x7FFFFFFF
    }

    private fun resolveName(seed: Long): String {
        if (dict.isEmpty()) return "s${(seed and 0xFFFFL)}"
        val idx = (seed.and(0x7FFFFFFFL) % dict.size).toInt()
        val raw = dict[idx].filter { it.isLetterOrDigit() || it == '_' }.take(40)
        return if (raw.isEmpty() || raw[0].isDigit()) "_$raw" else raw
    }

    private fun hasUnsafe(stmts: List<Statement>): Boolean =
        stmts.any { it is TryStmt || it is LabeledStmt || it is SwitchStmt } ||
                stmts.dropLast(1).any { containsReturn(it) }

    private fun containsReturn(stmt: Statement): Boolean {
        val queue: ArrayDeque<Statement> = ArrayDeque()
        queue.add(stmt)
        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: continue
            when (current) {
                is ReturnStmt -> return true
                is BlockStmt -> queue.addAll(current.statements)
                is IfStmt -> {
                    queue.add(current.thenStmt)
                    current.elseStmt.ifPresent { queue.add(it) }
                }
                is ForStmt -> current.body?.let { queue.add(it) }
                is ForEachStmt -> current.body?.let { queue.add(it) }
                is WhileStmt -> current.body?.let { queue.add(it) }
                is DoStmt -> current.body?.let { queue.add(it) }
                else -> {}
            }
        }
        return false
    }

    private fun isStatic(m: MethodDeclaration) =
        m.modifiers.any { it.keyword.asString() == "static" }
}
