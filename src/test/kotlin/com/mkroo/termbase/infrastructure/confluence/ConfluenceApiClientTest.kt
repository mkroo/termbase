package com.mkroo.termbase.infrastructure.confluence

import com.mkroo.termbase.infrastructure.config.ConfluenceProperties
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe

class ConfluenceApiClientTest :
    DescribeSpec({
        describe("ConfluenceApiClient") {
            val properties =
                ConfluenceProperties(
                    baseUrl = "https://test.atlassian.net",
                    email = "test@example.com",
                    apiToken = "test-api-token",
                )
            val client = ConfluenceApiClient(properties)

            describe("instance creation") {
                it("should create an instance") {
                    client shouldNotBe null
                }
            }
        }
    })
