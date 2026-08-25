package com.solidlink.db

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solidlink.common.OpaqueId
import com.solidlink.domain.Checkpoint
import com.solidlink.domain.ChunkState
import com.solidlink.domain.ChunkStatus
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SolidLinkDatabaseInstrumentedTest {
    private lateinit var database: SolidLinkDatabase
    private lateinit var repository: SolidLinkRepository

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SolidLinkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SolidLinkRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun verifiedChunkAndCheckpointAreBothDurable() = runBlocking {
        val now = Instant.parse("2026-08-25T10:00:00Z")
        val objectId = OpaqueId("object-1")
        val result = repository.persistVerifiedChunkAndCheckpoint(
            chunk = ChunkState(
                objectId = objectId,
                chunkIndex = 0,
                offset = 0,
                length = 4,
                digest = "sha256",
                state = ChunkStatus.VERIFIED,
                attempts = 1,
                durableAt = now,
            ),
            checkpoint = Checkpoint(
                objectId = objectId,
                highestDurableSequence = 7,
                verifiedRanges = listOf(0L..0L),
                stagingLength = 4,
                updatedAt = now,
            ),
        )

        assertTrue(result is com.solidlink.domain.TransitionResult.Success)
        assertEquals(1, (repository.loadChunks(objectId.value) as com.solidlink.domain.TransitionResult.Success).value.size)
        assertEquals(7, ((repository.loadCheckpoint(objectId.value) as com.solidlink.domain.TransitionResult.Success).value?.highestDurableSequence))
    }
}
