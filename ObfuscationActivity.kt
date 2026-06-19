package com.my.newproject7

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*
import java.io.File

class ObfuscationActivity : AppCompatActivity() {

    private lateinit var btnPickInput  : Button
    private lateinit var btnPickOutput : Button
    private lateinit var btnRun        : Button
    private lateinit var tvInputHint   : TextView
    private lateinit var tvOutputHint  : TextView
    private lateinit var tvLog         : TextView
    private lateinit var scrollLog     : ScrollView
    private lateinit var progressBar   : ProgressBar
    private lateinit var cbRename      : CheckBox
    private lateinit var cbControlFlow : CheckBox
    private lateinit var cbApiDispatch : CheckBox
    private lateinit var cbStringObf   : CheckBox
    private lateinit var cbFragmentation : CheckBox

    private var inputFile    : File? = null
    private var outputFolder : File? = null

    companion object {
        private const val RC_PICK_JAVA   = 201
        private const val RC_PICK_FOLDER = 202
        private const val RC_PERMS       = 203
        private const val RC_MANAGE_EXT  = 204
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUI()
        ensureStoragePermissions()
    }

    override fun onActivityResult(reqCode: Int, resCode: Int, data: Intent?) {
        super.onActivityResult(reqCode, resCode, data)
        if (resCode != Activity.RESULT_OK || data == null) return
        val uri = data.data ?: return
        when (reqCode) {
            RC_PICK_JAVA   -> resolveInputFile(uri)
            RC_PICK_FOLDER -> resolveOutputFolder(uri)
        }
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(req, perms, grants)
        if (req == RC_PERMS && grants.any { it != PackageManager.PERMISSION_GRANTED })
            log("  WARN  storage permission denied")
    }

    private fun buildUI() {
        val colorPrimary              = themeColor(com.google.android.material.R.attr.colorPrimary)
        val colorOnPrimary            = themeColor(com.google.android.material.R.attr.colorOnPrimary)
        val colorSecondary            = themeColor(com.google.android.material.R.attr.colorSecondary)
        val colorSecondaryContainer   = themeColor(com.google.android.material.R.attr.colorSecondaryContainer)
        val colorOnSecondaryContainer = themeColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
        val colorSurface              = themeColor(com.google.android.material.R.attr.colorSurface)
        val colorOnSurface            = themeColor(com.google.android.material.R.attr.colorOnSurface)
        val colorSurfaceVariant       = themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
        val colorOnSurfaceVariant     = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val colorOutlineVariant       = themeColor(com.google.android.material.R.attr.colorOutlineVariant)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(colorSurface)
        }

