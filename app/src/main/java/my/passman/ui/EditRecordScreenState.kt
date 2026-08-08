package my.passman.ui

data class EditRecordScreenState(
    val recordId: Long? = null,
    val isLoading: Boolean = false,
    val name: String = "",
    val secret: String = "",
    val comment: String = "",
    val secretVisible: Boolean = false,
    val showExitDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
)
