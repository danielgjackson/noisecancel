package dev.danjackson.noisecancel

import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*


class MainActivity : AppCompatActivity() {

    private var currentDeviceAddress: String? = null
    private var currentDeviceName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)

        send.setOnClickListener {
            run()
        }

        val sharedPreferences = getSharedPreferences("preferences", Context.MODE_PRIVATE)
        currentDeviceAddress = sharedPreferences.getString("device_address", null)
        currentDeviceName = sharedPreferences.getString("device_name", null)
        preferencesChanged()
    }

    private fun chooseDevice() {
        // Map of addresses to names
        val devices = mutableMapOf<String, String?>()

        // Add current device
        if (currentDeviceAddress != null && currentDeviceAddress?.isNotEmpty()!!) {
            devices[currentDeviceAddress!!] = currentDeviceName
        }

        // Get bonded devices
        val bondedDevices = BluetoothAdapter.getDefaultAdapter()?.bondedDevices.orEmpty()
        bondedDevices.forEach { bluetoothDevice ->
            devices[bluetoothDevice.address] = bluetoothDevice.name
        }

        val deviceAddressList = devices.keys.toList()

        val items = deviceAddressList.map { key -> "${devices[key]} <${key}>" }.toTypedArray()
        val currentDeviceIndex = deviceAddressList.indexOf(currentDeviceAddress)

        // User chooses
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_choose_device))
            .setSingleChoiceItems(
                items,
                currentDeviceIndex
            ) { dialog, which ->
                currentDeviceAddress = deviceAddressList[which]
                currentDeviceName = devices[currentDeviceAddress!!]

                // Update preferences
                val sharedPreferences = getSharedPreferences("preferences", Context.MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putString("device_address", currentDeviceAddress)
                editor.putString("device_name", currentDeviceName)
                editor.apply()

                preferencesChanged()
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun showDisclaimer1() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.disclaimer1_title))
            .setMessage(getString(R.string.disclaimer1_message))
            .setPositiveButton(getString(R.string.disclaimer1_accept),
                DialogInterface.OnClickListener { _, _ ->
                    showDisclaimer2()
                })
            .setNegativeButton(getString(R.string.disclaimer1_cancel), DialogInterface.OnClickListener { _, _ -> {} })
            .show()
    }

    private fun showDisclaimer2() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.disclaimer2_title))
            .setMessage(getString(R.string.disclaimer2_message))
            .setPositiveButton(getString(R.string.disclaimer2_accept),
                DialogInterface.OnClickListener { _, _ ->
                    chooseDevice()
                })
            .setNegativeButton(getString(R.string.disclaimer2_cancel), DialogInterface.OnClickListener { _, _ -> {} })
            .show()
    }

    private fun settings() {
        if (currentDeviceAddress.isNullOrEmpty()) {
            showDisclaimer1()
        } else {
            chooseDevice()
        }
    }


    private fun preferencesChanged() {
        device_address.setText("${currentDeviceName ?: ""} <${currentDeviceAddress ?: "no device set"}>")
    }

    private fun run() {
        if (currentDeviceAddress.isNullOrEmpty()) {
            settings()
        } else {
            val intent = Intent(this, SendService::class.java)
            SendService.enqueueWork(applicationContext, intent)
        }
    }

    private fun pinShortcut() {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(applicationContext)) {

            val icon = IconCompat.createWithResource(applicationContext, R.drawable.ic_quick_launcher_foreground)

            val pinShortcutInfo = ShortcutInfoCompat.Builder(applicationContext, "send")
                .setIcon(icon)
                .setShortLabel(getString(R.string.send_shortcut_short_label))
                .setLongLabel(getString(R.string.send_shortcut_long_label))
                .setIntent(Intent(applicationContext, QuickStart::class.java).setAction(Intent.ACTION_VIEW))
                .build()

            ShortcutManagerCompat.requestPinShortcut(applicationContext, pinShortcutInfo, null)
        } else {
            Toast.makeText(this, "Shortcut pinning not supported", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_send -> {
                run()
                true
            }
            R.id.action_settings -> {
                settings()
                true
            }
            R.id.action_pin_shortcut -> {
                pinShortcut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}