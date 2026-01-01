package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.reindex.ReindexingStatus
import com.mkroo.termbase.domain.repository.ReindexingStatusRepository
import com.mkroo.termbase.domain.repository.TermRepository
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.StringReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ReindexingService(
    private val elasticsearchTemplate: ElasticsearchTemplate,
    private val termRepository: TermRepository,
    private val reindexingStatusRepository: ReindexingStatusRepository,
) {
    companion object {
        const val ALIAS_NAME = "source_documents"
        const val INDEX_PREFIX = "source_documents_v"
    }

    @Transactional
    fun reindex(): ReindexingResult {
        val userDictionaryRules = termRepository.findAll().map { it.name }
        val newIndexName = generateNewIndexName()

        val status = getOrCreateStatus(newIndexName)
        val currentIndex =
            if (status.currentIndexName != newIndexName && indexExists(status.currentIndexName)) {
                status.currentIndexName
            } else {
                null
            }

        createIndexWithUserDictionary(newIndexName, userDictionaryRules)

        val reindexedCount =
            if (currentIndex != null) {
                reindexDocuments(currentIndex, newIndexName)
            } else {
                0L
            }

        switchAlias(currentIndex, newIndexName)

        if (currentIndex != null) {
            deleteIndex(currentIndex)
        }

        status.updateAfterReindexing(newIndexName)
        reindexingStatusRepository.save(status)

        return ReindexingResult(
            previousIndex = currentIndex,
            newIndex = newIndexName,
            documentCount = reindexedCount,
            userDictionarySize = userDictionaryRules.size,
        )
    }

    @Transactional
    fun markReindexingRequired() {
        val status = reindexingStatusRepository.findById(1) ?: return
        status.markReindexingRequired()
        reindexingStatusRepository.save(status)
    }

    @Transactional(readOnly = true)
    fun isReindexingRequired(): Boolean = reindexingStatusRepository.findById(1)?.reindexingRequired ?: true

    @Transactional
    fun initializeIfNeeded(): ReindexingResult? {
        val status = reindexingStatusRepository.findById(1)
        if (status == null || !indexExists(status.currentIndexName)) {
            return reindex()
        }
        return null
    }

    private fun getOrCreateStatus(defaultIndexName: String): ReindexingStatus =
        reindexingStatusRepository.findById(1) ?: ReindexingStatus(
            currentIndexName = defaultIndexName,
            reindexingRequired = true,
        )

    private fun indexExists(indexName: String): Boolean =
        elasticsearchTemplate.execute { client ->
            client.indices().exists { it.index(indexName) }.value()
        }

    private fun generateNewIndexName(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
        return "$INDEX_PREFIX$timestamp"
    }

    private fun createIndexWithUserDictionary(
        indexName: String,
        userDictionaryRules: List<String>,
    ) {
        elasticsearchTemplate.execute { client ->
            val userDictRulesJson =
                if (userDictionaryRules.isNotEmpty()) {
                    userDictionaryRules.joinToString(",") { "\"$it\"" }
                } else {
                    ""
                }

            val settingsJson =
                """
                {
                  "settings": {
                    "analysis": {
                      "tokenizer": {
                        "nori_user_dict_tokenizer": {
                          "type": "nori_tokenizer",
                          "decompound_mode": "mixed"${if (userDictRulesJson.isNotEmpty()) {
                    """,
                          "user_dictionary_rules": [$userDictRulesJson]"""
                } else {
                    ""
                }}
                        }
                      },
                      "analyzer": {
                        "korean_analyzer": {
                          "type": "custom",
                          "tokenizer": "nori_user_dict_tokenizer"
                        }
                      }
                    }
                  },
                  "mappings": {
                    "properties": {
                      "content": {
                        "type": "text",
                        "analyzer": "korean_analyzer",
                        "fielddata": true
                      },
                      "metadata": {
                        "type": "object",
                        "enabled": true
                      },
                      "timestamp": {
                        "type": "date"
                      }
                    }
                  }
                }
                """.trimIndent()

            client.indices().create { builder ->
                builder
                    .index(indexName)
                    .withJson(StringReader(settingsJson))
            }
        }
    }

    private fun reindexDocuments(
        sourceIndex: String,
        destIndex: String,
    ): Long =
        elasticsearchTemplate.execute { client ->
            val response =
                client.reindex { builder ->
                    builder
                        .source { s -> s.index(sourceIndex) }
                        .dest { d -> d.index(destIndex) }
                        .refresh(true)
                }
            response.total()!!
        }

    private fun switchAlias(
        oldIndex: String?,
        newIndex: String,
    ) {
        elasticsearchTemplate.execute { client ->
            val actions =
                buildList {
                    if (oldIndex != null) {
                        add(
                            co.elastic.clients.elasticsearch.indices.update_aliases.Action
                                .Builder()
                                .remove { r -> r.index(oldIndex).alias(ALIAS_NAME) }
                                .build(),
                        )
                    }
                    add(
                        co.elastic.clients.elasticsearch.indices.update_aliases.Action
                            .Builder()
                            .add { a -> a.index(newIndex).alias(ALIAS_NAME).isWriteIndex(true) }
                            .build(),
                    )
                }
            client.indices().updateAliases { it.actions(actions) }
        }
    }

    private fun deleteIndex(indexName: String) {
        elasticsearchTemplate.execute { client ->
            client.indices().delete { it.index(indexName) }
        }
    }
}

data class ReindexingResult(
    val previousIndex: String?,
    val newIndex: String,
    val documentCount: Long,
    val userDictionarySize: Int,
)
