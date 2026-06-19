package com.my.newproject7

import android.content.Context
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.system.measureTimeMillis

class ObfuscationOrchestrator(
    private val context: Context,
    private val log: (String) -> Unit
) {

    data class ObfuscationConfig(
        val enableRename        : Boolean = true,
        val enableControlFlow   : Boolean = true,
        val enableApiDispatch   : Boolean = true,
        val enableStringObf     : Boolean = true,
        val enableFragmentation : Boolean = false,
        val packageName         : String  = "com.my.newproject7"
    )

    data class ObfuscationResult(
        val outputFiles : List<File>,
        val mappingLog  : String,
        val stats       : Stats
    )

    data class Stats(
        val methodsRenamed    : Int,
        val fieldsRenamed     : Int,
        val stringsEncrypted  : Int,
        val apiCallsWrapped   : Int,
        val methodsFlattened  : Int
    )

    private val RECEIVER_TYPE_MAP = mapOf(
        "getPackageArchiveInfo" to "android.content.pm.PackageManager",
        "getInstalledPackages"  to "android.content.pm.PackageManager",
        "getPackageInfo"        to "android.content.pm.PackageManager",
        "getApplicationLabel"   to "android.content.pm.PackageManager",
        "getApplicationIcon"    to "android.content.pm.PackageManager",
        "queryIntentActivities" to "android.content.pm.PackageManager",
        "getActivityInfo"       to "android.content.pm.PackageManager",
        "resolveService"        to "android.content.pm.PackageManager",
        "resolveActivity"       to "android.content.pm.PackageManager"
    )

    fun process(
        inputFile: File,
        outputDir: File,
        config: ObfuscationConfig = ObfuscationConfig()
    ): ObfuscationResult {

        log("  Input  : ${inputFile.absolutePath}")
        log("  Output : ${outputDir.absolutePath}\n")

        outputDir.mkdirs()

        val loader = ConfigurationLoader(context)
        val dict = loader.load()
        log("  Dictionary: ${dict.size} symbols\n")

        var source = FileUtils.readFileToString(inputFile, StandardCharsets.UTF_8)

        val actualPackage = extractPackage(source)
        log("  Package: $actualPackage\n")

        val seed = actualPackage.hashCode().toLong()  // Seed موحد
        log("  Using seed: $seed for consistent renaming\n")

        // إصلاح بعض المشاكل قبل البدء
        val repaired = repairChainedDispatcherCalls(source)
        if (repaired != source) {
            source = repaired
            log("  Repaired chained dispatcher calls\n")
        }

        val outputFiles = mutableListOf<File>()
        val mappingLines = mutableListOf<String>()

        var methodsRenamed = 0
        var fieldsRenamed = 0
        var stringsEncrypted = 0
        var apiCallsWrapped = 0
        var methodsFlattened = 0

        // ====================== PIPELINE الجديد (الترتيب المطلوب) ======================

        // 1. String Encryption (أولاً)
        if (config.enableStringObf) {
            log("  Stage 1 — String Encryption")
            val time = measureTimeMillis {
                val stringObf = DictionaryStringObfuscator(log, actualPackage)
                val dictFile = resolveAssetFile()

                if (dictFile != null) {
                    stringObf.loadDictionary(dictFile)
                } else {
                    writeTempDict(dict, outputDir)?.let { stringObf.loadDictionary(it) }
                }

                source = stringObf.obfuscateSource(source)

                val poolSrc = stringObf.generatePoolClass(actualPackage)
                val decoderSrc = stringObf.generateDecoderClass(actualPackage)

                val poolFile = File(outputDir, "P.java")
                val decFile = File(outputDir, "D.java")

                FileUtils.writeStringToFile(poolFile, poolSrc, StandardCharsets.UTF_8)
                FileUtils.writeStringToFile(decFile, decoderSrc, StandardCharsets.UTF_8)

                outputFiles.add(poolFile)
                outputFiles.add(decFile)

                stringsEncrypted = Regex("""${Regex.escape(actualPackage)}\.D\.r\(""")
                    .findAll(source).count()
            }
            log("  ✓ String Encryption completed in ${time}ms | Encrypted: $stringsEncrypted\n")
        }

        // 2. Control Flow Flattening
        if (config.enableControlFlow) {
            log("  Stage 2 — Control Flow Flattening")
            val time = measureTimeMillis {
                val flattener = ControlFlowFlattener(dict)
                source = flattenAllMethods(source, flattener)
                methodsFlattened = countFlattened(source)
            }
            log("  ✓ Control Flow Flattening completed in ${time}ms | Flattened: $methodsFlattened\n")
        }

        // 3. API Dispatch
        if (config.enableApiDispatch) {
            log("  Stage 3 — API Dispatch Obfuscation")
            val time = measureTimeMillis {
                val dispatcher = ApiDispatchObfuscator(dict)
                source = dispatcher.processSource(source)
                apiCallsWrapped = dispatcher.registrySize

                if (apiCallsWrapped > 0) {
                    val dispSrc = dispatcher.generateDispatcherClass(actualPackage)
                    val dispFile = File(outputDir, "${dispatcher.dispatcherName}.java")
                    FileUtils.writeStringToFile(dispFile, dispSrc, StandardCharsets.UTF_8)
                    outputFiles.add(dispFile)
                }
            }
            log("  ✓ API Dispatch completed in ${time}ms | Wrapped: $apiCallsWrapped\n")
        }

// احذف أو ضع تعليقاً على هذا الجزء بالكامل داخل ObfuscationOrchestrator.kt
/*
if (config.enableFragmentation) {
    log("  Stage 4 — Runnable Fragmentation")
    val time = measureTimeMillis {
        val tempInput = File(outputDir, "_frag_temp.java")
        FileUtils.writeStringToFile(tempInput, source, StandardCharsets.UTF_8)
        source = FragmentationEngine(log).process(tempInput)
        tempInput.delete()
    }
    log("  ✓ Fragmentation completed in ${time}ms\n")
}
*/


        // 5. Source Renamer (آخراً)
        if (config.enableRename) {
            log("  Stage 5 — Source Renaming (Final Stage)")
            val time = measureTimeMillis {
                val renamer = SourceRenamer(dict, log, seed = seed)
                val result = renamer.rename(source)
                source = result.renamedSource
                mappingLines.addAll(result.mappingLog)

                methodsRenamed = result.mappingLog.count { it.startsWith("method") }
                fieldsRenamed = result.mappingLog.count { it.startsWith("field") }
            }
            log("  ✓ Renaming completed in ${time}ms | Renamed: $methodsRenamed methods, $fieldsRenamed fields\n")
        }

        // كتابة الملف النهائي
        val outName = "${inputFile.nameWithoutExtension}_obf.java"
        val mainOut = File(outputDir, outName)
        FileUtils.writeStringToFile(mainOut, source, StandardCharsets.UTF_8)
        outputFiles.add(0, mainOut)

        val mapContent = buildMappingFile(mappingLines, actualPackage)
        val mapFile = File(outputDir, "mapping.txt")
        FileUtils.writeStringToFile(mapFile, mapContent, StandardCharsets.UTF_8)
        outputFiles.add(mapFile)

        val stats = Stats(methodsRenamed, fieldsRenamed, stringsEncrypted, apiCallsWrapped, methodsFlattened)
        logSummary(stats, mainOut)

        return ObfuscationResult(outputFiles, mapContent, stats)
    }

    // ====================== الدوال المساعدة ======================

    private fun repairChainedDispatcherCalls(source: String): String {
        var result = source
        RECEIVER_TYPE_MAP.forEach { (methodName, castType) ->
            val pattern = Regex("""(\w+\.d\(\d+[^)]*\))\.${Regex.escape(methodName)}\s*\(""")
            result = pattern.replace(result) { m ->
                "(($castType) ${m.groupValues[1]}).$methodName("
            }
        }
        return result
    }

    private fun extractPackage(source: String): String =
        source.lineSequence()
            .firstOrNull { it.trimStart().startsWith("package ") }
            ?.trim()?.removePrefix("package ")?.trimEnd(';')?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "com.my.newproject7"

    private fun flattenAllMethods(source: String, flattener: ControlFlowFlattener): String {
        return try {
            val cu = com.github.javaparser.StaticJavaParser.parse(source)
            val cls = cu.findFirst(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration::class.java)
                .orElse(null) ?: return source

            val replacements = mutableListOf<Pair<String, String>>()
            cls.methods.forEach { method ->
                val original = method.toString().trim()
                val flattened = flattener.flatten(method)
                if (flattened != original) replacements.add(original to flattened)
            }

            var result = source
            replacements.forEach { (orig, flat) -> result = result.replace(orig, flat) }
            result
        } catch (e: Exception) {
            log("  Flatten error: ${e.message}")
            source
        }
    }

    private fun countFlattened(source: String): Int =
        Regex("""while\s*\(\w+\)\s*\{[\s\S]*?switch\s*\(""").findAll(source).count()

    private fun resolveAssetFile(): File? = try {
        val tmp = File(context.cacheDir, "0.txt")
        context.assets.open("0.txt").use { it.copyTo(tmp.outputStream()) }
        tmp
    } catch (_: Exception) { null }

    private fun writeTempDict(dict: List<String>, dir: File): File? {
        if (dict.isEmpty()) return null
        return try {
            val f = File(dir, "_dict_temp.txt")
            f.writeText(dict.joinToString("\n"), StandardCharsets.UTF_8)
            f
        } catch (_: Exception) { null }
    }

    private fun buildMappingFile(lines: List<String>, pkg: String): String = buildString {
        appendLine("# BraDex Obfuscation Mapping")
        appendLine("# Package: $pkg")
        appendLine("# Generated on: ${java.time.LocalDateTime.now()}")
        appendLine("#")
        lines.forEach { appendLine(it) }
    }

    private fun logSummary(stats: Stats, outFile: File) {
        log("\n=== Obfuscation Summary ===")
        log("  Methods renamed   : ${stats.methodsRenamed}")
        log("  Fields renamed    : ${stats.fieldsRenamed}")
        log("  Strings encrypted : ${stats.stringsEncrypted}")
        log("  API calls wrapped : ${stats.apiCallsWrapped}")
        log("  Methods flattened : ${stats.methodsFlattened}")
        log("  Final output      : ${outFile.name}")
        log("===========================\n")
    }
}