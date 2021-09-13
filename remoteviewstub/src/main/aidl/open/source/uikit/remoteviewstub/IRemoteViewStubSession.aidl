// IRemoteViewStubSession.aidl
package open.source.uikit.remoteviewstub;
import android.view.Surface;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.os.IBinder;
import android.content.res.Configuration;

// Declare any non-default types here with import statements

interface IRemoteViewStubSession {
    void dispatchTouchEvent(in MotionEvent event);

    void setSurface(in Surface surface);

    void setWindowToken(in IBinder token);

    void onConfigurationChanged(in Configuration configuration);

    void onSizeChanged(int width, int height);
}