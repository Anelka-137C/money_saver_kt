package com.example.money_saver_android.data.repository

import com.example.money_saver_android.data.local.FinancialPeriodDao
import com.example.money_saver_android.data.local.FinancialPeriodEntity
import com.example.money_saver_android.domain.model.FinancialPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFinancialPeriodRepository(
    private val dao: FinancialPeriodDao
) : FinancialPeriodRepository {

    override suspend fun savePeriod(period: FinancialPeriod): Long {
        val entity = FinancialPeriodEntity(
            id = period.id,
            startDate = period.startDate,
            endDate = period.endDate
        )
        return dao.insertPeriod(entity)
    }

    override suspend fun getPeriodForDate(date: String): FinancialPeriod? {
        val entity = dao.getPeriodForDate(date) ?: return null
        return FinancialPeriod(
            id = entity.id,
            startDate = entity.startDate,
            endDate = entity.endDate
        )
    }

    override fun getLatestPeriod(): Flow<FinancialPeriod?> {
        return dao.getLatestPeriod().map { entity ->
            entity?.let {
                FinancialPeriod(
                    id = it.id,
                    startDate = it.startDate,
                    endDate = it.endDate
                )
            }
        }
    }

    override fun getAllPeriods(): Flow<List<FinancialPeriod>> {
        return dao.getAllPeriods().map { list ->
            list.map {
                FinancialPeriod(
                    id = it.id,
                    startDate = it.startDate,
                    endDate = it.endDate
                )
            }
        }
    }

    override suspend fun deletePeriod(id: Long) {
        dao.deletePeriod(id)
    }
}
