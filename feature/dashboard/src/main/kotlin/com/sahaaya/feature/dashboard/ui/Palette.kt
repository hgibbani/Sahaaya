package com.sahaaya.feature.dashboard.ui

import androidx.compose.ui.graphics.Color

/**
 * Shared by the patient and caregiver dashboards so the two read as one app:
 * the same light ground, ink and pastel tiles on both sides.
 */
internal object Palette {
    val Background = Color(0xFFF6F8FC)
    val Ink = Color(0xFF1B2A41)
    val Muted = Color(0xFF5B6B82)
    val Accent = Color(0xFF2F7BEA)
    val Avatar = Color(0xFF9CC3F5)
    val ChipBg = Color(0xFFE8EEF6)
    val Border = Color(0xFFE3E8EF)

    val CallCircle = Color(0xFFD9F5E6)
    val CallIcon = Color(0xFF22A55B)

    val LinkBg = Color(0xFFE8F1FE)
    val LinkCircle = Color(0xFFD3E4FC)
    val LinkIcon = Color(0xFF3B82F6)

    val LocationBg = Color(0xFFE3F7EE)
    val LocationCircle = Color(0xFFCDEFDD)
    val LocationIcon = Color(0xFF22A55B)

    val MedicineBg = Color(0xFFF0EBFD)
    val MedicineCircle = Color(0xFFE2D9FB)
    val MedicineIcon = Color(0xFF7C4DDB)

    val ReminderBg = Color(0xFFFEF0E1)
    val ReminderCircle = Color(0xFFFCE0C2)
    val ReminderIcon = Color(0xFFF08A24)

    val EmergencyBg = Color(0xFFFDE7EA)
    val EmergencyCircle = Color(0xFFFBD3D8)
    val EmergencyIcon = Color(0xFFE5484D)

    val SettingsBg = Color(0xFFECEFF4)
    val SettingsCircle = Color(0xFFDDE2EA)
    val SettingsIcon = Color(0xFF64748B)

    // --- Caregiver-only accents ---------------------------------------------
    val CaregiverAvatar = Color(0xFFB9A8F0)
    val PatientAvatarCircle = Color(0xFFD9F5E6)
    val OnlineBg = Color(0xFFDDF6E7)
    val OnlineDot = Color(0xFF22A55B)
    val OfflineBg = Color(0xFFECEFF4)
    val OfflineDot = Color(0xFF94A3B8)
    val StripBg = Color(0xFFEEF3FB)
    val OutsideBg = Color(0xFFFDE7EA)
    val OutsideText = Color(0xFFC62828)
}
