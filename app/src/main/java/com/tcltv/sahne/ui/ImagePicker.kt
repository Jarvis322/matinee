package com.tcltv.sahne.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.tcltv.sahne.R
import com.tcltv.sahne.data.Gallery
import com.tcltv.sahne.data.GalleryFolder
import com.tcltv.sahne.data.GalleryImage
import com.tcltv.sahne.ui.home.focusScale
import com.tcltv.sahne.ui.home.scaleBy

private sealed interface GalleryState {
    data object Asking : GalleryState
    data object Denied : GalleryState
    data object Loading : GalleryState
    data class Ready(val folders: List<GalleryFolder>) : GalleryState
}

/**
 * Pick a picture from this TV or a USB drive: folders first, then a grid of thumbnails. Asks for permission to read
 * pictures only now, when the user has chosen to add one.
 */
@Composable
fun ImagePicker(onPicked: (GalleryImage) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val gallery = remember { Gallery(context.applicationContext) }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    var state by remember {
        mutableStateOf<GalleryState>(
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) GalleryState.Loading else GalleryState.Asking,
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state = if (granted) GalleryState.Loading else GalleryState.Denied
    }
    LaunchedEffect(Unit) { if (state == GalleryState.Asking) request.launch(permission) }
    LaunchedEffect(state) {
        if (state == GalleryState.Loading) state = GalleryState.Ready(gallery.folders())
    }
    var folder by remember { mutableStateOf<GalleryFolder?>(null) }

    Dialog(onDismissRequest = { if (folder != null) folder = null else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(680.dp)
                .heightIn(max = 420.dp)
                .glass(RoundedCornerShape(32.dp), GlassStyle.Dense)
                .padding(24.dp),
        ) {
            BasicText(folder?.name ?: stringResource(R.string.picker_title), maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(20.sp, 800))
            Spacer(Modifier.height(14.dp))
            when (val s = state) {
                GalleryState.Asking, GalleryState.Loading -> BasicText(stringResource(R.string.picker_loading), style = interStyle(13.sp, 500, Palette.TextSecondary))
                GalleryState.Denied -> BasicText(stringResource(R.string.picker_denied), style = interStyle(13.sp, 500, Palette.TextSecondary))
                is GalleryState.Ready -> when {
                    s.folders.isEmpty() -> BasicText(stringResource(R.string.picker_empty), style = interStyle(13.sp, 500, Palette.TextSecondary))
                    folder == null -> FolderList(s.folders) { folder = it }
                    else -> ImageGrid(gallery, folder!!, onPicked)
                }
            }
        }
    }
}

@Composable
private fun FolderList(folders: List<GalleryFolder>, onOpen: (GalleryFolder) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(folders.size) { index ->
            val f = folders[index]
            PickerRow(
                title = f.name,
                trailing = stringResource(R.string.picker_count, f.images.size),
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            ) { onOpen(f) }
        }
    }
}

@Composable
private fun PickerRow(title: String, trailing: String, modifier: Modifier, onClick: () -> Unit) {
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
        BasicText(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(14.sp, 600, if (focused) Palette.Background else Palette.TextPrimary), modifier = Modifier.weight(1f))
        BasicText(trailing, style = interStyle(12.sp, 500, if (focused) Palette.Background.copy(alpha = 0.7f) else Palette.TextSecondary))
    }
}

@Composable
private fun ImageGrid(gallery: Gallery, folder: GalleryFolder, onPicked: (GalleryImage) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(folder) { runCatching { first.requestFocus() } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(folder.images.size, key = { folder.images[it].uri.toString() }) { index ->
            val image = folder.images[index]
            Thumbnail(gallery, image, if (index == 0) Modifier.focusRequester(first) else Modifier) { onPicked(image) }
        }
    }
}

@Composable
private fun Thumbnail(gallery: Gallery, image: GalleryImage, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var bitmap by remember(image.uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image.uri) { bitmap = gallery.thumbnail(image.uri, THUMB_PX)?.asImageBitmap() }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .scaleBy(focusScale(focused, 1.07f))
            .clip(shape)
            .glass(shape, if (focused) GlassStyle.Lit else GlassStyle.Inset)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = image.name }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

private const val THUMB_PX = 360
