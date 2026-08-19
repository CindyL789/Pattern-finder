package com.example.data.repository

import com.example.data.db.PatternDao
import com.example.data.db.PatternEntity
import com.example.data.model.CrochetPattern
import com.example.data.model.PatternLink
import com.example.data.remote.GeminiSearchService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URLEncoder

class PatternRepository(private val patternDao: PatternDao) {

    val savedPatternsFlow: Flow<List<CrochetPattern>> = patternDao.getAllSavedPatterns().map { entities ->
        entities.map { entity ->
            val titleEncoded = URLEncoder.encode("${entity.title} crochet pattern", "UTF-8")
            CrochetPattern(
                id = entity.id,
                title = entity.title,
                authorOrSource = entity.authorOrSource,
                description = entity.description,
                category = entity.category,
                difficulty = entity.difficulty,
                isFree = entity.isFree,
                primaryLink = entity.primaryLink,
                sourcePlatform = entity.sourcePlatform,
                secondaryLinks = listOf(
                    PatternLink("Google Search", "https://www.google.com/search?q=$titleEncoded", "google"),
                    PatternLink("Ravelry", "https://www.ravelry.com/patterns/search#query=$titleEncoded", "ravelry"),
                    PatternLink("YouTube", "https://www.youtube.com/results?search_query=$titleEncoded", "youtube"),
                    PatternLink("Pinterest", "https://www.pinterest.com/search/pins/?q=$titleEncoded", "pinterest"),
                    PatternLink("Etsy", "https://www.etsy.com/search?q=$titleEncoded", "etsy")
                ),
                hookSize = entity.hookSize,
                yarnWeight = entity.yarnWeight,
                estimatedTime = entity.estimatedTime,
                keyStitches = entity.keyStitchesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                isSaved = true,
                userNotes = entity.userNotes
            )
        }
    }

    suspend fun savePattern(pattern: CrochetPattern, notes: String = "") {
        val entity = PatternEntity(
            id = pattern.id,
            title = pattern.title,
            authorOrSource = pattern.authorOrSource,
            description = pattern.description,
            category = pattern.category,
            difficulty = pattern.difficulty,
            isFree = pattern.isFree,
            primaryLink = pattern.primaryLink,
            sourcePlatform = pattern.sourcePlatform,
            hookSize = pattern.hookSize,
            yarnWeight = pattern.yarnWeight,
            estimatedTime = pattern.estimatedTime,
            keyStitchesCsv = pattern.keyStitches.joinToString(", "),
            userNotes = notes.ifBlank { pattern.userNotes }
        )
        patternDao.insertPattern(entity)
    }

    suspend fun removePattern(patternId: String) {
        patternDao.deletePatternById(patternId)
    }

    suspend fun updatePatternNotes(patternId: String, notes: String) {
        val existing = patternDao.getPatternById(patternId)
        if (existing != null) {
            patternDao.updatePattern(existing.copy(userNotes = notes))
        }
    }

    suspend fun searchPatterns(
        query: String,
        selectedCategory: String = "All",
        selectedDifficulty: String = "All",
        selectedFreeOnly: Boolean = false,
        useAiSearch: Boolean = true
    ): List<CrochetPattern> {
        val cleanQuery = query.trim()

        // If query is present and AI search enabled, attempt Gemini AI generation
        if (cleanQuery.isNotEmpty() && useAiSearch) {
            val aiResults = GeminiSearchService.searchPatternsWithGemini(cleanQuery)
            if (!aiResults.isNullOrEmpty()) {
                return applyFilters(aiResults, selectedCategory, selectedDifficulty, selectedFreeOnly)
            }
        }

        // Filter local curated database
        val matchedCurated = getCuratedCatalog().filter { pattern ->
            if (cleanQuery.isBlank()) true
            else {
                pattern.title.contains(cleanQuery, ignoreCase = true) ||
                pattern.description.contains(cleanQuery, ignoreCase = true) ||
                pattern.authorOrSource.contains(cleanQuery, ignoreCase = true) ||
                pattern.category.contains(cleanQuery, ignoreCase = true) ||
                pattern.keyStitches.any { it.contains(cleanQuery, ignoreCase = true) }
            }
        }

        val filtered = applyFilters(matchedCurated, selectedCategory, selectedDifficulty, selectedFreeOnly)

        // If no local results matched a non-empty query, generate fallback dynamic links for the query
        if (filtered.isEmpty() && cleanQuery.isNotEmpty()) {
            return generateDynamicPatternResultsForQuery(cleanQuery)
        }

        return filtered
    }

