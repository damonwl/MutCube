package com.dwl.mutcube.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCredentialStoreTest {
    private lateinit var context: Context
    private lateinit var preferencesName: String
    private lateinit var store: AndroidKeystoreCredentialStore

    @Before
    fun createStore() {
        context = ApplicationProvider.getApplicationContext()
        val suffix = UUID.randomUUID().toString()
        preferencesName = "credential-test-$suffix"
        store = AndroidKeystoreCredentialStore(
            context = context,
            keyAlias = "credential-test-$suffix",
            preferencesName = preferencesName,
        )
    }

    @Test
    fun credentialIsEncryptedAndCanBeDeleted() = runBlocking {
        store.write(AndroidKeystoreCredentialStore.DEFAULT_PROVIDER_ID, "sk-secret")

        assertEquals("sk-secret", store.read(AndroidKeystoreCredentialStore.DEFAULT_PROVIDER_ID))
        val storedValues = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).all.values
        assertFalse(storedValues.any { it.toString().contains("sk-secret") })

        store.delete(AndroidKeystoreCredentialStore.DEFAULT_PROVIDER_ID)
        assertNull(store.read(AndroidKeystoreCredentialStore.DEFAULT_PROVIDER_ID))
    }
}
