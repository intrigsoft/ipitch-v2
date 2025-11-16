package com.example.ipitch.proposalmanager.domain

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    val userId: UUID? = null,

    @Column(nullable = false)
    val userName: String,

    @Column(nullable = false, unique = true)
    val email: String
)
