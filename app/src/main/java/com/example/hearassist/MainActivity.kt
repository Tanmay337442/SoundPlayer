package com.example.hearassist

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

const val REQUEST_CODE = 0

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: ToggleButton
    private lateinit var seekBar: SeekBar
    private lateinit var settingsButton: Button
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

        if (permissionsDenied()) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT), REQUEST_CODE)
        }

        settingsButton = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            openSettings()
        }

        seekBar = findViewById(R.id.seekBar)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                factor = progress/4.0
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
            if (permissionsDenied()) {
                Toast.makeText(this@MainActivity, "Please enable required permissions in settings", Toast.LENGTH_SHORT).show()
                isRunning = false
                toggleButton.isChecked = false
                stopAudioService()
            } else if (isChecked && bluetoothConnected()) {
                isRunning = true
                startAudioService()
            } else {
                isRunning = false
                toggleButton.isChecked = false
                stopAudioService()
            }
        }

        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothReceiver, filter)

    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                Toast.makeText(context, "Bluetooth device disconnected", Toast.LENGTH_SHORT).show()
                isRunning = false
                toggleButton.isChecked = false
                stopAudioService()
            } else if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_OFF) {
                Toast.makeText(context, "Bluetooth turned off", Toast.LENGTH_SHORT).show()
                isRunning = false
                toggleButton.isChecked = false
                stopAudioService()
            }
        }
    }

    private fun permissionsDenied(): Boolean {
        return (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED
                || ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED)
    }

    private fun bluetoothConnected(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
            if (bluetoothAdapter == null) {
                Toast.makeText(this@MainActivity, "Device must support Bluetooth", Toast.LENGTH_SHORT).show()
                return false
            }
            if (!bluetoothAdapter.isEnabled) {
                Toast.makeText(this@MainActivity, "Must be paired to Bluetooth device", Toast.LENGTH_SHORT).show()
                return false
            }

            val bondedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
            for (device in bondedDevices) {
                val connected = device.javaClass.getMethod("isConnected").invoke(device) as Boolean
                if (connected) {
                    return true
                }
            }
            return false
        }
        Toast.makeText(this@MainActivity, "Please enable Bluetooth permissions in settings", Toast.LENGTH_SHORT).show()
        return false
    }

    private fun startAudioService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(this@MainActivity, AudioService::class.java)
            intent.putExtra("factor", factor)
            ContextCompat.startForegroundService(this@MainActivity, intent)
        } else {
            Toast.makeText(this@MainActivity, "Please enable audio recording permissions in settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAudioService() {
        val intent = Intent(this@MainActivity, AudioService::class.java)
        stopService(intent)
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.setData(uri)
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!(requestCode == REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this@MainActivity, "Please enable required permissions in settings", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
    }

}