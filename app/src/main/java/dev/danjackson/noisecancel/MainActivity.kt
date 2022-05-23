package dev.danjackson.noisecancel

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Observer


class MainActivity : AppCompatActivity() {

    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = Settings.getInstance(applicationContext)

        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))

        findViewById<Button>(R.id.send).setOnClickListener {
            run("off")
        }

        findViewById<ListView>(R.id.device_list).onItemClickListener = OnItemClickListener { adapter, _, position, _ ->
            val device = adapter.getItemAtPosition(position) as Device
            promptRemoveDevice(device)
        }

        settings.devicesData.observe(
            this
        ) {

            // TODO: Don't recreate the adapter on update
            val adapter: ArrayAdapter<Device> = object : ArrayAdapter<Device>(
                this,
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                settings.devicesData.value.orEmpty()
            ) {
                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup
                ): View {
                    val view: View = super.getView(position, convertView, parent)
                    view.findViewById<TextView>(android.R.id.text1).text =
                        "\uD83C\uDFA7  ${settings.devicesData.value?.get(position)?.name}"
                    view.findViewById<TextView>(android.R.id.text2).text =
                        "${settings.devicesData.value?.get(position)?.type?.fullName}"
                    return view
                }


            }
            findViewById<ListView>(R.id.device_list).adapter = adapter

            //adapter.notifyDataSetChanged()

            findViewById<TextView>(R.id.label_no_devices).visibility =
                if (it.isEmpty()) TextView.VISIBLE else TextView.INVISIBLE
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                addDevice()
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_CONNECT)) {
                    val builder = AlertDialog.Builder(this)
                    with(builder)
                    {
                        setTitle("Bluetooth Connect Permission")
                        setMessage("To connect to your device, this app requires Bluetooth Connect permission.")
                        setPositiveButton("OK") { _, _ ->

                        }
                        setPositiveButton("Cancel") { _, _ ->
                            Toast.makeText(applicationContext,"Cannot add device without Bluetooth Connect permission", Toast.LENGTH_SHORT).show()
                        }
                        show()
                    }
                } else {
                    Toast.makeText(applicationContext,"Cannot add device without Bluetooth Connect permission", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val bluetoothConnectRequestCode = 1

    // TODO: Change UI so adding a device is its own activity rather than two dialog boxes
    private fun addDevice() {
        // Prompt for Bluetooth permission if required
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), bluetoothConnectRequestCode)
            } else {
                Toast.makeText(applicationContext,"Problem requesting permission: Bluetooth Connect", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (!settings.agreedDisclaimer()) {
            showDisclaimer1()
        } else {
            chooseDeviceType()
        }
    }

    private fun chooseDeviceType() {
        val deviceTypes = mutableMapOf<String, String>()
        deviceTypes[DeviceType.NC700.toString()] = getString(R.string.settings_device_type_700)
        deviceTypes[DeviceType.QC35.toString()] = getString(R.string.settings_device_type_qc35)
        deviceTypes[DeviceType.QCE.toString()] = getString(R.string.settings_device_type_qce)
        deviceTypes[DeviceType.QC45.toString()] = getString(R.string.settings_device_type_qc45)

        val deviceTypesList = deviceTypes.keys.toList()

        val items = deviceTypesList.map { key -> deviceTypes[key] }.toTypedArray()

        // User chooses
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_choose_device_type))
            .setSingleChoiceItems(
                items,
                -1
            ) { dialog, which ->
                val deviceType = DeviceType.fromString(deviceTypesList[which])
                dialog.dismiss()
                chooseDevice(deviceType)
            }
            .create()
            .show()
    }

    private fun chooseDevice(deviceType: DeviceType) {
        // Map of addresses to names
        val devices = mutableMapOf<String, String?>()

        // Get bonded devices
        val bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        try {
            val bondedDevices = bluetoothManager.adapter?.bondedDevices.orEmpty()
            bondedDevices.forEach { bluetoothDevice ->
                devices[bluetoothDevice.address] = bluetoothDevice.name.orEmpty()
            }
        } catch (e: SecurityException) {
            Toast.makeText(applicationContext,"Problem adding device -- check permission given: Bluetooth Connect", Toast.LENGTH_SHORT).show()
        }

        val deviceAddressList = devices.keys.toList()

        val items = deviceAddressList.map { key -> "${devices[key]} <${key}>" }.toTypedArray()

        // User chooses
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_choose_device))
            .setSingleChoiceItems(
                items,
                -1
            ) { dialog, which ->
                val address = deviceAddressList[which]
                val name = devices[address].orEmpty()

                settings.addDevice(deviceType, address, name)

                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun showDisclaimer1() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.disclaimer1_title))
            .setMessage(getString(R.string.disclaimer1_message))
            .setPositiveButton(getString(R.string.disclaimer1_accept)
            ) { _, _ ->
                showDisclaimer2()
            }
            .setNegativeButton(getString(R.string.disclaimer1_cancel)) { _, _ -> run {} }
            .show()
    }

    private fun showDisclaimer2() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.disclaimer2_title))
            .setMessage(getString(R.string.disclaimer2_message))
            .setPositiveButton(getString(R.string.disclaimer2_accept)
            ) { _, _ ->
                chooseDeviceType()
            }
            .setNegativeButton(getString(R.string.disclaimer2_cancel)) { _, _ -> run {} }
            .show()
    }

    private fun promptRemoveDevice(device: Device) {
        AlertDialog.Builder(this)
            .setTitle("Remove?")
            .setMessage("Remove device: ${device.name}?")
            .setPositiveButton("Remove") { _, _ ->
                settings.removeDevice(device.address)
            }
            .setNegativeButton("Cancel") { _, _ -> run {} }
            .show()
    }

    private fun configure() {
        addDevice()
    }

    private fun run(level: String) {
        if (settings.devices.isEmpty()) {
            configure()
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
                configure()
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