package lucheart.heartsoos.wearapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.neovisionaries.ws.client.*
import java.net.URI
import kotlin.math.roundToInt


class HeartRateService : Service(), SensorEventListener2 {

    private val websocketEventsListener: WebSocketAdapter = object : WebSocketAdapter() {
        override fun onDisconnected(
            websocket: WebSocket?,
            serverCloseFrame: WebSocketFrame?,
            clientCloseFrame: WebSocketFrame?,
            closedByServer: Boolean
        ) {
            Log.i("WebSocket", "Disconnect")
            setupNewWebsocket()
        }

        override fun onConnectError(websocket: WebSocket?, exception: WebSocketException?) {
            Log.e("WebSocket", "Connect error", exception)
            setupNewWebsocket()
        }

        override fun onConnected(
            websocket: WebSocket?,
            headers: MutableMap<String, MutableList<String>>?
        ) {
            Log.i("WebSocket", "Successfully connected")
        }

        override fun onStateChanged(websocket: WebSocket?, newState: WebSocketState?) {
            val updateStateIntent = Intent();
            updateStateIntent.action = "updateState"
            updateStateIntent.putExtra("state", newState);
            sendBroadcast(updateStateIntent)
        }
    }


    private val stopAction = "STOP_ACTION"
    private lateinit var mSensorManager: SensorManager
    private var mHeartRateSensor: Sensor? = null
    private val factory = WebSocketFactory()
    private lateinit var _websocket: WebSocket
    private lateinit var wakeLock: PowerManager.WakeLock

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                stopAction -> {
                    stopSelf()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }

                "recreate" -> createNewWebsocket()
            }
        }
    }

    private var mBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    inner class LocalBinder : Binder() {
        fun getServerInstance(): HeartRateService {
            return this@HeartRateService
        }
    }

    fun getWebsocket(): WebSocket {
        return _websocket
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter()
        filter.addAction(stopAction)
        filter.addAction("recreate")
        registerReceiver(broadcastReceiver, filter)

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HeartSoos::BackgroundMeasuring").apply {
                acquire()
            }
        }

        createNewWebsocket()

    }

    private fun createNewWebsocket() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val url = prefs.getString("url", "ws://192.168.86.220:5566/ws/default")

        if (this::_websocket.isInitialized) {
            _websocket.removeListener(websocketEventsListener);
            _websocket.disconnect();
        }

        Log.i("WebSocket", "Creating new WebSocket for ${url}")
        _websocket = factory.createSocket(
            URI(url),
            2000
        )

        _websocket.addListener(websocketEventsListener)
        _websocket.connectAsynchronously()
    }

    private fun setupNewWebsocket() {
        Log.i("WebSocket", "Recreating....")
        var currentWebsocket = _websocket
        _websocket.disconnect()

        Thread.sleep(3000)

        if (currentWebsocket != _websocket) return

        _websocket = _websocket.recreate()
        _websocket.connectAsynchronously()

    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(broadcastReceiver)
        mSensorManager.unregisterListener(this)

        _websocket.removeListener(websocketEventsListener)
        _websocket.disconnect()
        wakeLock.release();
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        createNotificationChannel();
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, Intent(this, MainActivity::class.java), 0
        )

        val notification = NotificationCompat.Builder(this, "hrservice")
            .setContentTitle("HeartSoos")
            .setContentText("Streaming heart rate in the background...")
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                PendingIntent.getBroadcast(
                    this,
                    12345,
                    Intent(stopAction),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build();

        startForeground(1, notification)

        mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)

        return START_STICKY;
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "hrservice",
                "HeartWear Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager =
                getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onAccuracyChanged(p0: Sensor, p1: Int) {}

    override fun onFlushCompleted(p0: Sensor) {}

    private var oldRoundedHeartRate: Int = 0;

    override fun onSensorChanged(p0: SensorEvent) {
        val heartRate = p0.values[0].roundToInt()
        if (heartRate == oldRoundedHeartRate) return
        oldRoundedHeartRate = heartRate
        val updateHRIntent = Intent();
        updateHRIntent.action = "updateHR"
        updateHRIntent.putExtra("bpm", heartRate);
        sendBroadcast(updateHRIntent)

        try {
            _websocket.sendText("""{"heartRate": $heartRate}""")
        } catch (e: java.lang.Exception) {
            Log.e("WebSocket", "", e)
        }
    }
}
