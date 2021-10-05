package dev.danjackson.noisecancel

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.widget.Toast
import androidx.core.app.JobIntentService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.OutputStream
import java.util.*


enum class Level(val label: String) {
    NC_10("10"),
    NC_5("5"),
    NC_0("0"),
    OFF("off");

    override fun toString(): String {
        return this.label
    }

    companion object {
        fun fromString(string: String): Level {
            return when(string) {
                NC_10.label -> NC_10
                NC_5.label -> NC_5
                NC_0.label -> NC_0
                else -> OFF
            }
        }
    }
}


class SendService : JobIntentService() {

    override fun onHandleWork(intent: Intent) {
        val settings = Settings.getInstance(applicationContext)
        val devices = settings.findConnectedDevices()
        if (devices.isEmpty()) {
            showToast("No configured devices connected.")
        } else {
            val level = Level.fromString(intent.extras?.get("level")?.toString().orEmpty())
            for (device in devices) {
                run(device, level)
            }
        }
    }

    private val handler = Handler()
    private fun showToast(text: CharSequence?) {
        handler.post {
            Toast.makeText(this@SendService, text, Toast.LENGTH_LONG).show()
        }
    }


    private fun run(device: Device, level: Level) {
        try {
            var description = when (level) {
                Level.NC_10 -> getString(R.string.send_nc_10)
                Level.NC_5 -> getString(R.string.send_nc_5)
                Level.NC_0 -> getString(R.string.send_nc_0)
                Level.OFF -> getString(R.string.send_nc_off)
            }

            showToast(description)

            var message = when (device.type) {
                DeviceType.NC700 -> when (level) {
                    Level.NC_10 -> message700NoiseCancelling10
                    Level.NC_5 -> message700NoiseCancelling5
                    Level.NC_0 -> message700NoiseCancelling0
                    Level.OFF -> message700NoiseCancellingOff
                }
                DeviceType.QC35 -> when (level) {
                    Level.NC_10 -> messageQc35NoiseCancellingHigh
                    Level.NC_5 -> messageQc35NoiseCancellingMedium   // ?? does this do something on QC35ii ??
                    Level.NC_0 -> messageQc35NoiseCancellingLow
                    Level.OFF -> messageQc35NoiseCancellingOff
                }
                DeviceType.QCE -> when (level) {
                    Level.NC_10 -> messageQcEModeQuiet
                    Level.NC_5 -> messageQcEModeUser1       // not NC_5
                    Level.NC_0 -> messageQcEModeAware
                    Level.OFF -> messageQcEModeUser2        // not OFF
                }
                DeviceType.QC45 -> when (level) {
                    Level.NC_10 -> messageQc45ModeQuiet
                    Level.NC_5 -> messageQc45Mode2  // not NC_5: doesn't have a user mode 1, but device says "quiet" without seemingly changing mode?
                    Level.NC_0 -> messageQc45ModeAware
                    Level.OFF -> messageQc45ModeTest // messageQc45ModeAware  // not OFF: not found a way to disable all NC functions yet
                }
            }
            send(device, message, defaultSendTimes)
        } catch (e: Exception) {
            showToast("ERROR: ${e.message}")
        }
    }


    private fun send(device: Device, messageBuffer: ByteArray, @Suppress("SameParameterValue") multiple: Int) {
        println("RUN: Start...")

        val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            ?: throw IOException("No Bluetooth adapter.")

        if (!adapter.isEnabled) {
            throw IOException("Bluetooth adapter not enabled.")
        }

        println("RUN: Getting remote device: $device.address")
        val remoteDevice = adapter.getRemoteDevice(device.address)

        println("RUN: Creating socket to: $uuidSpp")
        val socket: BluetoothSocket?
        try {
            socket = remoteDevice.createRfcommSocketToServiceRecord(uuidSpp)
        } catch (e: IOException) {
            throw IOException("Problem creating socket", e)
        }

        try {
            println("RUN: Connecting...")
            try {
                socket.connect()
            } catch (e: IOException) {
                throw IOException("Problem connecting", e)
            }

            println("RUN: Creating output stream...")
            val stream: OutputStream?
            try {
                stream = socket.outputStream
            } catch (e: IOException) {
                throw IOException("Problem creating output stream", e)
            }

            val hexString = messageBuffer.joinToString(separator = " ") { b -> String.format("%02X", b) }
            println("RUN: Writing message ($multiple times): $hexString")
            try {
                repeat(multiple) {
                    stream.write(messageBuffer)
                    stream.flush()
                    runBlocking {
                        delay(333)
                    }
                }
            } catch (e: IOException) {
                throw IOException("Problem writing message", e)
            }

        } finally {
            try {
                println("RUN: Closing socket...")
                socket.close()
            } catch (e: IOException) {
                println("WARNING: Problem closing socket: $e")
            }
        }
        println("RUN: Done!")
    }

