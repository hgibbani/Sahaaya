package com.sahaaya.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.core.demo.DemoConfig

/**
 * The "DEMO MODE" pill shown in the top bar of every screen that has one.
 *
 * It exists so that nobody watching a demonstration - a mentor, an examiner, a
 * family trying the app - can mistake seeded local data for a live account.
 * Composes to nothing when [DemoConfig.ENABLED] is `false`, so switching back to
 * Firebase removes it everywhere at once without touching a single screen.
 */
@Composable
fun DemoModeBadge(modifier: Modifier = Modifier) {
    if (!DemoConfig.ENABLED) return

    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.extraSmall,
            )
            .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXs)
            .clearAndSetSemantics {
                contentDescription = "Demo mode. Data is stored on this device only."
            },
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = DemoConfig.BADGE_LABEL,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/**
 * Full-width explanation of what demo mode is, for the screens where a user
 * arrives before they have seen the badge - sign in and registration.
 */
@Composable
fun DemoModeBanner(modifier: Modifier = Modifier) {
    if (!DemoConfig.ENABLED) return

    Banner(
        message = "Demo mode: accounts and alerts are stored on this device only, " +
            "not in Firebase. Sign in as ${DemoConfig.PATIENT_EMAIL} or " +
            "${DemoConfig.CAREGIVER_EMAIL} with the password " +
            "\"${DemoConfig.SEED_PASSWORD}\", or register a new account.",
        tone = BannerTone.Info,
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview
@Composable
private fun DemoModeBadgePreview() {
    SahaayaTheme {
        DemoModeBadge()
    }
}
