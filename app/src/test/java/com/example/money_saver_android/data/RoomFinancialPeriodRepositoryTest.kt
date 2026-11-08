package com.example.money_saver_android.data

import android.content.Context
import androidx.room.Room
import com.example.money_saver_android.data.local.ConfigDatabase
import com.example.money_saver_android.data.repository.FinancialPeriodRepository
import com.example.money_saver_android.data.repository.RoomFinancialPeriodRepository
import com.example.money_saver_android.domain.model.FinancialPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomFinancialPeriodRepositoryTest {

    private lateinit var db: ConfigDatabase
    private lateinit var repository: FinancialPeriodRepository

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, ConfigDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomFinancialPeriodRepository(db.financialPeriodDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test_financial_period_dao_insert_and_get_period_covering_date() = runBlocking {
        val period = FinancialPeriod(
            startDate = "2026-11-01",
            endDate = "2026-11-30"
        )
        repository.savePeriod(period)

        val coveringPeriod = repository.getPeriodForDate("2026-11-07")
        assertNotNull(coveringPeriod)
        assertEquals("2026-11-01", coveringPeriod?.startDate)
        assertEquals("2026-11-30", coveringPeriod?.endDate)

        val nonCoveringPeriod = repository.getPeriodForDate("2026-12-01")
        assertNull(nonCoveringPeriod)
    }

    @Test
    fun test_financial_period_dao_get_all_periods_and_delete_period() = runBlocking {
        val period1 = FinancialPeriod(startDate = "2026-10-01", endDate = "2026-10-31")
        val period2 = FinancialPeriod(startDate = "2026-11-01", endDate = "2026-11-30")

        val id1 = repository.savePeriod(period1)
        val id2 = repository.savePeriod(period2)

        val periodsBeforeDelete = repository.getAllPeriods().first()
        assertEquals(2, periodsBeforeDelete.size)

        repository.deletePeriod(id1)

        val periodsAfterDelete = repository.getAllPeriods().first()
        assertEquals(1, periodsAfterDelete.size)
        assertEquals(id2, periodsAfterDelete[0].id)
    }
}