    companion object {
        // Bose Noise Cancelling Headphones 700
        // Noise cancellation `enabled` (0=off, 1=on), if enabled, on `level` (0-10):
        //   Send: 0x01 0x05 0x02 0x02 (10-level) (enabled)
        // When toggling enabled on or off, device always starts at level=10 regardless of level sent.  Send a second packet.
        //   Response: 0x01 0x05 0x03 0x03 0x0b (10-level) (enabled)
        //val messageBuffer = byteArrayOf(0x01, 0x05, 0x02, 0x02, (10 - level).toByte(), (if (noiseCancelling) 1 else 0).toByte())
        val message700NoiseCancellingOff = byteArrayOf(0x01, 0x05, 0x02, 0x02, 0x00, 0x00)
        val message700NoiseCancelling0 = byteArrayOf(0x01, 0x05, 0x02, 0x02, 0x0A, 0x01)
        val message700NoiseCancelling5 = byteArrayOf(0x01, 0x05, 0x02, 0x02, 0x05, 0x01)
        val message700NoiseCancelling10 = byteArrayOf(0x01, 0x05, 0x02, 0x02, 0x00, 0x01)
        
        // Bose QC35
        val messageQc35NoiseCancellingOff = byteArrayOf(0x01, 0x06, 0x02, 0x01, 0x00)
        val messageQc35NoiseCancellingHigh = byteArrayOf(0x01, 0x06, 0x02, 0x01, 0x01)
        val messageQc35NoiseCancellingMedium = byteArrayOf(0x01, 0x06, 0x02, 0x01, 0x02) // ?? does this do something on QC35ii ??
        val messageQc35NoiseCancellingLow = byteArrayOf(0x01, 0x06, 0x02, 0x01, 0x03)
        // QC35-RESPONSE:   0x01, 0x06, 0x03, 0x02, <level>, 0x0b

        // Bose QC Earbuds
        val messageQcEModeQuiet = byteArrayOf(0x1f, 0x03, 0x05, 0x02, 0x00, 0x01)   // mode 0 = quiet (NC_10)
        val messageQcEModeAware = byteArrayOf(0x1f, 0x03, 0x05, 0x02, 0x01, 0x01)   // mode 1 = aware (NC_0)
        val messageQcEModeUser1 = byteArrayOf(0x1f, 0x03, 0x05, 0x02, 0x02, 0x01)   // mode 2 = user-1 (used for NC_5)
        val messageQcEModeUser2 = byteArrayOf(0x1f, 0x03, 0x05, 0x02, 0x03, 0x01)   // mode 3 = user-2 (used for NC_OFF)

        // Bose QC45
        val messageQc45ModeQuiet = byteArrayOf(0x1f, 0x03, 0x05, 0x02, 0x00, 0x01)   // mode 0 = quiet (NC_10)
        val messageQc45ModeAware = byteArrayOf(0x1f, 0x03, 0x05, 0x02, 0x01, 0x01)   // mode 1 = aware (NC_0, also used for NC_OFF)
        val messageQc45Mode2 = byteArrayOf(0x1f, 0x03, 0x05, 0x02, 0x02, 0x01)   // mode 2 = doesn't have a user mode, but device says "quiet" without seemingly changing mode? (used for NC_5)
        val messageQc45ModeTest = byteArrayOf(0x1f, 0x03, 0x05, 0x02, 0x01, 0x01)    // just use aware as NC_OFF command not known to exist

        private val uuidSpp: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val defaultSendTimes = 3

        private const val JOB_ID = 1

        fun enqueueWork(context: Context, intent: Intent) {
            // val data = intent.data
            // val level = intent.extras?.get("level")?.toString().orEmpty()
            enqueueWork(context, SendService::class.java, JOB_ID, intent)
        }
    }

}
