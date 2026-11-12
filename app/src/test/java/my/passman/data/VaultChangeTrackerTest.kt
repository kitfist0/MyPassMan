package my.passman.data

import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Clock
import kotlin.time.Instant

class VaultChangeTrackerTest {
    private val database = mockk<AppDatabase>()
    private var storedChangedAt: Long? = null
    private var now = 10_000L

    private val vaultStateDao =
        object : VaultStateDao {
            override suspend fun getChangedAt(): Long? = storedChangedAt

            override suspend fun upsert(state: VaultState) {
                storedChangedAt = state.changedAt
            }
        }
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(now)
        }

    private val tracker = VaultChangeTracker(database, vaultStateDao, clock)

    @BeforeEach
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun changedAt_isZeroForUntouchedVault() =
        runTest {
            assertEquals(0L, tracker.changedAt())
        }

    @Test
    fun recordChange_runsBlockAndStampsCurrentTime() =
        runTest {
            val result = tracker.recordChange { "done" }

            assertEquals("done", result)
            assertEquals(now, tracker.changedAt())
        }

    @Test
    fun recordChange_staysStrictlyIncreasing_whenClockGoesBack() =
        runTest {
            storedChangedAt = 50_000L

            tracker.recordChange { }

            assertEquals(50_001L, tracker.changedAt())
        }

    @Test
    fun recordChange_withinSameMillisecond_stillMoves() =
        runTest {
            tracker.recordChange { }
            tracker.recordChange { }

            assertEquals(now + 1, tracker.changedAt())
        }

    @Test
    fun recordChange_failedBlock_doesNotStamp() =
        runTest {
            storedChangedAt = 5L

            assertThrows<IllegalStateException> { tracker.recordChange { error("boom") } }

            assertEquals(5L, tracker.changedAt())
        }

    @Test
    fun applySyncedChange_takesTheGivenTimestamp_evenIfOlder() =
        runTest {
            storedChangedAt = 50_000L

            val result = tracker.applySyncedChange(changedAt = 20_000L) { 42 }

            assertEquals(42, result)
            assertEquals(20_000L, tracker.changedAt())
        }
}
