package com.scvc.app

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private enum class Task { PACK, UNPACK, TRANSCODE }
    private var task = Task.PACK
    private var selectedInput: Uri? = null
    private var activeTransformer: Transformer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var packButton: Button
    private lateinit var unpackButton: Button
    private lateinit var transcodeButton: Button
    private lateinit var cancelButton: Button
    private lateinit var profile: Spinner
    private lateinit var historyText: TextView
    private lateinit var history: HistoryStore

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            setBusy(false); setStatus("ဖိုင်ရွေးချယ်မှု ပယ်ဖျက်လိုက်သည်။")
            return@registerForActivityResult
        }
        selectedInput = uri
        when (task) {
            Task.TRANSCODE -> createVideo.launch(baseName(displayName(uri)) + "_SCVC.mp4")
            Task.PACK -> createScx.launch(displayName(uri) + ".scx")
            Task.UNPACK -> try {
                val name = contentResolver.openInputStream(uri)?.use { ScxCodec.originalName(it) }
                    ?: error("SCX ဖိုင်ဖတ်မရပါ")
                createRestored.launch(name)
            } catch (e: Exception) {
                setBusy(false); setStatus("SCX ဖိုင်မမှန်ပါ: ${e.message}")
            }
        }
    }
    private val createScx = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val input = selectedInput
        if (uri == null || input == null) cancelled() else runScx(input, uri, Task.PACK)
    }
    private val createRestored = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val input = selectedInput
        if (uri == null || input == null) cancelled() else runScx(input, uri, Task.UNPACK)
    }
    private val createVideo = registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val input = selectedInput
        if (uri == null || input == null) cancelled() else runTranscode(input, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history = HistoryStore(this)
        buildUi()
        refreshHistory()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 24)
            setBackgroundColor(0xFFF3F6FB.toInt())
        }
        val title = label("SCVC", 32f).apply { gravity = Gravity.CENTER; setTextColor(0xFF13213B.toInt()) }
        val subtitle = label("SMARTCLOUD VIDEO COMPRESSOR · SCX HYBRID", 12f).apply { gravity = Gravity.CENTER }
        val intro = label("မူရင်းဖိုင်ကို မဖျက်ဘဲ အလုပ်လုပ်သည်။ Exact Mode က bytes အတိအကျ ပြန်ရစေပြီး Data-saving Mode က MP4 အသစ်အဖြစ် ပြန် encode လုပ်သည်။", 14f)
        profile = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("720p · မျှတ", "480p · ပိုသေးနိုင်", "1080p · Resolution မြင့်"))
        }
        packButton = Button(this).apply { text = "① Exact Mode · SCX သိမ်းရန်"; setOnClickListener { choose(Task.PACK) } }
        unpackButton = Button(this).apply { text = "② SCX မှ မူရင်းဖိုင် ပြန်ထုတ်ရန်"; setOnClickListener { choose(Task.UNPACK) } }
        transcodeButton = Button(this).apply { text = "③ Data-saving · MP4 ပြန်ချုံ့ရန်"; setOnClickListener { choose(Task.TRANSCODE) } }
        cancelButton = Button(this).apply {
            text = "လုပ်ဆောင်မှု ရပ်ရန်"; isEnabled = false
            setOnClickListener {
                activeTransformer?.cancel(); activeTransformer = null
                setBusy(false); setStatus("ရပ်တန့်လိုက်သည်။ မပြည့်စုံသော output ကို ဖျက်ပါ။")
            }
        }
        val note = label("သတိပြုရန် — SCX သည် မူရင်း bytes ကို ထိန်းသိမ်းသည်။ MP4/H.264/H.265 ကို SCX ထဲထည့်ရုံဖြင့် အရွယ်အစား များစွာလျော့မည်မဟုတ်ပါ။ Data-saving သည် 480p/720p/1080p သို့ H.264/AAC ဖြင့် ပြန် encode လုပ်သည်။ အရွယ်အစားလျော့မည်ဟု အာမမခံပါ။", 13f)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = ProgressBar.GONE }
        status = label("အဆင်သင့်။ လုပ်ဆောင်ချက်တစ်ခုကို ရွေးပါ။", 14f)
        val historyTitle = label("လုပ်ဆောင်မှုမှတ်တမ်း", 18f)
        val clear = Button(this).apply { text = "မှတ်တမ်းရှင်းရန်"; setOnClickListener { history.clear(); refreshHistory() } }
        historyText = label("မှတ်တမ်း မရှိသေးပါ။", 12f)
        listOf(title, subtitle, intro, profile, packButton, unpackButton, transcodeButton,
            cancelButton, note, progress, status, historyTitle, clear, historyText)
            .forEach { root.addView(it, matchWrap()) }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size; setTextColor(0xFF25324A.toInt()); setPadding(0, 10, 0, 10)
    }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun choose(action: Task) {
        task = action
        setBusy(true)
        openFile.launch(if (action == Task.TRANSCODE) arrayOf("video/*") else arrayOf("*/*"))
    }

    private fun runScx(input: Uri, output: Uri, action: Task) {
        setBusy(true)
        setStatus(if (action == Task.PACK) "SCX အဖြစ် သိမ်းနေသည်…" else "မူရင်းဖိုင် ပြန်ထုတ်နေသည်…")
        executor.execute {
            val name = displayName(input)
            val before = querySize(input)
            try {
                val source = contentResolver.openInputStream(input) ?: error("Input ဖိုင် မဖွင့်နိုင်ပါ")
                val target = contentResolver.openOutputStream(output, "wt") ?: error("Output ဖိုင် မဖွင့်နိုင်ပါ")
                val result = source.use { src -> target.use { dst ->
                    if (action == Task.PACK) ScxCodec.compress(src, dst, name) { n ->
                        main.post { status.text = "ဖတ်ပြီး ${formatBytes(n)}" }
                    } else ScxCodec.extract(src, dst) { n ->
                        main.post { status.text = "ပြန်ထုတ်ပြီး ${formatBytes(n)}" }
                    }
                } }
                history.add(HistoryEntry(0, System.currentTimeMillis(), action.name, name,
                    if (action == Task.PACK) "$name.scx" else "restored", result.inputBytes,
                    result.outputBytes, result.sha256, true))
                main.post {
                    setBusy(false)
                    setStatus("ပြီးပါပြီ။ Input ${formatBytes(result.inputBytes)} · Output ${formatBytes(result.outputBytes)} · SHA-256 ${result.sha256}")
                    refreshHistory()
                }
            } catch (e: Exception) {
                history.add(HistoryEntry(0, System.currentTimeMillis(), action.name, name, "output", before, 0, "", false))
                main.post {
                    setBusy(false); setStatus("မအောင်မြင်ပါ: ${e.message}။ မပြည့်စုံသော output ကို ဖျက်ပြီး ပြန်စမ်းပါ။")
                    refreshHistory()
                }
            }
        }
    }

    private fun runTranscode(input: Uri, output: Uri) {
        setBusy(true)
        val height = when (profile.selectedItemPosition) { 1 -> 480; 2 -> 1080; else -> 720 }
        setStatus("ဗီဒီယိုကို ယာယီဖိုင်သို့ ကူးနေသည်…")
        executor.execute {
            val sourceFile = File(cacheDir, "scvc_input_${System.currentTimeMillis()}.media")
            val outputFile = File(cacheDir, "scvc_output_${System.currentTimeMillis()}.mp4")
            try {
                contentResolver.openInputStream(input)?.use { src ->
                    FileOutputStream(sourceFile).use { dst -> src.copyTo(dst, 128 * 1024) }
                } ?: error("Input ဖိုင် မဖွင့်နိုင်ပါ")
                main.post {
                    setStatus("Media3 Encoder ဖြင့် ${height}p ပြန် encode လုပ်နေသည်…")
                    try {
                        activeTransformer = VideoTranscoder.start(
                            this, sourceFile, outputFile, height,
                            onProgress = { p -> progress.progress = p; status.text = "ပြန် encode လုပ်နေသည်… $p%" },
                            onCompleted = {
                                activeTransformer = null
                                setStatus("Encoding ပြီးပါပြီ။ Output သိမ်းနေသည်…")
                                executor.execute {
                                    try {
                                        contentResolver.openOutputStream(output, "wt")?.use { dst ->
                                            FileInputStream(outputFile).use { src -> src.copyTo(dst, 128 * 1024) }
                                        } ?: error("Output ဖိုင် မဖွင့်နိုင်ပါ")
                                        val inBytes = sourceFile.length()
                                        val outBytes = outputFile.length()
                                        val hash = sha256(outputFile)
                                        history.add(HistoryEntry(0, System.currentTimeMillis(), "TRANSCODE_${height}P",
                                            displayName(input), "SCVC_${height}p.mp4", inBytes, outBytes, hash, true))
                                        sourceFile.delete(); outputFile.delete()
                                        main.post {
                                            setBusy(false)
                                            val pct = if (inBytes > 0) 100.0 * (inBytes - outBytes) / inBytes else 0.0
                                            val verb = if (pct >= 0) "လျော့" else "ပိုကြီး"
                                            setStatus("ပြီးပါပြီ။ ${formatBytes(inBytes)} → ${formatBytes(outBytes)} · ${"%.1f".format(pct)}% $verb · SHA-256 $hash")
                                            refreshHistory()
                                        }
                                    } catch (e: Exception) {
                                        sourceFile.delete(); outputFile.delete()
                                        main.post { setBusy(false); setStatus("Output သိမ်းမရပါ: ${e.message}") }
                                    }
                                }
                            },
                            onError = { error ->
                                activeTransformer = null; sourceFile.delete(); outputFile.delete()
                                setBusy(false); setStatus("Video encode မအောင်မြင်ပါ: ${error.message}")
                            }
                        )
                    } catch (e: Exception) {
                        sourceFile.delete(); outputFile.delete()
                        setBusy(false); setStatus("ဗီဒီယိုကို ဖွင့်မရပါ: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                sourceFile.delete(); outputFile.delete()
                main.post { setBusy(false); setStatus("Input ဖတ်မရပါ: ${e.message}") }
            }
        }
    }

    private fun refreshHistory() {
        val rows = history.latest(12)
        historyText.text = if (rows.isEmpty()) "မှတ်တမ်း မရှိသေးပါ။" else rows.joinToString("\n\n") {
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.time))
            "$date · ${it.mode} · ${if (it.success) "အောင်မြင်" else "မအောင်မြင်"}\n${it.inputName}\n${formatBytes(it.inputBytes)} → ${formatBytes(it.outputBytes)}"
        }
    }

    private fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "video"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) name = c.getString(i) ?: name
            }
        }
        return name.substringAfterLast('/').substringAfterLast('\\').take(180).ifBlank { "video" }
    }
    private fun baseName(name: String) = name.substringBeforeLast('.', name).take(120)
    private fun querySize(uri: Uri): Long {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.SIZE)
                if (i >= 0 && !c.isNull(i)) return c.getLong(i)
            }
        }
        return 0
    }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n > 0) digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun formatBytes(v: Long): String = when {
        v >= 1024L * 1024L -> "%.2f MB".format(v / (1024.0 * 1024.0))
        v >= 1024L -> "%.1f KB".format(v / 1024.0)
        else -> "$v B"
    }
    private fun setBusy(busy: Boolean) {
        main.post {
            packButton.isEnabled = !busy; unpackButton.isEnabled = !busy
            transcodeButton.isEnabled = !busy; profile.isEnabled = !busy
            cancelButton.isEnabled = busy
            progress.visibility = if (busy) ProgressBar.VISIBLE else ProgressBar.GONE
            if (!busy) progress.progress = 0
        }
    }
    private fun setStatus(value: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) status.text = value else main.post { status.text = value }
    }
    private fun cancelled() { setBusy(false); setStatus("Output သိမ်းဆည်းမှု ပယ်ဖျက်လိုက်သည်။") }
    override fun onDestroy() {
        activeTransformer?.cancel()
        executor.shutdown()
        if (::history.isInitialized) history.close()
        super.onDestroy()
    }
}
