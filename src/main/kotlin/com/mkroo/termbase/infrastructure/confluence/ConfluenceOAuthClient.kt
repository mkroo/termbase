package com.mkroo.termbase.infrastructure.confluence

import com.mkroo.termbase.infrastructure.config.ConfluenceProperties
import com.mkroo.termbase.infrastructure.confluence.dto.AccessibleResource
import com.mkroo.termbase.infrastructure.confluence.dto.ConfluenceTokenResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class ConfluenceOAuthClient(
    private val properties: ConfluenceProperties,
    private val restClient: RestClient = RestClient.create(),
) {
    companion object {
        private const val AUTH_URL = "https://auth.atlassian.com"
        private const val API_URL = "https://api.atlassian.com"
        private val SCOPES =
            listOf(
                "read:confluence-space.summary",
                "read:confluence-content.summary",
                "read:confluence-content.all",
                "offline_access",
            )
    }

    fun buildAuthorizationUrl(state: String): String {
        val scopeParam = SCOPES.joinToString(" ")
        return buildString {
            append("$AUTH_URL/authorize")
            append("?audience=api.atlassian.com")
            append("&client_id=${encode(properties.clientId)}")
            append("&scope=${encode(scopeParam)}")
            append("&redirect_uri=${encode(properties.redirectUri)}")
            append("&state=${encode(state)}")
            append("&response_type=code")
            append("&prompt=consent")
        }
    }

    fun exchangeCodeForTokens(code: String): ConfluenceTokenResponse {
        val body =
            mapOf(
                "grant_type" to "authorization_code",
                "client_id" to properties.clientId,
                "client_secret" to properties.clientSecret,
                "code" to code,
                "redirect_uri" to properties.redirectUri,
            )

        return restClient
            .post()
            .uri("$AUTH_URL/oauth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(ConfluenceTokenResponse::class.java)
            ?: throw ConfluenceOAuthException("Failed to exchange code for tokens: empty response")
    }

    fun refreshAccessToken(refreshToken: String): ConfluenceTokenResponse {
        val body =
            mapOf(
                "grant_type" to "refresh_token",
                "client_id" to properties.clientId,
                "client_secret" to properties.clientSecret,
                "refresh_token" to refreshToken,
            )

        return restClient
            .post()
            .uri("$AUTH_URL/oauth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(ConfluenceTokenResponse::class.java)
            ?: throw ConfluenceOAuthException("Failed to refresh token: empty response")
    }

    fun getAccessibleResources(accessToken: String): List<AccessibleResource> =
        restClient
            .get()
            .uri("$API_URL/oauth/token/accessible-resources")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(Array<AccessibleResource>::class.java)
            ?.toList()
            ?: emptyList()

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}

class ConfluenceOAuthException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
