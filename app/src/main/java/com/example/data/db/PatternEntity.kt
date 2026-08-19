package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_patterns")
data class PatternEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authorOrSource: String,
    val description: String,
    val category: String,
    val difficulty: String,
    val isFree: Boolean,
    val primaryLink: String,
    val sourcePlatform: String,
    val hookSize: String,
    val yarnWeight: String,
    val estimatedTime: String,
    val keyStitchesCsv: String,
    val savedAtTimestamp: Long = System.currentTimeMillis(),
    val userNotes: String = "",
    val isCustomUserAdded: Boolean = false
)
