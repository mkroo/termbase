package com.mkroo.termbase

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TermbaseApplication

fun main(args: Array<String>) {
    runApplication<TermbaseApplication>(*args)
}
