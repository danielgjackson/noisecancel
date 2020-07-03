package dev.danjackson.noisecancel

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class TileServiceNc10 : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickStart::class.java)
        intent.putExtra("level", "10")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(intent)
    }
}
