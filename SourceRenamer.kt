package com.my.newproject7

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.visitor.ModifierVisitor
import com.github.javaparser.ast.visitor.Visitable
import java.util.concurrent.atomic.AtomicInteger

class SourceRenamer(
    private val dict: List<String>, 
    private val log: (String) -> Unit,
    private val seed: Long = 0x9e3779b9L   // للتناسق بين الملفات
) {

    private val counter   = AtomicInteger(0)
    private val methodMap = mutableMapOf<String, String>()
    private val fieldMap  = mutableMapOf<String, String>()

    private val PROTECTED_METHODS = setOf(
        "onCreate","onStart","onResume","onPause","onStop","onDestroy",
        "onCreateView","onViewCreated","onActivityCreated","onAttach","onDetach",
        "onSaveInstanceState","onRestoreInstanceState",
        "onCreateOptionsMenu","onOptionsItemSelected",
        "onRequestPermissionsResult","onActivityResult",
        "onClick","onLongClick","onTouch","onKey",
        "onDraw","onMeasure","onLayout",
        "run","call","execute","doInBackground","onPostExecute","onPreExecute",
        "compare","compareTo","equals","hashCode","toString","clone",
        "main","getView","getItem","getCount","getItemId",
        "show","dismiss","inflate","hide",
        "setText","getText","setEnabled","setVisibility","setChecked",
        "setOnClickListener","setOnCheckedChangeListener","setContentView",
        "setResult","finish","startActivity","startActivityForResult",
        "getSystemService","getPackageManager","getPackageArchiveInfo",
        "loadIcon","resolveActivity","setImageDrawable","setImageResource",
        "makeText","setTitle","setMessage","setPositiveButton","setNegativeButton",
        "addDrawerListener","syncState","setNavigationOnClickListener",
        "setBackgroundDrawable","setDisplayHomeAsUpEnabled","setHomeButtonEnabled",
        "setBackgroundColor","setTextColor","setColorFilter","clearColorFilter",
        "setPadding","setGravity","setTypeface",
        "getRoot","getAbsolutePath","getName","getParent","exists","mkdirs",
        "open","close","read","write","list","length",
        "put","get","remove","clear","size","isEmpty","contains",
        "add","addAll","toList","toSet","toMap",
        "apply","commit","edit",
        "start","stop","release","play",
        "interrupt","join",
        "printStackTrace","getMessage",
        "parse","format","valueOf","parseInt","toString",
        "substring","startsWith","endsWith","contains","replace",
        "toLowerCase","toUpperCase","trim",
        "setState","setSkipCollapsed",
        "from","newInstance",
        "accept", "onPicked", "onComplete", "onSuccess", "onError", 
        "onFailure", "onResponse", "onCancelled", "runOnUiThread",
        "doOnNext", "doOnError", "doOnComplete", "subscribe",
        "apply", "andThen", "compose"
    )

    data class RenameResult(val renamedSource: String, val mappingLog: List<String>)

    fun rename(source: String): RenameResult {
        methodMap.clear()
        fieldMap.clear()
        val mappingLog = mutableListOf<String>()

        val cu: CompilationUnit = try {
            StaticJavaParser.parse(source)
        } catch (e: Exception) {
            log("  parse failed: ${e.message}")
            return RenameResult(source, emptyList())
        }

        cu.findAll(ClassOrInterfaceDeclaration::class.java).forEach { cls ->
            collectMethodRenames(cls, mappingLog)
            collectFieldRenames(cls, mappingLog)
        }

        applyRenames(cu)
        return RenameResult(cu.toString(), mappingLog)
    }

    private fun collectMethodRenames(cls: ClassOrInterfaceDeclaration, log: MutableList<String>) {
        cls.methods.forEach { method ->
            val name = method.nameAsString
            if (name in PROTECTED_METHODS || isOverride(method)) return@forEach
            if (name.startsWith("_")) return@forEach

            val newName = nextName()
            methodMap[name] = newName
            log.add("method  $name -> $newName")
            this.log("    method  $name -> $newName")
        }
    }

    private fun collectFieldRenames(cls: ClassOrInterfaceDeclaration, log: MutableList<String>) {
        cls.fields.forEach { field: FieldDeclaration ->
            field.variables.forEach { v: VariableDeclarator ->
                val name = v.nameAsString
                if (name.startsWith("_")) return@forEach  // حماية متغيرات ControlFlowFlattener

                val newName = nextName()
                fieldMap[name] = newName
                log.add("field   $name -> $newName")
                this.log("    field   $name -> $newName")
            }
        }
    }

    private fun applyRenames(cu: CompilationUnit) {
        cu.accept(object : ModifierVisitor<Void>() {

            override fun visit(n: MethodDeclaration, arg: Void?): Visitable {
                val mapped = methodMap[n.nameAsString]
                if (mapped != null) n.setName(mapped)
                return super.visit(n, arg)
            }

            override fun visit(n: MethodCallExpr, arg: Void?): Visitable {
                val name = n.nameAsString
                if (name.startsWith("_")) return super.visit(n, arg)
                
                val mapped = methodMap[name]
                if (mapped != null && !n.scope.isPresent) n.setName(mapped)
                return super.visit(n, arg)
            }

            override fun visit(n: VariableDeclarator, arg: Void?): Visitable {
                val name = n.nameAsString
                if (name.startsWith("_")) return super.visit(n, arg)
                
                val mapped = fieldMap[name]
                if (mapped != null) n.setName(mapped)
                return super.visit(n, arg)
            }

            override fun visit(n: NameExpr, arg: Void?): Visitable {
                val name = n.nameAsString
                if (name.startsWith("_")) return super.visit(n, arg)
                
                val mapped = fieldMap[name]
                if (mapped != null) n.setName(mapped)
                return super.visit(n, arg)
            }

        }, null)
    }

    private fun isOverride(method: MethodDeclaration): Boolean =
        method.annotations.any { it.nameAsString == "Override" }

    // === nextName محسنة مع بذرة ثابتة للتناسق ===
    private fun nextName(): String {
        val id = counter.incrementAndGet()
        val effectiveSeed = seed xor (id * 0x6b43a9L)   // لتوزيع أفضل وتناسق

        if (dict.isEmpty()) return "_m${id}"

        val idx = ((effectiveSeed and 0x7FFFFFFFL) % dict.size).toInt()
        val raw = dict[idx].filter { c -> c.isLetterOrDigit() || c == '_' }.take(35)

        val finalName = if (raw.isEmpty() || raw[0].isDigit()) {
            "_${raw}_$id"
        } else {
            "${raw}_$id"
        }

        // تجنب التصادم مع الأسماء المحمية
        return if (finalName in PROTECTED_METHODS) "_${finalName}_$id" else finalName
    }
}