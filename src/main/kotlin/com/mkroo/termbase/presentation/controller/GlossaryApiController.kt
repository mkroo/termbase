package com.mkroo.termbase.presentation.controller

import com.mkroo.termbase.application.service.SynonymAddResult
import com.mkroo.termbase.application.service.SynonymService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/glossary")
class GlossaryApiController(
    private val synonymService: SynonymService,
) {
    @PostMapping("/{termName}/synonyms")
    fun addSynonym(
        @PathVariable termName: String,
        @RequestBody request: AddSynonymRequest,
    ): ResponseEntity<ApiResponse> =
        when (val result = synonymService.addSynonym(termName, request.synonymName)) {
            is SynonymAddResult.Success -> {
                ResponseEntity.ok(ApiResponse(true, "동의어가 추가되었습니다."))
            }

            is SynonymAddResult.TermNotFound -> {
                ResponseEntity
                    .badRequest()
                    .body(ApiResponse(false, "존재하지 않는 용어입니다: ${result.termName}"))
            }

            is SynonymAddResult.SameAsCanonical -> {
                ResponseEntity
                    .badRequest()
                    .body(ApiResponse(false, "동의어는 대표어와 같을 수 없습니다."))
            }

            is SynonymAddResult.AlreadyExistsAsTerm -> {
                ResponseEntity
                    .badRequest()
                    .body(ApiResponse(false, "이미 용어로 등록된 단어입니다: ${result.synonymName}"))
            }

            is SynonymAddResult.AlreadyExistsAsSynonym -> {
                ResponseEntity
                    .badRequest()
                    .body(ApiResponse(false, "이미 동의어로 등록된 단어입니다: ${result.synonymName}"))
            }

            is SynonymAddResult.AlreadyExistsAsIgnored -> {
                ResponseEntity
                    .badRequest()
                    .body(ApiResponse(false, "이미 무시된 단어로 등록되어 있습니다: ${result.synonymName}"))
            }
        }

    @DeleteMapping("/{termName}/synonyms/{synonymName}")
    fun removeSynonym(
        @PathVariable termName: String,
        @PathVariable synonymName: String,
    ): ResponseEntity<ApiResponse> =
        try {
            synonymService.removeSynonym(termName, synonymName)
            ResponseEntity.ok(ApiResponse(true, "동의어가 삭제되었습니다."))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message!!))
        }
}

data class AddSynonymRequest(
    val synonymName: String,
)

data class ApiResponse(
    val success: Boolean,
    val message: String,
)
