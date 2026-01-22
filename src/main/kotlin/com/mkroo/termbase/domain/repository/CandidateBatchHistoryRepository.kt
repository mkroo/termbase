package com.mkroo.termbase.domain.repository

import com.mkroo.termbase.domain.model.candidate.BatchStatus
import com.mkroo.termbase.domain.model.candidate.CandidateBatchHistory
import org.springframework.data.repository.Repository

interface CandidateBatchHistoryRepository : Repository<CandidateBatchHistory, Long> {
    fun save(batchHistory: CandidateBatchHistory): CandidateBatchHistory

    fun findById(id: Long): CandidateBatchHistory?

    fun findAll(): List<CandidateBatchHistory>

    fun findByStatus(status: BatchStatus): List<CandidateBatchHistory>

    fun findFirstByOrderByStartedAtDesc(): CandidateBatchHistory?

    fun count(): Long
}
