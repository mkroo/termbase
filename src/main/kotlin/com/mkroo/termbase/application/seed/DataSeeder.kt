package com.mkroo.termbase.application.seed

import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.model.ignoredterm.IgnoredTerm
import com.mkroo.termbase.domain.model.term.Term
import com.mkroo.termbase.domain.repository.IgnoredTermRepository
import com.mkroo.termbase.domain.repository.SourceDocumentRepository
import com.mkroo.termbase.domain.repository.TermRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ResourceLoader
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime

@Component
@Profile("local")
class DataSeeder(
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper,
    private val termRepository: TermRepository,
    private val ignoredTermRepository: IgnoredTermRepository,
    private val sourceDocumentRepository: SourceDocumentRepository,
    private val elasticsearchOperations: ElasticsearchOperations,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        logger.info("Starting data seeding...")
        seedTerms()
        seedIgnoredTerms()
        seedSourceDocuments()
        logger.info("Data seeding completed.")
    }

    private fun seedTerms() {
        val existingTerms = termRepository.findAll().map { it.name.lowercase() }.toSet()

        val termsData = loadSeedData<List<TermSeedData>>("seed-data/terms.json")
        var insertedCount = 0

        termsData.forEach { termData ->
            if (termData.name.lowercase() !in existingTerms) {
                val term =
                    Term(
                        name = termData.name,
                        definition = termData.definition,
                        createdAt = LocalDateTime.now(),
                    )
                termData.synonyms.forEach { synonymName ->
                    term.addSynonym(synonymName)
                }
                termRepository.save(term)
                insertedCount++
            }
        }

        logger.info("Terms seeded: $insertedCount new, ${termsData.size - insertedCount} skipped (already exist)")
    }

    private fun seedIgnoredTerms() {
        val existingIgnoredTerms = ignoredTermRepository.findAll().map { it.name.lowercase() }.toSet()

        val ignoredTermsData = loadSeedData<List<IgnoredTermSeedData>>("seed-data/ignored-terms.json")
        var insertedCount = 0

        ignoredTermsData.forEach { ignoredTermData ->
            if (ignoredTermData.name.lowercase() !in existingIgnoredTerms) {
                ignoredTermRepository.save(
                    IgnoredTerm(
                        name = ignoredTermData.name,
                        reason = ignoredTermData.reason,
                        createdAt = LocalDateTime.now(),
                    ),
                )
                insertedCount++
            }
        }

        logger.info(
            "Ignored terms seeded: $insertedCount new, ${ignoredTermsData.size - insertedCount} skipped (already exist)",
        )
    }

    private fun seedSourceDocuments() {
        val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
        if (!indexOps.exists()) {
            indexOps.createWithMapping()
        }

        val documentsData = loadSeedData<List<SourceDocumentSeedData>>("seed-data/source-documents.json")

        val documents =
            documentsData.map { docData ->
                SourceDocument(
                    id = docData.id,
                    content = docData.content,
                    metadata = docData.metadata,
                    timestamp = docData.timestamp,
                )
            }

        val result = sourceDocumentRepository.saveAll(documents)
        logger.info(
            "Source documents seeded: ${result.successCount} success, ${result.failureCount} failed out of ${result.totalCount}",
        )
    }

    private inline fun <reified T> loadSeedData(path: String): T {
        val resource = resourceLoader.getResource("classpath:$path")
        return objectMapper.readValue(resource.inputStream)
    }
}
