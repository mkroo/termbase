package com.mkroo.termbase.infrastructure.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ConfluenceConfigTest :
    DescribeSpec({
        describe("ConfluenceProperties") {
            it("기본값으로 생성할 수 있다") {
                val properties = ConfluenceProperties()

                properties.clientId shouldBe ""
                properties.clientSecret shouldBe ""
                properties.redirectUri shouldBe "http://localhost:8080/confluence/oauth/callback"
            }

            it("값을 지정하여 생성할 수 있다") {
                val properties =
                    ConfluenceProperties(
                        clientId = "test-client-id",
                        clientSecret = "test-client-secret",
                        redirectUri = "https://example.com/callback",
                    )

                properties.clientId shouldBe "test-client-id"
                properties.clientSecret shouldBe "test-client-secret"
                properties.redirectUri shouldBe "https://example.com/callback"
            }
        }
    })
