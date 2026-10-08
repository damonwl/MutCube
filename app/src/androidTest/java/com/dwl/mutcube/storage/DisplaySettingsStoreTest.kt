package com.dwl.mutcube.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplaySettingsStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun clearPreferences() {
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun settingsPersistAcrossStoreInstancesAndClampTextScale() = runBlocking {
        DisplaySettingsStore(context).write(DisplaySettings(ThemeMode.DARK, 2f, true, false))

        val restored = DisplaySettingsStore(context).settings.first()
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertEquals(1.3f, restored.textScale)
        assertEquals(true, restored.showMessageTime)
        assertFalse(restored.autoScroll)
    }
}
