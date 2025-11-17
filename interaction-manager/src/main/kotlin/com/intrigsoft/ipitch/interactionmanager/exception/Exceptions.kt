package com.intrigsoft.ipitch.interactionmanager.exception

import java.util.*

class CommentNotFoundException(id: UUID) : RuntimeException("Comment not found with id: $id")

class VoteNotFoundException(id: UUID) : RuntimeException("Vote not found with id: $id")

class InferredEntityNotFoundException(id: UUID) : RuntimeException("Inferred entity not found with id: $id")

class DuplicateVoteException(userId: UUID, targetType: String, targetId: UUID) :
    RuntimeException("User $userId has already voted on $targetType $targetId")

class UnauthorizedOperationException(message: String) : RuntimeException(message)

class InvalidOperationException(message: String) : RuntimeException(message)

class ElasticsearchSyncException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
