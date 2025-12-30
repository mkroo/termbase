package com.mkroo.termbase

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
class TermbaseApplicationTests : DescribeSpec() {
    init {
        extension(SpringExtension())

        describe("TermbaseApplication") {
            it("should load context") {
            }
        }
    }
}
