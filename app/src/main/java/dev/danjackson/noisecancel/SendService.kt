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

class SendService : JobIntentService() {

    override fun onHandleWork(intent: Intent) {
        // val address = intent.getStringExtra("address")?.toString()
        // val data = intent.data

        val level = intent.extras?.get("level")?.toString().orEmpty()

        try {
            val sharedPreferences = getSharedPreferences("preferences", Context.MODE_PRIVATE)
            val deviceAddress = sharedPreferences.getString("device_address", null)
            val deviceType = sharedPreferences.getString("device_type", null)

            var description = when (level) {
                "0" -> getString(R.string.send_nc_0)
                "5" -> getString(R.string.send_nc_5)
                "10" -> getString(R.string.send_nc_10)
                else -> getString(R.string.send_nc_off)
            }

            var message = when (deviceType) {
                "qc35" -> when (level) {
                    "0" -> messageQc35NoiseCancelling0
                    "5" -> messageQc35NoiseCancelling5
                    "10" -> messageQc35NoiseCancelling10
                    else -> messageQc35NoiseCancellingOff
                }
                "all" -> when (level) {
                    "0" -> messageAllNoiseCancelling0
                    "5" -> messageAllNoiseCancelling5
                    "10" -> messageAllNoiseCancelling10
                    else -> messageAllNoiseCancellingOff
                }
                else -> when (level) {  // 700
                    "0" -> message700NoiseCancelling0
                    "5" -> message700NoiseCancelling5
                    "10" -> message700NoiseCancelling10
                    else -> message700NoiseCancellingOff
                }
            }

            showToast(description)

            run(deviceAddress, message, defaultSendTimes)
        } catch (e: Exception) {
            showToast("ERROR: ${e.message}")
        }
    }

    private val handler = Handler()
    private fun showToast(text: CharSequence?) {
        handler.post {
            Toast.makeText(this@SendService, text, Toast.LENGTH_LONG).show()
        }
    }

    private fun run(address: String?, messageBuffer: ByteArray, @Suppress("SameParameterValue") multiple: Int) {
        println("RUN: Start...")

        if (address == null || address.isEmpty()) {
            throw IOException("Device not specified.")
        }

        val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            ?: throw IOException("No Bluetooth adapter.")

        if (!adapter.isEnabled) {
            throw IOException("Bluetooth adapter not enabled.")
        }

        println("RUN: Getting remote device: $address")
        val device = adapter.getRemoteDevice(address)

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

        // Experimental and risky combined QC35+700 messages
        val messageAllNoiseCancellingOff = byteArrayOf(0x01, 0x06, 0x02, 0x01, 0x00, 0x01, 0x05, 0x02, 0x02, 0x00, 0x00)
        val messageAllNoiseCancelling0 = byteArrayOf(0x01, 0x06, 0x02, 0x01, 0x03, 0x01, 0x05, 0x02, 0x02, 0x0A, 0x01)
        val messageAllNoiseCancelling5 = byteArrayOf(0x01, 0x06, 0x02, 0x01, 0x02, 0x01, 0x05, 0x02, 0x02, 0x05, 0x01)
        val messageAllNoiseCancelling10 = byteArrayOf(0x01, 0x06, 0x02, 0x01, 0x01, 0x01, 0x05, 0x02, 0x02, 0x00, 0x01)

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
