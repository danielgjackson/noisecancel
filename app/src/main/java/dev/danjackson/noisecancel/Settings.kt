package dev.danjackson.noisecancel

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.AudioManager
import android.os.Build
import androidx.lifecycle.MutableLiveData
import java.lang.IllegalArgumentException

enum class DeviceType(val label: String) {
    NC700("700"),
    QC35("qc35");

    override fun toString(): String {
        return this.label
    }

    companion object {
        fun fromString(string: String): DeviceType {
            return when(string) {
                NC700.label -> NC700
                QC35.label -> QC35
                else -> NC700
            }
        }
    }
}

data class Device(val type: DeviceType, val address: String, val name: String) {
    override fun toString(): String {
        return listOf(type.toString(), address, name).joinToString(separator = "\t")
    }

    companion object {
        fun fromString(input: String): Device {
            val parts = input.split("\t", ignoreCase = false, limit = 3)
            if (parts.count() < 3) throw IllegalArgumentException("Device string not well formed")
            val deviceType = DeviceType.fromString(parts[0])
            val address = parts[1]
            val name = parts[2]
            return Device(deviceType, address, name)
        }
    }
}

class Settings(private val applicationContext: Context) {

    private var sharedPreferences: SharedPreferences = applicationContext.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private var sharedPreferencesListener: OnSharedPreferenceChangeListener? = null

    var devicesData = MutableLiveData<List<Device>?>()
    var devices = mutableMapOf<String, Device>()

    // Determine that the disclaimer must have been agreed to if a device has been added
    fun agreedDisclaimer(): Boolean {
        return devices.count() != 0
    }

    fun addDevice(deviceType: DeviceType, address: String, name: String) {
        val device = Device(deviceType, address, name)
        devices[device.address] = device
        savePreferences()
    }

    fun removeDevice(address: String) {
        devices.remove(address)
        savePreferences()
    }

    private fun savePreferences() {
        val deviceStrings = devices.map { it.value.toString() }.toSet()

        val editor = sharedPreferences.edit()
        editor.putStringSet("devices", deviceStrings)
        editor.apply()
    }

    private fun preferencesUpdated() {
        // Resetting list from scratch
        devices.clear()

        // Add all devices from preferences
        val deviceStrings = sharedPreferences.getStringSet("devices", setOf<String>()).orEmpty()
        for (deviceString in deviceStrings) {
            try {
                val device = Device.fromString(deviceString)
                devices[device.address] = device
            } catch(e: Throwable) {
                // Problem parsing device string, ignore entry
                println("ERROR: Problem parsing device entry: $deviceString")
                continue
            }
        }

        // Set live data
        devicesData.postValue(devices.values.toList())
    }

    // TODO: Use a different technique as AudioManager.getDevices() is too limited on older API versions
    fun findConnectedDevices(): Set<Device> {
        val connected = mutableSetOf<Device>()
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        // Ignore everything if Bluetooth is not enabled
        if (bluetoothAdapter?.isEnabled == true) {
            // If we're on a recent version, check the list of connected devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val audioDeviceInfo = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (devInfo in audioDeviceInfo) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // Can match on address
                        if (devices.containsKey(devInfo.address)) connected.add(devices[devInfo.address]!!)
                    } else {
                        // Can only match on name (this is not good, what if the name has changed or names are not unique?)
                        for (device in devices) {
                            if (device.value.name == devInfo.productName) {
                                connected.add(device.value)
                            }
                        }
                    }
                }
            } else {
                // Can't use that method to determine what's connected, have to try all devices (should only be one or two, but not great)
                connected.addAll(devices.values)
            }
        }
        return connected
    }

    // Singleton
    companion object {
        private var singleInstance: Settings? = null
        fun getInstance(applicationContext: Context): Settings {
            synchronized(this) {
                if (singleInstance == null) {
                    singleInstance = Settings(applicationContext)
                }
                return singleInstance!!
            }
        }
    }


    private fun migratePreferences() {
        // If old preference exists, convert to new type and remove old
        val address = sharedPreferences.getString("device_address", null).orEmpty()
        if (address.isNotEmpty()) {
            val name = sharedPreferences.getString("device_name", null).orEmpty()
            val deviceType = if (sharedPreferences.getString("device_type", null)?.equals("qc35") == true) DeviceType.QC35 else DeviceType.NC700
            addDevice(deviceType, address, name)
            val editor = sharedPreferences.edit()
            editor.remove("device_address")
            editor.remove("device_name")
            editor.remove("device_type")
            editor.apply()
        }
    }


    init {
        preferencesUpdated()
        sharedPreferencesListener = OnSharedPreferenceChangeListener { _, _ ->
            preferencesUpdated()
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
        migratePreferences()
    }

}
