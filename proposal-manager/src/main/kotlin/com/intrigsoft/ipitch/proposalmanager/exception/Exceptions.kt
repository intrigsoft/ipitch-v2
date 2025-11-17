package com.intrigsoft.ipitch.proposalmanager.exception

class ProposalNotFoundException(message: String) : RuntimeException(message)

class ContributorNotFoundException(message: String) : RuntimeException(message)

class UnauthorizedOperationException(message: String) : RuntimeException(message)

class InvalidOperationException(message: String) : RuntimeException(message)
