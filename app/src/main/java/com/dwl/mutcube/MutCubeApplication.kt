package com.dwl.mutcube

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MutCubeApplication : Application() {
    val container by lazy { AppContainer(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            try {
                container.templateRuntime.initialize()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // A failed template initialization must not crash unrelated chat/settings.
                // Avoid writing record contents or provider details into system logs.
                Log.e("MutCube", "Template initialization failed: ${failure.javaClass.simpleName}")
            }
        }
    }
}
