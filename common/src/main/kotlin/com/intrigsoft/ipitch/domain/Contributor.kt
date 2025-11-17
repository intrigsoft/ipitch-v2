package com.intrigsoft.ipitch.domain

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "contributors")
data class Contributor(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    val role: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: ContributorStatus = ContributorStatus.PENDING,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false)
    val proposal: Proposal? = null
)
