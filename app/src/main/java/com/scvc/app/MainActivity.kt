package com.scvc.app

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private enum class Task { COMPRESS, EXTRACT }
    private var task: Task = Task.COMPRESS
    private var selectedInput: Uri? = null
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var compressButton: Button
    private lateinit var extractButton: Button

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedInput = uri
            prepareOutput(uri)
        } else setStatus("ဖိုင်ရွေးချယ်မှု ပယ်ဖျက်လိုက်သည်။")
    }

    private val createOutput = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val input = selectedInput
        if (uri == null || input == null) {
            setBusy(false)
            setStatus("သိမ်းဆည်းမှု ပယ်ဖျက်လိုက်သည်။")
            return@registerForActivityResult
        }
        runTask(input, uri, task)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFFF4F6FA.toInt())
        }
        val title = TextView(this).apply {
            text = "SCVC"
            textSize = 30f
            setTextColor(0xFF14213D.toInt())
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "SmartCloud Video Compressor · SCX Hybrid"
            textSize = 14f
            setTextColor(0xFF526078.toInt())
            gravity = Gravity.CENTER
        }
        val info = TextView(this).apply {
            text = "ဗီဒီယို/ဖိုင်ကို SCX ထဲသို့ ထည့်ပြီး မူရင်း bytes အတိုင်း ပြန်ထုတ်နိုင်သည်။ RAM သက်သာစေရန် 128 KB အပိုင်းလိုက် လုပ်ဆောင်သည်။"
            textSize = 15f
            setTextColor(0xFF25324A.toInt())
            setPadding(0, 24, 0, 16)
        }
        compressButton = Button(this).apply {
            text = "SCX သို့ ချုံ့သိမ်းရန်"
            setOnClickListener { start(Task.COMPRESS) }
        }
        extractButton = Button(this).apply {
            text = "SCX မှ မူရင်းဖိုင် ပြန်ထုတ်ရန်"
            setOnClickListener { start(Task.EXTRACT) }
        }
        val warning = TextView(this).apply {
            text = "သတိပြုရန် — MP4/H.264/H.265 ကဲ့သို့ ဖိုင်များသည် မူလကတည်းက ချုံ့ပြီးသားဖြစ်နိုင်သဖြင့် အရွယ်အစား လျော့မည်ဟု အာမမခံပါ။ SCX သည် အပိုင်းတစ်ခုချင်းစီအတွက် RAW fallback သုံးပြီး အပိုချုံ့မရသည့်အခါ မူရင်းအတိုင်း သိမ်းသည်။"
            textSize = 13f
            setTextColor(0xFF8A4B08.toInt())
            setPadding(0, 18, 0, 18)
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { visibility = ProgressBar.GONE }
        status = TextView(this).apply {
            text = "အဆင်သင့်"
            textSize = 14f
            setTextColor(0xFF14213D.toInt())
            setPadding(0, 12, 0, 12)
        }
        root.addView(title, matchWrap())
        root.addView(subtitle, matchWrap())
        root.addView(info, matchWrap())
        root.addView(compressButton, matchWrap())
        root.addView(extractButton, matchWrap())
        root.addView(warning, matchWrap())
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 12))
        root.addView(status, matchWrap())
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun start(action: Task) {
        task = action
        openFile.launch(arrayOf("*/*"))
    }

    private fun prepareOutput(uri: Uri) {
        try {
            val name = if (task == Task.COMPRESS) displayName(uri).let { "$it.scx" } else {
                contentResolver.openInputStream(uri)?.use { ScxCodec.originalName(it) }
                    ?: throw IllegalStateException("ဖိုင်ဖတ်မရပါ")
            }
            createOutput.launch(name)
        } catch (e: Exception) {
            setBusy(false)
            setStatus("ဖိုင်ခေါင်းစီး ဖတ်မရပါ: ${e.message}")
        }
    }

    private fun displayName(uri: Uri): String {
        var name = "video"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        }
        return name.substringAfterLast('/').take(180)
    }

    private fun runTask(input: Uri, output: Uri, action: Task) {
        setBusy(true)
        setStatus("လုပ်ဆောင်နေသည်…")
        executor.execute {
            try {
                val inputStream = contentResolver.openInputStream(input) ?: error("Input stream မဖွင့်နိုင်ပါ")
                val outputStream = contentResolver.openOutputStream(output, "wt") ?: error("Output stream မဖွင့်နိုင်ပါ")
                val result = inputStream.use { source ->
                    outputStream.use { target ->
                        if (action == Task.COMPRESS) ScxCodec.compress(source, target, displayName(input)) { bytes ->
                            runOnUiThread { status.text = "ဖတ်ပြီး: ${formatBytes(bytes)}" }
                        } else ScxCodec.extract(source, target) { bytes ->
                            runOnUiThread { status.text = "ပြန်ထုတ်ပြီး: ${formatBytes(bytes)}" }
                        }
                    }
                }
                runOnUiThread {
                    setBusy(false)
                    setStatus("ပြီးပါပြီ။ SHA-256: ${result.sha256}")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false)
                    setStatus("မအောင်မြင်ပါ: ${e.message ?: e.javaClass.simpleName}။ မပြီးဆုံးသေးသော output ဖိုင်ကို ဖျက်ပါ။")
                }
            }
        }
    }

    private fun formatBytes(value: Long): String = when {
        value >= 1024L * 1024L -> "%.2f MB".format(value / (1024.0 * 1024.0))
        value >= 1024L -> "%.1f KB".format(value / 1024.0)
        else -> "$value B"
    }

    private fun setBusy(busy: Boolean) {
        runOnUiThread {
            compressButton.isEnabled = !busy
            extractButton.isEnabled = !busy
            progress.visibility = if (busy) ProgressBar.VISIBLE else ProgressBar.GONE
        }
    }

    private fun setStatus(message: String) {
        status.text = message
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
