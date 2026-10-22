package com.tcltv.sahne.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tcltv.sahne.R
import com.tcltv.sahne.data.PickedImage
import java.io.File

/**
 * Picks a picture file through the system's file picker, so Matinee needs no storage permission. Many TVs ship only a
 * stub for that picker; when no real one is installed this lists the pictures in Matinee's own folder instead, which
 * a file manager or a USB transfer can fill.
 */
@Composable
fun ImagePicker(onPicked: (PickedImage) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val hasSystemPicker = remember { systemPickerAvailable(context.packageManager) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPicked(PickedImage(uri)) else onDismiss()
    }
    LaunchedEffect(hasSystemPicker) { if (hasSystemPicker) launcher.launch("image/*") }
    if (!hasSystemPicker) FolderFallback(context.getExternalFilesDir(PICTURES_DIR), onPicked, onDismiss)
}

private fun systemPickerAvailable(packageManager: PackageManager): Boolean {
    val intent = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
    return packageManager.queryIntentActivities(intent, 0).any { it.activityInfo.packageName != STUB_PACKAGE }
}

@Composable
private fun FolderFallback(folder: File?, onPicked: (PickedImage) -> Unit, onDismiss: () -> Unit) {
    val files = remember(folder) {
        folder?.apply { mkdirs() }?.listFiles { f -> f.isFile && f.extension.lowercase() in IMAGE_EXTENSIONS }?.sortedBy { it.name }.orEmpty()
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(files) { if (files.isNotEmpty()) runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(680.dp)
                .heightIn(max = 420.dp)
                .glass(RoundedCornerShape(32.dp), GlassStyle.Dense)
                .padding(24.dp),
        ) {
            BasicText(stringResource(R.string.picker_title), style = interStyle(20.sp, 800))
            Spacer(Modifier.height(10.dp))
            BasicText(stringResource(R.string.picker_no_system_picker, folder?.path.orEmpty()), style = interStyle(13.sp, 500, Palette.TextSecondary))
            Spacer(Modifier.height(14.dp))
            if (files.isEmpty()) {
                BasicText(stringResource(R.string.picker_empty), style = interStyle(13.sp, 500, Palette.TextSecondary))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(files.size) { index ->
                        FileRow(files[index].name, if (index == 0) Modifier.focusRequester(first) else Modifier) {
                            onPicked(PickedImage(Uri.fromFile(files[index])))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(name: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .then(if (focused) Modifier.glass(CircleShape, GlassStyle.Bright) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(14.sp, 600, if (focused) Palette.Background else Palette.TextPrimary))
    }
}

private const val PICTURES_DIR = "Pictures"
private const val STUB_PACKAGE = "com.android.tv.frameworkpackagestubs"
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
