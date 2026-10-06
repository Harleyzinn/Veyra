package app.veyra.android
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

class CaptureTile : TileService() {
    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Legacy overload is used only on API 26–33.
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("capture", true)
        if(Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else startActivityAndCollapse(intent)
    }
}
