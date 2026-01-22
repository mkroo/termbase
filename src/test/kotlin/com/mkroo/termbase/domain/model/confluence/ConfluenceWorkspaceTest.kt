package com.mkroo.termbase.domain.model.confluence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant

class ConfluenceWorkspaceTest :
    DescribeSpec({
        describe("ConfluenceWorkspace") {
            val fixedInstant = Instant.parse("2024-01-15T10:00:00Z")

            fun createWorkspace() =
                ConfluenceWorkspace(
                    siteId = "test-site",
                    siteName = "Test Site",
                    connectedAt = fixedInstant,
                )

            describe("addSpace") {
                it("should add a new space") {
                    val workspace = createWorkspace()

                    val space =
                        workspace.addSpace(
                            spaceId = "space-1",
                            spaceKey = "DEV",
                            name = "Development",
                        )

                    workspace.spaces shouldHaveSize 1
                    space.spaceId shouldBe "space-1"
                    space.spaceKey shouldBe "DEV"
                    space.name shouldBe "Development"
                    space.isSelected shouldBe false
                    space.workspace shouldBe workspace
                }

                it("should throw exception when adding duplicate space") {
                    val workspace = createWorkspace()
                    workspace.addSpace("space-1", "DEV", "Development")

                    shouldThrow<IllegalArgumentException> {
                        workspace.addSpace("space-1", "DEV2", "Development 2")
                    }
                }
            }

            describe("syncSpaces") {
                it("should add new spaces from remote") {
                    val workspace = createWorkspace()
                    val remoteSpaces =
                        listOf(
                            RemoteSpace("space-1", "DEV", "Development"),
                            RemoteSpace("space-2", "PROD", "Production"),
                        )

                    workspace.syncSpaces(remoteSpaces)

                    workspace.spaces shouldHaveSize 2
                    workspace.spaces.map { it.spaceId } shouldContainExactly listOf("space-1", "space-2")
                }

                it("should remove spaces not in remote") {
                    val workspace = createWorkspace()
                    workspace.addSpace("space-1", "DEV", "Development")
                    workspace.addSpace("space-2", "PROD", "Production")

                    val remoteSpaces =
                        listOf(
                            RemoteSpace("space-1", "DEV", "Development"),
                        )

                    workspace.syncSpaces(remoteSpaces)

                    workspace.spaces shouldHaveSize 1
                    workspace.spaces.first().spaceId shouldBe "space-1"
                }

                it("should update existing space name") {
                    val workspace = createWorkspace()
                    workspace.addSpace("space-1", "DEV", "Development")

                    val remoteSpaces =
                        listOf(
                            RemoteSpace("space-1", "DEV", "Development Updated"),
                        )

                    workspace.syncSpaces(remoteSpaces)

                    workspace.spaces shouldHaveSize 1
                    workspace.spaces.first().name shouldBe "Development Updated"
                }

                it("should preserve selection when updating spaces") {
                    val workspace = createWorkspace()
                    workspace.addSpace("space-1", "DEV", "Development")
                    workspace.selectSpace("DEV")

                    val remoteSpaces =
                        listOf(
                            RemoteSpace("space-1", "DEV", "Development Updated"),
                        )

                    workspace.syncSpaces(remoteSpaces)

                    workspace.spaces.first().isSelected shouldBe true
                }

                it("should handle empty remote spaces") {
                    val workspace = createWorkspace()
                    workspace.addSpace("space-1", "DEV", "Development")

                    workspace.syncSpaces(emptyList())

                    workspace.spaces.shouldBeEmpty()
                }
            }

            describe("selectSpace") {
                it("should select a space by key") {
                    val workspace = createWorkspace()
                    workspace.addSpace("space-1", "DEV", "Development")

                    workspace.selectSpace("DEV")

                    workspace.spaces.first().isSelected shouldBe true
                }

                it("should throw exception when space not found") {
                    val workspace = createWorkspace()

                    shouldThrow<IllegalArgumentException> {
                        workspace.selectSpace("NONEXISTENT")
                    }
                }
            }

            describe("deselectSpace") {
                it("should deselect a space by key") {
                    val workspace = createWorkspace()
                    workspace.addSpace("space-1", "DEV", "Development")
                    workspace.selectSpace("DEV")

                    workspace.deselectSpace("DEV")

                    workspace.spaces.first().isSelected shouldBe false
                }

                it("should throw exception when space not found") {
                    val workspace = createWorkspace()

                    shouldThrow<IllegalArgumentException> {
                        workspace.deselectSpace("NONEXISTENT")
                    }
                }
            }

            describe("selectedSpaces") {
                it("should return only selected spaces") {
                    val workspace = createWorkspace()
                    workspace.addSpace("space-1", "DEV", "Development")
                    workspace.addSpace("space-2", "PROD", "Production")
                    workspace.selectSpace("DEV")

                    workspace.selectedSpaces shouldHaveSize 1
                    workspace.selectedSpaces.first().spaceKey shouldBe "DEV"
                }

                it("should return empty list when no spaces selected") {
                    val workspace = createWorkspace()
                    workspace.addSpace("space-1", "DEV", "Development")

                    workspace.selectedSpaces.shouldBeEmpty()
                }
            }

            describe("spaces") {
                it("should return immutable copy of spaces") {
                    val workspace = createWorkspace()
                    workspace.addSpace("space-1", "DEV", "Development")

                    val spaces = workspace.spaces

                    spaces shouldHaveSize 1
                }
            }
        }
    })
