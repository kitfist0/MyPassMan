package my.passman.data

/**
 * Color a tag is shown with. Stored by name in the database and in backups, so entries
 * may be added but never renamed. The actual colors live in the UI theme, per light/dark mode.
 */
enum class TagColor {
    DEFAULT,
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    TEAL,
    BLUE,
    PURPLE,
    PINK,
    GRAY,
}
