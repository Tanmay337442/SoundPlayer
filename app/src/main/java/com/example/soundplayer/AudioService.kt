package com.example.soundplayer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

const val SAMPLE_RATE = 44100
const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
const val CHANNEL_ID = "audio_service"
const val NOTIFICATION_ID = 1

class AudioService : Service() {

    private lateinit var audioRecord: AudioRecord
    private lateinit var audioTrack: AudioTrack
    private var bufferSize: Int = 0
    private var isRecording = false
    private var thread: Thread? = null

    override fun onCreate() {
        super.onCreate()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
            stopSelf()
            return
        }

        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord = AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .build()

        startForegroundService()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Audio Service")
            .setContentText("Recording and playing audio in the background")
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRecording = true
        val factor = intent?.getDoubleExtra("factor", 1.0) ?: 1.0
        Log.d("AudioService", "Factor received: $factor")
        thread = Thread {
            val buffer = ByteArray(bufferSize)
            val shortBuffer = ShortArray(bufferSize/2)
            audioRecord.startRecording()
            audioTrack.play()
            while (isRecording) {
                val numBytesRead = audioRecord.read(buffer, 0, bufferSize)
                if (numBytesRead > 0) {
                    for (i in 0..<numBytesRead step 2) {
                        var sound = (((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))*factor).toInt().toShort()
                        if (sound > Short.MAX_VALUE) {
                            sound = Short.MAX_VALUE
                        } else if (sound < Short.MIN_VALUE) {
                            sound = Short.MIN_VALUE
                        }
                        shortBuffer[i / 2] = sound
                    }
                    audioTrack.write(shortBuffer, 0, numBytesRead/2)
                }
            }
        }
        thread?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        audioRecord.stop()
        audioRecord.release()
        audioTrack.stop()
        audioRecord.release()
        thread?.interrupt()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

}