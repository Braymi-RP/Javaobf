package com.my.newproject7

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.VariableDeclarationExpr
import com.github.javaparser.ast.stmt.*
import kotlinx.coroutines.*
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class FragmentationEngine(private val log: (String) -> Unit) {

    companion object {
        const val CHUNK_SIZE  = 2
        const val CTX_PREFIX  = "_Ctx"
        const val FRAG_PREFIX = "_F"
        const val RET_FIELD   = "_ret"
        const val CTX_VAR     = "_c"

        private val CHECKED_EXCEPTION_PATTERNS = listOf(
            "KeyStore","KeyStoreException","CertificateException","UnrecoverableKeyException",
            "ZipFile","DexFileFactory","loadDexFile","Smali.assemble","SmaliOptions",
            "getInputStream","getAssets().open","InputStream","FileOutputStream",
            "NoSuchAlgorithmException"
        )
    }

    private val idGen             = AtomicInteger(0)
    private var methodsTotal      = 0
    private var methodsFragmented = 0
    private var classesGenerated  = 0
    private val masker            = XorStringMasker()

    private fun fragSuffix(index: Int): String {
        var n  = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26).toChar())
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    fun process(file: File): String {
        log("  Reading source")
        val src = FileUtils.readFileToString(file, StandardCharsets.UTF_8)

        log("  Parsing AST")
        val cu: CompilationUnit = try {
            StaticJavaParser.parse(src)
        } catch (e: Exception) {
            throw RuntimeException("Parse failed: ${e.message}", e)
        }

        val primary = cu.findFirst(ClassOrInterfaceDeclaration::class.java)
            .orElseThrow { RuntimeException("No class found in '${file.name}'") }

        methodsTotal = primary.methods.size
        log("  Class   : '${primary.nameAsString}'")
        log("  Methods : $methodsTotal")
        log("  XOR key : ${masker.key}\n")

        val fragmented = buildOutput(cu, primary)

        log("  Masking strings")
        val masked = masker.maskSource(fragmented)
        val count  = Regex("_dec\\(").findAll(masked).count()
        log("  Strings encrypted: $count")
        log("  Fragmented: $methodsFragmented  Inner classes: $classesGenerated")

        return masked
    }

    private fun buildOutput(cu: CompilationUnit, cls: ClassOrInterfaceDeclaration): String {
        val sb        = StringBuilder()
        val tab       = "    "
        val outerName = cls.nameAsString

        cu.packageDeclaration.ifPresent { sb.appendLine("package ${it.nameAsString};").appendLine() }
        cu.imports.forEach { sb.appendLine(it.toString().trim()) }
        if (!cu.imports.isEmpty()) sb.appendLine()

        val classMods  = cls.modifiers.joinToString(" ") { it.keyword.asString() }
        val classKw    = if (cls.isInterface) "interface" else "class"
        val extendsStr = cls.extendedTypes.joinToString(", ")    { it.asString() }
        val implsStr   = cls.implementedTypes.joinToString(", ") { it.asString() }

        if (classMods.isNotBlank()) sb.append("$classMods ")
        sb.append("$classKw ${cls.nameAsString}")
        if (extendsStr.isNotBlank()) sb.append(" extends $extendsStr")
        if (implsStr.isNotBlank())   sb.append(" implements $implsStr")
        sb.appendLine(" {").appendLine()

        masker.decStub(tab).lineSequence().forEach { sb.appendLine(it) }
        sb.appendLine()

        cls.fields.forEach { f -> sb.appendLine("$tab${f.toString().trim()}") }
        if (!cls.fields.isEmpty()) sb.appendLine()

        cls.constructors.forEach { ctor ->
            ctor.toString().trim().lineSequence().forEach { sb.appendLine("$tab$it") }
            sb.appendLine()
        }

        cls.methods.forEach { method ->
            val result = fragmentMethod(method, outerName)
            result.generatedClasses.forEach { innerClass ->
                innerClass.lineSequence().forEach { sb.appendLine("$tab$it") }
                sb.appendLine()
            }
            result.methodStub.lineSequence().forEach { sb.appendLine("$tab$it") }
            sb.appendLine()
        }

        cls.members.filterIsInstance<ClassOrInterfaceDeclaration>().forEach { nested ->
            nested.toString().trim().lineSequence().forEach { sb.appendLine("$tab$it") }
            sb.appendLine()
        }

        sb.append("}")
        return sb.toString()
    }

    data class FragResult(val methodStub: String, val generatedClasses: List<String>)

    private fun fragmentMethod(method: MethodDeclaration, outerName: String): FragResult {
        val mName    = method.nameAsString
        val body     = method.body.orElse(null)
        val allStmts = body?.statements?.toList() ?: emptyList()

        val superStmts = allStmts.filter  { isSuperCall(it) }
        val stmts      = allStmts.filter  { !isSuperCall(it) }
        val bodyText   = stmts.joinToString("\n") { it.toString() }

        val skipReason = when {
            body == null               -> "abstract/native"
            stmts.size < 2             -> "too short"
            isStaticMethod(method)     -> "static"
            hasUnsafeFlow(stmts)       -> "unsafe flow"
            hasUncoveredCheckedCalls(method, bodyText) -> "checked exceptions"
            hasVoidReturnConflict(method, stmts)       -> "void return conflict"
            else                       -> null
        }

        if (skipReason != null) {
            log("  skip $mName() ($skipReason)")
            return FragResult(method.toString().trim(), emptyList())
        }

        log("  frag $mName() [${stmts.size} stmts]")

        val id        = idGen.incrementAndGet()
        val ctxClass  = "$CTX_PREFIX$id"
        val returnType = method.type.asString()
        val isVoid    = returnType == "void"
        val chunks    = stmts.chunked(CHUNK_SIZE)
        val scope     = analyzeScope(chunks, method)
        val generated = mutableListOf<String>()

        generated += makeContextClass(ctxClass, scope, returnType, isVoid)

        val fragNames = chunks.indices.map { i -> "$FRAG_PREFIX$id${fragSuffix(i)}" }

        chunks.forEachIndexed { i, chunk ->
            generated += makeFragmentClass(
                name      = fragNames[i],
                nextName  = fragNames.getOrNull(i + 1),
                ctxClass  = ctxClass,
                chunk     = chunk,
                scope     = scope,
                isVoid    = isVoid,
                outerName = outerName
            )
        }

        val stub = makeMethodStub(method, ctxClass, fragNames.first(), scope, isVoid, returnType, superStmts)
        classesGenerated  += generated.size
        methodsFragmented++
        return FragResult(stub, generated)
    }

    data class VarInfo(val type: String, val name: String) {
        val isArrayType: Boolean get() = type.endsWith("[]")
    }

    data class Scope(val params: List<VarInfo>, val shared: List<VarInfo>) {
        val ctxFields   : List<VarInfo> get() = (params + shared).distinctBy { it.name }
        val ctxNames    : Set<String>   get() = ctxFields.map { it.name }.toSet()
        val sharedNames : Set<String>   get() = shared.map { it.name }.toSet()
    }

    private fun analyzeScope(chunks: List<List<Statement>>, method: MethodDeclaration): Scope {
        val params = method.parameters.map { VarInfo(it.type.asString(), it.nameAsString) }
        val shared = mutableListOf<VarInfo>()
        chunks.forEachIndexed { idx, chunk ->
            val declaredHere = chunk.flatMap { extractTopLevelDecls(it) }
            val laterCode    = chunks.drop(idx + 1).flatten().joinToString("\n") { it.toString() }
            declaredHere.forEach { v ->
                val usedLater = laterCode.contains(Regex("\\b${Regex.escape(v.name)}\\b"))
                if (usedLater && shared.none { it.name == v.name }) shared += v
            }
        }
        return Scope(params, shared)
    }

    private fun extractTopLevelDecls(stmt: Statement): List<VarInfo> {
        if (stmt !is ExpressionStmt) return emptyList()
        val vde = stmt.expression as? VariableDeclarationExpr ?: return emptyList()
        return vde.variables.map { v -> VarInfo(v.type.asString(), v.nameAsString) }
    }

    private fun isStaticMethod(m: MethodDeclaration) =
        m.modifiers.any { it.keyword.asString() == "static" }

    private fun isSuperCall(stmt: Statement): Boolean =
        stmt.toString().trimStart().startsWith("super.")

    private fun hasUnsafeFlow(stmts: List<Statement>): Boolean {
        if (stmts.any { it is TryStmt || it is LabeledStmt }) return true
        stmts.dropLast(1).forEach { if (containsReturnAnywhere(it)) return true }
        val last = stmts.lastOrNull()
        if (last != null && last !is ReturnStmt && containsReturnAnywhere(last)) return true
        return false
    }

    private fun hasUncoveredCheckedCalls(method: MethodDeclaration, bodyText: String): Boolean {
        val declaredThrows = method.thrownExceptions.map { it.asString() }.toSet()
        if (declaredThrows.isNotEmpty()) return true
        return CHECKED_EXCEPTION_PATTERNS.any { pattern -> bodyText.contains(pattern) }
    }

    private fun hasVoidReturnConflict(method: MethodDeclaration, stmts: List<Statement>): Boolean {
        if (method.type.asString() != "void") return false
        return stmts.any { stmt -> containsValueReturn(stmt) }
    }

    private fun containsValueReturn(stmt: Statement): Boolean = when (stmt) {
        is ReturnStmt  -> stmt.expression.isPresent
        is BlockStmt   -> stmt.statements.any { containsValueReturn(it) }
        is IfStmt      -> containsValueReturn(stmt.thenStmt) ||
                          stmt.elseStmt.map { containsValueReturn(it) }.orElse(false)
        is ForStmt     -> containsValueReturn(stmt.body)
        is ForEachStmt -> containsValueReturn(stmt.body)
        is WhileStmt   -> containsValueReturn(stmt.body)
        is DoStmt      -> containsValueReturn(stmt.body)
        else           -> false
    }

    private fun containsReturnAnywhere(stmt: Statement): Boolean = when (stmt) {
        is ReturnStmt  -> true
        is BlockStmt   -> stmt.statements.any { containsReturnAnywhere(it) }
        is IfStmt      -> containsReturnAnywhere(stmt.thenStmt) ||
                          stmt.elseStmt.map { containsReturnAnywhere(it) }.orElse(false)
        is ForStmt     -> containsReturnAnywhere(stmt.body)
        is ForEachStmt -> containsReturnAnywhere(stmt.body)
        is WhileStmt   -> containsReturnAnywhere(stmt.body)
        is DoStmt      -> containsReturnAnywhere(stmt.body)
        else           -> false
    }

    private fun makeContextClass(ctxClass: String, scope: Scope, returnType: String, isVoid: Boolean): String = buildString {
        appendLine("private final class $ctxClass {")
        scope.ctxFields.forEach { v -> appendLine("    ${v.type} ${v.name};") }
        if (!isVoid) appendLine("    $returnType $RET_FIELD;")
        append("}")
    }

    private fun makeFragmentClass(
        name: String, nextName: String?, ctxClass: String,
        chunk: List<Statement>, scope: Scope, isVoid: Boolean, outerName: String
    ): String = buildString {
        appendLine("private final class $name implements Runnable {")
        appendLine("    private final $ctxClass $CTX_VAR;")
        appendLine()
        appendLine("    $name($ctxClass $CTX_VAR) { this.$CTX_VAR = $CTX_VAR; }")
        appendLine()
        appendLine("    @Override")
        appendLine("    public void run() {")
        chunk.forEach { stmt ->
            val transformed = transformStatement(stmt, scope, isVoid)
            val qualified   = qualifyThis(transformed, outerName)
            qualified.lineSequence().filter { it.isNotBlank() }
                .forEach { line -> appendLine("        $line") }
        }
        if (nextName != null) appendLine("        new $nextName($CTX_VAR).run();")
        appendLine("    }")
        append("}")
    }

    private fun makeMethodStub(
        method: MethodDeclaration, ctxClass: String, firstFrag: String,
        scope: Scope, isVoid: Boolean, returnType: String, superStmts: List<Statement>
    ): String = buildString {
        val mods   = method.modifiers.joinToString(" ") { it.keyword.asString() }
        val params = method.parameters.joinToString(", ") { "${it.type} ${it.nameAsString}" }
        val throws = method.thrownExceptions.takeIf { !it.isEmpty() }
            ?.joinToString(", ") { it.asString() }?.let { " throws $it" } ?: ""

        method.annotations.forEach { anno -> appendLine(anno.toString().trim()) }
        val modsStr = if (mods.isNotBlank()) "$mods " else ""
        appendLine("${modsStr}$returnType ${method.nameAsString}($params)$throws {")
        superStmts.forEach { s -> appendLine("    ${s.toString().trim()}") }
        appendLine("    final $ctxClass $CTX_VAR = new $ctxClass();")
        scope.params.forEach { p -> appendLine("    $CTX_VAR.${p.name} = ${p.name};") }
        appendLine("    new $firstFrag($CTX_VAR).run();")
        if (!isVoid) appendLine("    return $CTX_VAR.$RET_FIELD;")
        append("}")
    }

    private fun transformStatement(stmt: Statement, scope: Scope, isVoid: Boolean): String {
        if (stmt is ReturnStmt) {
            return if (isVoid) "return;"
            else {
                val expr = stmt.expression.map { redirectRefs(it.toString(), scope.ctxNames) }.orElse("null")
                "$CTX_VAR.$RET_FIELD = $expr;\nreturn;"
            }
        }
        if (stmt is ExpressionStmt && stmt.expression is VariableDeclarationExpr) {
            val vde = stmt.expression as VariableDeclarationExpr
            return buildString {
                vde.variables.forEach { v ->
                    val initStr = v.initializer.map { redirectRefs(it.toString(), scope.ctxNames) }.orElse(null)
                    if (v.nameAsString in scope.sharedNames) {
                        if (initStr != null) appendLine("$CTX_VAR.${v.nameAsString} = $initStr;")
                    } else {
                        val init = if (initStr != null) " = $initStr" else ""
                        appendLine("${v.type} ${v.nameAsString}$init;")
                    }
                }
            }.trimEnd()
        }
        return redirectRefs(stmt.toString().trim(), scope.ctxNames)
    }

    private fun redirectRefs(code: String, ctxFields: Set<String>): String {
        if (ctxFields.isEmpty()) return code
        var result = code
        ctxFields.sortedByDescending { it.length }.forEach { field ->
            result = Regex("(?<!\\.)\\b${Regex.escape(field)}\\b")
                .replace(result) { "$CTX_VAR.$field" }
        }
        return result
    }

    private fun qualifyThis(code: String, outerName: String): String =
        Regex("(?<![\\w.])this\\b").replace(code) { "$outerName.this" }
}