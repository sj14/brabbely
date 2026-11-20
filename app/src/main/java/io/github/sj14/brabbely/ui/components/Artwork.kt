package io.github.sj14.brabbely.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

/**
 * Square cover art with rounded corners. If [url] is missing or fails to load, [fallbackUrl]
 * (usually the podcast cover) is shown instead, and a placeholder if that fails too.
 */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    fallbackUrl: String? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val hasFallback = fallbackUrl != null && fallbackUrl != url
        CoverImage(
            url = url ?: fallbackUrl,
            onError = { if (hasFallback) CoverImage(fallbackUrl, onError = { Placeholder() }) else Placeholder() },
        )
    }
}

@Composable
private fun CoverImage(url: String?, onError: @Composable () -> Unit) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        error = { onError() },
        loading = { Placeholder() },
    )
}

@Composable
private fun Placeholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.Podcasts,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
