package com.dwl.mutcube.storage

import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenerationForegroundServiceTest {
    @Suppress("DEPRECATION")
    @Test
    fun generationServiceCanProtectAndReleaseBackgroundWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        GenerationForegroundService.start(context)
        val running = eventually {
            context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == GenerationForegroundService::class.java.name && it.foreground }
        }
        assertTrue("generation service did not enter foreground", running)

        assertTrue(GenerationForegroundService.stop(context))
        assertTrue(eventually {
            context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
                .none { it.service.className == GenerationForegroundService::class.java.name }
        })
    }

    private fun eventually(predicate: () -> Boolean): Boolean {
        repeat(30) {
            if (predicate()) return true
            Thread.sleep(100)
        }
        return false
    }
}
