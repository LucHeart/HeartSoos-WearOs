package lucheart.heartsoos.wearapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_settings.*

class SettingActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        textUrl.setText(prefs.getString("url", "ws://192.168.2.4:666/ws/default"))
    }

    fun goBack(view: View) {
        finish()
    }

    fun saveAndReconnect(view: View) {
        Log.i("Settings", "Save and Reconnect")
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        with (prefs.edit()) {
            putString("url", textUrl.text.toString())
            apply()
        }
        sendBroadcast(Intent("recreate").setPackage("lucheart.heartsoos.wearapp"))
        finish()
    }

}