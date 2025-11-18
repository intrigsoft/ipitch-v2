package com.intrigsoft.ipitch.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "proposals")
data class Proposal(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(nullable = false)
    val ownerId: String, // Keycloak user ID

    @OneToMany(mappedBy = "proposal", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val contributors: MutableList<Contributor> = mutableListOf(),

    @Column(nullable = false)
    var version: String = "0.0.0",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProposalStatus = ProposalStatus.DRAFT,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var stats: Map<String, Any> = emptyMap(),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    // Git-related fields
    @Column(nullable = false)
    var workingBranch: String? = null,

    @Column
    var gitCommitHash: String? = null
)
