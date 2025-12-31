package com.mkroo.termbase.domain.repository

import com.mkroo.termbase.domain.model.term.Term
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface TermRepository : Repository<Term, Long> {
    fun save(term: Term): Term

    fun findByName(name: String): Term?

    fun existsByName(name: String): Boolean

    fun delete(term: Term)

    fun findAll(): List<Term>

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Synonym s WHERE s.name = :name")
    fun existsBySynonymName(
        @Param("name") name: String,
    ): Boolean
}
