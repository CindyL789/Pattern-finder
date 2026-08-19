package com.example.data.model

data class CrochetPattern(
    val id: String,
    val title: String,
    val authorOrSource: String,
    val description: String,
    val category: String, // e.g. "Amigurumi", "Wearables", "Blankets", "Accessories", "Home Decor"
    val difficulty: String, // e.g. "Beginner", "Easy", "Intermediate", "Advanced"
    val isFree: Boolean,
    val primaryLink: String,
    val sourcePlatform: String, // e.g. "Ravelry", "YouTube", "LoveCrafts", "Etsy", "Pinterest", "Web Blog"
    val secondaryLinks: List<PatternLink> = emptyList(),
    val hookSize: String = "4.0 mm (G-6)",
    val yarnWeight: String = "Worsted / Medium",
    val estimatedTime: String = "3-5 hours",
    val keyStitches: List<String> = listOf("Single Crochet", "Double Crochet", "Magic Ring"),
    val isSaved: Boolean = false,
    val userNotes: String = ""
)

data class PatternLink(
    val platformName: String, // e.g., "Google Search", "YouTube Video", "Ravelry", "Etsy", "Pinterest"
    val url: String,
    val iconType: String = "web"
)
