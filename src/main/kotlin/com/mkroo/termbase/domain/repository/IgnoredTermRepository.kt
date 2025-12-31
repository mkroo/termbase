package com.mkroo.termbase.domain.repository

import com.mkroo.termbase.domain.model.ignoredterm.IgnoredTerm
import org.springframework.data.repository.Repository

interface IgnoredTermRepository : Repository<IgnoredTerm, Long> {
    fun save(ignoredTerm: IgnoredTerm): IgnoredTerm

    fun findByName(name: String): IgnoredTerm?

    fun existsByName(name: String): Boolean

    fun delete(ignoredTerm: IgnoredTerm)

    fun findAll(): List<IgnoredTerm>
}
