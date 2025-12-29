package com.mkroo.termbase

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<TermbaseApplication>().with(TestcontainersConfiguration::class).run(*args)
}
