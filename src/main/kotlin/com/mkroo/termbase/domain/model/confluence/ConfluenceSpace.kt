package com.mkroo.termbase.domain.model.confluence

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
@Table(name = "confluence_spaces")
class ConfluenceSpace(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    val spaceId: String,
    @Column(nullable = false)
    val spaceKey: String,
    @Column(nullable = false)
    var name: String,
    @Column(nullable = false)
    var isSelected: Boolean = false,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    val workspace: ConfluenceWorkspace,
)
