package com.chenyue404.ntfy2broadcast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

/**
 * Created by chenyue on 2023/7/14 0014.
 */
class NtfyService : Service() {

    companion object {
        const val URL = "url"
        const val TOKEN = "token"
        const val ACTION = "action"
        const val EXTRA_KEY = "extra_key"

        private val _running: MutableLiveData<Boolean> = MutableLiveData(false)
        val running: LiveData<Boolean> = _running

        private val _msg: MutableLiveData<String?> = MutableLiveData()
        val msg: LiveData<String?> = _msg
    }

    private val MAX_RECONNECT_NUM = 5
    private var reconnectNum = 0
    private val okHttpClient by lazy {
        OkHttpClient.Builder().build()
    }
    private var wsRequest: Request? = null
    private val wsListener by lazy {
        object : WebSocketListener() {
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log("onClosed, code=$code, reason=$reason")
                toast("Disconnected")
                _running.postValue(false)
                _msg.postValue("---disconnected---")
                stopSelf()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                log("onClosing, code=$code, reason=$reason")
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                log("onFailure, code=${response?.code ?: ""}, reason=${response?.body?.string() ?: ""}")
                toast("onFailure")
                _running.postValue(false)
                reconnect()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                log("onMessage, text=$text")
                _msg.postValue(text)
                dealMsg(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                log("onMessage, bytes=$bytes")
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                log("onOpen, code=${response.code}, reason=${response.body?.string() ?: ""}")
                toast("Connected")
                _running.postValue(true)
                _msg.postValue("---connected---")
                reconnectNum = 0
            }
        }
    }
    private var action: String? = null
    private var extraKey: String? = null

    override fun onCreate() {
        super.onCreate()

        val channelId = "NtfyServiceId"
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(
                    channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        startForeground(
            1000,
            Notification
                .Builder(this, channelId)
                .setSmallIcon(R.drawable.test)
                .setContentTitle("NtfyService Title")
                .setContentText("NtfyService Text")
                .build()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        _running.postValue(false)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        log("onStartCommand")
        val url = intent.getStringExtra(URL)
        val token = intent.getStringExtra(TOKEN)
        action = intent.getStringExtra(ACTION)
        extraKey = intent.getStringExtra(EXTRA_KEY)
        if (url.isNullOrEmpty()
            || action.isNullOrEmpty()
            || extraKey.isNullOrEmpty()
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        wsRequest = Request.Builder()
            .url(url)
            .apply {
                if (!token.isNullOrEmpty()) {
                    addHeader("Authorization", token)
                }
            }
            .build()
        connect()
        return START_REDELIVER_INTENT
    }

    private fun connect() {
        if (_running.value == true) return
        wsRequest?.let {
            okHttpClient.newWebSocket(it, wsListener)
        }
    }

    private fun reconnect() {
        if (_running.value == true) return
        if (reconnectNum >= MAX_RECONNECT_NUM) {
            log("reconnect: MAX_RECONNECT_NUM")
            toast("Reconnect over $MAX_RECONNECT_NUM, please check url or network")
            return
        }
        Handler().postDelayed({
            reconnectNum++
            _msg.postValue("---failure, reconnecting $reconnectNum---")
            connect()
        }, 1000)
    }

    private fun log(str: String) {
        Log.d("NtfyService", str)
    }

    private fun toast(str: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this@NtfyService, str, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dealMsg(msg: String) {
        val command = try {
            JSONObject(msg).get("message").toString()
        } catch (e: Exception) {
            Log.e("NtfyService", "msg=$msg\ne=$e")
            return
        }
        sendBroadcast(Intent(action).putExtra(extraKey, command))
    }
}