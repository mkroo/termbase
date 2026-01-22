package com.mkroo.termbase.infrastructure.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ConfluenceConfigTest :
    DescribeSpec({
        describe("ConfluenceProperties") {
            it("기본값으로 생성할 수 있다") {
                val properties = ConfluenceProperties()

                properties.baseUrl shouldBe ""
                properties.email shouldBe ""
                properties.apiToken shouldBe ""
            }

            it("값을 지정하여 생성할 수 있다") {
                val properties =
                    ConfluenceProperties(
                        baseUrl = "https://test.atlassian.net",
                        email = "test@example.com",
                        apiToken = "test-api-token",
                    )

                properties.baseUrl shouldBe "https://test.atlassian.net"
                properties.email shouldBe "test@example.com"
                properties.apiToken shouldBe "test-api-token"
            }

            it("isConfigured는 모든 필드가 설정되어 있을 때 true를 반환한다") {
                val properties =
                    ConfluenceProperties(
                        baseUrl = "https://test.atlassian.net",
                        email = "test@example.com",
                        apiToken = "test-api-token",
                    )

                properties.isConfigured() shouldBe true
            }

            it("isConfigured는 하나라도 비어있으면 false를 반환한다") {
                val properties = ConfluenceProperties(baseUrl = "https://test.atlassian.net")

                properties.isConfigured() shouldBe false
            }

            it("getSiteId는 baseUrl에서 사이트 ID를 추출한다") {
                val properties =
                    ConfluenceProperties(
                        baseUrl = "https://mycompany.atlassian.net",
                        email = "test@example.com",
                        apiToken = "test-api-token",
                    )

                properties.getSiteId() shouldBe "mycompany"
            }

            it("getBasicAuthHeader는 올바른 Basic Auth 헤더를 생성한다") {
                val properties =
                    ConfluenceProperties(
                        baseUrl = "https://test.atlassian.net",
                        email = "test@example.com",
                        apiToken = "test-api-token",
                    )

                val header = properties.getBasicAuthHeader()

                header shouldBe "Basic dGVzdEBleGFtcGxlLmNvbTp0ZXN0LWFwaS10b2tlbg=="
            }
        }
    })
