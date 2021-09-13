package open.source.uikit.remoteviewstub

import android.content.Context
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.os.Binder
import android.os.IBinder
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView

class RemoteViewStub @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr) {

    private val client: RemoteViewStubManager.Client

    init {
        val array = context.obtainStyledAttributes(attrs, R.styleable.RemoteViewStub)
        val layoutId = array.getResourceId(R.styleable.RemoteViewStub_android_layout, 0)
        client = RemoteViewStubManager.getInstance(context)
            .newClient(this, layoutId)
        array.recycle()
        super.setSurfaceTextureListener(client.listener)
        super.setOpaque(false)
    }

    private val session: IRemoteViewStubSession?
        get() = client.session

    override fun getSurfaceTextureListener(): SurfaceTextureListener? {
        return null
    }

    override fun setOpaque(opaque: Boolean) {
        throw UnsupportedOperationException()
    }

    override fun setSurfaceTextureListener(listener: SurfaceTextureListener?) {
        throw UnsupportedOperationException()
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (super.dispatchTouchEvent(event)) {
            return true
        }
        return session?.runCatching { dispatchTouchEvent(event) }
            ?.isSuccess ?: false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        session?.runCatching { onSizeChanged(w, h) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        session?.runCatching { setWindowToken(windowToken) }
    }

    override fun onDetachedFromWindow() {
        session?.runCatching { setWindowToken(null) }
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        session?.runCatching { onConfigurationChanged(newConfig) }
    }

}