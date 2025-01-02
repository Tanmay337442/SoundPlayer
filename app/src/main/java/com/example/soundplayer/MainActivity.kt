package com.example.soundplayer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate

const val REQUEST_CODE_RECORD = 0
const val REQUEST_CODE_BLUETOOTH = 1

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: ToggleButton
    private lateinit var seekBar: SeekBar
    private lateinit var timer: Timer
    private var factor = 1.0
    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD)
        }

        seekBar = findViewById(R.id.seekBar)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                Log.d("SeekBar", "Progress: $progress")
                factor = progress/30.0
                isRunning = false
                toggleButton.isChecked = false
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        toggleButton = findViewById(R.id.toggleButton)

        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            Log.d("MainActivity", "ToggleButton checked: $isChecked")
            if (isChecked && bluetoothConnected()) {
                isRunning = true
                startAudioService()
            } else {
                isRunning = false
                stopAudioService()
            }
        }

    }

    private fun bluetoothConnected(): Boolean {
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_CODE_BLUETOOTH)
        }

        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this@MainActivity, "Device must support Bluetooth", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Must be paired to Bluetooth device", Toast.LENGTH_SHORT).show()
            return false
        }
        val connectedDevices: List<BluetoothDevice>? = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
        return !connectedDevices.isNullOrEmpty()
    }

    private fun startBluetoothChecker() {
        timer = Timer(true)
        timer.scheduleAtFixedRate(0, 1000) {
            if (!bluetoothConnected()) {
                isRunning = false
                toggleButton.isChecked = false
            }
            Log.d("MainActivity", "Connected: ${bluetoothConnected()}")
        }
    }

    private fun stopBluetoothChecker() {
        if (::timer.isInitialized) {
            timer.cancel()
        }
    }

    private fun startAudioService() {
        startBluetoothChecker()
        val intent = Intent(this@MainActivity, AudioService::class.java)
        intent.putExtra("factor", factor)
        ContextCompat.startForegroundService(this@MainActivity, intent)
    }

    private fun stopAudioService() {
        stopBluetoothChecker()
        val intent = Intent(this@MainActivity, AudioService::class.java)
        stopService(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!(requestCode == REQUEST_CODE_RECORD && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this@MainActivity, "Recording permission denied", Toast.LENGTH_SHORT).show()
        }
    }

}