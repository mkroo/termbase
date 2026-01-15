package com.mkroo.termbase.infrastructure.confluence.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("refresh_token")
    val refreshToken: String,
    @JsonProperty("expires_in")
    val expiresIn: Long,
    @JsonProperty("token_type")
    val tokenType: String,
    val scope: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccessibleResource(
    val id: String,
    val url: String,
    val name: String,
    val scopes: List<String>,
)
