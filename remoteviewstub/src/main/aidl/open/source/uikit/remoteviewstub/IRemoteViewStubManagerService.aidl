// IRemoteViewStubSession.aidl
package open.source.uikit.remoteviewstub;
import android.view.Surface;
import android.view.MotionEvent;
import android.os.IBinder;
import open.source.uikit.remoteviewstub.IRemoteViewStubSession;
import open.source.uikit.remoteviewstub.IRemoteViewStubClient;

// Declare any non-default types here with import statements

interface IRemoteViewStubManagerService {
    IRemoteViewStubSession openSession(
        int layoutId,
        in IRemoteViewStubClient client,
        in IBinder windowToken,
        in Surface surface,
        int width,
        int height
    );
}