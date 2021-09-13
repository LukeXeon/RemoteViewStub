package open.source.uikit.remoteviewstub.sample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast

class MainActivity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = SystemClock.uptimeMillis()
        setContentView(R.layout.layout_web_view)
        Toast.makeText(this, "time=" + (SystemClock.uptimeMillis() - start), Toast.LENGTH_SHORT)
            .show()
    }
}