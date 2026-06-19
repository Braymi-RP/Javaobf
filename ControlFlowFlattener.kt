package com.my.newproject7

import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.stmt.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import java.util.ArrayDeque

class ControlFlowFlattener(private val dict: List<String>) {

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
        val flag = resolveName(uid * 0x9e3779b9L)
        val junk = resolveName(uid * 0xDEADF00DL)
        val decoy = resolveName(uid * 0xABCDEF01L)

        val retStmt = allStmts.lastOrNull()?.takeIf { it is ReturnStmt } as? ReturnStmt
        val workStmts = if (retStmt != null) allStmts.dropLast(1) else allStmts

        val chunks = workStmts.chunked(1)
        val realStates = List(chunks.size + 1) { i -> stateHash(uid, i) }
        val exitState = realStates.last()

        val fakeStates = generateFakeStates(uid, chunks.size * 4 + 8, realStates)

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
            
            // ط§ط³طھط®ط¯ط§ظ… Raw String ظ„طھط¬ظ†ط¨ ظ…ط´ط§ظƒظ„ Escape Sequences
            appendLine("""$mod ${method.type} ${method.nameAsString}($params)$throwsClause {""")

            appendLine("    int $sv = ${realStates[0]};")
            appendLine("    int $sv2 = $sv ^ ${hex(realStates[0] xor 0x5A5A5A5A)};")
            appendLine("    int $flag = 1;")
            appendLine("    int $junk = 0;")
            appendLine("    int $decoy = ${hex((uid * 0x87654321L).toInt())};")

            appendLine("    while ($flag != 0) {")
            appendLine("        switch ($sv) {")

            chunks.forEachIndexed { i, chunk ->
                val current = realStates[i]
                val next = realStates[i + 1]

                val junk1 = fakeStates[i * 3]
                val junk2 = fakeStates[i * 3 + 1]
                val junk3 = fakeStates[i * 3 + 2]

                appendLine("            case $junk1:")
                appendLine("                $decoy = ($decoy * 0x11 ^ $sv2) + $junk1;")
                appendLine("                $junk ^= ($sv >>> 4) ^ $decoy;")
                appendLine("                $sv = ${fakeStates.random()};")
                appendLine("                break;")

                appendLine("            case $junk2:")
                appendLine("                $sv2 = $sv2 ^ ${hex(junk2)};")
                appendLine("                $junk = ($junk << 1) | ($sv2 & 1);")
                appendLine("                $sv = $junk1;")
                appendLine("                break;")

                appendLine("            case $current: {")
                chunk.forEach { stmt ->
                    stmt.toString().trim().lines().forEach { line ->
                        appendLine("                $line")
                    }
                }

                if (i < chunks.size / 2 && chunks.size > 3) {
                    appendLine("                switch ($sv2 % ${fakeStates.size + 7}) {")
                    appendLine("                    case ${abs(junk3 % 127)}:")
                    appendLine("                        $decoy ^= $sv;")
                    appendLine("                        $sv2 = ${nextFakeTransition(junk3, fakeStates)};")
                    appendLine("                        break;")
                    appendLine("                    default:")
                    appendLine("                        $sv = ${advancedTransition(current, next, uid)};")
                    appendLine("                        break;")
                    appendLine("                }")
                } else {
                    appendLine("                $sv = ${advancedTransition(current, next, uid)};")
                }

                appendLine("                $sv2 ^= ${hex(current xor next)};")
                appendLine("                break;")
                appendLine("            }")
            }

            appendLine("            case $exitState:")
            appendLine("                $flag = 0;")
            appendLine("                $junk = 0;")
            appendLine("                break;")

            fakeStates.takeLast(6).forEach { fs ->
                appendLine("            case $fs:")
                appendLine("                $decoy = ($decoy * 0x13 ^ $sv) & 0xFFFF;")
                appendLine("                $sv = ${fakeStates.random()};")
                appendLine("                break;")
            }

            appendLine("            default:")
            appendLine("                $flag = 0;")
            appendLine("                break;")
            appendLine("        }")
            appendLine("    }")

            if (retStmt != null) {
                val expr = retStmt.expression.map { it.toString() }.orElse(null)
                if (expr != null) appendLine("    return $expr;")
                else appendLine("    return;")
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