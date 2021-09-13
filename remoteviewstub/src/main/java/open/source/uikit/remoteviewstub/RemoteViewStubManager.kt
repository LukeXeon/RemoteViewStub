package open.source.uikit.remoteviewstub

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.SurfaceTexture
import android.os.IBinder
import android.os.Process
import android.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.util.*
import kotlin.collections.HashSet
import kotlin.concurrent.thread

internal class RemoteViewStubManager(
    private val application: Application
) {
    private var service: IRemoteViewStubManagerService? = null
    private val lock = Any()
    private val activeSet = HashSet<PhantomReference<SessionClient>>()
    private val pendingSet = Collections.newSetFromMap(WeakHashMap<SessionClient, Boolean>())
    private val queue = ReferenceQueue<SessionClient>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            binder: IBinder?
        ) {
            synchronized(lock) {
                val s = IRemoteViewStubManagerService
                    .Stub
                    .asInterface(binder)
                for (client in pendingSet) {
                    openSession(s, client)
                }
                for (ref in activeSet.toTypedArray()) {
                    val client = ref.get()
                    if (client != null) {
                        openSession(s, client)
                    } else {
                        activeSet.remove(ref)
                    }
                }
                pendingSet.clear()
                service = s
            }
        }

        override fun onServiceDisconnected(
            name: ComponentName?
        ) {
            synchronized(lock) {
                service = null
            }
            bindService()
        }
    }

    init {
        bindService()
        thread(name = "RemoteViewStub::LifecycleMonitor") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
            while (true) {
                val ref = queue.remove()
                synchronized(lock) {
                    activeSet.remove(ref)
                }
            }
        }
    }

    private fun bindService() {
        application.bindService(
            Intent(application, RemoteViewStubManagerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun openSession(
        service: IRemoteViewStubManagerService,
        client: SessionClient
    ) {
        if (client.openSession(service)) {
            synchronized(lock) {
                activeSet.add(PhantomReference(client, queue))
            }
        } else {
            synchronized(lock) {
                pendingSet.add(client)
            }
        }
    }

    fun newClient(
        view: RemoteViewStub,
        layoutId: Int
    ): Client {
        if (layoutId == 0) {
            return NoOpClient
        }
        synchronized(lock) {
            val client = SessionClient(view, layoutId)
            val s = service
            if (s != null) {
                openSession(s, client)
            } else {
                pendingSet.add(client)
            }
            return client
        }
    }

    interface Client {
        val session: IRemoteViewStubSession?
        val listener: TextureView.SurfaceTextureListener?
    }

    private object NoOpClient : Client {
        override val session: IRemoteViewStubSession?
            get() = null
        override val listener: TextureView.SurfaceTextureListener?
            get() = null
    }

    private class SessionClient(
        private val view: RemoteViewStub,
        private val layoutId: Int
    ) : IRemoteViewStubClient.Stub(),
        TextureView.SurfaceTextureListener,
        IBinder.DeathRecipient,
        Client {

        override var session: IRemoteViewStubSession? = null

        override val listener: TextureView.SurfaceTextureListener
            get() = this

        private var surface: Surface? = null

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

        fun openSession(service: IRemoteViewStubManagerService): Boolean {
            val s = service.runCatching {
                openSession(
                    layoutId,
                    this@SessionClient,
                    view.windowToken,
                    surface,
                    view.width,
                    view.height
                )
            }.getOrNull()
            session = s
            return s != null
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
            return@lazy { view, event ->
                if (getViewRootImplMethod != null && dispatchUnhandledInputEventMethod != null) {
                    dispatchUnhandledInputEventMethod.invoke(
                        getViewRootImplMethod.invoke(view),
                        event
                    )
                }
            }
        }
        private val instanceLock = Any()
        private var instance: RemoteViewStubManager? = null
        fun getInstance(context: Context): RemoteViewStubManager {
            synchronized(instanceLock) {
                var s = instance
                if (s == null) {
                    s = RemoteViewStubManager(context.applicationContext as Application)
                    instance = s
                }
                return s
            }
        }
    }
}