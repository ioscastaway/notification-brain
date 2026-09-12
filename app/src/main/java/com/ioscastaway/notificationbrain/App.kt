package com.ioscastaway.notificationbrain

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        graph.crashCollector.install()
        scope.launch {
            graph.crashCollector.importPending(graph.repository)
            graph.repository.prune()
        }
    }

    companion object {
        lateinit var graph: Graph
            private set
    }
}
