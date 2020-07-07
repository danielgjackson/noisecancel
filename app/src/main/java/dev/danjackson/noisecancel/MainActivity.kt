package dev.danjackson.noisecancel

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
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
    private var currentDeviceType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)

        send.setOnClickListener {
            run("off")
        }

        val sharedPreferences = getSharedPreferences("preferences", Context.MODE_PRIVATE)
        currentDeviceAddress = sharedPreferences.getString("device_address", null)
        currentDeviceName = sharedPreferences.getString("device_name", null)
        currentDeviceType = sharedPreferences.getString("device_type", null)
        preferencesChanged()
    }

    private fun chooseDeviceType() {
        val deviceTypes = mutableMapOf<String, String>()
        deviceTypes.put("700", getString(R.string.settings_device_type_700))
        deviceTypes.put("qc35", getString(R.string.settings_device_type_qc35))
        deviceTypes.put("all", getString(R.string.settings_device_type_all))

        val deviceTypesList = deviceTypes.keys.toList()

        val items = deviceTypesList.map { key -> deviceTypes[key] }.toTypedArray()
        val currentDeviceTypeIndex = deviceTypesList.indexOf(currentDeviceType)

        // User chooses
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_choose_device_type))
            .setSingleChoiceItems(
                items,
                currentDeviceTypeIndex
            ) { dialog, which ->
                currentDeviceType = deviceTypesList[which]

                // Update preferences
                val sharedPreferences = getSharedPreferences("preferences", Context.MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putString("device_type", currentDeviceType)
                editor.apply()

                preferencesChanged()

                dialog.dismiss()
                chooseDevice()
            }
            .create()
            .show()
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
                    chooseDeviceType()
                })
            .setNegativeButton(getString(R.string.disclaimer2_cancel), DialogInterface.OnClickListener { _, _ -> {} })
            .show()
    }

    private fun settings() {
        if (currentDeviceAddress.isNullOrEmpty()) {
            showDisclaimer1()
        } else {
            chooseDeviceType()
        }
    }


    private fun preferencesChanged() {
        label_device_address.setText(
            when (currentDeviceType) {
                "qc35" -> R.string.settings_device_type_qc35
                "all" -> R.string.settings_device_type_all
                else -> R.string.settings_device_type_700  // 700
            }
        )
        device_address.setText("${currentDeviceName ?: ""} <${currentDeviceAddress ?: "no device set"}> [${currentDeviceType ?: "unknown"}]")
    }

    private fun run(level: String) {
        if (currentDeviceAddress.isNullOrEmpty()) {
            settings()
        } else {
            val intent = Intent(this, SendService::class.java)
            intent.putExtra("level", level)
            SendService.enqueueWork(applicationContext, intent)
        }
    }

    private fun pinShortcut(id: String, iconId: Int, shortLabel: Int, longLabel: Int, level: String) {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(applicationContext)) {
            val icon = IconCompat.createWithResource(applicationContext, iconId)
            val pinShortcutInfo = ShortcutInfoCompat.Builder(applicationContext, id)
                .setIcon(icon)
                .setShortLabel(getString(shortLabel))
                .setLongLabel(getString(longLabel))
                .setIntent(Intent(applicationContext, QuickStart::class.java).setAction(Intent.ACTION_VIEW).putExtra("level", level))
                .build()
            ShortcutManagerCompat.requestPinShortcut(applicationContext, pinShortcutInfo, null)
        } else {
            Toast.makeText(this, "Shortcut pinning not supported", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            menu.setGroupDividerEnabled(true)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_nc_off -> {
                run("off")
                true
            }
            R.id.action_settings -> {
                settings()
                true
            }
            R.id.action_nc_0 -> {
                run("0")
                true
            }
            R.id.action_nc_5 -> {
                run("5")
                true
            }
            R.id.action_nc_10 -> {
                run("10")
                true
            }
            R.id.action_pin_shortcut_nc_off -> {
                pinShortcut("nc_off", R.mipmap.ic_quick_launcher_off, R.string.nc_off_shortcut_short_label, R.string.nc_off_shortcut_long_label, "off")
                true
            }
            R.id.action_pin_shortcut_nc_0 -> {
                pinShortcut("nc_0", R.mipmap.ic_quick_launcher_0, R.string.nc_0_shortcut_short_label, R.string.nc_0_shortcut_long_label, "0")
                true
            }
            R.id.action_pin_shortcut_nc_5 -> {
                pinShortcut("nc_5", R.mipmap.ic_quick_launcher_5, R.string.nc_5_shortcut_short_label, R.string.nc_5_shortcut_long_label, "5")
                true
            }
            R.id.action_pin_shortcut_nc_10 -> {
                pinShortcut("nc_10", R.mipmap.ic_quick_launcher_10, R.string.nc_10_shortcut_short_label, R.string.nc_10_shortcut_long_label, "10")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}