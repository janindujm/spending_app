package com.spendplan.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendplan.app.AppContainer
import com.spendplan.app.SpendPlanApp
import com.spendplan.app.data.AppSettings
import com.spendplan.app.domain.BudgetPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as SpendPlanApp).container

/** Creates a ViewModel scoped to the current navigation entry, built from the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** The selected budget month plus the settings it depends on. */
data class MonthContext(val month: YearMonth, val settings: AppSettings)

/**
 * Base for screens with a month selector. The month starts at the current budget month
 * (respecting the month start day) plus [initialOffset], e.g. +1 for "next month".
 */
abstract class MonthViewModel(
    container: AppContainer,
    private val initialOffset: Long = 0,
    initialMonth: YearMonth? = null,
) : ViewModel() {
    private val selected = MutableStateFlow(initialMonth)

    val monthContext: StateFlow<MonthContext?> =
        combine(container.settingsRepository.settings, selected) { settings, month ->
            MonthContext(
                month ?: BudgetPeriod.current(LocalDate.now(), settings.monthStartDay).plusMonths(initialOffset),
                settings,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    protected val monthContextFlow = monthContext.filterNotNull()

    open fun previousMonth() = shift(-1)
    open fun nextMonth() = shift(1)

    private fun shift(by: Long) {
        val current = monthContext.value?.month ?: return
        selected.value = current.plusMonths(by)
    }
}
