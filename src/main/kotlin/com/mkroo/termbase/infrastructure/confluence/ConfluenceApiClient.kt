package com.mkroo.termbase.infrastructure.confluence

import com.mkroo.termbase.domain.model.confluence.RemoteSpace
import com.mkroo.termbase.infrastructure.config.ConfluenceProperties
import com.mkroo.termbase.infrastructure.confluence.dto.ConfluencePageDto
import com.mkroo.termbase.infrastructure.confluence.dto.ConfluencePagesResponse
import com.mkroo.termbase.infrastructure.confluence.dto.ConfluenceSpacesResponse
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ConfluenceApiClient(
    private val confluenceProperties: ConfluenceProperties,
    private val restClient: RestClient = RestClient.create(),
) {
    companion object {
        private const val DEFAULT_PAGE_LIMIT = 100
    }

    private val baseApiUrl: String
        get() = "${confluenceProperties.baseUrl}/wiki/api/v2"

    private val authHeader: String
        get() = confluenceProperties.getBasicAuthHeader()

    fun fetchAllSpaces(): List<RemoteSpace> {
        val allSpaces = mutableListOf<RemoteSpace>()
        var cursor: String? = null

        do {
            val response = fetchSpaces(cursor)
            allSpaces.addAll(
                response.results.map {
                    RemoteSpace(id = it.id, key = it.key, name = it.name)
                },
            )
            cursor = extractCursor(response.links?.next)
        } while (cursor != null)

        return allSpaces
    }

    fun fetchAllPagesInSpace(spaceId: String): List<ConfluencePageDto> {
        val allPages = mutableListOf<ConfluencePageDto>()
        var cursor: String? = null

        do {
            val response = fetchPages(spaceId, cursor)
            allPages.addAll(response.results)
            cursor = extractCursor(response.links?.next)
        } while (cursor != null)

        return allPages
    }

    fun fetchPageWithBody(pageId: String): ConfluencePageDto? =
        restClient
            .get()
            .uri("$baseApiUrl/pages/$pageId?body-format=storage")
            .header("Authorization", authHeader)
            .retrieve()
            .body(ConfluencePageDto::class.java)

    private fun fetchSpaces(cursor: String? = null): ConfluenceSpacesResponse {
        val uri =
            buildString {
                append("$baseApiUrl/spaces?limit=$DEFAULT_PAGE_LIMIT")
                cursor?.let { append("&cursor=$it") }
            }

        return restClient
            .get()
            .uri(uri)
            .header("Authorization", authHeader)
            .retrieve()
            .body(ConfluenceSpacesResponse::class.java)
            ?: ConfluenceSpacesResponse(results = emptyList())
    }

    private fun fetchPages(
        spaceId: String,
        cursor: String? = null,
    ): ConfluencePagesResponse {
        val uri =
            buildString {
                append("$baseApiUrl/spaces/$spaceId/pages?limit=$DEFAULT_PAGE_LIMIT&status=current")
                cursor?.let { append("&cursor=$it") }
            }

        return restClient
            .get()
            .uri(uri)
            .header("Authorization", authHeader)
            .retrieve()
            .body(ConfluencePagesResponse::class.java)
            ?: ConfluencePagesResponse(results = emptyList())
    }

    private fun extractCursor(nextLink: String?): String? {
        if (nextLink == null) return null
        val cursorParam = "cursor="
        val cursorIndex = nextLink.indexOf(cursorParam)
        if (cursorIndex == -1) return null
        val startIndex = cursorIndex + cursorParam.length
        val endIndex = nextLink.indexOf('&', startIndex).takeIf { it != -1 } ?: nextLink.length
        return nextLink.substring(startIndex, endIndex).takeIf { it.isNotBlank() }
    }
}
