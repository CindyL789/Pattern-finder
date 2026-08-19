package com.example.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.AiGeneratedPattern
import com.example.data.model.AiPatternSection
import com.example.data.model.AiPatternStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiPatternGeneratorService {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Generates a complete, structured crochet pattern from a user prompt using Gemini 3.5 Flash.
     */
    suspend fun generatePattern(
        userPrompt: String,
        preferredCategory: String? = null,
        preferredDifficulty: String? = null,
        preferredHookSize: String? = null,
        preferredYarnWeight: String? = null
    ): AiGeneratedPattern = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val isValidKey = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY" && apiKey != "YOUR_GEMINI_API_KEY"

        if (isValidKey) {
            try {
                val systemPrompt = """
                    You are a master crochet designer, pattern writer, and technical editor.
                    Write a complete, authentic, professionally structured crochet pattern based on the user's prompt: "$userPrompt".
                    ${if (!preferredCategory.isNullOrBlank() && preferredCategory != "All") "Category: $preferredCategory." else ""}
                    ${if (!preferredDifficulty.isNullOrBlank() && preferredDifficulty != "All") "Difficulty: $preferredDifficulty." else ""}
                    ${if (!preferredHookSize.isNullOrBlank() && preferredHookSize != "Auto") "Hook Size: $preferredHookSize." else ""}
                    ${if (!preferredYarnWeight.isNullOrBlank() && preferredYarnWeight != "Auto") "Yarn Weight: $preferredYarnWeight." else ""}

                    Ensure the pattern contains:
                    1. A catchy Title and aesthetic Subtitle
                    2. Recommended Category, Difficulty, Estimated Time, Hook Size, Yarn Weight, Colors, Yardage, Gauge, Finished Size, Notions, and Key Stitches.
                    3. Structured Sections (e.g. "Main Body / Head", "Accents / Brim", "Assembly") with clear round-by-round or row-by-row instructions, stitch counts at the end of each step (e.g. "[18 sts]"), and helpful tips.
                    4. Assembly & Finishing instructions, crafter pro tips, and care instructions.
                    5. A list of 4 harmonious hex color codes representing the suggested yarn palette (e.g. ["#E07A5F", "#F4F1DE", "#81B29A", "#3D405B"]).

                    Return ONLY valid JSON matching this schema:
                    {
                      "title": "String",
                      "subtitle": "String",
                      "category": "String (one of 'Amigurumi', 'Wearables', 'Blankets', 'Accessories', 'Home Decor')",
                      "difficulty": "String (one of 'Beginner', 'Easy', 'Intermediate', 'Advanced')",
                      "estimatedTime": "String (e.g. '2-3 hours')",
                      "hookSize": "String (e.g. '4.0 mm (G-6)')",
                      "yarnWeight": "String (e.g. 'Worsted / Medium (4)' or 'Plush Chenille')",
                      "yarnColors": ["String", "String"],
                      "estimatedYardage": "String",
                      "gauge": "String",
                      "finishedDimensions": "String",
                      "notions": ["String", "String"],
                      "keyStitches": ["String", "String"],
                      "sections": [
                        {
                          "sectionTitle": "String",
                          "sectionNotes": "String",
                          "steps": [
                            {
                              "stepLabel": "String (e.g. 'Rnd 1' or 'Row 1-3')",
                              "instruction": "String (e.g. 'MR, 6 sc in ring')",
                              "stitchCount": "String (e.g. '6 sts')",
                              "tipOrNote": "String (optional tip)"
                            }
                          ]
                        }
                      ],
                      "assemblyNotes": "String",
                      "tips": ["String", "String"],
                      "careInstructions": "String",
                      "swatchHexColors": ["#HEX1", "#HEX2", "#HEX3", "#HEX4"]
                    }
                """.trimIndent()

                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", systemPrompt)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.4)
                        put("responseMimeType", "application/json")
                    })
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                    .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (!responseBody.isNullOrBlank()) {
                        val parsed = parsePatternJson(responseBody, userPrompt)
                        if (parsed != null) {
                            return@withContext parsed
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback to high quality procedural pattern generator if API is unavailable or key not configured
        return@withContext generateFallbackPattern(userPrompt, preferredCategory, preferredDifficulty)
    }

    /**
     * Generates a sample craft image of the finished crochet pattern using Gemini 2.5 Flash Image.
     */
    suspend fun generateSampleImage(
        prompt: String,
        patternTitle: String,
        yarnColors: List<String> = emptyList()
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val isValidKey = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY" && apiKey != "YOUR_GEMINI_API_KEY"

        if (!isValidKey) {
            return@withContext null
        }

        try {
            val colorDesc = if (yarnColors.isNotEmpty()) " in colors: ${yarnColors.joinToString(", ")}" else ""
            val imagePrompt = "A professional studio photograph of a finished handmade crochet creation: $patternTitle ($prompt)$colorDesc. " +
                    "Made entirely with soft textured yarn, distinct visible crochet stitches, beautiful soft aesthetic studio lighting, " +
                    "resting on a clean wooden crafting table with a crochet hook and yarn skein nearby. 4k resolution craft photography, crisp focus."

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", imagePrompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("IMAGE")
                    })
                    put("imageConfig", JSONObject().apply {
                        put("aspectRatio", "1:1")
                        put("imageSize", "1K")
                    })
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key=$apiKey")
                .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val responseBody = response.body?.string() ?: return@withContext null
            val responseObj = JSONObject(responseBody)
            val candidates = responseObj.optJSONArray("candidates") ?: return@withContext null
            if (candidates.length() == 0) return@withContext null

            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return@withContext null
            val parts = content.optJSONArray("parts") ?: return@withContext null

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val inlineData = part.optJSONObject("inlineData")
                if (inlineData != null) {
                    val base64Data = inlineData.optString("data", "")
                    if (base64Data.isNotBlank()) {
                        return@withContext base64Data
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * Converts Base64 encoded image string into an Android Bitmap.
     */
    fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parsePatternJson(responseJsonStr: String, prompt: String): AiGeneratedPattern? {
        try {
            val root = JSONObject(responseJsonStr)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null
            val rawText = parts.getJSONObject(0).optString("text", "")
            if (rawText.isBlank()) return null

            val data = JSONObject(rawText)
            val title = data.optString("title", "AI Crafted Crochet Pattern")
            val subtitle = data.optString("subtitle", "Handmade custom pattern designed for $prompt")
            val category = data.optString("category", "Amigurumi")
            val difficulty = data.optString("difficulty", "Easy")
            val estimatedTime = data.optString("estimatedTime", "3-4 hours")
            val hookSize = data.optString("hookSize", "4.0 mm (G-6)")
            val yarnWeight = data.optString("yarnWeight", "Worsted / Medium (4)")
            val estimatedYardage = data.optString("estimatedYardage", "180-250 yds")
            val gauge = data.optString("gauge", "16 sc x 18 rows = 4\" (10 cm)")
            val finishedDimensions = data.optString("finishedDimensions", "Approx. 7\" x 5\"")
            val assemblyNotes = data.optString("assemblyNotes", "Weave in all yarn tails and sew parts securely using a tapestry needle.")
            val careInstructions = data.optString("careInstructions", "Hand wash in cool water with gentle detergent, reshape and air dry flat.")

            val yarnColors = jsonArrayToList(data.optJSONArray("yarnColors"))
            val notions = jsonArrayToList(data.optJSONArray("notions"))
            val keyStitches = jsonArrayToList(data.optJSONArray("keyStitches"))
            val tips = jsonArrayToList(data.optJSONArray("tips"))
            val swatchHexColors = jsonArrayToList(data.optJSONArray("swatchHexColors")).ifEmpty {
                listOf("#E07A5F", "#F4F1DE", "#81B29A", "#3D405B")
            }

            val sections = mutableListOf<AiPatternSection>()
            val sectionsArr = data.optJSONArray("sections")
            if (sectionsArr != null) {
                for (i in 0 until sectionsArr.length()) {
                    val secObj = sectionsArr.getJSONObject(i)
                    val secTitle = secObj.optString("sectionTitle", "Section ${i + 1}")
                    val secNotes = secObj.optString("sectionNotes", "")
                    val stepsList = mutableListOf<AiPatternStep>()

                    val stepsArr = secObj.optJSONArray("steps")
                    if (stepsArr != null) {
                        for (j in 0 until stepsArr.length()) {
                            val stepObj = stepsArr.getJSONObject(j)
                            val stepLabel = stepObj.optString("stepLabel", "Round ${j + 1}")
                            val instruction = stepObj.optString("instruction", "")
                            val stitchCount = stepObj.optString("stitchCount", "")
                            val tip = stepObj.optString("tipOrNote").takeIf { it.isNotBlank() }

                            stepsList.add(
                                AiPatternStep(
                                    stepIndex = j + 1,
                                    stepLabel = stepLabel,
                                    instruction = instruction,
                                    stitchCount = stitchCount,
                                    tipOrNote = tip
                                )
                            )
                        }
                    }

                    sections.add(
                        AiPatternSection(
                            sectionTitle = secTitle,
                            sectionNotes = secNotes,
                            steps = stepsList
                        )
                    )
                }
            }

            return AiGeneratedPattern(
                id = "ai_pat_${System.currentTimeMillis()}",
                prompt = prompt,
                title = title,
                subtitle = subtitle,
                category = category,
                difficulty = difficulty,
                estimatedTime = estimatedTime,
                hookSize = hookSize,
                yarnWeight = yarnWeight,
                yarnColors = yarnColors.ifEmpty { listOf("Main Color", "Contrast Color") },
                estimatedYardage = estimatedYardage,
                gauge = gauge,
                finishedDimensions = finishedDimensions,
                notions = notions.ifEmpty { listOf("Tapestry needle", "Stitch markers", "Fiberfill stuffing", "Scissors") },
                keyStitches = keyStitches.ifEmpty { listOf("Magic Ring (MR)", "Single Crochet (sc)", "Increase (inc)", "Decrease (dec)") },
                sections = sections,
                assemblyNotes = assemblyNotes,
                tips = tips.ifEmpty { listOf("Use stitch markers to keep track of the start of each round", "Stuff firmly as you go for best shape") },
                careInstructions = careInstructions,
                swatchHexColors = swatchHexColors
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val str = arr.optString(i)
            if (str.isNotBlank()) list.add(str)
        }
        return list
    }

    /**
     * High-quality procedural fallback generator customized to the user's prompt.
     */
    private fun generateFallbackPattern(
        userPrompt: String,
        category: String?,
        difficulty: String?
    ): AiGeneratedPattern {
        val cleanPrompt = userPrompt.trim().replaceFirstChar { it.uppercase() }
        val determinedCat = when {
            cleanPrompt.contains("bee", true) || cleanPrompt.contains("frog", true) || cleanPrompt.contains("cat", true) ||
                    cleanPrompt.contains("bear", true) || cleanPrompt.contains("plush", true) || cleanPrompt.contains("amigurumi", true) ||
                    cleanPrompt.contains("dino", true) || cleanPrompt.contains("mushroom", true) -> "Amigurumi"
            cleanPrompt.contains("hat", true) || cleanPrompt.contains("beanie", true) || cleanPrompt.contains("bag", true) ||
                    cleanPrompt.contains("tote", true) || cleanPrompt.contains("scarf", true) -> "Accessories"
            cleanPrompt.contains("blanket", true) || cleanPrompt.contains("throw", true) || cleanPrompt.contains("afghan", true) -> "Blankets"
            cleanPrompt.contains("sweater", true) || cleanPrompt.contains("cardigan", true) || cleanPrompt.contains("top", true) ||
                    cleanPrompt.contains("vest", true) -> "Wearables"
            cleanPrompt.contains("pillow", true) || cleanPrompt.contains("coaster", true) || cleanPrompt.contains("plant", true) ||
                    cleanPrompt.contains("basket", true) -> "Home Decor"
            !category.isNullOrBlank() && category != "All" -> category
            else -> "Amigurumi"
        }

        val determinedDifficulty = if (!difficulty.isNullOrBlank() && difficulty != "All") difficulty else "Easy"

        val sections = when (determinedCat) {
            "Amigurumi" -> listOf(
                AiPatternSection(
                    sectionTitle = "Main Body & Head",
                    sectionNotes = "Worked in continuous spiral rounds. Do not join at end of rounds.",
                    steps = listOf(
                        AiPatternStep(1, "Rnd 1", "Make a Magic Ring, 6 sc into ring", "6 sts", "Place stitch marker in last st to track rounds"),
                        AiPatternStep(2, "Rnd 2", "2 sc in each st around (inc x6)", "12 sts"),
                        AiPatternStep(3, "Rnd 3", "(1 sc in next st, 1 inc in next st) repeat 6 times", "18 sts"),
                        AiPatternStep(4, "Rnd 4", "(2 sc, 1 inc) repeat 6 times", "24 sts"),
                        AiPatternStep(5, "Rnd 5", "(3 sc, 1 inc) repeat 6 times", "30 sts"),
                        AiPatternStep(6, "Rnd 6-10", "Sc in each st around (5 rounds total)", "30 sts", "Keep even tension"),
                        AiPatternStep(7, "Rnd 11", "(3 sc, 1 dec) repeat 6 times. Insert safety eyes between Rnds 7-8.", "24 sts", "Begin stuffing firmly"),
                        AiPatternStep(8, "Rnd 12", "(2 sc, 1 dec) repeat 6 times", "18 sts"),
                        AiPatternStep(9, "Rnd 13", "(1 sc, 1 dec) repeat 6 times. Add final stuffing.", "12 sts"),
                        AiPatternStep(10, "Rnd 14", "Dec around 6 times. Fasten off leaving 6\" tail to cinch closed.", "6 sts")
                    )
                ),
                AiPatternSection(
                    sectionTitle = "Decorative Details & Features",
                    sectionNotes = "Make 2 for symmetry (wings/ears/arms as applicable).",
                    steps = listOf(
                        AiPatternStep(1, "Rnd 1", "Magic Ring, 6 sc in ring", "6 sts"),
                        AiPatternStep(2, "Rnd 2", "(1 sc, 1 inc) repeat 3 times", "9 sts"),
                        AiPatternStep(3, "Rnd 3-4", "Sc in each st around", "9 sts"),
                        AiPatternStep(4, "Rnd 5", "Flatten opening and 4 sc across to close. Fasten off with 8\" tail for sewing.", "4 sts")
                    )
                )
            )
            "Accessories" -> listOf(
                AiPatternSection(
                    sectionTitle = "Main Body / Crown",
                    sectionNotes = "Starting from top crown down to the brim.",
                    steps = listOf(
                        AiPatternStep(1, "Rnd 1", "Magic Ring, ch 2 (counts as hdc), 9 hdc in ring, sl st to top of ch-2", "10 sts"),
                        AiPatternStep(2, "Rnd 2", "Ch 2, 2 hdc in each st around, sl st to join", "20 sts"),
                        AiPatternStep(3, "Rnd 3", "Ch 2, (1 hdc, 2 hdc in next st) repeat around, sl st to join", "30 sts"),
                        AiPatternStep(4, "Rnd 4", "Ch 2, (2 hdc, 2 hdc in next st) repeat around, sl st to join", "40 sts"),
                        AiPatternStep(5, "Rnd 5", "Ch 2, (3 hdc, 2 hdc in next st) repeat around, sl st to join", "50 sts"),
                        AiPatternStep(6, "Rnd 6-14", "Ch 2, hdc in each st around, sl st to join (9 rounds)", "50 sts", "Adjust length to desired depth"),
                        AiPatternStep(7, "Rnd 15 (Brim)", "Ch 2, (4 hdc, 2 hdc in next st) repeat around for gentle flare", "60 sts"),
                        AiPatternStep(8, "Rnd 16-17", "Hdc in each st around. Fasten off and weave ends.", "60 sts")
                    )
                )
            )
            else -> listOf(
                AiPatternSection(
                    sectionTitle = "Foundation & Body",
                    sectionNotes = "Chain multiple according to desired width.",
                    steps = listOf(
                        AiPatternStep(1, "Row 1 (Foundation)", "Ch 42. Sc in 2nd ch from hook and each ch across, turn.", "41 sts"),
                        AiPatternStep(2, "Row 2", "Ch 2 (counts as dc), dc across in each st, turn.", "41 sts"),
                        AiPatternStep(3, "Row 3-24", "Ch 2, work textured stitch pattern (dc, fpdc alternating) across, turn.", "41 sts", "Maintains cozy stretch"),
                        AiPatternStep(4, "Row 25", "Ch 1, sc across. Fasten off and block lightly.", "41 sts")
                    )
                ),
                AiPatternSection(
                    sectionTitle = "Border & Edging",
                    sectionNotes = "Worked evenly around all 4 perimeter sides.",
                    steps = listOf(
                        AiPatternStep(1, "Rnd 1", "Join yarn with sl st at corner, 3 sc in each corner, sc evenly along edges, join.", "Approx 120 sts"),
                        AiPatternStep(2, "Rnd 2", "Ch 1, reverse single crochet (crab stitch) around for textured finished border.", "120 sts")
                    )
                )
            )
        }

        return AiGeneratedPattern(
            id = "ai_pat_${System.currentTimeMillis()}",
            prompt = userPrompt,
            title = "Custom $cleanPrompt Pattern",
            subtitle = "Beautiful artisan handcrafted $determinedCat pattern tailored to your design vision.",
            category = determinedCat,
            difficulty = determinedDifficulty,
            estimatedTime = if (determinedCat == "Amigurumi") "2-3 hours" else if (determinedCat == "Accessories") "3-4 hours" else "8-12 hours",
            hookSize = if (determinedCat == "Amigurumi") "3.5 mm (E-4)" else "4.5 mm (7)",
            yarnWeight = if (determinedCat == "Amigurumi") "Worsted / Cotton (4)" else "Medium Worsted (4)",
            yarnColors = listOf("Honey Butter Cream", "Sage Botanical Green", "Terracotta Coral", "Espresso Brown"),
            estimatedYardage = "180 - 240 yards",
            gauge = "16 sts x 18 rows = 4 inches (10 cm) in single crochet",
            finishedDimensions = if (determinedCat == "Amigurumi") "6.5\" height x 4.5\" width" else "One size fits most crafters",
            notions = listOf("Tapestry / yarn needle", "Locking stitch markers", "Polyester fiberfill", "9mm Safety eyes", "Embroidery floss for smile"),
            keyStitches = listOf("Magic Ring (MR)", "Single Crochet (sc)", "Increase (2 sc in same st)", "Invisible Decrease (inv dec)", "Slip Stitch (sl st)"),
            sections = sections,
            assemblyNotes = "Pin all decorative parts before sewing to ensure symmetry. Use yarn tails and whipstitch through both loops for sturdy attachment.",
            tips = listOf(
                "Maintain firm, consistent tension so stuffing does not show between stitches.",
                "Use invisible decrease (working through front loops only) for smooth, seamless decreases.",
                "Stuff small amounts at a time and shape with your thumbs as you work."
            ),
            careInstructions = "Spot clean or hand wash in cool water with gentle wool wash. Reshape and lay flat to dry away from direct heat.",
            swatchHexColors = listOf("#E07A5F", "#F4F1DE", "#81B29A", "#3D405B")
        )
    }
}
