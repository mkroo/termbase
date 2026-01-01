package com.mkroo.termbase.application.service

import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval
import co.elastic.clients.elasticsearch._types.aggregations.TermsExclude
import co.elastic.clients.elasticsearch._types.query_dsl.Query
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
    private val ignoredTermRepository: IgnoredTermRepository,
    private val termRepository: TermRepository,
) : SourceDocumentAnalyzer {
    override fun getTopFrequentTerms(size: Int): List<TermFrequency> {
        val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
        if (!indexOps.exists()) {
            return emptyList()
        }

        val excludedTerms = collectExcludedTerms()

        val query =
            NativeQuery
                .builder()
                .withAggregation(
                    "top_terms",
                    Aggregation.of { agg ->
                        agg.terms { terms ->
                            val termsBuilder = terms.field("content").size(size)
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

        return buckets.map { bucket ->
            TermFrequency(
                term = bucket.key().stringValue(),
                count = bucket.docCount(),
            )
        }
    }

    private fun collectExcludedTerms(): Set<String> {
        val ignoredTermNames = ignoredTermRepository.findAll().map { it.name.lowercase() }
        val terms = termRepository.findAll()
        val registeredTermNames = terms.map { it.name.lowercase() }
        val synonymNames = terms.flatMap { it.synonyms.map { s -> s.name.lowercase() } }

        return (ignoredTermNames + registeredTermNames + synonymNames).toSet()
    }

    override fun getTermFrequency(term: String): Long {
        val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
        if (!indexOps.exists()) {
            return 0
        }

        val query =
            NativeQuery
                .builder()
                .withQuery(
                    Query.of { q ->
                        q.match { m ->
                            m.field("content").query(term)
                        }
                    },
                ).withMaxResults(0)
                .build()

        val searchHits = elasticsearchOperations.search(query, SourceDocument::class.java)
        return searchHits.totalHits
    }

    override fun getTermFrequencyTimeSeries(
        term: String,
        interval: TimeSeriesInterval,
        days: Int,
    ): List<TimeSeriesFrequency> {
        val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
        if (!indexOps.exists()) {
            return emptyList()
        }

        val calendarInterval =
            when (interval) {
                TimeSeriesInterval.DAY -> CalendarInterval.Day
                TimeSeriesInterval.WEEK -> CalendarInterval.Week
                TimeSeriesInterval.MONTH -> CalendarInterval.Month
            }

        val fromDate = Instant.now().minusSeconds(days.toLong() * 24 * 60 * 60)

        val query =
            NativeQuery
                .builder()
                .withQuery(
                    Query.of { q ->
                        q.bool { b ->
                            b
                                .must(
                                    Query.of { mq ->
                                        mq.match { m ->
                                            m.field("content").query(term)
                                        }
                                    },
                                ).filter(
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
        val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
        if (!indexOps.exists()) {
            return emptyList()
        }

        val actualSize = size.coerceIn(1, MAX_DOCUMENT_SIZE)

        val query =
            NativeQuery
                .builder()
                .withQuery(
                    Query.of { q ->
                        q.match { m ->
                            m.field("content").query(term)
                        }
                    },
                ).withHighlightQuery(
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

    companion object {
        const val MAX_DOCUMENT_SIZE = 100
    }
}
