package com.sahaaya.common.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing constants.
 *
 * [Dimens.MinTouchTarget] is 56dp rather than the Android default of 48dp:
 * the people using the patient side of this app frequently have tremor or
 * reduced fine motor control, and every interactive element in Sahaaya is
 * expected to meet this floor.
 */
object Dimens {
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 16.dp
    val SpaceLg = 24.dp
    val SpaceXl = 32.dp
    val SpaceXxl = 48.dp

    val ScreenPadding = 20.dp
    val CardPadding = 20.dp

    val MinTouchTarget = 56.dp
    val PrimaryButtonHeight = 60.dp
    val IconBadge = 48.dp
    val IconBadgeLarge = 64.dp
}
