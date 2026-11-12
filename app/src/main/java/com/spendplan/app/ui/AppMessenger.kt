package com.spendplan.app.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import com.spendplan.app.AppContainer
import com.spendplan.app.data.DeletedTransaction
import kotlinx.coroutines.launch

/**
 * App-wide snackbar messages. Lives at the root so an "Undo" survives navigating away from
 * the screen that deleted something, and restores through app-scoped repositories.
 */
class AppMessenger(
    private val snackbar: SnackbarHostState,
    private val container: AppContainer,
) {
    fun show(message: String) {
        container.appScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    fun showUndoDelete(deleted: DeletedTransaction, message: String = "Transaction deleted") {
        container.appScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                container.transactionRepository.restore(deleted)
            }
            container.onDataChanged()
        }
    }
}

val LocalAppMessenger = staticCompositionLocalOf<AppMessenger> { error("AppMessenger not provided") }
