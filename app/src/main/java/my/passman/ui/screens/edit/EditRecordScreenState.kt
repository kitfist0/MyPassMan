package my.passman.ui.screens.edit

data class EditRecordScreenState(
    val recordId: Long? = null,
    val isLoading: Boolean = false,
    val name: String = "",
    val secret: String = "",
    val comment: String = "",
    val secretVisible: Boolean = false,
    val showExitDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val canSave: Boolean = false,
    val created: Long? = null,
    val modified: Long? = null,
)
