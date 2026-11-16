package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.viewmodel.ImageLabUiState
import com.example.ui.viewmodel.ImageLabViewModel
import com.example.utils.DocumentExportManager
import com.google.firebase.ai.type.AspectRatio

@Composable
fun ImageLabScreen(
    viewModel: ImageLabViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val genState by viewModel.genState.collectAsStateWithLifecycle()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val sourceBitmap by viewModel.sourceBitmap.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
    var showOriginalCompare by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            showOriginalCompare = false
            viewModel.loadSourceFromUri(context, uri)
        }
    }

    val savePngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val bmp = (genState as? ImageLabUiState.Success)?.resultBitmap ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Studio Gambar & Edit Foto AI", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("pick_image_to_edit_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pilih Foto")
                    }
                }

                if (sourceBitmap != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                bitmap = sourceBitmap!!.asImageBitmap(),
                                contentDescription = "Foto referensi untuk diedit",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mode Edit Foto Aktif", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                Text("Tulis instruksi perubahan di bawah.", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.clearSourceImage() }) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus foto sumber")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { viewModel.updatePrompt(it) },
                    modifier = Modifier.fillMaxWidth().testTag("image_prompt_input"),
                    label = {
                        Text(
                            if (sourceBitmap != null) "Instruksi edit (mis. ubah latar menjadi cyberpunk neon)"
                            else "Deskripsikan gambar baru (mis. arsitektur futuristik di pegunungan)"
                        )
                    },
                    minLines = 2,
                    maxLines = 4
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ratios = listOf(
                        AspectRatio.SQUARE_1x1 to "1:1",
                        AspectRatio.LANDSCAPE_16x9 to "16:9",
                        AspectRatio.PORTRAIT_9x16 to "9:16",
                        AspectRatio.LANDSCAPE_4x3 to "4:3"
                    )
                    ratios.forEach { (ratio, label) ->
                        FilterChip(
                            selected = aspectRatio == ratio,
                            onClick = { viewModel.setAspectRatio(ratio) },
                            label = { Text(label) }
                        )
                    }
                }

                Button(
                    onClick = {
                        showOriginalCompare = false
                        viewModel.generateOrEdit()
                    },
                    enabled = prompt.isNotBlank() && genState !is ImageLabUiState.Loading,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("generate_edit_image_button")
                ) {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (sourceBitmap != null) "Edit Foto dengan AI" else "Buat Gambar AI")
                }
            }
        }

        // Result Canvas
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                when (val state = genState) {
                    is ImageLabUiState.Idle -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_studio),
                                contentDescription = "Ilustrasi Studio Gambar AI",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            )
                            Text(
                                text = "Pilih foto untuk diedit atau masukkan deskripsi untuk membuat gambar baru dengan Gemini Nano Banana 2.1.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is ImageLabUiState.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 48.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(40.dp))
                            Text(
                                text = "Menyintesis gambar AI (estimasi 30–60 detik)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is ImageLabUiState.Success -> {
                        val displayBitmap = if (showOriginalCompare && sourceBitmap != null) sourceBitmap!! else state.resultBitmap
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(
                                bitmap = displayBitmap.asImageBitmap(),
                                contentDescription = if (showOriginalCompare) "Gambar Asli" else "Hasil Gambar AI",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (sourceBitmap != null) {
                                    OutlinedButton(onClick = { showOriginalCompare = !showOriginalCompare }) {
                                        Icon(Icons.Default.Compare, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (showOriginalCompare) "Lihat Hasil" else "Lihat Asli")
                                    }
                                }
                                OutlinedButton(onClick = { viewModel.useOutputAsSource(state.resultBitmap) }) {
                                    Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Edit Lagi")
                                }
                                OutlinedButton(onClick = {
                                    savePngLauncher.launch("karsa_ai_${System.currentTimeMillis()}.png")
                                }) {
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Simpan")
                                }
                                Button(onClick = {
                                    DocumentExportManager.shareBitmap(context, state.resultBitmap)
                                }) {
                                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Bagikan")
                                }
                            }
                        }
                    }
                    is ImageLabUiState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
