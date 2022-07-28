package lucheart.heartsoos.wearapp

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.neovisionaries.ws.client.WebSocketState
import kotlinx.android.synthetic.main.activity_main.*
import lucheart.heartsoos.wearapp.HeartRateService.LocalBinder


class MainActivity : Activity() {

    private var service: HeartRateService? = null

    private var broadcastReceiver = object : BroadcastReceiver() {

        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "updateHR" -> textBPM.text = "${intent.extras!!.get("bpm")} bpm"
                "updateState" -> updateWebSocketState(intent.extras!!.get("state") as WebSocketState)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermission(android.Manifest.permission.BODY_SENSORS, 100);

        val filter = IntentFilter()
        filter.addAction("updateHR")
        filter.addAction("updateState")
        registerReceiver(broadcastReceiver, filter)
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

        heartButton.setColorFilter(color)
    }


    private fun checkPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this@MainActivity, permission) != PackageManager.PERMISSION_DENIED) return
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
        super.onStart();

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
        val stopIntent = Intent()
        stopIntent.action = "STOP_ACTION";
        var pendingIntentStopAction =
            PendingIntent.getBroadcast(this, 12345, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        pendingIntentStopAction.send()
    }

}


