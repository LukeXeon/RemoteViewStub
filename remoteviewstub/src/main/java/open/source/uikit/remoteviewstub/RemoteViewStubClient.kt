package open.source.uikit.remoteviewstub

import android.graphics.SurfaceTexture
import android.os.IBinder
import android.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

internal class RemoteViewStubClient(
    private val view: RemoteViewStub,
    val layoutId: Int
) : IRemoteViewStubClient.Stub(),
    TextureView.SurfaceTextureListener,
    IBinder.DeathRecipient {

    var session: IRemoteViewStubSession? = null

    var surface: Surface? = null
        private set

    val windowToken: IBinder?
        get() = view.windowToken

    val width: Int
        get() = view.width

    val height: Int
        get() = view.height

    override fun dispatchUnhandledTouchEvent(event: MotionEvent) {
        GlobalScope.launch(Dispatchers.Main) {
            dispatchUnhandledInputEvent(view, event)
            event.recycle()
        }
    }

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture?,
        width: Int,
        height: Int
    ) {
        surface = Surface(surfaceTexture)
        session?.runCatching { setSurface(surface) }
    }

    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture?,
        width: Int,
        height: Int
    ) {

    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture?): Boolean {
        surface?.release()
        surface = null
        session?.runCatching { setSurface(null) }
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture?) {

    }

    override fun binderDied() {
        GlobalScope.launch(Dispatchers.Main) {
            session = null
        }
    }


    companion object {
        @Suppress("DiscouragedPrivateApi")
        private val dispatchUnhandledInputEvent by lazy<(View, MotionEvent) -> Unit> {
            val getViewRootImplMethod = View::class.java
                .runCatching {
                    getDeclaredMethod("getViewRootImpl")
                        .apply {
                            isAccessible = true
                        }
                }.getOrNull()
            val dispatchUnhandledInputEventMethod = getViewRootImplMethod
                ?.returnType
                ?.runCatching {
                    getDeclaredMethod(
                        "dispatchUnhandledInputEvent",
                        InputEvent::class.java
                    ).apply {
                        isAccessible = true
                    }
                }?.getOrNull()
            return@lazy { view, ev ->
                if (getViewRootImplMethod != null && dispatchUnhandledInputEventMethod != null) {
                    dispatchUnhandledInputEventMethod.invoke(getViewRootImplMethod.invoke(view), ev)
                }
            }
        }
    }
}