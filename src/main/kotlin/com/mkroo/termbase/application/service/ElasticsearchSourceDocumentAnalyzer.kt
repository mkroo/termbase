package com.mkroo.termbase.application.service

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation
import co.elastic.clients.elasticsearch._types.aggregations.TermsExclude
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.model.document.TermFrequency
import com.mkroo.termbase.domain.repository.IgnoredTermRepository
import com.mkroo.termbase.domain.repository.TermRepository
import com.mkroo.termbase.domain.service.SourceDocumentAnalyzer
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Service

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
}
