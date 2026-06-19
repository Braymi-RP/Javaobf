package com.my.newproject7

import java.util.concurrent.atomic.AtomicInteger

class ApiDispatchObfuscator(private val dict: List<String>) {

    private val counter  = AtomicInteger(0)
    private val registry = mutableMapOf<String, Int>()
    private val entries  = mutableMapOf<Int, ApiEntry>()

    data class ApiEntry(val objectExpr: String, val methodName: String)

    val dispatcherName: String get() = buildClassName(dict.size / 3)
    val registrySize: Int      get() = registry.size

    private val VOID_ONLY_APIS = setOf(
        "startActivity","startService","stopService","sendBroadcast",
        "registerReceiver","unregisterReceiver","requestPermissions",
        "setContentView","startActivityForResult","takePersistableUriPermission",
        "addDrawerListener","setNavigationOnClickListener",
        "setDisplayHomeAsUpEnabled","setHomeButtonEnabled"
    )

    private val FULL_CALL_PATTERN = Regex(
        """(?<!["\w])([a-zA-Z_${'$'}][\w.${'$'}]*(?:\([^()]*\))?)\.([a-zA-Z_${'$'}][\w${'$'}]*)\s*\(([^()]*)\)"""
    )

    fun processSource(source: String): String {
        var result  = source
        val matches = FULL_CALL_PATTERN.findAll(source).toList().reversed()
        matches.forEach { m ->
            val methodName = m.groupValues[2]
            if (methodName !in VOID_ONLY_APIS) return@forEach

            val objectExpr = m.groupValues[1].trim()
            val argsRaw    = m.groupValues[3].trim()

            if (isStaticClassExpr(objectExpr)) return@forEach

            val afterCall = result.getOrElse(m.range.last + 1) { ' ' }
            if (afterCall == '.') return@forEach

            val key = "$objectExpr.$methodName"
            val id  = registry.getOrPut(key) {
                val newId = counter.incrementAndGet()
                entries[newId] = ApiEntry(objectExpr, methodName)
                newId
            }

            val replacement = if (argsRaw.isEmpty())
                "$dispatcherName.d($id, $objectExpr)"
            else
                "$dispatcherName.d($id, $objectExpr, $argsRaw)"

            result = result.replaceRange(m.range.first, m.range.last + 1, replacement)
        }
        return result
    }

    fun generateDispatcherClass(packageName: String): String = buildString {
        val cn = dispatcherName
        appendLine("package $packageName;")
        appendLine()
        appendLine("public final class $cn {")
        appendLine()
        appendLine("    private $cn() {}")
        appendLine()
        appendLine("    private static final java.util.HashMap<Integer, String> _r = new java.util.HashMap<>();")
        appendLine()
        appendLine("    static {")
        entries.forEach { (id, entry) ->
            appendLine("        _r.put($id, \"${entry.methodName}\");")
        }
        appendLine("    }")
        appendLine()
        appendLine("    @SuppressWarnings(\"unchecked\")")
        appendLine("    public static <T> T d(int id, Object target, Object... args) {")
        appendLine("        if (target == null) return null;")
        appendLine("        String mn = _r.get(id);")
        appendLine("        if (mn == null) return null;")
        appendLine("        try {")
        appendLine("            Class<?>[] pt = resolveTypes(args);")
        appendLine("            java.lang.reflect.Method m = findMethod(target.getClass(), mn, pt);")
        appendLine("            if (m == null) return null;")
        appendLine("            m.setAccessible(true);")
        appendLine("            return (T) m.invoke(target, args);")
        appendLine("        } catch (java.lang.reflect.InvocationTargetException e) {")
        appendLine("            Throwable c = e.getCause();")
        appendLine("            if (c instanceof RuntimeException) throw (RuntimeException) c;")
        appendLine("            return null;")
        appendLine("        } catch (Exception ignored) {")
        appendLine("            return null;")
        appendLine("        }")
        appendLine("    }")
        appendLine()
        appendLine("    private static java.lang.reflect.Method findMethod(Class<?> cls, String name, Class<?>[] pt) {")
        appendLine("        Class<?> c = cls;")
        appendLine("        while (c != null) {")
        appendLine("            try { return c.getDeclaredMethod(name, pt); }")
        appendLine("            catch (NoSuchMethodException ignored) {}")
        appendLine("            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {")
        appendLine("                if (m.getName().equals(name) && m.getParameterCount() == pt.length) return m;")
        appendLine("            }")
        appendLine("            c = c.getSuperclass();")
        appendLine("        }")
        appendLine("        return null;")
        appendLine("    }")
        appendLine()
        appendLine("    private static Class<?>[] resolveTypes(Object[] args) {")
        appendLine("        Class<?>[] t = new Class<?>[args.length];")
        appendLine("        for (int i = 0; i < args.length; i++)")
        appendLine("            t[i] = args[i] != null ? args[i].getClass() : Object.class;")
        appendLine("        return t;")
        appendLine("    }")
        appendLine()
        append("}")
    }

    private fun isStaticClassExpr(expr: String): Boolean {
        val clean = expr.trim()
        return clean.isEmpty() ||
               clean[0].isUpperCase() ||
               (clean.contains('.') &&
                clean.split('.').last().let { it.isNotEmpty() && it[0].isUpperCase() })
    }

    private fun buildClassName(seed: Int): String {
        if (dict.isEmpty()) return "Dp"
        val raw = dict[seed.coerceIn(0, dict.size - 1)]
            .filter { it.isLetterOrDigit() }.take(10)
        return if (raw.isEmpty() || raw[0].isDigit()) "D$raw" else raw
    }
}