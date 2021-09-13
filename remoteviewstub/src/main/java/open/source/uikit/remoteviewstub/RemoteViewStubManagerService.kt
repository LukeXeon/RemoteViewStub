package open.source.uikit.remoteviewstub

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.Surface
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal class RemoteViewStubManagerService : Service() {

    override fun onBind(intent: Intent?): IBinder {
        return object : IRemoteViewStubManagerService.Stub() {
            override fun openSession(
                layoutId: Int,
                client: IRemoteViewStubClient,
                windowToken: IBinder?,
                surface: Surface?,
                width: Int,
                height: Int
            ): IRemoteViewStubSession {
                return newSession(
                    layoutId,
                    client,
                    windowToken,
                    surface,
                    width,
                    height
                )
            }
        }
    }

    private fun newSession(
        layoutId: Int,
        client: IRemoteViewStubClient,
        windowToken: IBinder?,
        surface: Surface?,
        width: Int,
        height: Int
    ): IRemoteViewStubSession {
        return RemoteViewStubSession(
            this,
            layoutId,
            client,
            windowToken,
            surface,
            width,
            height
        )
    }
}