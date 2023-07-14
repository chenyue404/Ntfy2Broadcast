package com.chenyue404.ntfy2broadcast

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Created by chenyue on 2023/7/14 0014.
 */
class MainActivity : AppCompatActivity() {

    private val etUrl: EditText by lazy { findViewById(R.id.etUrl) }
    private val etToken: EditText by lazy { findViewById(R.id.etToken) }
    private val etAction: EditText by lazy { findViewById(R.id.etAction) }
    private val etExtraKey: EditText by lazy { findViewById(R.id.etExtraKey) }
    private val tv01: TextView by lazy { findViewById(R.id.tv01) }
    private val tv02: TextView by lazy { findViewById(R.id.tv02) }
    private val tv03: TextView by lazy { findViewById(R.id.tv03) }
    private val tv04: TextView by lazy { findViewById(R.id.tv04) }
    private val tv05: TextView by lazy { findViewById(R.id.tv05) }
    private val btStart: Button by lazy { findViewById(R.id.btStart) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sp = getSharedPreferences("data", MODE_PRIVATE)
        sp.getString(NtfyService.URL, "")?.takeIf { it.isNotEmpty() }?.let {
            etUrl.setText(it)
        }
        sp.getString(NtfyService.TOKEN, "")?.takeIf { it.isNotEmpty() }?.let {
            etToken.setText(it)
        }
        sp.getString(NtfyService.ACTION, "")?.takeIf { it.isNotEmpty() }?.let {
            etAction.setText(it)
        }
        sp.getString(NtfyService.EXTRA_KEY, "")?.takeIf { it.isNotEmpty() }?.let {
            etExtraKey.setText(it)
        }

        val ntfyServiceIntent = Intent(this, NtfyService::class.java)
        btStart.setOnClickListener {
            if (NtfyService.running.value == false) {
                val url = etUrl.text.toString().takeIf { it.isNotEmpty() } ?: kotlin.run {
                    Toast.makeText(this, "Url is required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val token = etToken.text.toString()
                val action = etAction.text.toString().takeIf { it.isNotEmpty() } ?: kotlin.run {
                    Toast.makeText(this, "Action is required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val extraKey = etExtraKey.text.toString().takeIf { it.isNotEmpty() } ?: kotlin.run {
                    Toast.makeText(this, "Extra key is required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startService(
                    ntfyServiceIntent
                        .putExtra(NtfyService.URL, url)
                        .putExtra(NtfyService.TOKEN, token)
                        .putExtra(NtfyService.ACTION, action)
                        .putExtra(NtfyService.EXTRA_KEY, extraKey)
                )
                sp.edit {
                    putString(NtfyService.URL, url)
                    putString(NtfyService.TOKEN, token)
                    putString(NtfyService.ACTION, action)
                    putString(NtfyService.EXTRA_KEY, extraKey)
                }
                btStart.text = "Connecting"
            } else {
                stopService(ntfyServiceIntent)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.POST_NOTIFICATIONS
                ), 1000
            )
        }

        val tvArray = arrayOf(tv05, tv04, tv03, tv02, tv01)
        NtfyService.running.observe(this) {
            btStart.text = if (it) "Stop" else "Start"
        }
        NtfyService.msg.observe(this) {
            tvArray.forEachIndexed { index, textView ->
                textView.text = when (index) {
                    tvArray.size - 1 -> it ?: ""
                    else -> tvArray[index + 1].text
                }
            }
        }

        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
    }
}