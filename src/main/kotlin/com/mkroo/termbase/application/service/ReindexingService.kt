package com.mkroo.termbase.application.service

import com.mkroo.termbase.domain.model.reindex.ReindexingStatus
import com.mkroo.termbase.domain.model.term.Term
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

        val STOPTAGS =
            listOf(
                "NP",
                "NR",
                "VV",
                "VA",
                "VX",
                "VCP",
                "VCN",
                "MM",
                "MAG",
                "MAJ",
                "IC",
                "JKS",
                "JKC",
                "JKG",
                "JKO",
                "JKB",
                "JKV",
                "JKQ",
                "JC",
                "JX",
                "EP",
                "EF",
                "EC",
                "ETN",
                "ETM",
                "XPN",
                "XSN",
                "XSV",
                "XSA",
                "XR",
                "SF",
                "SE",
                "SSO",
                "SSC",
                "SC",
                "SY",
                "SN",
                "SP",
            )
    }

    @Transactional
    fun reindex(): ReindexingResult {
        val terms = termRepository.findAll()
        // For terms with spaces, use space-removed version in user dictionary
        val userDictionaryRules =
            terms.map { it.name.replace(" ", "") }.distinct()
        val synonymRules = buildSynonymRules(terms)
        val newIndexName = generateNewIndexName()

        val status = getOrCreateStatus(newIndexName)

        // Check if ALIAS_NAME exists as a real index (not managed by reindexing)
        val aliasExistsAsRealIndex = checkIfAliasExistsAsRealIndex()

        val currentIndex =
            when {
                aliasExistsAsRealIndex -> ALIAS_NAME
                status.currentIndexName != newIndexName && indexExists(status.currentIndexName) -> status.currentIndexName
                else -> null
            }

        createIndexWithUserDictionary(newIndexName, userDictionaryRules, synonymRules)

        val reindexedCount =
            if (currentIndex != null) {
                reindexDocuments(currentIndex, newIndexName)
            } else {
                0L
            }

        // Delete the real index if it exists before creating alias
        if (aliasExistsAsRealIndex) {
            deleteIndex(ALIAS_NAME)
        }

        switchAlias(if (aliasExistsAsRealIndex) null else currentIndex, newIndexName)

        if (currentIndex != null && !aliasExistsAsRealIndex) {
            deleteIndex(currentIndex)
        }

        status.updateAfterReindexing(newIndexName)
        reindexingStatusRepository.save(status)

        return ReindexingResult(
            previousIndex = currentIndex,
            newIndex = newIndexName,
            documentCount = reindexedCount,
            userDictionarySize = userDictionaryRules.size,
            synonymRulesSize = synonymRules.size,
        )
    }

    private fun buildSynonymRules(terms: List<Term>): List<String> =
        terms
            .filter { it.synonyms.isNotEmpty() }
            .map { term ->
                // Remove spaces from both synonyms and term name for Elasticsearch compatibility
                val synonymNames = term.synonyms.joinToString(", ") { it.name.replace(" ", "") }
                "$synonymNames => ${term.name.replace(" ", "").lowercase()}"
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
        synonymRules: List<String>,
    ) {
        elasticsearchTemplate.execute { client ->
            val userDictRulesJson =
                if (userDictionaryRules.isNotEmpty()) {
                    userDictionaryRules.joinToString(",") { "\"$it\"" }
                } else {
                    ""
                }

            val synonymRulesJson =
                if (synonymRules.isNotEmpty()) {
                    synonymRules.joinToString(",") { "\"$it\"" }
                } else {
                    ""
                }

            val stoptagsJson = STOPTAGS.joinToString(",") { "\"$it\"" }

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
                      "filter": {
                        "noun_filter": {
                          "type": "nori_part_of_speech",
                          "stoptags": [$stoptagsJson]
                        }${if (synonymRulesJson.isNotEmpty()) {
                    """,
                        "synonym_filter": {
                          "type": "synonym",
                          "synonyms": [$synonymRulesJson]
                        }"""
                } else {
                    ""
                }}
                      },
                      "analyzer": {
                        "korean_analyzer": {
                          "type": "custom",
                          "tokenizer": "nori_user_dict_tokenizer",
                          "filter": ["lowercase", "noun_filter"${if (synonymRulesJson.isNotEmpty()) {
                    """, "synonym_filter""""
                } else {
                    ""
                }}]
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

    private fun checkIfAliasExistsAsRealIndex(): Boolean =
        elasticsearchTemplate.execute { client ->
            // Check if ALIAS_NAME exists as an index
            val exists = client.indices().exists { it.index(ALIAS_NAME) }.value()
            if (!exists) return@execute false

            // Check if it's a real index (not an alias pointing to another index)
            // getAlias returns info about the index if ALIAS_NAME is a real index
            val aliasResponse = client.indices().getAlias { it.index(ALIAS_NAME) }
            val indexInfo = aliasResponse[ALIAS_NAME]

            // If indexInfo exists, ALIAS_NAME is a real index
            indexInfo != null
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
    val synonymRulesSize: Int,
)
