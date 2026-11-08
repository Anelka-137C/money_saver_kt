package com.example.money_saver_android.domain

import com.example.money_saver_android.data.repository.FinancialPeriodRepository
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.usecase.PeriodResult
import com.example.money_saver_android.domain.usecase.PeriodUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePeriodRepositoryForUseCase : FinancialPeriodRepository {
    private val periods = mutableListOf<FinancialPeriod>()
    private val _allPeriods = MutableStateFlow<List<FinancialPeriod>>(emptyList())
    private val _latestPeriod = MutableStateFlow<FinancialPeriod?>(null)

    override suspend fun savePeriod(period: FinancialPeriod): Long {
        val newId = if (period.id == 0L) (periods.size + 1).toLong() else period.id
        val newPeriod = period.copy(id = newId)
        periods.removeAll { it.id == newId }
        periods.add(newPeriod)
        _allPeriods.value = periods.toList()
        _latestPeriod.value = newPeriod
        return newId
    }

    override suspend fun getPeriodForDate(date: String): FinancialPeriod? {
        return periods.findLast { it.startDate <= date && it.endDate >= date }
    }

    override fun getLatestPeriod(): Flow<FinancialPeriod?> = _latestPeriod

    override fun getAllPeriods(): Flow<List<FinancialPeriod>> = _allPeriods

    override suspend fun deletePeriod(id: Long) {
        periods.removeAll { it.id == id }
        _allPeriods.value = periods.toList()
        if (_latestPeriod.value?.id == id) {
            _latestPeriod.value = null
        }
    }
}

class PeriodUseCaseTest {

    private lateinit var repository: FakePeriodRepositoryForUseCase
    private lateinit var useCase: PeriodUseCase

    @Before
    fun setup() {
        repository = FakePeriodRepositoryForUseCase()
        useCase = PeriodUseCase(repository, currentDateProvider = { "2026-11-07" })
    }

    @Test
    fun test_period_use_case_allows_past_periods_and_rejects_invalid_ranges() = runBlocking {
        // End before start
        val resultInverted = useCase.savePeriod(FinancialPeriod(startDate = "2026-11-20", endDate = "2026-11-10"))
        assertTrue(resultInverted is PeriodResult.Error)
        assertEquals("La fecha de fin no puede ser anterior a la de inicio", (resultInverted as PeriodResult.Error).message)

        // Future range not covering today (today is 2026-11-07)
        val resultFuture = useCase.savePeriod(FinancialPeriod(startDate = "2026-11-10", endDate = "2026-11-20"))
        assertTrue(resultFuture is PeriodResult.Error)
        assertEquals("El período debe incluir la fecha actual o pertenecer a un mes anterior", (resultFuture as PeriodResult.Error).message)

        // Past period (month before: 2026-10-01 to 2026-10-31)
        val resultPast = useCase.savePeriod(FinancialPeriod(startDate = "2026-10-01", endDate = "2026-10-31"))
        assertTrue(resultPast is PeriodResult.Success)

        // Current period covering today
        val resultCurrent = useCase.savePeriod(FinancialPeriod(startDate = "2026-11-01", endDate = "2026-11-30"))
        assertTrue(resultCurrent is PeriodResult.Success)

        val all = useCase.getAllPeriods().first()
        assertEquals(2, all.size)
    }

    @Test
    fun test_period_use_case_rejects_overlapping_dates_with_existing_period() = runBlocking {
        // Save initial period: 2026-10-01 to 2026-10-31
        useCase.savePeriod(FinancialPeriod(startDate = "2026-10-01", endDate = "2026-10-31"))

        // Attempt to save another period sharing boundary date (startDate == 2026-10-31)
        val resultBoundary = useCase.savePeriod(FinancialPeriod(startDate = "2026-10-31", endDate = "2026-11-30"))
        assertTrue(resultBoundary is PeriodResult.Error)
        assertEquals("Las fechas no pueden repetirse ni solaparse con otro período", (resultBoundary as PeriodResult.Error).message)

        // Attempt to save another period inside existing range
        val resultInside = useCase.savePeriod(FinancialPeriod(startDate = "2026-10-05", endDate = "2026-10-25"))
        assertTrue(resultInside is PeriodResult.Error)
        assertEquals("Las fechas no pueden repetirse ni solaparse con otro período", (resultInside as PeriodResult.Error).message)

        // Valid non-overlapping adjacent period: 2026-11-01 to 2026-11-30
        val resultValid = useCase.savePeriod(FinancialPeriod(startDate = "2026-11-01", endDate = "2026-11-30"))
        assertTrue(resultValid is PeriodResult.Success)

        // Editing an existing period with its own dates is allowed
        val savedId = (resultValid as PeriodResult.Success).periodId
        val resultEditOwn = useCase.savePeriod(FinancialPeriod(id = savedId, startDate = "2026-11-01", endDate = "2026-11-30"))
        assertTrue(resultEditOwn is PeriodResult.Success)
    }
}
