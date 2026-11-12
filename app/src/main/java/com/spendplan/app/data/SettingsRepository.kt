package com.spendplan.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spendplan.app.domain.BudgetPeriod
import com.spendplan.app.domain.Currencies
import com.spendplan.app.domain.CurrencyOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val currencyCode: String = Currencies.LKR.code,
    val monthStartDay: Int = 1,
    val notificationsEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
) {
    val currency: CurrencyOption get() = Currencies.byCode(currencyCode)
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val CURRENCY = stringPreferencesKey("currency")
        val MONTH_START_DAY = intPreferencesKey("month_start_day")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            currencyCode = p[Keys.CURRENCY] ?: Currencies.LKR.code,
            monthStartDay = BudgetPeriod.clampStartDay(p[Keys.MONTH_START_DAY] ?: 1),
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: false,
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setCurrency(code: String) = store.edit { it[Keys.CURRENCY] = code }
    suspend fun setMonthStartDay(day: Int) = store.edit { it[Keys.MONTH_START_DAY] = BudgetPeriod.clampStartDay(day) }
    suspend fun setNotificationsEnabled(enabled: Boolean) = store.edit { it[Keys.NOTIFICATIONS] = enabled }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.DYNAMIC_COLOR] = enabled }
}