    private fun applyFilters(
        list: List<CrochetPattern>,
        category: String,
        difficulty: String,
        freeOnly: Boolean
    ): List<CrochetPattern> {
        return list.filter { pattern ->
            val matchesCategory = (category == "All" || pattern.category.equals(category, ignoreCase = true))
            val matchesDifficulty = (difficulty == "All" || pattern.difficulty.equals(difficulty, ignoreCase = true))
            val matchesFree = (!freeOnly || pattern.isFree)
            matchesCategory && matchesDifficulty && matchesFree
        }
    }

    private fun generateDynamicPatternResultsForQuery(userQuery: String): List<CrochetPattern> {
        val qEncoded = URLEncoder.encode("$userQuery crochet pattern free", "UTF-8")
        val qShort = userQuery.replaceFirstChar { it.uppercase() }

        return listOf(
            CrochetPattern(
                id = "dyn_rav_${System.currentTimeMillis()}",
                title = "$qShort Pattern Collection",
                authorOrSource = "Ravelry Database",
                description = "Extensive search results and downloadable PDFs for $userQuery on Ravelry.",
                category = "Accessories",
                difficulty = "Intermediate",
                isFree = true,
                primaryLink = "https://www.ravelry.com/patterns/search#query=$qEncoded",
                sourcePlatform = "Ravelry",
                secondaryLinks = listOf(
                    PatternLink("Ravelry Direct", "https://www.ravelry.com/patterns/search#query=$qEncoded", "ravelry"),
                    PatternLink("Google Search", "https://www.google.com/search?q=$qEncoded", "google")
                ),
                hookSize = "4.0 mm - 5.0 mm",
                yarnWeight = "Worsted or DK",
                estimatedTime = "Varies",
                keyStitches = listOf("Single Crochet", "Double Crochet", "Chain")
            ),
            CrochetPattern(
                id = "dyn_yt_${System.currentTimeMillis()}",
                title = "$qShort Step-by-Step Video Tutorial",
                authorOrSource = "YouTube Crafters",
                description = "Watch beginner-friendly step-by-step video tutorials and visual guides for $userQuery.",
                category = "Wearables",
                difficulty = "Easy",
                isFree = true,
                primaryLink = "https://www.youtube.com/results?search_query=$qEncoded",
                sourcePlatform = "YouTube",
                secondaryLinks = listOf(
                    PatternLink("YouTube Videos", "https://www.youtube.com/results?search_query=$qEncoded", "youtube"),
                    PatternLink("Pinterest Board", "https://www.pinterest.com/search/pins/?q=$qEncoded", "pinterest")
                ),
                hookSize = "4.5 mm (7)",
                yarnWeight = "Medium / Worsted",
                estimatedTime = "2-4 hours",
                keyStitches = listOf("Magic Ring", "Single Crochet", "Slip Stitch")
            ),
            CrochetPattern(
                id = "dyn_pin_${System.currentTimeMillis()}",
                title = "$qShort Free Pattern Pins & Blogs",
                authorOrSource = "Pinterest Crafters",
                description = "Curated Pinterest pins, diagram charts, and blog posts matching $userQuery.",
                category = "Home Decor",
                difficulty = "Beginner",
                isFree = true,
                primaryLink = "https://www.pinterest.com/search/pins/?q=$qEncoded",
                sourcePlatform = "Pinterest",
                secondaryLinks = listOf(
                    PatternLink("Pinterest Pins", "https://www.pinterest.com/search/pins/?q=$qEncoded", "pinterest"),
                    PatternLink("Etsy Listings", "https://www.etsy.com/search?q=$qEncoded", "etsy")
                ),
                hookSize = "5.0 mm (H-8)",
                yarnWeight = "Chunky / Bulky",
                estimatedTime = "1-3 hours",
                keyStitches = listOf("Half Double Crochet", "Chain", "Cluster Stitch")
            )
        )
    }

