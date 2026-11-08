package com.example.money_saver_android.viewmodel

import com.example.money_saver_android.data.repository.FinancialPeriodRepository
import com.example.money_saver_android.domain.model.FinancialPeriod
import com.example.money_saver_android.domain.usecase.PeriodUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeFinancialPeriodRepository(
    initialPeriod: FinancialPeriod? = null
) : FinancialPeriodRepository {
    private val periods = mutableListOf<FinancialPeriod>()
    private val _latestPeriod = MutableStateFlow<FinancialPeriod?>(initialPeriod)
    private val _allPeriods = MutableStateFlow<List<FinancialPeriod>>(periods.toList())

    init {
        if (initialPeriod != null) {
            periods.add(initialPeriod)
            _allPeriods.value = periods.toList()
        }
    }

    override suspend fun savePeriod(period: FinancialPeriod): Long {
        val newPeriod = period.copy(id = if (period.id == 0L) (periods.size + 1).toLong() else period.id)
        periods.removeAll { it.id == newPeriod.id }
        periods.add(newPeriod)
        _latestPeriod.value = newPeriod
        _allPeriods.value = periods.toList()
        return newPeriod.id
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

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeFinancialPeriodRepository
    private lateinit var useCase: PeriodUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeFinancialPeriodRepository()
        useCase = PeriodUseCase(repository, currentDateProvider = { "2026-11-07" })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_period_validation_rejects_end_date_before_start_date() = runTest {
        val viewModel = PeriodViewModel(useCase)
        advanceUntilIdle()

        viewModel.onStartDateChanged("2026-11-15")
        viewModel.onEndDateChanged("2026-11-10")
        viewModel.savePeriod()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertNull(repository.getPeriodForDate("2026-11-07"))
    }

    @Test
    fun test_period_validation_rejects_range_not_covering_current_date() = runTest {
        val viewModel = PeriodViewModel(useCase)
        advanceUntilIdle()

        // Range in the future (does not cover 2026-11-07)
        viewModel.onStartDateChanged("2026-11-10")
        viewModel.onEndDateChanged("2026-11-20")
        viewModel.savePeriod()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertNull(repository.getPeriodForDate("2026-11-07"))
    }

    @Test
    fun test_save_valid_period_persists_and_updates_active_period() = runTest {
        val viewModel = PeriodViewModel(useCase)
        advanceUntilIdle()

        // Initially no active period
        assertEquals(false, viewModel.uiState.value.isPeriodConfigured)
        assertNull(viewModel.uiState.value.activePeriod)

        viewModel.onStartDateChanged("2026-11-01")
        viewModel.onEndDateChanged("2026-11-30")
        viewModel.savePeriod()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(true, viewModel.uiState.value.isPeriodConfigured)
        assertEquals("2026-11-01", viewModel.uiState.value.activePeriod?.startDate)
        assertEquals("2026-11-30", viewModel.uiState.value.activePeriod?.endDate)

        val persisted = repository.getPeriodForDate("2026-11-07")
        assertNotNull(persisted)
        assertEquals("2026-11-01", persisted?.startDate)
        assertEquals("2026-11-30", persisted?.endDate)

        // Modifying listed period updates it preserving id
        viewModel.updatePeriod(persisted!!.copy(endDate = "2026-12-05"))
        advanceUntilIdle()

        assertEquals("2026-12-05", viewModel.uiState.value.activePeriod?.endDate)
        assertEquals(persisted.id, viewModel.uiState.value.activePeriod?.id)
    }

    @Test
    fun test_save_period_clears_form_inputs_and_updates_period_list() = runTest {
        val viewModel = PeriodViewModel(useCase)
        advanceUntilIdle()

        viewModel.onStartDateChanged("2026-11-01")
        viewModel.onEndDateChanged("2026-11-30")
        viewModel.savePeriod()
        advanceUntilIdle()

        // Inputs must be cleared for a new entry
        assertEquals("", viewModel.uiState.value.startDateInput)
        assertEquals("", viewModel.uiState.value.endDateInput)

        // All periods list is populated
        assertEquals(1, viewModel.uiState.value.allPeriods.size)
        assertEquals("2026-11-01", viewModel.uiState.value.allPeriods[0].startDate)
        assertEquals("2026-11-30", viewModel.uiState.value.allPeriods[0].endDate)
    }

    @Test
    fun test_delete_period_removes_it_and_updates_active_period_state() = runTest {
        val viewModel = PeriodViewModel(useCase)
        advanceUntilIdle()

        viewModel.onStartDateChanged("2026-11-01")
        viewModel.onEndDateChanged("2026-11-30")
        viewModel.savePeriod()
        advanceUntilIdle()

        val activeId = viewModel.uiState.value.activePeriod!!.id
        assertEquals(true, viewModel.uiState.value.isPeriodConfigured)

        viewModel.deletePeriod(activeId)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activePeriod)
        assertEquals(false, viewModel.uiState.value.isPeriodConfigured)
        assertEquals(0, viewModel.uiState.value.allPeriods.size)
    }
}
