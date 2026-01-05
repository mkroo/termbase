package com.mkroo.termbase.application.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval
import co.elastic.clients.elasticsearch._types.aggregations.TermsExclude
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest
import com.mkroo.termbase.domain.model.document.HighlightedSourceDocument
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.model.document.TermFrequency
import com.mkroo.termbase.domain.model.document.TimeSeriesFrequency
import com.mkroo.termbase.domain.model.document.TimeSeriesInterval
import com.mkroo.termbase.domain.repository.IgnoredTermRepository
import com.mkroo.termbase.domain.repository.TermRepository
import com.mkroo.termbase.domain.service.SourceDocumentAnalyzer
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.query.HighlightQuery
import org.springframework.data.elasticsearch.core.query.highlight.Highlight
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId

@Service
class ElasticsearchSourceDocumentAnalyzer(
    private val elasticsearchOperations: ElasticsearchOperations,
    private val elasticsearchClient: ElasticsearchClient,
    private val ignoredTermRepository: IgnoredTermRepository,
    private val termRepository: TermRepository,
) : SourceDocumentAnalyzer {
    override fun getTopFrequentTerms(size: Int): List<TermFrequency> {
        if (!hasDocuments()) {
            return emptyList()
        }

        val excludedTerms = collectExcludedTerms()

        // Request more terms to account for POS tag and single-letter filtering
        val requestSize = size * 5

        val query =
            NativeQuery
                .builder()
                .withAggregation(
                    "top_terms",
                    Aggregation.of { agg ->
                        agg.terms { terms ->
                            val termsBuilder = terms.field("content").size(requestSize)
                            if (excludedTerms.isNotEmpty()) {
                                termsBuilder.exclude(
                                    TermsExclude.of { ex -> ex.terms(excludedTerms.toList()) },
                                )
                            }
                            termsBuilder
                        }
                    },
                ).withMaxResults(0)
                .build()

        val searchHits = elasticsearchOperations.search(query, SourceDocument::class.java)

        val aggregations = searchHits.aggregations as ElasticsearchAggregations

        val termsAggregation = aggregations.get("top_terms")!!

        val buckets =
            termsAggregation
                .aggregation()
                .aggregate
                .sterms()
                .buckets()
                .array()

        val candidateTerms =
            buckets
                .map { bucket ->
                    TermFrequency(
                        term = bucket.key().stringValue(),
                        count = bucket.docCount(),
                        score = bucket.docCount().toDouble(),
                    )
                }.filterNot { isSingleLetterEnglish(it.term) }

        val posTagMap = getPosTags(candidateTerms.map { it.term })

        return candidateTerms
            .map { it.copy(posTag = posTagMap[it.term]) }
            .filter { it.posTag in RECOMMENDED_POS_TAGS }
            .take(size)
    }

    private fun getPosTags(terms: List<String>): Map<String, String> {
        if (terms.isEmpty()) {
            return emptyMap()
        }

        val text = terms.joinToString(" ")

        return try {
            val request =
                AnalyzeRequest
                    .Builder()
                    .index("source_documents")
                    .analyzer("korean_analyzer")
                    .text(text)
                    .explain(true)
                    .build()

            val response = elasticsearchClient.indices().analyze(request)

            response
                .detail()
                ?.tokenizer()
                ?.tokens()
                ?.associate { token ->
                    val term = token.token()
                    val posTag =
                        token
                            .attributes()["leftPOS"]
                            ?.toString()
                            ?.trim('"')
                            ?.substringBefore("(")
                            ?: "UNKNOWN"
                    term to posTag
                } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun isSingleLetterEnglish(term: String): Boolean = term.length == 1 && term.first() in 'a'..'z'

    private fun collectExcludedTerms(): Set<String> {
        val ignoredTermNames = ignoredTermRepository.findAll().map { it.name.lowercase() }
        val terms = termRepository.findAll()
        val registeredTermNames = terms.map { it.name.lowercase() }
        val synonymNames = terms.flatMap { it.synonyms.map { s -> s.name.lowercase() } }

        return (ignoredTermNames + registeredTermNames + synonymNames).toSet()
    }

    override fun getTermFrequency(term: String): Long {
        if (!hasDocuments()) {
            return 0
        }

        val searchTerms = collectSearchTerms(term)

        val query =
            NativeQuery
                .builder()
                .withQuery(buildSearchQuery(searchTerms))
                .withMaxResults(0)
                .build()

        val searchHits = elasticsearchOperations.search(query, SourceDocument::class.java)
        return searchHits.totalHits
    }

    override fun getTermFrequencyTimeSeries(
        term: String,
        interval: TimeSeriesInterval,
        days: Int,
    ): List<TimeSeriesFrequency> {
        if (!hasDocuments()) {
            return emptyList()
        }

        val calendarInterval =
            when (interval) {
                TimeSeriesInterval.DAY -> CalendarInterval.Day
                TimeSeriesInterval.WEEK -> CalendarInterval.Week
                TimeSeriesInterval.MONTH -> CalendarInterval.Month
            }

        val fromDate = Instant.now().minusSeconds(days.toLong() * 24 * 60 * 60)
        val searchTerms = collectSearchTerms(term)

        val query =
            NativeQuery
                .builder()
                .withQuery(
                    Query.of { q ->
                        q.bool { b ->
                            b
                                .must(buildSearchQuery(searchTerms))
                                .filter(
                                    Query.of { fq ->
                                        fq.range { r ->
                                            r.date { d ->
                                                d.field("timestamp").gte(fromDate.toString())
                                            }
                                        }
                                    },
                                )
                        }
                    },
                ).withAggregation(
                    "frequency_over_time",
                    Aggregation.of { agg ->
                        agg.dateHistogram { dh ->
                            dh.field("timestamp").calendarInterval(calendarInterval)
                        }
                    },
                ).withMaxResults(0)
                .build()

        val searchHits = elasticsearchOperations.search(query, SourceDocument::class.java)

        val aggregations = searchHits.aggregations as? ElasticsearchAggregations ?: return emptyList()
        val dateHistogramAgg = aggregations.get("frequency_over_time") ?: return emptyList()

        return dateHistogramAgg
            .aggregation()
            .aggregate
            .dateHistogram()
            .buckets()
            .array()
            .map { bucket ->
                val epochMillis = bucket.key()
                val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                TimeSeriesFrequency(
                    date = date,
                    count = bucket.docCount(),
                )
            }
    }

    override fun searchDocumentsByTerm(
        term: String,
        size: Int,
    ): List<HighlightedSourceDocument> {
        if (!hasDocuments()) {
            return emptyList()
        }

        val actualSize = size.coerceIn(1, MAX_DOCUMENT_SIZE)

        // Collect all search terms: the term itself and its synonyms
        val searchTerms = collectSearchTerms(term)

        val query =
            NativeQuery
                .builder()
                .withQuery(buildSearchQuery(searchTerms))
                .withHighlightQuery(
                    HighlightQuery(
                        Highlight(
                            listOf(HighlightField("content")),
                        ),
                        SourceDocument::class.java,
                    ),
                ).withSort { s ->
                    s.field { f ->
                        f.field("timestamp").order(SortOrder.Desc)
                    }
                }.withMaxResults(actualSize)
                .build()

        val searchHits = elasticsearchOperations.search(query, SourceDocument::class.java)

        return searchHits.searchHits.map { hit ->
            val document = hit.content
            val highlightedContent =
                hit.getHighlightField("content").firstOrNull() ?: document.content

            HighlightedSourceDocument(
                id = document.id ?: "",
                content = document.content,
                highlightedContent = highlightedContent,
                timestamp = document.timestamp,
            )
        }
    }

    private fun collectSearchTerms(term: String): List<String> {
        val registeredTerm = termRepository.findByName(term)
        return if (registeredTerm != null) {
            listOf(term) + registeredTerm.synonyms.map { it.name }
        } else {
            listOf(term)
        }
    }

    private fun buildSearchQuery(searchTerms: List<String>): Query =
        if (searchTerms.size == 1) {
            Query.of { q ->
                q.matchPhrase { m ->
                    m.field("content").query(searchTerms.first())
                }
            }
        } else {
            Query.of { q ->
                q.bool { b ->
                    b
                        .should(
                            searchTerms.map { searchTerm ->
                                Query.of { sq ->
                                    sq.matchPhrase { m ->
                                        m.field("content").query(searchTerm)
                                    }
                                }
                            },
                        ).minimumShouldMatch("1")
                }
            }
        }

    private fun hasDocuments(): Boolean {
        val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
        if (!indexOps.exists()) {
            return false
        }

        val countQuery =
            NativeQuery
                .builder()
                .withMaxResults(0)
                .build()

        return try {
            val searchHits = elasticsearchOperations.search(countQuery, SourceDocument::class.java)
            searchHits.totalHits > 0
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val MAX_DOCUMENT_SIZE = 100

        // 용어 사전 후보로 적합한 품사 태그: 일반명사, 고유명사, 외국어
        private val RECOMMENDED_POS_TAGS = setOf("NNG", "NNP", "SL")
    }
}
