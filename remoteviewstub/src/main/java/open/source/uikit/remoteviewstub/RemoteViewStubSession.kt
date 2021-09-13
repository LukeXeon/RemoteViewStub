package open.source.uikit.remoteviewstub

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.os.*
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.core.os.HandlerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.android.HandlerDispatcher
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

    private class SurfaceOwner(
        private val surface: Surface,
        private val dispatcher: HandlerDispatcher
    ) {
        private var isLockCanvas: Boolean = false

        suspend fun waitLockCanvas(): Canvas? {
            return withContext(dispatcher.immediate) {
                while (isLockCanvas) {
                    if (surface.isValid) {
                        yield()
                    } else {
                        return@withContext null
                    }
                }
                val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    surface.lockHardwareCanvas()
                } else {
                    surface.lockCanvas(null)
                }
                isLockCanvas = true
                return@withContext canvas
            }
        }

        suspend fun unlockCanvasAndPost(canvas: Canvas) {
            withContext(dispatcher.immediate) {
                surface.unlockCanvasAndPost(canvas)
                isLockCanvas = false
            }
        }
    }

    private class HostView(context: Context) : ViewGroup(context) {

        val content: View?
            get() {
                return if (childCount > 0) {
                    getChildAt(0)
                } else {
                    null
                }
            }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val content = content
            if (content != null) {
                content.measure(widthMeasureSpec, heightMeasureSpec)
                setMeasuredDimension(content.measuredWidth, content.measuredHeight)
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }

        override fun dispatchDraw(canvas: Canvas?) {}

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            content?.layout(l, t, r, b)
        }
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
    private val hostView = HostView(context)

    @Volatile
    private var surfaceOwner: SurfaceOwner? = null
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
            updateSurface(surface)
            window.isClippingEnabled = false
            window.contentView = hostView
            hostView.viewTreeObserver.addOnDrawListener(this@RemoteViewStubSession)
            hostView.viewTreeObserver.addOnScrollChangedListener(this@RemoteViewStubSession)
            val start = SystemClock.uptimeMillis()
            LayoutInflater.from(context).inflate(layoutId, hostView, true)
            Log.d(TAG, "inflate time=" + (SystemClock.uptimeMillis() - start))
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
    private fun updateSurface(surface: Surface?) {
        surfaceOwner = if (surface != null)
            SurfaceOwner(surface, renderThreadDispatcher)
        else
            null
        hostView.invalidate()
    }

    override fun binderDied() {
        GlobalScope.launch(Dispatchers.Main) {
            window.dismiss()
        }
    }

    override fun onDraw() {
        GlobalScope.launch(renderThreadDispatcher) {
            val holder = surfaceOwner ?: return@launch
            val canvas = holder.waitLockCanvas() ?: return@launch
            withContext(Dispatchers.Main) {
                val start = SystemClock.uptimeMillis()
                hostView.content?.draw(canvas)
                Log.d(TAG, "onDraw=" + (SystemClock.uptimeMillis() - start))
            }
            holder.unlockCanvasAndPost(canvas)
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
            updateSurface(surface)
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