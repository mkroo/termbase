package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.confluence.ConfluenceWorkspace
import com.mkroo.termbase.domain.repository.ConfluenceWorkspaceRepository
import com.mkroo.termbase.infrastructure.confluence.ConfluenceOAuthClient
import com.mkroo.termbase.infrastructure.confluence.ConfluenceOAuthException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class ConfluenceOAuthService(
    private val oauthClient: ConfluenceOAuthClient,
    private val workspaceRepository: ConfluenceWorkspaceRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun buildAuthorizationUrl(): AuthorizationUrlResult {
        val state = UUID.randomUUID().toString()
        val url = oauthClient.buildAuthorizationUrl(state)
        return AuthorizationUrlResult(url = url, state = state)
    }

    @Transactional
    fun handleCallback(
        code: String,
        state: String,
    ): ConfluenceWorkspace {
        val tokenResponse = oauthClient.exchangeCodeForTokens(code)

        val resources = oauthClient.getAccessibleResources(tokenResponse.accessToken)
        if (resources.isEmpty()) {
            throw ConfluenceOAuthException("No accessible Confluence sites found")
        }

        val resource = resources.first()

        val existingWorkspace = workspaceRepository.findByCloudId(resource.id)
        if (existingWorkspace != null) {
            existingWorkspace.updateTokens(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                expiresIn = tokenResponse.expiresIn,
                clock = clock,
            )
            return workspaceRepository.save(existingWorkspace)
        }

        val workspace =
            ConfluenceWorkspace(
                cloudId = resource.id,
                siteName = resource.name,
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                tokenExpiresAt = Instant.now(clock).plusSeconds(tokenResponse.expiresIn),
                connectedAt = Instant.now(clock),
            )

        return workspaceRepository.save(workspace)
    }

    @Transactional
    fun disconnect(cloudId: String) {
        val workspace =
            workspaceRepository.findByCloudId(cloudId)
                ?: throw IllegalArgumentException("Workspace not found: $cloudId")
        workspaceRepository.delete(workspace)
    }

    data class AuthorizationUrlResult(
        val url: String,
        val state: String,
    )
}
