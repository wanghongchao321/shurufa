package com.example.voicetranslateime

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class PermissionActivity : Activity() {
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusText = TextView(this).apply {
            textSize = 18f
        }

        val permissionButton = Button(this).apply {
            text = "授予麦克风权限"
            setOnClickListener {
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_MICROPHONE
                )
            }
        }

        val enableImeButton = Button(this).apply {
            text = "启用输入法"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val selectImeButton = Button(this).apply {
            text = "选择输入法"
            setOnClickListener {
                getSystemService(InputMethodManager::class.java)
                    .showInputMethodPicker()
            }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(48, 72, 48, 48)
                addView(statusText)
                addView(permissionButton)
                addView(enableImeButton)
                addView(selectImeButton)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE) refreshStatus()
    }

    private fun refreshStatus() {
        val micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        statusText.text = if (micGranted) {
            "麦克风权限：已授予\n\n短按输入法按钮切换模式；长按说话，松开发送。"
        } else {
            "麦克风权限：未授予\n\n请先授权，再启用并选择本输入法。"
        }
    }

    private companion object {
        const val REQUEST_MICROPHONE = 1001
    }
}
