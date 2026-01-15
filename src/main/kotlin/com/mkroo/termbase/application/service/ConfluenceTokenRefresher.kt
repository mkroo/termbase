package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.confluence.ConfluenceWorkspace
import com.mkroo.termbase.domain.repository.ConfluenceWorkspaceRepository
import com.mkroo.termbase.infrastructure.confluence.ConfluenceOAuthClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class ConfluenceTokenRefresher(
    private val workspaceRepository: ConfluenceWorkspaceRepository,
    private val oauthClient: ConfluenceOAuthClient,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun ensureValidToken(workspace: ConfluenceWorkspace): ConfluenceWorkspace {
        if (!workspace.isTokenExpired(clock)) {
            return workspace
        }

        val tokenResponse = oauthClient.refreshAccessToken(workspace.refreshToken)
        workspace.updateTokens(
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken,
            expiresIn = tokenResponse.expiresIn,
            clock = clock,
        )

        return workspaceRepository.save(workspace)
    }
}
