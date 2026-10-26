package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.RemoteMutationOutboxItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RemoteMutationOutboxDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun queuedMutationsAreScopedToTheirUserAndTenant() {
        val dao = database.remoteMutationOutboxDao()

        kotlinx.coroutines.runBlocking {
            dao.insert(mutation("one", "user-a", "https://school-a/api/v1"))
            dao.insert(mutation("two", "user-a", "https://school-b/api/v1"))
            dao.insert(mutation("three", "user-b", "https://school-a/api/v1"))

            assertEquals(1, dao.countFor("user-a", "https://school-a/api/v1"))
            assertEquals(
                listOf("one"),
                dao.getFor("user-a", "https://school-a/api/v1").map { it.id }
            )
        }
    }

    private fun mutation(id: String, ownerId: String, tenantUrl: String) =
        RemoteMutationOutboxItem(
            id = id,
            ownerId = ownerId,
            tenantUrl = tenantUrl,
            path = "exeats/",
            payloadJson = "{}",
            attempts = 0,
            lastError = null,
            createdAt = 1L
        )
}
