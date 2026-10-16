package br.com.stilooverboost.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android host for the self-contained V0.4 HTML dashboard.
 * Bluetooth Classic SPP via the STILO_OVERBOOST_GATEWAY 3.0.2 text protocol.
 * There is NO Internet, no CAN bus access from the phone and no automatic CAN TX.
 */
@SuppressLint("MissingPermission", "SetJavaScriptEnabled")
class MainActivity : Activity() {
    private lateinit var browser: WebView
    private val adapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val currentSession = AtomicInteger(0)
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var deviceName = ""
    @Volatile private var active = true
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingJson: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(244, 247, 252)
        window.navigationBarColor = android.graphics.Color.rgb(244, 247, 252)
        browser = WebView(this)
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.allowFileAccess = true
        browser.settings.allowContentAccess = false
        browser.settings.setAllowFileAccessFromFileURLs(false)
        browser.settings.setAllowUniversalAccessFromFileURLs(false)
        browser.settings.javaScriptCanOpenWindowsAutomatically = false
        browser.settings.setSupportMultipleWindows(false)
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
        }
        browser.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    startActivityForResult(intent, 105)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    filePathCallback?.onReceiveValue(null)
                    false
                }
            }
        }
        browser.addJavascriptInterface(Bridge(), "AndroidBridge")
        setContentView(browser)
        browser.loadUrl("file:///android_asset/index.html")
    }

    private fun notifyJs(event: JSONObject) {
        if (!active) return
        runOnUiThread {
            if (active) browser.evaluateJavascript(
                "window.stiloNative && window.stiloNative.onEvent(${event});", null
            )
        }
    }

    private fun event(type: String, detail: String = "") {
        val body = JSONObject().put("type", type)
        if (type == "error") body.put("message", detail)
        else if (type == "connected") body.put("name", detail)
        notifyJs(body)
    }

    private fun permitted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun chooser() {
        if (!permitted()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 104)
            return
        }
        val bt = adapter
        if (bt == null) { event("error", "Bluetooth indisponível"); return }
        if (!bt.isEnabled) {
            AlertDialog.Builder(this).setTitle("Bluetooth desligado")
                .setMessage("Ative o Bluetooth no Android e tente novamente.")
                .setPositiveButton("Configurações") { _, _ ->
                    startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                }.setNegativeButton("Cancelar", null).show()
            return
        }
        val paired = try { bt.bondedDevices.toList().sortedBy { it.name ?: "" } }
            catch (_: SecurityException) { emptyList() }
        if (paired.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Nenhum dispositivo pareado")
                .setMessage("Pareie STILO_OVERBOOST nas configurações Bluetooth do Android.")
                .setPositiveButton("Bluetooth") { _, _ ->
                    startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                }.setNegativeButton("Voltar", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Conectar ao gateway")
            .setItems(paired.map { "${it.name ?: "Sem nome"}  •  ${it.address}" }.toTypedArray()) { _, index ->
                connect(paired[index])
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun disconnect() {
        currentSession.incrementAndGet()
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        deviceName = ""
        event("disconnected")
    }

    private fun connect(device: BluetoothDevice) {
        disconnect()
        val attempt = currentSession.incrementAndGet()
        executor.execute {
            var candidate: BluetoothSocket? = null
            try {
                candidate = device.createRfcommSocketToServiceRecord(sppUuid)
                candidate.connect()
                if (attempt != currentSession.get()) { candidate.close(); return@execute }
                socket = candidate
                deviceName = device.name ?: device.address
                event("connected", deviceName)
                val writer = candidate.outputStream
                // Read loop owns this worker. Separate writer calls use short threads.
                Thread {
                    try {
                        synchronized(candidate) {
                            for (command in arrayOf("HELLO", "STATUS", "CAN LIST", "DEC LIST")) {
                                writer.write((command + "\n").toByteArray(Charsets.UTF_8))
                                writer.flush()
                                notifyJs(JSONObject().put("type", "tx").put("line", command))
                            }
                        }
                    } catch (_: Exception) { }
                }.start()
                val reader = BufferedReader(InputStreamReader(candidate.inputStream, Charsets.UTF_8))
                while (active && attempt == currentSession.get()) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    if (line.length > 16384) continue
                    notifyJs(JSONObject().put("type", "gateway").put("line", line))
                }
            } catch (e: Exception) {
                if (attempt == currentSession.get()) event("error", e.message ?: "Erro de conexão")
            } finally {
                try { candidate?.close() } catch (_: Exception) {}
                if (attempt == currentSession.get()) {
                    socket = null
                    deviceName = ""
                    event("disconnected")
                }
            }
        }
    }

    private fun allowedCommand(command: String): Boolean {
        // Intentionally excluded: AC ENABLE, all TX and TXT arming/sending,
        // REBOOT, WIFI and arbitrary console commands. CAN TX requires a
        // separately instrumented isolated-bench validation and app review.
        val fixed = setOf("HELLO", "STATUS", "CAN STATS", "CAN LIST", "DEC LIST",
            "DISCOVER START SPEED", "DISCOVER START SWITCH", "DISCOVER START UNKNOWN",
            "DISCOVER STOP", "DISCOVER MARK", "DISCOVER STATUS", "AC STATUS",
            "TX DISARM", "CAN LISTEN", "VSS OFF", "VSS ON", "DEC SAVE")
        if (command in fixed) return true
        if (Regex("^ROLE SPEED (-1|[0-9]|1[0-5])$").matches(command)) return true
        if (Regex("^DEC SET (?:[0-9]|1[0-5]) [A-Za-z0-9_]{1,15} 0x[0-9A-Fa-f]{1,3} [0-7] [1-4] (?:BE|LE) 0x[0-9A-Fa-f]{1,8} (?:[0-9]|[12][0-9]|3[01]) -?[0-9]+(?:\\.[0-9]+)? -?[0-9]+(?:\\.[0-9]+)? [A-Za-z0-9_]{1,8} [0-9]{2,5} [01]$").matches(command)) return true
        return false
    }

    private fun send(command: String) {
        val line = command.trim()
        if (!allowedCommand(line)) { event("error", "Comando não autorizado nesta versão"); return }
        val connection = socket
        if (connection == null || !connection.isConnected) {
            event("error", "Gateway desconectado")
            return
        }
        Thread {
            try {
                synchronized(connection) {
                    connection.outputStream.write((line + "\n").toByteArray(Charsets.UTF_8))
                    connection.outputStream.flush()
                    notifyJs(JSONObject().put("type", "tx").put("line", line))
                }
            } catch (e: Exception) { event("error", e.message ?: "Falha no envio") }
        }.start()
    }

    private fun exportText(name: String, content: String) {
        runOnUiThread {
            if (content.length > 3_000_000) { event("error", "Mapa muito grande"); return@runOnUiThread }
            pendingJson = content
            val safeName = name.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(70)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, safeName)
            }
            try { startActivityForResult(intent, 106) }
            catch (e: Exception) { event("error", "Não foi possível abrir seletor de arquivos") }
        }
    }

    @Deprecated("Legacy Activity result for SDK 26 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 105) {
            val uri = if (resultCode == RESULT_OK) data?.data else null
            fileCallback?.onReceiveValue(uri?.let { arrayOf(it) })
            fileCallback = null
        } else if (requestCode == 106) {
            val value = pendingJson
            pendingJson = null
            if (resultCode == RESULT_OK && data?.data != null && value != null) {
                try {
                    contentResolver.openOutputStream(data.data!!)?.use {
                        it.write(value.toByteArray(Charsets.UTF_8))
                    } ?: event("error", "Falha ao salvar arquivo")
                } catch (e: Exception) { event("error", e.message ?: "Falha ao salvar") }
            }
        }
    }

    inner class Bridge {
        @JavascriptInterface fun chooseDevice() { runOnUiThread { chooser() } }
        @JavascriptInterface fun disconnect() { this@MainActivity.disconnect() }
        @JavascriptInterface fun send(command: String) { this@MainActivity.send(command) }
        @JavascriptInterface fun saveText(name: String, content: String) {
            this@MainActivity.exportText(name, content)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 104 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) chooser()
    }

    override fun onDestroy() {
        active = false
        currentSession.incrementAndGet()
        try { socket?.close() } catch (_: Exception) {}
        executor.shutdownNow()
        browser.removeJavascriptInterface("AndroidBridge")
        browser.destroy()
        super.onDestroy()
    }
}
