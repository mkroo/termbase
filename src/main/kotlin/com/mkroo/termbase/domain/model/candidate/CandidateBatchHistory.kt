package com.mkroo.termbase.domain.model.candidate

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "candidate_batch_history")
class CandidateBatchHistory(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "total_documents", nullable = false)
    var totalDocuments: Int = 0,
    @Column(name = "total_candidates", nullable = false)
    var totalCandidates: Int = 0,
    @Column(name = "started_at", nullable = false, updatable = false)
    val startedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BatchStatus = BatchStatus.RUNNING,
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,
) {
    fun complete(
        totalDocuments: Int,
        totalCandidates: Int,
    ) {
        require(status == BatchStatus.RUNNING) { "RUNNING 상태의 배치만 완료할 수 있습니다." }
        this.totalDocuments = totalDocuments
        this.totalCandidates = totalCandidates
        this.status = BatchStatus.COMPLETED
        this.completedAt = LocalDateTime.now()
    }

    fun fail(errorMessage: String) {
        require(status == BatchStatus.RUNNING) { "RUNNING 상태의 배치만 실패 처리할 수 있습니다." }
        this.status = BatchStatus.FAILED
        this.errorMessage = errorMessage
        this.completedAt = LocalDateTime.now()
    }

    val isRunning: Boolean get() = status == BatchStatus.RUNNING
    val isCompleted: Boolean get() = status == BatchStatus.COMPLETED
    val isFailed: Boolean get() = status == BatchStatus.FAILED
}
