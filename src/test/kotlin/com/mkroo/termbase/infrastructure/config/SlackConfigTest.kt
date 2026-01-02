package com.mkroo.termbase.infrastructure.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SlackConfigTest :
    DescribeSpec({
        describe("SlackProperties") {
            it("기본값으로 생성할 수 있다") {
                val properties = SlackProperties()

                properties.botToken shouldBe ""
                properties.signingSecret shouldBe ""
            }

            it("값을 지정하여 생성할 수 있다") {
                val properties =
                    SlackProperties(
                        botToken = "xoxb-test-token",
                        signingSecret = "test-signing-secret",
                    )

                properties.botToken shouldBe "xoxb-test-token"
                properties.signingSecret shouldBe "test-signing-secret"
            }
        }
    })
