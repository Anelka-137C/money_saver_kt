package com.example.money_saver_android.data.repository

import com.example.money_saver_android.domain.model.FinancialPeriod
import kotlinx.coroutines.flow.Flow

interface FinancialPeriodRepository {
    suspend fun savePeriod(period: FinancialPeriod): Long
    suspend fun getPeriodForDate(date: String): FinancialPeriod?
    fun getLatestPeriod(): Flow<FinancialPeriod?>
    fun getAllPeriods(): Flow<List<FinancialPeriod>>
    suspend fun deletePeriod(id: Long)
}
