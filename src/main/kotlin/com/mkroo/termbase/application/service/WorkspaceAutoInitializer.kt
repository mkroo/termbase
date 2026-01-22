package com.mkroo.termbase.application.service

import com.mkroo.termbase.infrastructure.config.ConfluenceProperties
import com.mkroo.termbase.infrastructure.config.SlackProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class WorkspaceAutoInitializer(
    private val confluenceBatchService: ConfluenceBatchService,
    private val slackChannelsBatchService: SlackChannelsBatchService,
    private val confluenceProperties: ConfluenceProperties,
    private val slackProperties: SlackProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun initializeWorkspaces() {
        initializeConfluence()
        initializeSlack()
    }

    private fun initializeConfluence() {
        if (!confluenceProperties.isConfigured()) {
            log.info("Confluence 설정이 없어 자동 연동을 건너뜁니다.")
            return
        }

        try {
            val workspace = confluenceBatchService.initializeWorkspace()
            log.info("Confluence 워크스페이스 자동 연동 완료: {}", workspace.siteName)
        } catch (e: Exception) {
            log.warn("Confluence 자동 연동 실패: {}", e.message)
        }
    }

    private fun initializeSlack() {
        if (!slackProperties.isConfigured()) {
            log.info("Slack 설정이 없어 자동 연동을 건너뜁니다.")
            return
        }

        try {
            val workspace = slackChannelsBatchService.initializeWorkspace()
            log.info("Slack 워크스페이스 자동 연동 완료: {}", workspace.teamName)
        } catch (e: Exception) {
            log.warn("Slack 자동 연동 실패: {}", e.message)
        }
    }
}
