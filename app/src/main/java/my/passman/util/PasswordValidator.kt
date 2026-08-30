package my.passman.util

object PasswordValidator {
    fun validate(password: String): List<PasswordRequirement> {
        val requirements = mutableListOf<PasswordRequirement>()
        if (password.length < 8) {
            requirements.add(PasswordRequirement.MIN_LENGTH)
        }
        if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            requirements.add(PasswordRequirement.LETTERS_AND_DIGITS)
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            requirements.add(PasswordRequirement.HAS_SYMBOL)
        }
        return requirements
    }

    enum class PasswordRequirement {
        MIN_LENGTH,
        LETTERS_AND_DIGITS,
        HAS_SYMBOL,
    }
}
