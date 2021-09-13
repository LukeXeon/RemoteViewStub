package open.source.uikit.remoteviewstub

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.os.*
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.core.os.HandlerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher

internal class RemoteViewStubSession(
    context: Context,
    @LayoutRes
    layoutId: Int,
    client: IRemoteViewStubClient,
    token: IBinder?,
    surface: Surface?,
    width: Int,
    height: Int
) : IRemoteViewStubSession.Stub(),
    ViewTreeObserver.OnDrawListener,
    ViewTreeObserver.OnScrollChangedListener,
    IBinder.DeathRecipient {

    private class SurfaceHolder(val surface: Surface) {
        var isLockCanvas: Boolean = false
    }

    private val window = PopupWindow()
    private val renderThread = HandlerThread(
        toString(),
        Process.THREAD_PRIORITY_BACKGROUND
    ).apply { start() }
    private val callbackList = RemoteCallbackList<IRemoteViewStubClient>()
    private val renderThreadDispatcher = HandlerCompat
        .createAsync(renderThread.looper)
        .asCoroutineDispatcher()
    private val hostView = FrameLayout(context)

    @Volatile
    private var surfaceHolder: SurfaceHolder? = null
        set(value) {
            field = value
            if (value != null) {
                hostView.invalidate()
            }
        }

    init {
        callbackList.register(client)
        client.asBinder().linkToDeath(this, 0)
        GlobalScope.launch(Dispatchers.Main) {
            window.width = width
            window.height = height
            setSurfaceInternal(surface)
            window.isClippingEnabled = false
            window.contentView = hostView
            hostView.viewTreeObserver.addOnDrawListener(this@RemoteViewStubSession)
            hostView.viewTreeObserver.addOnScrollChangedListener(this@RemoteViewStubSession)
            LayoutInflater.from(context).inflate(layoutId, hostView, true)
            if (token != null) {
                popupWindow(token)
            }
        }
    }

    @MainThread
    private fun popupWindow(token: IBinder) {
        window.showAtLocation(
            token,
            Gravity.CENTER,
            Int.MIN_VALUE,
            Int.MIN_VALUE
        )
    }

    @MainThread
    private fun setSurfaceInternal(surface: Surface?) {
        surfaceHolder = if (surface != null) SurfaceHolder(surface) else null
    }

    override fun binderDied() {
        GlobalScope.launch(Dispatchers.Main) {
            window.dismiss()
        }
    }

    override fun onDraw() {
        GlobalScope.launch(renderThreadDispatcher) {
            val holder = surfaceHolder
            if (holder != null && holder.surface.isValid) {
                while (holder.isLockCanvas) {
                    yield()
                }
                val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    holder.surface.lockHardwareCanvas()
                } else {
                    holder.surface.lockCanvas(null)
                }
                holder.isLockCanvas = true
                withContext(Dispatchers.Main) {
                    val start = SystemClock.uptimeMillis()
                    try {
                        hostView.draw(canvas)
                    } finally {
                        Log.d(TAG, "onPreDraw=" + (SystemClock.uptimeMillis() - start))
                    }
                }
                holder.surface.unlockCanvasAndPost(canvas)
                holder.isLockCanvas = false
            }
        }
    }

    override fun onScrollChanged() {
        onDraw()
    }

    override fun dispatchTouchEvent(event: MotionEvent) {
        GlobalScope.launch(Dispatchers.Main) {
            if (!hostView.dispatchTouchEvent(event)) {
                withContext(Dispatchers.IO) {
                    if (callbackList.beginBroadcast() == 1) {
                        callbackList.getBroadcastItem(0)
                            .runCatching { dispatchUnhandledTouchEvent(event) }
                        callbackList.finishBroadcast()
                        event.recycle()
                        return@withContext
                    }
                }
            }
            event.recycle()
        }
    }

    override fun setSurface(surface: Surface?) {
        GlobalScope.launch(Dispatchers.Main) {
            setSurfaceInternal(surface)
        }
    }

    override fun onConfigurationChanged(configuration: Configuration?) {
        GlobalScope.launch(Dispatchers.Main) {
            hostView.dispatchConfigurationChanged(configuration)
        }
    }

    override fun setWindowToken(token: IBinder?) {
        GlobalScope.launch(Dispatchers.Main) {
            window.dismiss()
            if (token != null) {
                popupWindow(token)
            }
        }
    }

    override fun onSizeChanged(width: Int, height: Int) {
        GlobalScope.launch(Dispatchers.Main) {
            if (window.isShowing) {
                window.update(width, height)
            } else {
                window.width = width
                window.height = height
            }
        }
    }

    companion object {
        private const val TAG = "RemoteViewStubSession"

        private val showAtLocationMethod = PopupWindow::class.java
            .getDeclaredMethod(
                "showAtLocation",
                IBinder::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            .apply {
                isAccessible = true
            }


        internal fun PopupWindow.showAtLocation(token: IBinder, gravity: Int, x: Int, y: Int) {
            showAtLocationMethod.invoke(this, token, gravity, x, y)
        }
    }

}