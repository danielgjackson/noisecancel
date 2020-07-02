package dev.danjackson.noisecancel

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

/*
val intent = Intent()
intent.action = "dev.danjackson.noisecancel.SEND"
intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
sendBroadcast(intent)
*/

class QuickStart : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, SendService::class.java)
        SendService.enqueueWork(applicationContext, intent)

        finish()
    }
}
