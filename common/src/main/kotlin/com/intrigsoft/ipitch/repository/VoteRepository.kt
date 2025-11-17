package com.intrigsoft.ipitch.repository

import com.intrigsoft.ipitch.domain.Vote
import com.intrigsoft.ipitch.domain.VoteTargetType
import com.intrigsoft.ipitch.domain.VoteType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface VoteRepository : JpaRepository<Vote, UUID> {
    fun findByUserIdAndTargetTypeAndTargetId(
        userId: UUID,
        targetType: VoteTargetType,
        targetId: UUID
    ): Vote?

    fun findByTargetTypeAndTargetId(
        targetType: VoteTargetType,
        targetId: UUID
    ): List<Vote>

    fun countByTargetTypeAndTargetIdAndVoteType(
        targetType: VoteTargetType,
        targetId: UUID,
        voteType: VoteType
    ): Long

    @Query("""
        SELECT COUNT(v) FROM Vote v
        WHERE v.targetType = :targetType
        AND v.targetId = :targetId
        AND v.voteType = 'UP'
    """)
    fun countUpvotes(targetType: VoteTargetType, targetId: UUID): Long

    @Query("""
        SELECT COUNT(v) FROM Vote v
        WHERE v.targetType = :targetType
        AND v.targetId = :targetId
        AND v.voteType = 'DOWN'
    """)
    fun countDownvotes(targetType: VoteTargetType, targetId: UUID): Long

    @Query("""
        SELECT
            CAST(SUM(CASE WHEN v.voteType = 'UP' THEN 1 ELSE 0 END) -
            SUM(CASE WHEN v.voteType = 'DOWN' THEN 1 ELSE 0 END) AS long)
        FROM Vote v
        WHERE v.targetType = :targetType
        AND v.targetId = :targetId
    """)
    fun getVoteScore(targetType: VoteTargetType, targetId: UUID): Long?

    fun deleteByUserIdAndTargetTypeAndTargetId(
        userId: UUID,
        targetType: VoteTargetType,
        targetId: UUID
    )
}