    private fun getCuratedCatalog(): List<CrochetPattern> {
        return listOf(
            CrochetPattern(
                id = "p1",
                title = "Classic Granny Square Cardigan",
                authorOrSource = "HayHay Crochet",
                description = "A viral cozy cardigan made from classic granny squares with flared cuffs and oversized fit.",
                category = "Wearables",
                difficulty = "Intermediate",
                isFree = true,
                primaryLink = "https://www.youtube.com/results?search_query=HayHay+Crochet+Granny+Square+Cardigan",
                sourcePlatform = "YouTube",
                secondaryLinks = listOf(
                    PatternLink("YouTube Tutorial", "https://www.youtube.com/results?search_query=HayHay+Crochet+Granny+Square+Cardigan", "youtube"),
                    PatternLink("Ravelry", "https://www.ravelry.com/patterns/search#query=Granny+Square+Cardigan", "ravelry"),
                    PatternLink("Pinterest Pins", "https://www.pinterest.com/search/pins/?q=Granny+Square+Cardigan+Pattern", "pinterest")
                ),
                hookSize = "5.0 mm (H-8)",
                yarnWeight = "Worsted / Medium (4)",
                estimatedTime = "12-16 hours",
                keyStitches = listOf("Double Crochet", "Chain 1 Space", "Granny Cluster")
            ),
            CrochetPattern(
                id = "p2",
                title = "Cute Amigurumi Frog & Bucket Hat",
                authorOrSource = "Chubby Bear Crochet",
                description = "Adorable plush frog amigurumi with a removable tiny bucket hat. Perfect beginner project!",
                category = "Amigurumi",
                difficulty = "Beginner",
                isFree = true,
                primaryLink = "https://www.youtube.com/results?search_query=Chubby+Bear+Crochet+Frog+Amigurumi",
                sourcePlatform = "YouTube",
                secondaryLinks = listOf(
                    PatternLink("YouTube Tutorial", "https://www.youtube.com/results?search_query=Chubby+Bear+Crochet+Frog+Amigurumi", "youtube"),
                    PatternLink("Ravelry PDF", "https://www.ravelry.com/patterns/search#query=Amigurumi+Frog+Bucket+Hat", "ravelry"),
                    PatternLink("Etsy Pattern", "https://www.etsy.com/search?q=Amigurumi+Frog+Crochet+Pattern", "etsy")
                ),
                hookSize = "3.5 mm (E-4)",
                yarnWeight = "Plush Velvet or Worsted",
                estimatedTime = "2-3 hours",
                keyStitches = listOf("Magic Ring", "Single Crochet", "Increase", "Invisible Decrease")
            ),
            CrochetPattern(
                id = "p3",
                title = "Ribbed Chunky Beanie with Fold-Over Brim",
                authorOrSource = "Bella Coco Crochet",
                description = "Easy 1-hour ribbed winter beanie using simple half double crochet in back loops only.",
                category = "Accessories",
                difficulty = "Beginner",
                isFree = true,
                primaryLink = "https://www.lovecrafts.com/en-us/search?q=Ribbed+Chunky+Beanie",
                sourcePlatform = "LoveCrafts",
                secondaryLinks = listOf(
                    PatternLink("LoveCrafts Free Pattern", "https://www.lovecrafts.com/en-us/search?q=Ribbed+Chunky+Beanie", "lovecrafts"),
                    PatternLink("YouTube Walkthrough", "https://www.youtube.com/results?search_query=Bella+Coco+Ribbed+Beanie", "youtube"),
                    PatternLink("Ravelry", "https://www.ravelry.com/patterns/search#query=Ribbed+HDC+Beanie", "ravelry")
                ),
                hookSize = "6.0 mm (J-10)",
                yarnWeight = "Chunky / Bulky (5)",
                estimatedTime = "1-2 hours",
                keyStitches = listOf("Half Double Crochet", "Back Loop Only (BLO)", "Chain")
            ),
            CrochetPattern(
                id = "p4",
                title = "Textured Waffle Stitch Blanket",
                authorOrSource = "Yarnspirations",
                description = "Squishy, deep-textured waffle stitch lap blanket that is warm and squishy for cozy evenings.",
                category = "Blankets",
                difficulty = "Easy",
                isFree = true,
                primaryLink = "https://www.ravelry.com/patterns/search#query=Waffle+Stitch+Blanket+Yarnspirations",
                sourcePlatform = "Ravelry",
                secondaryLinks = listOf(
                    PatternLink("Ravelry Download", "https://www.ravelry.com/patterns/search#query=Waffle+Stitch+Blanket+Yarnspirations", "ravelry"),
                    PatternLink("Google Pattern PDF", "https://www.google.com/search?q=Yarnspirations+Waffle+Stitch+Blanket+free+pattern", "google")
                ),
                hookSize = "5.5 mm (I-9)",
                yarnWeight = "Aran / Worsted (4)",
                estimatedTime = "20-25 hours",
                keyStitches = listOf("Double Crochet", "Front Post Double Crochet (FPDC)", "Chain")
            ),
            CrochetPattern(
                id = "p5",
                title = "Vintage Daisy Granny Square Bag",
                authorOrSource = "SweetSofties Blog",
                description = "Charming retro tote bag constructed from 13 daisy flower granny squares with sturdy straps.",
                category = "Accessories",
                difficulty = "Easy",
                isFree = true,
                primaryLink = "https://www.pinterest.com/search/pins/?q=Daisy+Granny+Square+Tote+Bag+Pattern",
                sourcePlatform = "Pinterest",
                secondaryLinks = listOf(
                    PatternLink("Pinterest Inspiration", "https://www.pinterest.com/search/pins/?q=Daisy+Granny+Square+Tote+Bag+Pattern", "pinterest"),
                    PatternLink("YouTube Guide", "https://www.youtube.com/results?search_query=Daisy+Granny+Square+Bag+crochet", "youtube"),
                    PatternLink("Etsy PDF", "https://www.etsy.com/search?q=Daisy+Granny+Square+Bag+Pattern", "etsy")
                ),
                hookSize = "4.0 mm (G-6)",
                yarnWeight = "Cotton Worsted (4)",
                estimatedTime = "6-8 hours",
                keyStitches = listOf("Puff Stitch", "Granny Square", "Single Crochet Join")
            ),
            CrochetPattern(
                id = "p6",
                title = "Bralette Crop Top with Scalloped Trim",
                authorOrSource = "PassioKnit Kaddy",
                description = "Trendy summer crochet bralette crop top with adjustable lace-up back and scalloped edges.",
                category = "Wearables",
                difficulty = "Intermediate",
                isFree = true,
                primaryLink = "https://www.youtube.com/results?search_query=PassioKnit+Kaddy+Crochet+Bralette",
                sourcePlatform = "YouTube",
                secondaryLinks = listOf(
                    PatternLink("YouTube Tutorial", "https://www.youtube.com/results?search_query=PassioKnit+Kaddy+Crochet+Bralette", "youtube"),
                    PatternLink("Ravelry", "https://www.ravelry.com/patterns/search#query=Crochet+Crop+Top+Bralette", "ravelry")
                ),
                hookSize = "3.5 mm (E-4)",
                yarnWeight = "Sport / Fine Cotton (2-3)",
                estimatedTime = "4-6 hours",
                keyStitches = listOf("Single Crochet", "Half Double Crochet", "Shell / Scallop Stitch")
            ),
            CrochetPattern(
                id = "p7",
                title = "Giant Plushie Plush Bee Amigurumi",
                authorOrSource = "Hooked by Robin",
                description = "Super fast and soft chubby plush bumblebee made with fluffy chenille yarn.",
                category = "Amigurumi",
                difficulty = "Beginner",
                isFree = true,
                primaryLink = "https://www.youtube.com/results?search_query=Hooked+by+Robin+Chubby+Bee",
                sourcePlatform = "YouTube",
                secondaryLinks = listOf(
                    PatternLink("YouTube Video", "https://www.youtube.com/results?search_query=Hooked+by+Robin+Chubby+Bee", "youtube"),
                    PatternLink("Ravelry", "https://www.ravelry.com/patterns/search#query=Chubby+Plush+Bee", "ravelry"),
                    PatternLink("Pinterest", "https://www.pinterest.com/search/pins/?q=Plush+Crochet+Bee", "pinterest")
                ),
                hookSize = "6.5 mm (K-10.5)",
                yarnWeight = "Super Bulky Plush Chenille (6)",
                estimatedTime = "1 hour",
                keyStitches = listOf("Magic Ring", "Single Crochet", "Color Change")
            ),
            CrochetPattern(
                id = "p8",
                title = "Boho Hanging Macrame-Style Plant Hanger",
                authorOrSource = "Crafty Intentions",
                description = "Bohemian style crochet plant hanger that fits 4-6 inch pots with spiral texture.",
                category = "Home Decor",
                difficulty = "Beginner",
                isFree = true,
                primaryLink = "https://www.google.com/search?q=Boho+crochet+plant+hanger+free+pattern",
                sourcePlatform = "Web Blog",
                secondaryLinks = listOf(
                    PatternLink("Google Search", "https://www.google.com/search?q=Boho+crochet+plant+hanger+free+pattern", "google"),
                    PatternLink("Pinterest", "https://www.pinterest.com/search/pins/?q=Crochet+Plant+Hanger+Pattern", "pinterest")
                ),
                hookSize = "5.0 mm (H-8)",
                yarnWeight = "Macrame Cord or Cotton (4)",
                estimatedTime = "1-2 hours",
                keyStitches = listOf("Chain", "Double Crochet", "Solomon's Knot")
            ),
            CrochetPattern(
                id = "p9",
                title = "Ruffled Bucket Hat",
                authorOrSource = "KittenCrochet",
                description = "Y2K aesthetic bucket hat with flared ruffled brim using multi-color striping.",
                category = "Accessories",
                difficulty = "Easy",
                isFree = true,
                primaryLink = "https://www.etsy.com/search?q=Ruffled+Bucket+Hat+Crochet+Pattern",
                sourcePlatform = "Etsy",
                secondaryLinks = listOf(
                    PatternLink("Etsy Shop", "https://www.etsy.com/search?q=Ruffled+Bucket+Hat+Crochet+Pattern", "etsy"),
                    PatternLink("YouTube Video", "https://www.youtube.com/results?search_query=Ruffled+Bucket+Hat+crochet", "youtube")
                ),
                hookSize = "4.0 mm (G-6)",
                yarnWeight = "Cotton / Worsted",
                estimatedTime = "2-3 hours",
                keyStitches = listOf("Double Crochet", "Increase", "Ruffle Edge")
            ),
            CrochetPattern(
                id = "p10",
                title = "Lace Dragonfly Summer Shawl",
                authorOrSource = "MyPicot Patterns",
                description = "Elegant lightweight triangular shawl with lace dragonfly stitch motifs.",
                category = "Wearables",
                difficulty = "Advanced",
                isFree = true,
                primaryLink = "https://www.ravelry.com/patterns/search#query=Lace+Dragonfly+Shawl",
                sourcePlatform = "Ravelry",
                secondaryLinks = listOf(
                    PatternLink("Ravelry Chart", "https://www.ravelry.com/patterns/search#query=Lace+Dragonfly+Shawl", "ravelry"),
                    PatternLink("LoveCrafts", "https://www.lovecrafts.com/en-us/search?q=Dragonfly+Shawl", "lovecrafts")
                ),
                hookSize = "3.25 mm (D-3)",
                yarnWeight = "Fingering / Sock (1)",
                estimatedTime = "15-18 hours",
                keyStitches = listOf("Triple Crochet", "Chain Space", "Dragonfly Motif")
            )
        )
    }
}
