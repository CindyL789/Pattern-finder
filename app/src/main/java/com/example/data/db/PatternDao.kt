package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PatternDao {
    @Query("SELECT * FROM saved_patterns ORDER BY savedAtTimestamp DESC")
    fun getAllSavedPatterns(): Flow<List<PatternEntity>>

    @Query("SELECT * FROM saved_patterns WHERE id = :id LIMIT 1")
    suspend fun getPatternById(id: String): PatternEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: PatternEntity)

    @Query("DELETE FROM saved_patterns WHERE id = :id")
    suspend fun deletePatternById(id: String)

    @Update
    suspend fun updatePattern(pattern: PatternEntity)
}
