package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.AiGeneratedPattern
import com.example.data.model.Project
import com.example.data.remote.GeminiPatternGeneratorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiPatternStudioUiState(
    val promptInput: String = "",
    val selectedCategory: String = "All", // All, Amigurumi, Wearables, Blankets, Accessories, Home Decor
    val selectedDifficulty: String = "All", // All, Beginner, Easy, Intermediate, Advanced
    val selectedHookSize: String = "Auto",
    val selectedYarnWeight: String = "Auto",
    val isGeneratingPattern: Boolean = false,
    val isGeneratingImage: Boolean = false,
    val generationStatus: String? = null,
    val currentPattern: AiGeneratedPattern? = null,
    val sampleImageBitmap: ImageBitmap? = null,
    val isImageViewerOpen: Boolean = false,
    val activeSectionTab: Int = 0,
    val savedPatterns: List<AiGeneratedPattern> = emptyList(),
    val quickInspirations: List<String> = listOf(
        "Strawberry Cow Amigurumi with tiny flower crown",
        "Vintage Daisy Granny Square Tote Bag with sturdy straps",
        "Pastel Mushroom Bucket Hat with ruffled brim",
        "Cozy Chunky Honeycomb Throw Blanket",
        "Baby Dragon Rattle with soft textured scales",
        "Ribbed Forest Green Cardigan with wooden buttons"
    ),
    val snackbarMessage: String? = null
)

class AiPatternGeneratorViewModel(application: Application) : AndroidViewModel(application) {

    private val projectDao = AppDatabase.getDatabase(application).projectDao()

    private val _uiState = MutableStateFlow(AiPatternStudioUiState())
    val uiState: StateFlow<AiPatternStudioUiState> = _uiState.asStateFlow()

    // In-memory cache of saved AI patterns with their decoded bitmaps
    private val savedPatternsList = mutableListOf<AiGeneratedPattern>()
    private val imageCache = mutableMapOf<String, ImageBitmap>()

    init {
        // Pre-load a featured demo pattern so crafters can immediately see how it looks
        loadFeaturedPattern()
    }

    fun onPromptChange(newPrompt: String) {
        _uiState.update { it.copy(promptInput = newPrompt) }
    }

    fun onCategorySelect(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onDifficultySelect(difficulty: String) {
        _uiState.update { it.copy(selectedDifficulty = difficulty) }
    }

    fun onHookSizeSelect(hookSize: String) {
        _uiState.update { it.copy(selectedHookSize = hookSize) }
    }

    fun onYarnWeightSelect(yarnWeight: String) {
        _uiState.update { it.copy(selectedYarnWeight = yarnWeight) }
    }

    fun selectSectionTab(tabIndex: Int) {
        _uiState.update { it.copy(activeSectionTab = tabIndex) }
    }

    fun toggleImageViewer(isOpen: Boolean) {
        _uiState.update { it.copy(isImageViewerOpen = isOpen) }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun generateFromPrompt(userPrompt: String? = null) {
        val promptToUse = userPrompt ?: _uiState.value.promptInput
        if (promptToUse.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Please enter a crochet pattern idea or prompt!") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    promptInput = promptToUse,
                    isGeneratingPattern = true,
                    isGeneratingImage = true,
                    generationStatus = "Drafting stitch-by-step pattern with AI...",
                    sampleImageBitmap = null,
                    activeSectionTab = 0
                )
            }

            try {
                // Step 1: Generate the full structured pattern
                val pattern = GeminiPatternGeneratorService.generatePattern(
                    userPrompt = promptToUse,
                    preferredCategory = _uiState.value.selectedCategory,
                    preferredDifficulty = _uiState.value.selectedDifficulty,
                    preferredHookSize = _uiState.value.selectedHookSize,
                    preferredYarnWeight = _uiState.value.selectedYarnWeight
                )

                _uiState.update {
                    it.copy(
                        currentPattern = pattern,
                        isGeneratingPattern = false,
                        generationStatus = "Crafting AI sample photo visual..."
                    )
                }

                // Step 2: Generate sample craft photo
                val imageBase64 = GeminiPatternGeneratorService.generateSampleImage(
                    prompt = promptToUse,
                    patternTitle = pattern.title,
                    yarnColors = pattern.yarnColors
                )

                var decodedBitmap: ImageBitmap? = null
                if (!imageBase64.isNullOrBlank()) {
                    val rawBitmap = GeminiPatternGeneratorService.base64ToBitmap(imageBase64)
                    if (rawBitmap != null) {
                        decodedBitmap = rawBitmap.asImageBitmap()
                        imageCache[pattern.id] = decodedBitmap
                    }
                }

                val finalPattern = pattern.copy(imageBase64 = imageBase64)

                _uiState.update {
                    it.copy(
                        currentPattern = finalPattern,
                        sampleImageBitmap = decodedBitmap,
                        isGeneratingImage = false,
                        generationStatus = null,
                        snackbarMessage = "Pattern and sample photo generated successfully!"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isGeneratingPattern = false,
                        isGeneratingImage = false,
                        generationStatus = null,
                        snackbarMessage = "Generation note: Created offline pattern template."
                    )
                }
            }
        }
    }

