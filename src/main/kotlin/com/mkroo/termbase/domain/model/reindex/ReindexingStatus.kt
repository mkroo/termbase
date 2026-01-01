package com.mkroo.termbase.domain.model.reindex

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "reindexing_status")
class ReindexingStatus(
    @Id
    val id: Long = 1,
    @Column(nullable = false)
    var currentIndexName: String,
    @Column(nullable = false)
    var lastReindexedAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false)
    var reindexingRequired: Boolean = false,
) {
    fun markReindexingRequired() {
        this.reindexingRequired = true
    }

    fun updateAfterReindexing(newIndexName: String) {
        this.currentIndexName = newIndexName
        this.lastReindexedAt = LocalDateTime.now()
        this.reindexingRequired = false
    }
}
