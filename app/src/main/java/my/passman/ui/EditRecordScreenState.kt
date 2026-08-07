package my.passman.ui

data class EditRecordScreenState(
    val name: String = "",
    val secret: String = "",
    val comment: String = "",
    val secretVisible: Boolean = false,
    val showExitDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
)