        root.addView(makeText("BraDex :: Obfuscation Engine") {
            setTextColor(colorPrimary)
            textSize = 17f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        })
        root.addView(makeText("v4.0 — Source-Level Protection Pipeline") {
            setTextColor(colorOnSurfaceVariant)
            textSize = 10f
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, 0, 0, dp(16))
        })

        root.addView(divider(colorOutlineVariant))

        // ---------- INPUT ----------
        root.addView(sectionHeader("[ INPUT ]", colorSecondary))

        btnPickInput = makeButton("  > pick .java file", colorSecondaryContainer, colorOnSecondaryContainer)
        root.addView(btnPickInput, fullWidth(dp(50), dp(8)))

        val (inputLayout, inputEdit) = outlinedField("Selected file", colorOnSurface)
        inputEdit.setText("  none selected")
        tvInputHint = inputEdit
        root.addView(inputLayout, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT, dp(8)))

        // ---------- OUTPUT ----------
        root.addView(sectionHeader("[ OUTPUT ]", colorSecondary))

        btnPickOutput = makeButton("  > pick output folder", colorSecondaryContainer, colorOnSecondaryContainer)
        root.addView(btnPickOutput, fullWidth(dp(50), dp(8)))

        val (outputLayout, outputEdit) = outlinedField("Output folder", colorOnSurface)
        outputEdit.setText("  none selected")
        tvOutputHint = outputEdit
        root.addView(outputLayout, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT, dp(8)))

        root.addView(divider(colorOutlineVariant))

        // ---------- STAGES (inside MaterialCardView) ----------
        root.addView(sectionHeader("[ STAGES ]", colorSecondary))

        val stagesCard = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(colorSurfaceVariant)
        }
        val stagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        cbRename        = makeCheckbox("  [1] AST Rename (methods / fields)",     colorOnSurfaceVariant, colorPrimary, true)
        cbControlFlow   = makeCheckbox("  [2] Control Flow Flatten (switch+xor)", colorOnSurfaceVariant, colorPrimary, true)
        cbApiDispatch   = makeCheckbox("  [3] API Dispatch Obfuscation (reflect)",colorOnSurfaceVariant, colorPrimary, true)
        cbStringObf     = makeCheckbox("  [4] String Encryption (dict-xor pool)", colorOnSurfaceVariant, colorPrimary, true)
        cbFragmentation = makeCheckbox("  [5] Runnable Fragmentation (heavy)",    colorOnSurfaceVariant, colorPrimary, false)

        listOf(cbRename, cbControlFlow, cbApiDispatch, cbStringObf, cbFragmentation)
            .forEach { stagesContainer.addView(it) }

        stagesCard.addView(stagesContainer)
        root.addView(stagesCard, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT, dp(8)))

        root.addView(divider(colorOutlineVariant))

        btnRun = makeButton("  > RUN PIPELINE", colorPrimary, colorOnPrimary, isPrimary = true)
        root.addView(btnRun, fullWidth(dp(50), dp(12)))

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressTintList = ColorStateList.valueOf(colorPrimary)
            }
        }
        root.addView(progressBar, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT, dp(2)))

        root.addView(sectionHeader("[ LOG ]", colorSecondary))

        scrollLog = ScrollView(this)
        tvLog = TextView(this).apply {
            text = "  BraDex ready.\n"
            setTextColor(colorOnSurfaceVariant)
            setBackgroundColor(colorSurfaceVariant)
            setTypeface(Typeface.MONOSPACE)
            textSize = 9f
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scrollLog.addView(tvLog)
        root.addView(scrollLog,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        btnPickInput.setOnClickListener {
            startActivityForResult(
                Intent.createChooser(
                    Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }, "Select .java source"
                ), RC_PICK_JAVA
            )
        }

        btnPickOutput.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), RC_PICK_FOLDER)
            } else {
                outputFolder = Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    .also { it.mkdirs() }
                tvOutputHint.text = "  ${outputFolder!!.absolutePath}"
            }
        }

        btnRun.setOnClickListener { runPipeline() }
    }

    private fun ensureStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle("All Files Access Required")
                    .setMessage("BraDex needs full storage access for APK manipulation.")
                    .setPositiveButton("Settings") { _, _ ->
                        startActivityForResult(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:$packageName")
                            ), RC_MANAGE_EXT
                        )
                    }
                    .setNegativeButton("Skip", null).show()
            }
        } else {
            val needed = listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty())
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), RC_PERMS)
        }
    }

    private fun resolveInputFile(uri: Uri) {
        val realPath = queryDataColumn(uri)
        val file: File = if (realPath != null && File(realPath).exists()) {
            File(realPath)
        } else {
            File(cacheDir, "obf_input.java").also { cache ->
                try {
                    contentResolver.openInputStream(uri)?.use { it.copyTo(cache.outputStream()) }
                } catch (e: Exception) { log("  ERROR  ${e.message}"); return }
            }
        }
        if (!file.exists()) { log("  ERROR  file not found: ${file.absolutePath}"); return }
        inputFile = file
        tvInputHint.text = "  ${uri.lastPathSegment ?: file.name}  (${file.length()} B)"
        log("  INPUT  ${file.absolutePath}")
    }

    private fun resolveOutputFolder(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {}

        val path = uri.path ?: ""
        val segs = path.split(":")
        val folder: File = if (segs.size >= 2) {
            val volId    = segs[0].substringAfterLast("/")
            val relative = segs[1]
            val base     = if (volId.equals("primary", true))
                Environment.getExternalStorageDirectory().absolutePath
            else "/storage/$volId"
            File("$base/$relative")
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        }
        folder.mkdirs()
        outputFolder = folder
        tvOutputHint.text = "  ${folder.absolutePath}"
        log("  OUTPUT  ${folder.absolutePath}")
    }

    private fun queryDataColumn(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        return try {
            contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndex("_data")) else null
            }
        } catch (_: Exception) { null }
    }

    private fun runPipeline() {
        val src = inputFile?.takeIf { it.exists() } ?: run {
            Toast.makeText(this, "Select a valid .java input file", Toast.LENGTH_SHORT).show()
            return
        }
        val dst = outputFolder ?: run {
            Toast.makeText(this, "Select output folder", Toast.LENGTH_SHORT).show()
            return
        }

        val config = ObfuscationOrchestrator.ObfuscationConfig(
            enableRename        = cbRename.isChecked,
            enableControlFlow   = cbControlFlow.isChecked,
            enableApiDispatch   = cbApiDispatch.isChecked,
            enableStringObf     = cbStringObf.isChecked,
            enableFragmentation = cbFragmentation.isChecked,
            packageName         = src.readLines()
                .firstOrNull { it.startsWith("package ") }
                ?.removePrefix("package ")?.removeSuffix(";")?.trim()
                ?: "com.my.newproject7"
        )

        btnRun.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvLog.text = ""
        log("  src    : ${src.absolutePath}")
        log("  dst    : ${dst.absolutePath}\n")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val orchestrator = ObfuscationOrchestrator(this@ObfuscationActivity, ::logIO)
                val result       = orchestrator.process(src, dst, config)

                withContext(Dispatchers.Main) {
                    log("\n  SUCCESS")
                    result.outputFiles.forEach { f -> log("    => ${f.name}") }
                    progressBar.visibility = View.GONE
                    btnRun.isEnabled = true
                    Toast.makeText(
                        this@ObfuscationActivity,
                        "Done — ${result.outputFiles.size} file(s) written",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    log("\n  FAILED  ${e.javaClass.simpleName}: ${e.message}")
                    e.cause?.let { log("  caused  ${it.message}") }
                    progressBar.visibility = View.GONE
                    btnRun.isEnabled = true
                }
            }
        }
    }

    private fun logIO(msg: String) = runOnUiThread { log(msg) }

    private fun log(msg: String) {
        tvLog.append("$msg\n")
        scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    private fun dp(v: Int)       = (v * resources.displayMetrics.density).toInt()
    private fun color(hex: String) = android.graphics.Color.parseColor(hex)

    private fun themeColor(attrId: Int, fallback: Int = android.graphics.Color.GRAY): Int =
        MaterialColors.getColor(this, attrId, fallback)

    private fun makeText(t: String, init: TextView.() -> Unit = {}) =
        TextView(this).apply { text = t; init() }

    private fun sectionHeader(label: String, headerColor: Int) = makeText(label) {
        setTextColor(headerColor)
        textSize = 10f
        setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        setPadding(0, dp(12), 0, dp(6))
    }

    private fun outlinedField(label: String, textColor: Int): Pair<TextInputLayout, TextInputEditText> {
        val layout = TextInputLayout(this).apply {
            hint = label
            setBoxCornerRadii(
                dp(12).toFloat(), dp(12).toFloat(),
                dp(12).toFloat(), dp(12).toFloat()
            )
        }
        val editText = TextInputEditText(layout.context).apply {
            isFocusable = false
            isClickable = false
            isLongClickable = false
            inputType = InputType.TYPE_NULL
            setTextColor(textColor)
            setTypeface(Typeface.MONOSPACE)
            textSize = 11f
        }
        layout.addView(editText)
        return layout to editText
    }

    private fun makeButton(
        label: String,
        backgroundColor: Int,
        textColor: Int,
        isPrimary: Boolean = false
    ): MaterialButton = MaterialButton(this).apply {
        text = label
        isAllCaps = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        cornerRadius = dp(16)
        backgroundTintList = ColorStateList.valueOf(backgroundColor)
        setTextColor(textColor)
        setTypeface(Typeface.MONOSPACE, if (isPrimary) Typeface.BOLD else Typeface.NORMAL)
        textSize = if (isPrimary) 14f else 12f
        setPadding(dp(16), 0, dp(16), 0)
    }

    private fun makeCheckbox(label: String, textColor: Int, tintColor: Int, checked: Boolean) =
        CheckBox(this).apply {
            text = label
            isChecked = checked
            setTextColor(textColor)
            setTypeface(Typeface.MONOSPACE)
            textSize = 10.5f
            gravity = Gravity.CENTER_VERTICAL
            buttonTintList = ColorStateList.valueOf(tintColor)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

    private fun divider(lineColor: Int) = View(this).apply {
        setBackgroundColor(lineColor)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        ).also { it.setMargins(0, dp(8), 0, dp(8)) }
    }

    private fun fullWidth(
        heightPx: Int = LinearLayout.LayoutParams.WRAP_CONTENT,
        marginV: Int = 0
    ) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPx)
        .also { it.setMargins(0, marginV, 0, marginV) }
}