package com.mkroo.termbase

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class TermbaseApplicationTests : DescribeSpec() {
    init {
        extension(SpringExtension())

        describe("TermbaseApplication") {
            it("should load context") {
            }
        }
    }
}
