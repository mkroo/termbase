package com.mkroo.termbase.infrastructure.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SlackConfigTest :
    DescribeSpec({
        describe("SlackProperties") {
            it("기본값으로 생성할 수 있다") {
                val properties = SlackProperties()

                properties.botToken shouldBe ""
                properties.workspaceId shouldBe ""
            }

            it("값을 지정하여 생성할 수 있다") {
                val properties =
                    SlackProperties(
                        botToken = "xoxb-test-token",
                        workspaceId = "T-test-workspace",
                    )

                properties.botToken shouldBe "xoxb-test-token"
                properties.workspaceId shouldBe "T-test-workspace"
            }
        }
    })
