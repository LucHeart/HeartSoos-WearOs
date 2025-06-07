package lucheart.heartsoos.wearapp

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.neovisionaries.ws.client.WebSocketState
import lucheart.heartsoos.wearapp.HeartRateService.LocalBinder
import lucheart.heartsoos.wearapp.databinding.ActivityMainBinding


class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding

    private var service: HeartRateService? = null

    private var broadcastReceiver = object : BroadcastReceiver() {

        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "updateHR" -> binding.textBPM.text = "${intent.extras!!.get("bpm")} bpm"
                "updateState" -> updateWebSocketState(intent.extras!!.get("state") as WebSocketState)
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermission(android.Manifest.permission.BODY_SENSORS, 100)
        checkPermission(android.Manifest.permission.BODY_SENSORS_BACKGROUND, 101)
        checkPermission(android.Manifest.permission.FOREGROUND_SERVICE, 110)
        checkPermission(android.Manifest.permission.FOREGROUND_SERVICE_HEALTH, 111)
        checkPermission(android.Manifest.permission.INTERNET, 120)
        checkPermission(android.Manifest.permission.WAKE_LOCK, 130)
        checkPermission(android.Manifest.permission.POST_NOTIFICATIONS, 140)
        checkPermission(android.Manifest.permission.HIGH_SAMPLING_RATE_SENSORS, 150)
        checkPermission("android.permission.READ_HEART_RATE", 160)


        val filter = IntentFilter()
        filter.addAction("updateHR")
        filter.addAction("updateState")
        registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    fun switchToSettings(view: View) {
        Intent(this, SettingActivity::class.java).also { intent ->
            startActivity(intent)
        }
    }

    private fun updateWebSocketState(state: WebSocketState) {
        val color = when(state) {
            WebSocketState.OPEN -> Color.GREEN
            WebSocketState.CLOSED, WebSocketState.CLOSING -> Color.RED
            WebSocketState.CREATED -> Color.LTGRAY
            WebSocketState.CONNECTING -> Color.argb(255, 204, 105, 0)
        }

        binding.heartButton.setColorFilter(color)
    }


    private fun checkPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this@MainActivity, permission) != PackageManager.PERMISSION_DENIED) return
        Log.d("MainActivity", "Requesting permission: $permission")
        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(permission), requestCode)
    }

    private var mConnection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, ser: IBinder) {
            Toast.makeText(this@MainActivity, "Service is connected", Toast.LENGTH_SHORT).show()
            val mLocalBinder = ser as LocalBinder
            service = mLocalBinder.getServerInstance()

            updateWebSocketState(service!!.getWebsocket().state)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Toast.makeText(this@MainActivity, "Service is disconnected", Toast.LENGTH_SHORT).show()
            service = null
        }

    }

    override fun onStart() {
        super.onStart()

        Intent(this, HeartRateService::class.java).also { intent ->
            bindService(intent, mConnection, 0)
            startService(intent)
        }
    }

    override fun onStop() {
        super.onStop()
        if(service != null) {
            unbindService(mConnection)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d("MainActivity", "onDestroy called")
        val stopIntent = Intent()
        stopIntent.action = "STOP_ACTION"
        var pendingIntentStopAction =
            PendingIntent.getBroadcast(this, 12345, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        pendingIntentStopAction.send()

        unregisterReceiver(broadcastReceiver)
    }

}


