package com.mkroo.termbase.domain.repository

import com.mkroo.termbase.domain.model.confluence.ConfluenceWorkspace
import org.springframework.data.repository.Repository

interface ConfluenceWorkspaceRepository : Repository<ConfluenceWorkspace, Long> {
    fun save(workspace: ConfluenceWorkspace): ConfluenceWorkspace

    fun findByCloudId(cloudId: String): ConfluenceWorkspace?

    fun findAll(): List<ConfluenceWorkspace>

    fun delete(workspace: ConfluenceWorkspace)

    fun existsByCloudId(cloudId: String): Boolean
}
