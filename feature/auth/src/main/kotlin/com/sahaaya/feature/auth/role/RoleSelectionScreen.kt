package com.sahaaya.feature.auth.role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.sahaaya.common.components.ActionCard
import com.sahaaya.common.components.SahaayaScreen
import com.sahaaya.common.components.TertiaryButton
import com.sahaaya.common.theme.Dimens
import com.sahaaya.common.theme.SahaayaTheme
import com.sahaaya.domain.model.Role

/**
 * First screen of the app: which side of the care relationship are you?
 *
 * This is a fork rather than a dropdown on the registration form because the
 * choice is permanent and shapes everything after it - the dashboard, the
 * security rules, the half of a pairing the account occupies. It deserves a
 * screen where both options are stated plainly.
 */
@Composable
fun RoleSelectionScreen(
    onRoleChosen: (Role) -> Unit,
    onSignInInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SahaayaScreen(
        title = "Welcome to Sahaaya",
        modifier = modifier,
    ) {
        Text(
            text = "Who is setting up this phone?",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "This choice decides what the app shows you. It cannot be " +
                "changed later, so pick the person who will actually use this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceSm))

        ActionCard(
            title = "I am the patient",
            subtitle = "This phone stays with the person receiving care",
            icon = Icons.Filled.Elderly,
            onClick = { onRoleChosen(Role.PATIENT) },
        )

        ActionCard(
            title = "I am a caregiver",
            subtitle = "I look after someone and want to be alerted",
            icon = Icons.Filled.Favorite,
            onClick = { onRoleChosen(Role.CAREGIVER) },
            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
            iconBackground = MaterialTheme.colorScheme.secondaryContainer,
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceLg))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            Text(
                text = "Already have an account?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TertiaryButton(text = "Sign in", onClick = onSignInInstead)
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceMd))

        Text(
            text = "Sahaaya means support. It does not replace a caregiver - " +
                "it stands beside them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SpaceMd),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RoleSelectionPreview() {
    SahaayaTheme {
        RoleSelectionScreen(onRoleChosen = {}, onSignInInstead = {})
    }
}
