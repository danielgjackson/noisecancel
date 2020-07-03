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
            var description = getString(R.string.send_nc_off)
            var message = messageNoiseCancellingOff

            when (level) {
                "0" -> {
                    description = getString(R.string.send_nc_0)
                    message = messageNoiseCancelling0
                }
                "5" -> {
                    description = getString(R.string.send_nc_5)
                    message = messageNoiseCancelling5
                }
                "10" -> {
                    description = getString(R.string.send_nc_10)
                    message = messageNoiseCancelling10
                }
            }

            showToast(description)

            val sharedPreferences = getSharedPreferences("preferences", Context.MODE_PRIVATE)
            val deviceAddress = sharedPreferences.getString("device_address", null)

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

            //val messageBuffer = byteArrayOf(0x01, 0x05, 0x02, 0x02, (10 - level).toByte(), (if (noiseCancelling) 1 else 0).toByte())
            val hexString = SendService.messageNoiseCancellingOff.joinToString(separator = " ") { b -> String.format("%02X", b) }
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
        // Noise cancellation `enabled` (0=off, 1=on), if enabled, on `level` (0-10):
        //   Send: 0x01 0x05 0x02 0x02 (10-level) (enabled)
        // When toggling enabled on or off, device always starts at level=10 regardless of level sent.  Send a second packet.
        //   Response: 0x01 0x05 0x03 0x03 0x0b (10-level) (enabled)
        //val messageBuffer = byteArrayOf(0x01, 0x05, 0x02, 0x02, (10 - level).toByte(), (if (noiseCancelling) 1 else 0).toByte())
        val messageNoiseCancellingOff = byteArrayOf(0x01, 0x05, 0x02, 0x02, 0x00, 0x00)
        val messageNoiseCancelling0 = byteArrayOf(0x01, 0x05, 0x02, 0x02, 0x0A, 0x01)
        val messageNoiseCancelling5 = byteArrayOf(0x01, 0x05, 0x02, 0x02, 0x05, 0x01)
        val messageNoiseCancelling10 = byteArrayOf(0x01, 0x05, 0x02, 0x02, 0x00, 0x01)
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
