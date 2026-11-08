package com.example.money_saver_android.domain.usecase

import com.example.money_saver_android.data.repository.FinancialPeriodRepository
import com.example.money_saver_android.domain.model.FinancialPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class PeriodResult {
    data class Success(val periodId: Long) : PeriodResult()
    data class Error(val message: String) : PeriodResult()
}

class PeriodUseCase(
    private val repository: FinancialPeriodRepository,
    private val currentDateProvider: () -> String = {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
) {
    fun getAllPeriods(): Flow<List<FinancialPeriod>> {
        return repository.getAllPeriods()
    }

    suspend fun getActivePeriod(date: String = currentDateProvider()): FinancialPeriod? {
        return repository.getPeriodForDate(date)
    }

    fun getLatestPeriod(): Flow<FinancialPeriod?> {
        return repository.getLatestPeriod()
    }

    suspend fun savePeriod(period: FinancialPeriod): PeriodResult {
        val start = period.startDate.trim()
        val end = period.endDate.trim()
        val today = currentDateProvider()

        if (start.isBlank() || end.isBlank()) {
            return PeriodResult.Error("Las fechas no pueden estar vacías")
        }

        if (end < start) {
            return PeriodResult.Error("La fecha de fin no puede ser anterior a la de inicio")
        }

        val isPastPeriod = end < today
        val coversToday = start <= today && today <= end
        if (!isPastPeriod && !coversToday) {
            return PeriodResult.Error("El período debe incluir la fecha actual o pertenecer a un mes anterior")
        }

        val existingPeriods = repository.getAllPeriods().first()
        val hasOverlap = existingPeriods.any { existing ->
            existing.id != period.id && (start <= existing.endDate && end >= existing.startDate)
        }
        if (hasOverlap) {
            return PeriodResult.Error("Las fechas no pueden repetirse ni solaparse con otro período")
        }

        val id = repository.savePeriod(period.copy(startDate = start, endDate = end))
        return PeriodResult.Success(id)
    }

    suspend fun deletePeriod(id: Long): PeriodResult {
        repository.deletePeriod(id)
        return PeriodResult.Success(id)
    }
}
