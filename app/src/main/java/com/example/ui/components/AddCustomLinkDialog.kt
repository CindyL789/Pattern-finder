package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomLinkDialog(
    onDismiss: () -> Unit,
    onSaveCustomPattern: (
        title: String,
        link: String,
        sourcePlatform: String,
        category: String,
        difficulty: String,
        hookSize: String,
        yarnWeight: String,
        notes: String
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var sourcePlatform by remember { mutableStateOf("Web Blog") }
    var category by remember { mutableStateOf("Accessories") }
    var difficulty by remember { mutableStateOf("Easy") }
    var hookSize by remember { mutableStateOf("4.0 mm") }
    var yarnWeight by remember { mutableStateOf("Worsted") }
    var notes by remember { mutableStateOf("") }

    var categoryExpanded by remember { mutableStateOf(false) }
    val categories = listOf("Amigurumi", "Wearables", "Blankets", "Accessories", "Home Decor")

    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Custom Pattern Link",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) isError = false
                    },
                    label = { Text("Pattern Title *") },
                    isError = isError && title.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_title_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        if (it.isNotBlank()) isError = false
                    },
                    label = { Text("Pattern Web Link (URL) *") },
                    placeholder = { Text("https://...") },
                    isError = isError && link.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_link_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    category = item
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hookSize,
                        onValueChange = { hookSize = it },
                        label = { Text("Hook Size") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = yarnWeight,
                        onValueChange = { yarnWeight = it },
                        label = { Text("Yarn Weight") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Yarn Colors (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank() || link.isBlank()) {
                        isError = true
                    } else {
                        val formattedLink = if (!link.startsWith("http://") && !link.startsWith("https://")) {
                            "https://$link"
                        } else link

                        val platform = when {
                            link.contains("youtube.com", ignoreCase = true) || link.contains("youtu.be", ignoreCase = true) -> "YouTube"
                            link.contains("ravelry.com", ignoreCase = true) -> "Ravelry"
                            link.contains("etsy.com", ignoreCase = true) -> "Etsy"
                            link.contains("pinterest.com", ignoreCase = true) -> "Pinterest"
                            link.contains("lovecrafts.com", ignoreCase = true) -> "LoveCrafts"
                            else -> "Web Blog"
                        }

                        onSaveCustomPattern(
                            title,
                            formattedLink,
                            platform,
                            category,
                            difficulty,
                            hookSize,
                            yarnWeight,
                            notes
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_custom_pattern_button")
            ) {
                Text("Save Pattern Link", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
