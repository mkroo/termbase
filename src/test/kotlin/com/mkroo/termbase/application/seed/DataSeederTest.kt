package com.mkroo.termbase.application.seed

import com.mkroo.termbase.TestcontainersConfiguration
import com.mkroo.termbase.domain.model.document.SourceDocument
import com.mkroo.termbase.domain.model.ignoredterm.IgnoredTerm
import com.mkroo.termbase.domain.model.term.Term
import com.mkroo.termbase.domain.repository.IgnoredTermRepository
import com.mkroo.termbase.domain.repository.TermRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test", "local")
@Transactional
class DataSeederTest : DescribeSpec() {
    @Autowired
    private lateinit var dataSeeder: DataSeeder

    @Autowired
    private lateinit var termRepository: TermRepository

    @Autowired
    private lateinit var ignoredTermRepository: IgnoredTermRepository

    @Autowired
    private lateinit var elasticsearchOperations: ElasticsearchOperations

    init {
        extension(SpringExtension())

        beforeEach {
            termRepository.findAll().forEach { termRepository.delete(it) }
            ignoredTermRepository.findAll().forEach { ignoredTermRepository.delete(it) }

            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }
        }

        afterEach {
            val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
            if (indexOps.exists()) {
                indexOps.delete()
            }
        }

        describe("DataSeeder") {
            describe("run") {
                it("seeds terms with synonyms from JSON file") {
                    dataSeeder.run(DefaultApplicationArguments())

                    val terms = termRepository.findAll()
                    terms.shouldHaveAtLeastSize(1)

                    val apiTerm = termRepository.findByName("API")
                    apiTerm shouldBe termRepository.findByName("API")
                    apiTerm!!.synonyms.map { it.name } shouldHaveAtLeastSize 1
                }

                it("seeds ignored terms from JSON file") {
                    dataSeeder.run(DefaultApplicationArguments())

                    val ignoredTerms = ignoredTermRepository.findAll()
                    ignoredTerms.shouldHaveAtLeastSize(1)
                }

                it("seeds source documents to Elasticsearch") {
                    dataSeeder.run(DefaultApplicationArguments())

                    val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
                    indexOps.exists() shouldBe true
                }

                it("skips existing terms when seeding") {
                    termRepository.save(
                        Term(
                            name = "API",
                            definition = "existing definition",
                            createdAt = LocalDateTime.now(),
                        ),
                    )

                    dataSeeder.run(DefaultApplicationArguments())

                    val apiTerm = termRepository.findByName("API")!!
                    apiTerm.definition shouldBe "existing definition"
                }

                it("skips existing ignored terms when seeding") {
                    ignoredTermRepository.save(
                        IgnoredTerm(
                            name = "하다",
                            reason = "existing reason",
                            createdAt = LocalDateTime.now(),
                        ),
                    )

                    dataSeeder.run(DefaultApplicationArguments())

                    val ignoredTerm = ignoredTermRepository.findByName("하다")!!
                    ignoredTerm.reason shouldBe "existing reason"
                }

                it("creates index if not exists before seeding documents") {
                    val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
                    indexOps.exists() shouldBe false

                    dataSeeder.run(DefaultApplicationArguments())

                    indexOps.exists() shouldBe true
                }

                it("seeds documents even when index already exists") {
                    val indexOps = elasticsearchOperations.indexOps(SourceDocument::class.java)
                    indexOps.createWithMapping()
                    indexOps.exists() shouldBe true

                    dataSeeder.run(DefaultApplicationArguments())

                    indexOps.exists() shouldBe true
                }
            }
        }
    }
}
