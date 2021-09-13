package open.source.uikit.remoteviewstub

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
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
    private val activeList = HashSet<PhantomReference<RemoteViewStubClient>>()
    private val waitList = Collections.newSetFromMap(WeakHashMap<RemoteViewStubClient, Boolean>())
    private val queue = ReferenceQueue<RemoteViewStubClient>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            binder: IBinder?
        ) {
            synchronized(lock) {
                val s = IRemoteViewStubManagerService
                    .Stub
                    .asInterface(binder)
                for (client in waitList) {
                    openSession(s, client)
                }
                for (ref in activeList.toTypedArray()) {
                    val client = ref.get()
                    if (client != null) {
                        openSession(s, client)
                    } else {
                        activeList.remove(ref)
                    }
                }
                waitList.clear()
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
                    activeList.remove(ref)
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
        client: RemoteViewStubClient
    ) {
        val session = service.runCatching {
            openSession(
                client.layoutId,
                client,
                client.windowToken,
                client.surface,
                client.width,
                client.height
            )
        }.getOrNull()
        if (session != null) {
            synchronized(lock) {
                activeList.add(PhantomReference(client, queue))
            }
            client.session = session
        } else {
            synchronized(lock) {
                waitList.add(client)
            }
        }
    }

    fun openSession(
        client: RemoteViewStubClient
    ) {
        synchronized(lock) {
            val s = service
            if (s != null) {
                openSession(s, client)
            } else {
                waitList.add(client)
            }
        }
    }

    companion object {
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