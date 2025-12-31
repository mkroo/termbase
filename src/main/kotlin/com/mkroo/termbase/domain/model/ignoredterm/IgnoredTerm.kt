package com.mkroo.termbase.domain.model.ignoredterm

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "ignored_terms")
class IgnoredTerm(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true)
    val name: String,
    @Column(nullable = false)
    var reason: String,
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun updateReason(newReason: String) {
        require(newReason.isNotBlank()) { "무시 사유는 필수입니다." }
        this.reason = newReason
    }
}
