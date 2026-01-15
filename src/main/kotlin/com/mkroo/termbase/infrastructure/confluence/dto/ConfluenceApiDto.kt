package com.mkroo.termbase.infrastructure.confluence.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceSpacesResponse(
    val results: List<ConfluenceSpaceDto>,
    @JsonProperty("_links")
    val links: ConfluenceLinksDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceSpaceDto(
    val id: String,
    val key: String,
    val name: String,
    val type: String? = null,
    val status: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluencePagesResponse(
    val results: List<ConfluencePageDto>,
    @JsonProperty("_links")
    val links: ConfluenceLinksDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluencePageDto(
    val id: String,
    val title: String,
    val status: String? = null,
    val body: ConfluenceBodyDto? = null,
    val version: ConfluenceVersionDto? = null,
    @JsonProperty("spaceId")
    val spaceId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceBodyDto(
    val storage: ConfluenceStorageDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceStorageDto(
    val value: String,
    val representation: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceVersionDto(
    val number: Int,
    @JsonProperty("createdAt")
    val createdAt: Instant? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceLinksDto(
    val next: String? = null,
)
