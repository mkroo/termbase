package com.mkroo.termbase.domain.model.slack

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "slack_collection_checkpoints")
class SlackCollectionCheckpoint(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true)
    val channelId: String,
    @Column(nullable = false)
    var lastCollectedTs: String,
    @Column(nullable = false)
    var lastCollectedAt: Instant,
) {
    fun updateCheckpoint(
        messageTs: String,
        collectedAt: Instant,
    ) {
        this.lastCollectedTs = messageTs
        this.lastCollectedAt = collectedAt
    }
}
