// IRemoteViewStubClient.aidl
package open.source.uikit.remoteviewstub;

// Declare any non-default types here with import statements

interface IRemoteViewStubClient {
    void dispatchUnhandledTouchEvent(in MotionEvent event);
}