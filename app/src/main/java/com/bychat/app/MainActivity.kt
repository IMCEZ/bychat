package com.bychat.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var db: LocalDb
    private lateinit var audio: AudioController
    private var username = ""
    private var credential = ""
    private var client: ChatClient? = null
    private var currentRoom = ""
    private var owner = ""
    private var recording = false
    private var pendingRecord: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val messages = mutableListOf<Message>()
    private var adapter: MessageAdapter? = null

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingRecord?.invoke() else toast("需要麦克风权限才能录音")
        pendingRecord = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = (application as BychatApp).db
        audio = AudioController(this)
        showLogin()
    }

    private fun column(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        views.forEach { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }) }
    }

    private fun field(hint: String, password: Boolean = false, number: Boolean = false) = EditText(this).apply {
        this.hint = hint
        setSingleLine(true)
        if (password) inputType = 0x81
        if (number) inputType = 2
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { action() } }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 28f
        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primary))
        gravity = Gravity.CENTER
        setPadding(0, dp(24), 0, dp(24))
    }

    private fun showLogin() {
        val user = field("用户名（2—24个字符）")
        val pass = field("密码（至少6位）", password = true)
        val status = TextView(this).apply { gravity = Gravity.CENTER; setTextColor(Color.RED) }
        val login = button("登录") {
            val name = user.text.toString().trim()
            val key = db.login(name, pass.text.toString().toCharArray())
            if (key == null) status.text = "用户名或密码错误" else {
                username = name; credential = key; showHome()
            }
        }
        val register = button("创建本地账号") {
            val name = user.text.toString().trim()
            val raw = pass.text.toString()
            if (db.createAccount(name, raw.toCharArray())) {
                status.setTextColor(ContextCompat.getColor(this, R.color.primary))
                status.text = "账号创建成功，请登录"
                pass.text.clear()
            } else {
                status.setTextColor(Color.RED)
                status.text = "用户名格式错误、密码不足6位，或用户名已存在"
            }
        }
        setContentView(ScrollView(this).apply { addView(column(title("Bychat · 边聊"), user, pass, login, register, status)) })
    }

    private fun showHome() {
        client?.close(); client = null
        val welcome = TextView(this).apply { text = "你好，$username"; textSize = 18f; gravity = Gravity.CENTER }
        setContentView(ScrollView(this).apply {
            addView(column(
                title("边聊"), welcome,
                button("创建手机服务器") { showHostForm() },
                button("连接聊天服务器") { showConnectForm() },
                button(if (HostService.running) "停止当前手机服务器" else "手机服务器未运行") {
                    if (HostService.running) {
                        startService(Intent(this@MainActivity, HostService::class.java).setAction("stop"))
                        toast("服务器已停止")
                        showHome()
                    }
                },
                button("退出账号") { username = ""; credential = ""; showLogin() }
            ))
        })
    }

    private fun showHostForm() {
        val room = field("房间名称").apply { setText("边聊房间") }
        val password = field("房间密码（可留空）", password = true)
        val port = field("监听端口").apply { setText("18888") }
        val status = TextView(this).apply { setTextColor(Color.RED) }
        setContentView(ScrollView(this).apply { addView(column(
            title("创建服务器"), room, password, port,
            button("启动并进入") {
                val p = port.text.toString().toIntOrNull()
                val r = room.text.toString().trim()
                if (p == null || p !in 1024..65535 || r.length !in 1..40) {
                    status.text = "请输入有效房间名和1024—65535端口"
                } else {
                    val intent = Intent(this@MainActivity, HostService::class.java).apply {
                        putExtra("port", p); putExtra("owner", username); putExtra("credential", credential)
                        putExtra("room", r); putExtra("password", password.text.toString())
                    }
                    ContextCompat.startForegroundService(this@MainActivity, intent)
                    handler.postDelayed({ connect("127.0.0.1", p, r, password.text.toString()) }, 350)
                }
            },
            button("返回") { showHome() }, status
        )) })
    }

    private fun showConnectForm() {
        val host = field("服务器公网 IP 或域名")
        val port = field("端口").apply { setText("18888") }
        val room = field("房间名称").apply { setText("边聊房间") }
        val password = field("房间密码（可留空）", password = true)
        val status = TextView(this).apply { setTextColor(Color.RED) }
        setContentView(ScrollView(this).apply { addView(column(
            title("连接服务器"), host, port, room, password,
            button("连接") {
                val h = host.text.toString().trim()
                val p = port.text.toString().toIntOrNull()
                val r = room.text.toString().trim()
                if (h.isBlank() || p == null || p !in 1..65535 || r.isBlank()) status.text = "服务器地址、端口或房间名无效"
                else connect(h, p, r, password.text.toString())
            },
            button("返回") { showHome() }, status
        )) })
    }

    private fun connect(host: String, port: Int, room: String, password: String) {
        currentRoom = room
        messages.clear()
        showConnecting()
        client?.close()
        client = ChatClient(::receive) { reason ->
            if (!isFinishing) AlertDialog.Builder(this).setTitle("连接断开").setMessage(reason).setCancelable(false).setPositiveButton("返回") { _, _ -> showHome() }.show()
        }.also { it.connect(host, port, username, credential, room, password) }
    }

    private fun showConnecting() {
        setContentView(column(title("正在连接……"), button("取消") { client?.close(); showHome() }))
    }

    private fun receive(packet: Packet) {
        when (packet.action) {
            "ready" -> { owner = packet.data.orEmpty(); showChat() }
            "message" -> packet.message?.let {
                db.save(it)
                messages += it
                adapter?.notifyDataSetChanged()
                findViewById<ListView?>(1001)?.setSelection(messages.lastIndex)
            }
            "error" -> toast(packet.error ?: "服务器错误")
            "notice" -> toast(packet.data ?: "操作完成")
        }
    }

    private fun showChat() {
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(TextView(this@MainActivity).apply { text = currentRoom; textSize = 21f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, dp(48), 1f))
            if (username == owner) addView(button("管理") { showAdmin() }, LinearLayout.LayoutParams(dp(84), dp(48)))
            addView(button("退出") { client?.close(); client = null; showHome() }, LinearLayout.LayoutParams(dp(72), dp(48)))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.primary))
        }
        val list = ListView(this).apply {
            id = 1001
            divider = null
            adapter = MessageAdapter().also { this@MainActivity.adapter = it }
            setOnItemClickListener { _, _, position, _ ->
                val message = messages[position]
                if (message.type == "audio" && !audio.play(message.content)) toast("语音播放失败")
            }
        }
        val input = field("输入消息").apply { maxLines = 4; setSingleLine(false) }
        val send = button("发送") {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                client?.send(Packet("message", message = Message("", currentRoom, username, "text", text.take(4000), 0)))
                input.text.clear()
            }
        }
        val record = Button(this).apply {
            text = "按住录音"
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { requestRecording { if (audio.start()) { recording = true; text = "松开发送"; handler.postDelayed({ finishRecording(this) }, 60_000) } else toast("无法启动录音") }; true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { finishRecording(this); true }
                    else -> false
                }
            }
        }
        val bottom = LinearLayout(this).apply {
            setPadding(dp(8), dp(6), dp(8), dp(6)); gravity = Gravity.BOTTOM
            addView(record, LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(send, LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun requestRecording(start: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else { pendingRecord = start; audioPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }

    private fun finishRecording(button: Button) {
        if (!recording) return
        recording = false
        handler.removeCallbacksAndMessages(null)
        button.text = "按住录音"
        val encoded = audio.stop()
        if (encoded == null) toast("录音过短、过长或保存失败")
        else client?.send(Packet("message", message = Message("", currentRoom, username, "audio", encoded, 0)))
    }

    private fun showAdmin() {
        val target = field("目标用户名")
        val actions = arrayOf("踢出", "禁言", "解除禁言", "封禁", "解除封禁", "清空聊天记录")
        AlertDialog.Builder(this).setTitle("房主管理").setView(target).setItems(actions) { _, which ->
            val command = arrayOf("kick", "mute", "unmute", "ban", "unban", "clear")[which]
            if (command != "clear" && target.text.toString().trim().isEmpty()) toast("请输入目标用户名")
            else if (command == "clear") AlertDialog.Builder(this).setTitle("确认清空？").setMessage("服务器上的本房间记录将被删除。").setPositiveButton("清空") { _, _ -> sendAdmin("", command) }.setNegativeButton("取消", null).show()
            else sendAdmin(target.text.toString().trim(), command)
        }.setNegativeButton("取消", null).show()
    }

    private fun sendAdmin(target: String, command: String) = client?.send(Packet("admin", target = target, data = command)).let { Unit }

    override fun onBackPressed() {
        if (client != null) AlertDialog.Builder(this).setMessage("退出当前聊天？").setPositiveButton("退出") { _, _ -> client?.close(); client = null; showHome() }.setNegativeButton("取消", null).show()
        else super.onBackPressed()
    }

    override fun onDestroy() { client?.close(); audio.release(); super.onDestroy() }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class MessageAdapter : ArrayAdapter<Message>(this@MainActivity, android.R.layout.simple_list_item_1, messages) {
        private val time = SimpleDateFormat("HH:mm", Locale.getDefault())
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = getItem(position)!!
            return (convertView as? TextView ?: TextView(context)).apply {
                setPadding(dp(14), dp(9), dp(14), dp(9))
                textSize = if (item.type == "system") 13f else 16f
                gravity = if (item.type == "system") Gravity.CENTER else if (item.sender == username) Gravity.END else Gravity.START
                setTextColor(if (item.type == "system") Color.GRAY else Color.rgb(35, 45, 42))
                text = when (item.type) {
                    "system" -> item.content
                    "audio" -> "${item.sender}  ${time.format(Date(item.timestamp))}\n▶ 语音消息（点击播放）"
                    else -> "${item.sender}  ${time.format(Date(item.timestamp))}\n${item.content}"
                }
            }
        }
    }
}
