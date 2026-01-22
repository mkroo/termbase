package com.mkroo.termbase.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "confluence")
data class ConfluenceProperties(
    val baseUrl: String = "",
    val email: String = "",
    val apiToken: String = "",
) {
    fun isConfigured(): Boolean = baseUrl.isNotBlank() && email.isNotBlank() && apiToken.isNotBlank()

    fun getBasicAuthHeader(): String {
        val credentials = "$email:$apiToken"
        val encoded =
            java.util.Base64
                .getEncoder()
                .encodeToString(credentials.toByteArray())
        return "Basic $encoded"
    }

    fun getSiteId(): String = baseUrl.removePrefix("https://").removeSuffix(".atlassian.net").removeSuffix("/")
}

@Configuration
@EnableConfigurationProperties(ConfluenceProperties::class)
class ConfluenceConfig
