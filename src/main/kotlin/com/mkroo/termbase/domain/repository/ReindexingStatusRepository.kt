package com.mkroo.termbase.domain.repository

import com.mkroo.termbase.domain.model.reindex.ReindexingStatus
import org.springframework.data.repository.Repository

interface ReindexingStatusRepository : Repository<ReindexingStatus, Long> {
    fun save(status: ReindexingStatus): ReindexingStatus

    fun findById(id: Long): ReindexingStatus?

    fun deleteById(id: Long)
}
