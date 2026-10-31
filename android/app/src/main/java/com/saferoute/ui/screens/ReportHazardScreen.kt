package com.saferoute.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.saferoute.data.model.HazardType
import com.saferoute.ui.viewmodel.ReportHazardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHazardScreen(
    onNavigateBack: () -> Unit,
    onReportSubmitted: () -> Unit,
    viewModel: ReportHazardViewModel = hiltViewModel()
) {
    var selectedHazardType by remember { mutableStateOf<HazardType?>(null) }
    var description by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val isSubmitting = viewModel.isSubmitting.value
    val error = viewModel.error.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Hazard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Select Hazard Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(HazardType.values()) { hazardType ->
                    HazardTypeCard(
                        hazardType = hazardType,
                        isSelected = selectedHazardType == hazardType,
                        onClick = { selectedHazardType = hazardType }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Description (Optional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add details about the hazard...") },
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Photo button
            Button(
                onClick = { /* TODO: Open camera */ },
                modifier = Modifier.fillMaxWidth(),
                enabled = false // TODO: Enable when camera implemented
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Add Photo")
            }

            Spacer(modifier = Modifier.weight(1f))

            // Submit button
            Button(
                onClick = {
                    selectedHazardType?.let { type ->
                        viewModel.submitReport(
                            hazardType = type,
                            description = description.ifEmpty { null },
                            onSuccess = onReportSubmitted,
                            onError = { message ->
                                // Show error
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = selectedHazardType != null && !isSubmitting,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Submit Report",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HazardTypeCard(
    hazardType: HazardType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hazardName = when (hazardType) {
        HazardType.SPEED_BREAKER -> "Speed Breaker"
        HazardType.POTHOLE -> "Pothole"
        HazardType.SHARP_CURVE -> "Sharp Curve"
        HazardType.DANGEROUS_INTERSECTION -> "Dangerous Intersection"
        HazardType.ROAD_CONSTRUCTION -> "Road Construction"
        HazardType.ACCIDENT -> "Accident"
        HazardType.WATERLOGGING -> "Waterlogging"
        HazardType.ROAD_OBSTRUCTION -> "Road Obstruction"
        HazardType.DAMAGED_ROAD -> "Damaged Road"
        HazardType.RAILWAY_CROSSING -> "Railway Crossing"
        HazardType.OTHER -> "Other"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hazardName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
