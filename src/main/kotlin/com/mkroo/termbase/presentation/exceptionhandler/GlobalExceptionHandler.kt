package com.mkroo.termbase.presentation.exceptionhandler

import com.mkroo.termbase.presentation.controller.IgnoredTermNotFoundException
import com.mkroo.termbase.presentation.controller.TermNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus

@ControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(TermNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleTermNotFoundException(ex: TermNotFoundException): String = "error/404"

    @ExceptionHandler(IgnoredTermNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleIgnoredTermNotFoundException(ex: IgnoredTermNotFoundException): String = "error/404"
}
