package com.sahaaya.core.validation

/**
 * Input rules shared by every form in the app.
 *
 * Validation lives in :core rather than in the screens so that the same rule is
 * applied whether input arrives from the registration form, a profile edit, or
 * (later) an imported record. Each function returns `null` when the value is
 * acceptable, or the reason it is not.
 */
object Validators {

    const val MIN_PASSWORD_LENGTH = 8
    const val PAIRING_CODE_LENGTH = 6

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    /** Indian mobile numbers, with or without the +91 country code. */
    private val PHONE_REGEX = Regex("^(\\+91[- ]?)?[6-9]\\d{9}$")

    private val PAIRING_CODE_REGEX = Regex("^[A-HJ-NP-Z2-9]{$PAIRING_CODE_LENGTH}$")

    fun email(value: String): String? = when {
        value.isBlank() -> "Email is required"
        !EMAIL_REGEX.matches(value.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun password(value: String): String? = when {
        value.isEmpty() -> "Password is required"
        value.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters"
        value.none { it.isDigit() } -> "Include at least one number"
        value.none { it.isLetter() } -> "Include at least one letter"
        else -> null
    }

    fun confirmPassword(password: String, confirmation: String): String? = when {
        confirmation.isEmpty() -> "Please confirm your password"
        password != confirmation -> "Passwords do not match"
        else -> null
    }

    fun fullName(value: String): String? = when {
        value.isBlank() -> "Name is required"
        value.trim().length < 2 -> "Name is too short"
        value.trim().length > 60 -> "Name is too long"
        else -> null
    }

    fun phoneNumber(value: String, required: Boolean = true): String? {
        val trimmed = value.trim()
        return when {
            trimmed.isBlank() -> if (required) "Phone number is required" else null
            !PHONE_REGEX.matches(trimmed.replace(" ", "")) -> "Enter a valid 10-digit mobile number"
            else -> null
        }
    }

    fun pairingCode(value: String): String? = when {
        value.isBlank() -> "Enter the code shown on the patient's phone"
        !PAIRING_CODE_REGEX.matches(value.trim().uppercase()) ->
            "Codes are $PAIRING_CODE_LENGTH letters and numbers"
        else -> null
    }

    fun relationship(value: String): String? = when {
        value.isBlank() -> "Relationship is required"
        value.trim().length > 40 -> "Relationship is too long"
        else -> null
    }

    /** Free-text clinical notes. Optional, but bounded so a document stays small. */
    fun notes(value: String, maxLength: Int = 1000): String? = when {
        value.length > maxLength -> "Please keep this under $maxLength characters"
        else -> null
    }
}
