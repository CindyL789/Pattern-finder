package com.example.data.model

import com.example.data.db.PatternEntity

data class AiGeneratedPattern(
    val id: String,
    val prompt: String,
    val title: String,
    val subtitle: String,
    val category: String, // Amigurumi, Wearables, Blankets, Accessories, Home Decor
    val difficulty: String, // Beginner, Easy, Intermediate, Advanced
    val estimatedTime: String,
    val hookSize: String,
    val yarnWeight: String,
    val yarnColors: List<String> = emptyList(),
    val estimatedYardage: String = "150-250 yards",
    val gauge: String = "16 sts x 18 rows = 4 inches (10 cm)",
    val finishedDimensions: String = "Approx. 8\" x 6\"",
    val notions: List<String> = listOf("Tapestry needle", "Stitch markers", "Scissors"),
    val keyStitches: List<String> = listOf("Magic Ring (MR)", "Single Crochet (sc)", "Increase (inc)", "Decrease (dec)"),
    val sections: List<AiPatternSection> = emptyList(),
    val assemblyNotes: String = "",
    val tips: List<String> = emptyList(),
    val careInstructions: String = "Hand wash cool, lay flat to dry.",
    val imageBase64: String? = null,
    val imageUrl: String? = null,
    val swatchHexColors: List<String> = listOf("#E07A5F", "#F4F1DE", "#81B29A", "#3D405B"),
    val createdAt: Long = System.currentTimeMillis(),
    val isSaved: Boolean = false
) {
    fun totalRoundsOrRows(): Int {
        return sections.sumOf { it.steps.size }
    }

    fun toFormattedMarkdown(): String {
        val sb = StringBuilder()
        sb.appendLine("# $title")
        sb.appendLine("*$subtitle*")
        sb.appendLine()
        sb.appendLine("## 📋 Pattern Overview")
        sb.appendLine("- **Category:** $category")
        sb.appendLine("- **Difficulty:** $difficulty")
        sb.appendLine("- **Estimated Time:** $estimatedTime")
        sb.appendLine("- **Finished Size:** $finishedDimensions")
        sb.appendLine("- **Gauge:** $gauge")
        sb.appendLine()
        sb.appendLine("## 🧶 Materials & Tools")
        sb.appendLine("- **Hook Size:** $hookSize")
        sb.appendLine("- **Yarn Weight:** $yarnWeight")
        if (yarnColors.isNotEmpty()) {
            sb.appendLine("- **Yarn Colors:** ${yarnColors.joinToString(", ")}")
        }
        sb.appendLine("- **Yardage:** $estimatedYardage")
        if (notions.isNotEmpty()) {
            sb.appendLine("- **Notions:** ${notions.joinToString(", ")}")
        }
        sb.appendLine()
        sb.appendLine("## 🪡 Stitches & Abbreviations")
        keyStitches.forEach { sb.appendLine("- $it") }
        sb.appendLine()
        sb.appendLine("## 📜 Step-by-Step Instructions")
        sections.forEach { section ->
            sb.appendLine("### ${section.sectionTitle}")
            if (section.sectionNotes.isNotBlank()) {
                sb.appendLine("*${section.sectionNotes}*")
            }
            section.steps.forEach { step ->
                val stitchCountStr = if (step.stitchCount.isNotBlank()) " [${step.stitchCount}]" else ""
                sb.appendLine("- **${step.stepLabel}:** ${step.instruction}$stitchCountStr")
                if (!step.tipOrNote.isNullOrBlank()) {
                    sb.appendLine("  *💡 Tip: ${step.tipOrNote}*")
                }
            }
            sb.appendLine()
        }
        if (assemblyNotes.isNotBlank()) {
            sb.appendLine("## 🪡 Assembly & Finishing")
            sb.appendLine(assemblyNotes)
            sb.appendLine()
        }
        if (tips.isNotEmpty()) {
            sb.appendLine("## 💡 Crafter Pro Tips")
            tips.forEach { sb.appendLine("- $it") }
            sb.appendLine()
        }
        sb.appendLine("## 🧼 Care Instructions")
        sb.appendLine(careInstructions)
        return sb.toString()
    }

    fun toProject(): Project {
        val totalSteps = totalRoundsOrRows().coerceAtLeast(1)
        val formattedPattern = toFormattedMarkdown()
        return Project(
            id = 0,
            title = title,
            category = category,
            status = "Active",
            currentRow = 1,
            targetRows = totalSteps,
            currentStitchInRow = 0,
            targetStitchesInRow = 0,
            repeatCount = 1,
            targetRepeats = 1,
            hookSize = hookSize,
            yarnDetails = "$yarnWeight • ${yarnColors.joinToString(", ").ifBlank { "Custom Colors" }}",
            notes = "AI-generated from prompt: \"$prompt\"\n\n$subtitle",
            patternText = formattedPattern,
            currentPatternStepIndex = 0,
            totalMinutesSpent = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun toPatternEntity(): PatternEntity {
        return PatternEntity(
            id = id,
            title = title,
            authorOrSource = "Stitch Mind AI Studio",
            description = "$subtitle\n\nPrompt: \"$prompt\"",
            category = category,
            difficulty = difficulty,
            isFree = true,
            primaryLink = "local://ai_pattern/$id",
            sourcePlatform = "AI Generated",
            hookSize = hookSize,
            yarnWeight = yarnWeight,
            estimatedTime = estimatedTime,
            keyStitchesCsv = keyStitches.joinToString(", "),
            userNotes = toFormattedMarkdown(),
            isCustomUserAdded = true
        )
    }
}

data class AiPatternSection(
    val sectionTitle: String,
    val sectionNotes: String = "",
    val steps: List<AiPatternStep> = emptyList()
)

data class AiPatternStep(
    val stepIndex: Int,
    val stepLabel: String, // e.g., "Rnd 1", "Rnd 2-4", "Row 1"
    val instruction: String,
    val stitchCount: String = "", // e.g. "6 sts", "12 sts", "24 sts"
    val tipOrNote: String? = null,
    val isCompleted: Boolean = false
)
