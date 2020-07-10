package dev.danjackson.noisecancel

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.widget.Toast
import androidx.core.app.JobIntentService
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
                    Level.NC_10 -> messageQc35NoiseCancelling10
                    Level.NC_5 -> messageQc35NoiseCancelling5
                    Level.NC_0 -> messageQc35NoiseCancelling0
                    Level.OFF -> messageQc35NoiseCancellingOff
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
        val device = adapter.getRemoteDevice(device.address)

        println("RUN: Creating socket to: $uuidSpp")
        val socket: BluetoothSocket?
        try {
            socket = device.createRfcommSocketToServiceRecord(uuidSpp)
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
        // Bose 700
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
        val messageQc35NoiseCancelling0 = byteArrayOf(0x01, 0x06, 0x02, 0x01, 0x03)
        val messageQc35NoiseCancelling5 = byteArrayOf(0x01, 0x06, 0x02, 0x01, 0x02) // ?
        val messageQc35NoiseCancelling10 = byteArrayOf(0x01, 0x06, 0x02, 0x01, 0x01)
        // QC35-RESPONSE:   0x01, 0x06, 0x03, 0x02, <level>, 0x0b


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
