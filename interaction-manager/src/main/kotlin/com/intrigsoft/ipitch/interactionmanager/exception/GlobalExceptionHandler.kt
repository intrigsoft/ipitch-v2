package com.intrigsoft.ipitch.interactionmanager.exception

import com.intrigsoft.ipitch.interactionmanager.dto.response.ApiResponse
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(CommentNotFoundException::class)
    fun handleCommentNotFoundException(ex: CommentNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Comment not found: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Comment not found"))
    }

    @ExceptionHandler(VoteNotFoundException::class)
    fun handleVoteNotFoundException(ex: VoteNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Vote not found: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Vote not found"))
    }

    @ExceptionHandler(InferredEntityNotFoundException::class)
    fun handleInferredEntityNotFoundException(ex: InferredEntityNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Inferred entity not found: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message ?: "Inferred entity not found"))
    }

    @ExceptionHandler(DuplicateVoteException::class)
    fun handleDuplicateVoteException(ex: DuplicateVoteException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Duplicate vote attempt: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.message ?: "Duplicate vote"))
    }

    @ExceptionHandler(UnauthorizedOperationException::class)
    fun handleUnauthorizedOperationException(ex: UnauthorizedOperationException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Unauthorized operation: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(ex.message ?: "Unauthorized operation"))
    }

    @ExceptionHandler(InvalidOperationException::class)
    fun handleInvalidOperationException(ex: InvalidOperationException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn { "Invalid operation: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.message ?: "Invalid operation"))
    }

    @ExceptionHandler(ElasticsearchSyncException::class)
    fun handleElasticsearchSyncException(ex: ElasticsearchSyncException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error(ex) { "Elasticsearch sync error: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("Search index sync failed: ${ex.message}"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val errors = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        logger.warn { "Validation error: $errors" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Validation failed: $errors"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.error(ex) { "Unexpected error: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred: ${ex.message}"))
    }
}
