package com.mkroo.termbase.infrastructure.confluence

import com.mkroo.termbase.infrastructure.config.ConfluenceProperties
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

class ConfluenceOAuthClientTest :
    DescribeSpec({
        describe("ConfluenceOAuthClient") {
            val properties =
                ConfluenceProperties(
                    clientId = "test-client-id",
                    clientSecret = "test-client-secret",
                    redirectUri = "http://localhost:8080/callback",
                )
            val client = ConfluenceOAuthClient(properties)

            describe("buildAuthorizationUrl") {
                it("should build correct authorization URL") {
                    val url = client.buildAuthorizationUrl("test-state")

                    url shouldStartWith "https://auth.atlassian.com/authorize"
                    url shouldContain "client_id=test-client-id"
                    url shouldContain "redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fcallback"
                    url shouldContain "state=test-state"
                    url shouldContain "response_type=code"
                    url shouldContain "prompt=consent"
                }

                it("should include required scopes") {
                    val url = client.buildAuthorizationUrl("test-state")

                    url shouldContain "scope="
                    url shouldContain "read%3Aconfluence-space.summary"
                    url shouldContain "read%3Aconfluence-content.summary"
                    url shouldContain "read%3Aconfluence-content.all"
                    url shouldContain "offline_access"
                }

                it("should include audience parameter") {
                    val url = client.buildAuthorizationUrl("test-state")

                    url shouldContain "audience=api.atlassian.com"
                }
            }
        }

        describe("ConfluenceOAuthException") {
            it("should create exception with message") {
                val exception = ConfluenceOAuthException("Test error")

                exception.message shouldBe "Test error"
            }

            it("should create exception with message and cause") {
                val cause = RuntimeException("Root cause")
                val exception = ConfluenceOAuthException("Test error", cause)

                exception.message shouldBe "Test error"
                exception.cause shouldBe cause
            }
        }
    })
