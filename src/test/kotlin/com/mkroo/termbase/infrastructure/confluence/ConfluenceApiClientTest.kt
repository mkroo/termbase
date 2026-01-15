package com.mkroo.termbase.infrastructure.confluence

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe

class ConfluenceApiClientTest :
    DescribeSpec({
        describe("ConfluenceApiClient") {
            val client = ConfluenceApiClient()

            describe("instance creation") {
                it("should create an instance") {
                    client shouldNotBe null
                }
            }
        }
    })
