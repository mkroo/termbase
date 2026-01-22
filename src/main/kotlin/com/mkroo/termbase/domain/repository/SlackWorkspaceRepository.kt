package com.mkroo.termbase.domain.repository

import com.mkroo.termbase.domain.model.slack.SlackWorkspace
import org.springframework.data.jpa.repository.JpaRepository

interface SlackWorkspaceRepository : JpaRepository<SlackWorkspace, Long> {
    fun findByTeamId(teamId: String): SlackWorkspace?
}
