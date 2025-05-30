package lucheart.heartsoos.wearapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.preference.PreferenceManager
import lucheart.heartsoos.wearapp.databinding.ActivitySettingsBinding

class SettingActivity : Activity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.textUrl.setText(prefs.getString("url", "ws://192.168.2.4:666/ws/default"))
    }

    fun goBack(view: View) {
        finish()
    }

    fun saveAndReconnect(view: View) {
        Log.i("Settings", "Save and Reconnect")
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        with (prefs.edit()) {
            putString("url", binding.textUrl.text.toString())
            apply()
        }
        sendBroadcast(Intent("recreate").setPackage("lucheart.heartsoos.wearapp"))
        finish()
    }

}