package com.intrigsoft.ipitch.proposalmanager.exception

import com.intrigsoft.ipitch.proposalmanager.dto.response.ApiResponse
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ProposalNotFoundException::class)
    fun handleProposalNotFound(e: ProposalNotFoundException): ResponseEntity<ApiResponse<Unit>> {
        logger.warn { "Proposal not found: ${e.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiResponse(success = false, message = e.message ?: "Proposal not found")
        )
    }

    @ExceptionHandler(ContributorNotFoundException::class)
    fun handleContributorNotFound(e: ContributorNotFoundException): ResponseEntity<ApiResponse<Unit>> {
        logger.warn { "Contributor not found: ${e.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiResponse(success = false, message = e.message ?: "Contributor not found")
        )
    }

    @ExceptionHandler(UnauthorizedOperationException::class)
    fun handleUnauthorized(e: UnauthorizedOperationException): ResponseEntity<ApiResponse<Unit>> {
        logger.warn { "Unauthorized operation: ${e.message}" }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiResponse(success = false, message = e.message ?: "Unauthorized operation")
        )
    }

    @ExceptionHandler(InvalidOperationException::class)
    fun handleInvalidOperation(e: InvalidOperationException): ResponseEntity<ApiResponse<Unit>> {
        logger.warn { "Invalid operation: ${e.message}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse(success = false, message = e.message ?: "Invalid operation")
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val errors = e.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        logger.warn { "Validation error: $errors" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse(success = false, message = errors.joinToString(", "))
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ApiResponse<Unit>> {
        logger.error(e) { "Unexpected error: ${e.message}" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponse(success = false, message = "An unexpected error occurred: ${e.message}")
        )
    }
}
