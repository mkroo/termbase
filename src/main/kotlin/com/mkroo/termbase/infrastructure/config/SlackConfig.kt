package com.mkroo.termbase.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "slack")
data class SlackProperties(
    val botToken: String = "",
    val signingSecret: String = "",
)

@Configuration
@EnableConfigurationProperties(SlackProperties::class)
class SlackConfig
