package com.mkroo.termbase.domain.model.slack

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "slack_channels")
class SlackChannel(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    val channelId: String,
    @Column(nullable = false)
    var name: String,
    @Column(nullable = false)
    var isPrivate: Boolean = false,
    @Column(nullable = false)
    var isSelected: Boolean = false,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    val workspace: SlackWorkspace,
)
