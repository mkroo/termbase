package com.mkroo.termbase.domain.model.candidate

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "term_candidates")
class TermCandidate(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true)
    val term: String,
    @Column(nullable = false, columnDefinition = "JSON")
    val components: String,
    @Column(nullable = false)
    val count: Int,
    @Column(nullable = false, precision = 10, scale = 6)
    val pmi: BigDecimal,
    @Column(nullable = false, precision = 10, scale = 6)
    val npmi: BigDecimal,
    @Column(name = "doc_count", nullable = false)
    val docCount: Int,
    @Column(nullable = false, precision = 10, scale = 6)
    val idf: BigDecimal,
    @Column(name = "avg_tfidf", nullable = false, precision = 10, scale = 6)
    val avgTfidf: BigDecimal,
    @Column(name = "relevance_score", nullable = false, precision = 10, scale = 6)
    val relevanceScore: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CandidateStatus = CandidateStatus.PENDING,
    @Column(name = "reviewed_by")
    var reviewedBy: String? = null,
    @Column(name = "reviewed_at")
    var reviewedAt: LocalDateTime? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun approve(reviewer: String) {
        require(status == CandidateStatus.PENDING) { "PENDING 상태의 후보만 승인할 수 있습니다." }
        this.status = CandidateStatus.APPROVED
        this.reviewedBy = reviewer
        this.reviewedAt = LocalDateTime.now()
    }

    fun reject(reviewer: String) {
        require(status == CandidateStatus.PENDING) { "PENDING 상태의 후보만 거절할 수 있습니다." }
        this.status = CandidateStatus.REJECTED
        this.reviewedBy = reviewer
        this.reviewedAt = LocalDateTime.now()
    }

    val isPending: Boolean get() = status == CandidateStatus.PENDING
    val isApproved: Boolean get() = status == CandidateStatus.APPROVED
    val isRejected: Boolean get() = status == CandidateStatus.REJECTED
}