    fun regenerateImageOnly() {
        val pattern = _uiState.value.currentPattern ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingImage = true,
                    generationStatus = "Rendering new AI sample photo..."
                )
            }

            val imageBase64 = GeminiPatternGeneratorService.generateSampleImage(
                prompt = pattern.prompt,
                patternTitle = pattern.title,
                yarnColors = pattern.yarnColors
            )

            var decodedBitmap: ImageBitmap? = null
            if (!imageBase64.isNullOrBlank()) {
                val rawBitmap = GeminiPatternGeneratorService.base64ToBitmap(imageBase64)
                if (rawBitmap != null) {
                    decodedBitmap = rawBitmap.asImageBitmap()
                    imageCache[pattern.id] = decodedBitmap
                }
            }

            _uiState.update {
                it.copy(
                    sampleImageBitmap = decodedBitmap ?: it.sampleImageBitmap,
                    currentPattern = pattern.copy(imageBase64 = imageBase64 ?: pattern.imageBase64),
                    isGeneratingImage = false,
                    generationStatus = null,
                    snackbarMessage = if (decodedBitmap != null) "New sample photo rendered!" else "Using standard craft visual."
                )
            }
        }
    }

    fun toggleStepCompletion(sectionIdx: Int, stepIdx: Int) {
        val pattern = _uiState.value.currentPattern ?: return
        if (sectionIdx !in pattern.sections.indices) return

        val section = pattern.sections[sectionIdx]
        if (stepIdx !in section.steps.indices) return

        val currentStep = section.steps[stepIdx]
        val updatedStep = currentStep.copy(isCompleted = !currentStep.isCompleted)

        val updatedSteps = section.steps.toMutableList()
        updatedSteps[stepIdx] = updatedStep

        val updatedSections = pattern.sections.toMutableList()
        updatedSections[sectionIdx] = section.copy(steps = updatedSteps)

        _uiState.update {
            it.copy(currentPattern = pattern.copy(sections = updatedSections))
        }
    }

    fun savePatternToLibrary() {
        val pattern = _uiState.value.currentPattern ?: return
        val updatedPattern = pattern.copy(isSaved = true)

        if (savedPatternsList.none { it.id == pattern.id }) {
            savedPatternsList.add(0, updatedPattern)
        }

        _uiState.update {
            it.copy(
                currentPattern = updatedPattern,
                savedPatterns = savedPatternsList.toList(),
                snackbarMessage = "Saved '${pattern.title}' to AI Patterns Library!"
            )
        }
    }

    fun loadSavedPattern(pattern: AiGeneratedPattern) {
        val cachedImage = imageCache[pattern.id] ?: if (!pattern.imageBase64.isNullOrBlank()) {
            val bmp = GeminiPatternGeneratorService.base64ToBitmap(pattern.imageBase64)
            bmp?.asImageBitmap()?.also { imageCache[pattern.id] = it }
        } else null

        _uiState.update {
            it.copy(
                currentPattern = pattern,
                sampleImageBitmap = cachedImage,
                promptInput = pattern.prompt,
                activeSectionTab = 0,
                snackbarMessage = "Loaded '${pattern.title}'"
            )
        }
    }

    fun deleteSavedPattern(patternId: String) {
        savedPatternsList.removeAll { it.id == patternId }
        val current = _uiState.value.currentPattern
        val updatedCurrent = if (current?.id == patternId) current.copy(isSaved = false) else current

        _uiState.update {
            it.copy(
                savedPatterns = savedPatternsList.toList(),
                currentPattern = updatedCurrent,
                snackbarMessage = "Removed pattern from library."
            )
        }
    }

    fun startAsActiveProject(onProjectCreated: (Project) -> Unit) {
        val pattern = _uiState.value.currentPattern ?: return
        viewModelScope.launch {
            val projectEntity = pattern.toProject()
            val newId = projectDao.insertProject(projectEntity)
            val createdProject = projectEntity.copy(id = newId)
            _uiState.update {
                it.copy(snackbarMessage = "Created active Project Notebook! Ready for Row Counter.")
            }
            onProjectCreated(createdProject)
        }
    }

    private fun loadFeaturedPattern() {
        val defaultPattern = AiGeneratedPattern(
            id = "featured_frog",
            prompt = "Chubby pastel frog amigurumi with tiny bucket hat and flower",
            title = "Chubby Cottagecore Frog & Bucket Hat",
            subtitle = "Adorably round plush frog with an interchangeable matching pastel daisy bucket hat.",
            category = "Amigurumi",
            difficulty = "Beginner",
            estimatedTime = "2.5 hours",
            hookSize = "3.5 mm (E-4)",
            yarnWeight = "Worsted / Cotton (4)",
            yarnColors = listOf("Moss Green", "Buttercream Yellow", "Blush Pink", "Snow White"),
            estimatedYardage = "120 yds main color, 35 yds accent colors",
            gauge = "16 sc x 18 rows = 4 inches (10 cm)",
            finishedDimensions = "5.5\" tall x 4\" wide",
            notions = listOf("3.5mm crochet hook", "8mm safety eyes (2)", "Polyester fiberfill stuffing", "Yarn tapestry needle", "Pink embroidery thread for cheeks"),
            keyStitches = listOf("Magic Ring (MR)", "Single Crochet (sc)", "Increase (inc)", "Invisible Decrease (dec)", "Half Double Crochet (hdc)", "Slip Stitch (sl st)"),
            sections = listOf(
                com.example.data.model.AiPatternSection(
                    sectionTitle = "Frog Body & Head (One Piece)",
                    sectionNotes = "Worked in continuous spiral rounds from top to bottom.",
                    steps = listOf(
                        com.example.data.model.AiPatternStep(1, "Rnd 1", "Make MR, 6 sc into ring", "6 sts", "Place stitch marker in 1st stitch"),
                        com.example.data.model.AiPatternStep(2, "Rnd 2", "2 sc in each st around (inc x6)", "12 sts"),
                        com.example.data.model.AiPatternStep(3, "Rnd 3", "(1 sc, 1 inc) repeat 6 times", "18 sts"),
                        com.example.data.model.AiPatternStep(4, "Rnd 4", "(2 sc, 1 inc) repeat 6 times", "24 sts"),
                        com.example.data.model.AiPatternStep(5, "Rnd 5", "(3 sc, 1 inc) repeat 6 times", "30 sts"),
                        com.example.data.model.AiPatternStep(6, "Rnd 6-11", "Sc in each st around (6 rounds)", "30 sts", "Insert safety eyes between Rnds 7-8, 6 sts apart"),
                        com.example.data.model.AiPatternStep(7, "Rnd 12", "(3 sc, 1 dec) repeat 6 times", "24 sts", "Stuff firmly with fiberfill"),
                        com.example.data.model.AiPatternStep(8, "Rnd 13", "(2 sc, 1 dec) repeat 6 times", "18 sts"),
                        com.example.data.model.AiPatternStep(9, "Rnd 14", "(1 sc, 1 dec) repeat 6 times", "12 sts", "Top up stuffing"),
                        com.example.data.model.AiPatternStep(10, "Rnd 15", "Dec around 6 times. Fasten off and cinch closed.", "6 sts")
                    )
                ),
                com.example.data.model.AiPatternSection(
                    sectionTitle = "Big Eye Bumps (Make 2)",
                    sectionNotes = "Work in green yarn.",
                    steps = listOf(
                        com.example.data.model.AiPatternStep(1, "Rnd 1", "MR, 6 sc in ring", "6 sts"),
                        com.example.data.model.AiPatternStep(2, "Rnd 2", "(1 sc, 1 inc) repeat 3 times", "9 sts"),
                        com.example.data.model.AiPatternStep(3, "Rnd 3", "Sc in each st around", "9 sts", "Fasten off leaving 8\" tail for sewing")
                    )
                ),
                com.example.data.model.AiPatternSection(
                    sectionTitle = "Mini Removable Bucket Hat",
                    sectionNotes = "Work in Buttercream Yellow yarn.",
                    steps = listOf(
                        com.example.data.model.AiPatternStep(1, "Rnd 1", "MR, 6 sc in ring", "6 sts"),
                        com.example.data.model.AiPatternStep(2, "Rnd 2", "Inc in each st around", "12 sts"),
                        com.example.data.model.AiPatternStep(3, "Rnd 3", "(1 sc, 1 inc) repeat 6 times", "18 sts"),
                        com.example.data.model.AiPatternStep(4, "Rnd 4", "In Back Loops Only (BLO): sc in each st around", "18 sts"),
                        com.example.data.model.AiPatternStep(5, "Rnd 5-6", "In both loops: sc in each st around (2 rounds)", "18 sts"),
                        com.example.data.model.AiPatternStep(6, "Rnd 7 (Brim)", "In Front Loops Only: (2 sc, 1 inc) repeat 6 times", "24 sts"),
                        com.example.data.model.AiPatternStep(7, "Rnd 8", "(3 sc, 1 inc) repeat 6 times, sl st to join. Fasten off.", "30 sts")
                    )
                )
            ),
            assemblyNotes = "1. Sew eye bumps onto top of head between Rnds 3-5.\n2. Embroider a gentle V-shaped smile between eyes with black floss.\n3. Embroider small blush marks below each eye with pink yarn.\n4. Place bucket hat on frog head at a playful tilt.",
            tips = listOf(
                "Use invisible decreases on the bottom to keep the base neat and stable.",
                "Embroider facial features before cinching the bottom closed for easy access inside the piece."
            ),
            careInstructions = "Spot clean with damp cloth, or gentle hand wash in cold water.",
            swatchHexColors = listOf("#74A57F", "#F6E8B1", "#F3AFA6", "#FFFFFF")
        )

        savedPatternsList.add(defaultPattern)
        _uiState.update {
            it.copy(
                currentPattern = defaultPattern,
                savedPatterns = savedPatternsList.toList()
            )
        }
    }
}
