package com.mkroo.termbase

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    // "demo" profile skips data seeding (DataSeeder only runs with "local" profile)
    // "secrets" profile loads local secrets from application-secrets.yml
    val existingProfiles = System.getProperty("spring.profiles.active") ?: ""
    val profiles =
        if (existingProfiles.isNotBlank()) {
            "demo,$existingProfiles"
        } else {
            "demo,secrets"
        }
    System.setProperty("spring.profiles.active", profiles)
    fromApplication<TermbaseApplication>()
        .with(TestcontainersConfiguration::class)
        .run(*args)
}
