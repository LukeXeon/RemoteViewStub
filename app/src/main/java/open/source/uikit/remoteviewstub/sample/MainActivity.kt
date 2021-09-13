package open.source.uikit.remoteviewstub.sample

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = SystemClock.uptimeMillis()
        setContentView(R.layout.activity_main)
        Toast.makeText(
            this,
            "time=" + (SystemClock.uptimeMillis() - start),
            Toast.LENGTH_SHORT
        ).show()
    }
}