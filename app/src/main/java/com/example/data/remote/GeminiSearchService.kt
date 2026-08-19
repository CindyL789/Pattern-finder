package com.example.data.remote

import com.example.BuildConfig
import com.example.data.model.CrochetPattern
import com.example.data.model.PatternLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object GeminiSearchService {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun searchPatternsWithGemini(userQuery: String): List<CrochetPattern>? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext null
        }

        try {
            val encodedQuery = URLEncoder.encode(userQuery, "UTF-8")
            val promptText = """
                You are a master crochet expert and pattern finder assistant.
                The user is searching for crochet patterns with query: "$userQuery".
                
                Generate a list of 4 to 6 specific matching crochet pattern ideas, video tutorials, or popular blog patterns.
                For each pattern, provide realistic crafting details and generated direct search/view links for popular crochet platforms: Ravelry, YouTube, Pinterest, Etsy, LoveCrafts, and Google.

                Output ONLY valid JSON matching this schema array:
                [
                  {
                    "title": "String - pattern title",
                    "authorOrSource": "String - e.g. 'Bella Coco Crochet', 'Yarnspirations', 'Ravelry Crafter', 'SweetSofties'",
                    "description": "String - clear summary of pattern style, construction, and features",
                    "category": "String - one of 'Amigurumi', 'Wearables', 'Blankets', 'Accessories', 'Home Decor'",
                    "difficulty": "String - one of 'Beginner', 'Easy', 'Intermediate', 'Advanced'",
                    "isFree": boolean,
                    "sourcePlatform": "String - e.g. 'Ravelry', 'YouTube', 'LoveCrafts', 'Etsy', 'Pinterest', 'Web Blog'",
                    "hookSize": "String - e.g. '4.0 mm (G-6)' or '5.0 mm (H-8)'",
                    "yarnWeight": "String - e.g. 'Worsted / Medium', 'Plush Chunky', 'DK / Light', 'Fingering'",
                    "estimatedTime": "String - e.g. '2-3 hours' or '12-15 hours'",
                    "keyStitches": ["String", "String", "String"],
                    "primaryPlatformUrl": "String - full HTTPS URL or generated search URL for this pattern"
                  }
                ]
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", promptText)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val responseBodyString = response.body?.string() ?: return@withContext null
            val responseObj = JSONObject(responseBodyString)
            val candidates = responseObj.optJSONArray("candidates") ?: return@withContext null
            if (candidates.length() == 0) return@withContext null

            val firstCandidate = candidates.getJSONObject(0)
            val contentObj = firstCandidate.optJSONObject("content") ?: return@withContext null
            val parts = contentObj.optJSONArray("parts") ?: return@withContext null
            if (parts.length() == 0) return@withContext null

            val textResult = parts.getJSONObject(0).optString("text", "")
            if (textResult.isBlank()) return@withContext null

            val jsonArray = JSONArray(textResult)
            val patternsList = mutableListOf<CrochetPattern>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val title = item.optString("title", "Crochet Pattern")
                val author = item.optString("authorOrSource", "Craft Designer")
                val description = item.optString("description", "Crochet pattern matching $userQuery.")
                val category = item.optString("category", "Accessories")
                val difficulty = item.optString("difficulty", "Easy")
                val isFree = item.optBoolean("isFree", true)
                val platform = item.optString("sourcePlatform", "Ravelry")
                val hookSize = item.optString("hookSize", "4.0 mm (G-6)")
                val yarnWeight = item.optString("yarnWeight", "Worsted / Medium")
                val estimatedTime = item.optString("estimatedTime", "3-4 hours")
                
                val stitchesArray = item.optJSONArray("keyStitches")
                val keyStitches = mutableListOf<String>()
                if (stitchesArray != null) {
                    for (j in 0 until stitchesArray.length()) {
                        keyStitches.add(stitchesArray.getString(j))
                    }
                }
                if (keyStitches.isEmpty()) {
                    keyStitches.addAll(listOf("Single Crochet", "Chain", "Double Crochet"))
                }

                val titleEncoded = URLEncoder.encode("$title crochet pattern", "UTF-8")
                val primaryUrl = item.optString("primaryPlatformUrl").ifBlank {
                    when (platform.lowercase()) {
                        "youtube" -> "https://www.youtube.com/results?search_query=$titleEncoded"
                        "ravelry" -> "https://www.ravelry.com/patterns/search#query=$titleEncoded"
                        "etsy" -> "https://www.etsy.com/search?q=$titleEncoded"
                        "pinterest" -> "https://www.pinterest.com/search/pins/?q=$titleEncoded"
                        "lovecrafts" -> "https://www.lovecrafts.com/en-us/search?q=$titleEncoded"
                        else -> "https://www.google.com/search?q=$titleEncoded"
                    }
                }

                val secondaryLinks = listOf(
                    PatternLink("Google Search", "https://www.google.com/search?q=$titleEncoded", "google"),
                    PatternLink("Ravelry", "https://www.ravelry.com/patterns/search#query=$titleEncoded", "ravelry"),
                    PatternLink("YouTube Video", "https://www.youtube.com/results?search_query=$titleEncoded", "youtube"),
                    PatternLink("Pinterest", "https://www.pinterest.com/search/pins/?q=$titleEncoded", "pinterest"),
                    PatternLink("Etsy Shop", "https://www.etsy.com/search?q=$titleEncoded", "etsy")
                )

                patternsList.add(
                    CrochetPattern(
                        id = "gemini_${System.currentTimeMillis()}_$i",
                        title = title,
                        authorOrSource = author,
                        description = description,
                        category = category,
                        difficulty = difficulty,
                        isFree = isFree,
                        primaryLink = primaryUrl,
                        sourcePlatform = platform,
                        secondaryLinks = secondaryLinks,
                        hookSize = hookSize,
                        yarnWeight = yarnWeight,
                        estimatedTime = estimatedTime,
                        keyStitches = keyStitches
                    )
                )
            }

            patternsList
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
