package com.mkroo.termbase

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    // "demo" profile skips data seeding (DataSeeder only runs with "local" profile)
    System.setProperty("spring.profiles.active", "demo")
    fromApplication<TermbaseApplication>()
        .with(TestcontainersConfiguration::class)
        .run(*args)
}
