package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.application.service.SlackEventService
import com.mkroo.termbase.infrastructure.slack.SlackSignatureVerifier
import com.mkroo.termbase.infrastructure.slack.dto.SlackEventCallback
import com.mkroo.termbase.infrastructure.slack.dto.SlackMessageEvent
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@RestController
@RequestMapping("/api/slack")
class SlackEventController(
    private val signatureVerifier: SlackSignatureVerifier,
    private val slackEventService: SlackEventService,
    private val objectMapper: ObjectMapper,
) {
    @PostMapping("/events")
    fun handleEvent(
        @RequestHeader("X-Slack-Request-Timestamp") timestamp: String,
        @RequestHeader("X-Slack-Signature") signature: String,
        @RequestBody rawBody: String,
    ): ResponseEntity<*> {
        if (!signatureVerifier.verify(timestamp, rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Unit>()
        }

        val payload = objectMapper.readTree(rawBody)
        val type = payload.get("type")?.textValue()

        return when (type) {
            "url_verification" -> {
                val challenge = payload.get("challenge")?.textValue() ?: ""
                ResponseEntity.ok(mapOf("challenge" to challenge))
            }

            "event_callback" -> {
                val eventCallback = objectMapper.readValue<SlackEventCallback>(rawBody)
                handleEventCallback(eventCallback)
            }

            else -> ResponseEntity.ok().build<Unit>()
        }
    }

    private fun handleEventCallback(callback: SlackEventCallback): ResponseEntity<*> {
        when (val event = callback.event) {
            is SlackMessageEvent -> {
                slackEventService.processMessageEvent(event, callback.teamId)
            }
        }
        return ResponseEntity.ok().build<Unit>()
    }
}
