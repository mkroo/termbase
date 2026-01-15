package com.mkroo.termbase.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "confluence")
data class ConfluenceProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "http://localhost:8080/confluence/oauth/callback",
)

@Configuration
@EnableConfigurationProperties(ConfluenceProperties::class)
class ConfluenceConfig
