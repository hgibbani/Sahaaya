package com.sahaaya.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme

/** The three kinds of inline message the app shows. */
enum class BannerTone { Error, Success, Info }

/**
 * Inline message strip.
 *
 * Marked as an assertive live region so a screen reader announces a failed sign
 * in immediately, instead of only when the user happens to swipe onto it.
 */
@Composable
fun Banner(
    message: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Error,
) {
    val container: Color
    val content: Color
    val icon: ImageVector

    when (tone) {
        BannerTone.Error -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Filled.ErrorOutline
        }
        BannerTone.Success -> {
            container = SahaayaTheme.status.okContainer
            content = MaterialTheme.colorScheme.onSurface
            icon = Icons.Filled.CheckCircle
        }
        BannerTone.Info -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
            icon = Icons.Outlined.Info
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = container, shape = MaterialTheme.shapes.small)
            .padding(Dimens.SpaceMd)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
        )
    }
}

/** Small status pill, e.g. an active or revoked pairing. */
@Composable
fun StatusChip(
    label: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
) {
    val container = when (tone) {
        BannerTone.Error -> MaterialTheme.colorScheme.errorContainer
        BannerTone.Success -> SahaayaTheme.status.okContainer
        BannerTone.Info -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (tone) {
        BannerTone.Error -> MaterialTheme.colorScheme.onErrorContainer
        BannerTone.Success -> MaterialTheme.colorScheme.onSurface
        BannerTone.Info -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = modifier
            .background(color = container, shape = MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXs),
    )
}
