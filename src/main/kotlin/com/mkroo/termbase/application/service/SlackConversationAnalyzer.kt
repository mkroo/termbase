package com.mkroo.termbase.application.service

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation
import com.mkroo.termbase.domain.model.slack.SlackMessage
import com.mkroo.termbase.domain.model.slack.TermFrequency
import com.mkroo.termbase.domain.service.ConversationAnalyzer
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Service

@Service
class SlackConversationAnalyzer(
    private val elasticsearchOperations: ElasticsearchOperations,
) : ConversationAnalyzer {
    override fun getTopFrequentTerms(size: Int): List<TermFrequency> {
        val query =
            NativeQuery
                .builder()
                .withAggregation(
                    "top_terms",
                    Aggregation.of { agg ->
                        agg.terms { terms ->
                            terms
                                .field("content")
                                .size(size)
                        }
                    },
                ).withMaxResults(0)
                .build()

        val searchHits = elasticsearchOperations.search(query, SlackMessage::class.java)

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
}
