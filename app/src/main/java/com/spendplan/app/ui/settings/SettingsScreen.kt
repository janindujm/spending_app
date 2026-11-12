package com.spendplan.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spendplan.app.AppContainer
import com.spendplan.app.BuildConfigInfo
import com.spendplan.app.data.AppSettings
import com.spendplan.app.data.ThemeMode
import com.spendplan.app.domain.Currencies
import com.spendplan.app.ui.LocalAppMessenger
import com.spendplan.app.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.settingsRepository
    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setCurrency(code: String) = viewModelScope.launch { repo.setCurrency(code) }
    fun setMonthStartDay(day: Int) = viewModelScope.launch { repo.setMonthStartDay(day) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { repo.setDynamicColor(on) }
    fun setNotifications(on: Boolean) = viewModelScope.launch {
        repo.setNotificationsEnabled(on)
        if (on) container.onDataChanged()
    }

    fun exportCsv(uri: Uri, done: (String) -> Unit) = viewModelScope.launch {
        done(
            runCatching { container.backupRepository.exportCsv(uri) }
                .fold({ "Exported $it transactions" }, { "Export failed: ${it.message}" }),
        )
    }

    fun backup(uri: Uri, done: (String) -> Unit) = viewModelScope.launch {
        done(
            runCatching { container.backupRepository.backup(uri) }
                .fold({ "Backup saved ($it transactions)" }, { "Backup failed: ${it.message}" }),
        )
    }

    fun restore(uri: Uri, done: (String) -> Unit) = viewModelScope.launch {
        done(
            runCatching { container.backupRepository.restore(uri) }.fold(
                { "Restored ${it.transactions} transactions, ${it.categories} categories and ${it.plans} plans" },
                { "Restore failed: ${it.message}" },
            ),
        )
        container.runRecurringNow()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenCategories: () -> Unit, onOpenRecurring: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    val context = LocalContext.current

    var showCurrency by rememberSaveable { mutableStateOf(false) }
    var showStartDay by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setNotifications(granted)
        if (!granted) messenger.show("Notifications permission denied — budget alerts stay off")
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportCsv(it, messenger::show) }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.backup(it, messenger::show) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.restore(it, messenger::show) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        val s = settings ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("General") }
            item {
                SettingRow(Icons.Filled.ShoppingCart, "Currency", "${s.currency.displayName} (${s.currency.symbol})") { showCurrency = true }
            }
            item {
                SettingRow(
                    Icons.Filled.DateRange,
                    "Month starts on day",
                    if (s.monthStartDay == 1) "1 (calendar month)" else "${s.monthStartDay} — e.g. your salary day",
                ) { showStartDay = true }
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    headlineContent = { Text("Budget alerts") },
                    supportingContent = { Text("Notify when a category reaches 80% and 100% of its budget") },
                    trailingContent = {
                        Switch(
                            checked = s.notificationsEnabled,
                            onCheckedChange = { on ->
                                val needsPermission = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                if (needsPermission) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    vm.setNotifications(on)
                                }
                            },
                        )
                    },
                )
            }

            item { SectionHeader("Appearance") }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null)
                        Text("Theme", Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = s.themeMode == mode,
                                onClick = { vm.setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(label) }
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    ListItem(
                        headlineContent = { Text("Dynamic color") },
                        supportingContent = { Text("Use colors from your wallpaper") },
                        trailingContent = { Switch(checked = s.dynamicColor, onCheckedChange = vm::setDynamicColor) },
                    )
                }
            }

            item { SectionHeader("Manage") }
            item { SettingRow(Icons.AutoMirrored.Filled.List, "Categories", "Add, rename, recolor or delete categories", onOpenCategories) }
            item { SettingRow(Icons.Filled.Refresh, "Recurring transactions", "Pause or delete repeating entries", onOpenRecurring) }

            item { SectionHeader("Data") }
            item {
                SettingRow(Icons.Filled.Share, "Export transactions (CSV)", "Save a spreadsheet-friendly file") {
                    csvLauncher.launch("spendplan-transactions-${LocalDate.now()}.csv")
                }
            }
            item {
                SettingRow(Icons.Filled.Lock, "Back up data (JSON)", "Save everything to a file you choose") {
                    backupLauncher.launch("spendplan-backup-${LocalDate.now()}.json")
                }
            }
            item {
                SettingRow(Icons.Filled.Refresh, "Restore from backup", "Replace all data with a backup file") { confirmRestore = true }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    headlineContent = { Text("SpendPlan ${BuildConfigInfo.versionName(context)}") },
                    supportingContent = {
                        Text("All data is stored only on this device. The app has no internet access and no analytics.")
                    },
                )
            }
        }

        if (showCurrency) {
            AlertDialog(
                onDismissRequest = { showCurrency = false },
                title = { Text("Currency") },
                text = {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(Currencies.all, key = { it.code }) { c ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = c.code == s.currencyCode,
                                        onClick = { vm.setCurrency(c.code); showCurrency = false },
                                        role = Role.RadioButton,
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = c.code == s.currencyCode, onClick = null)
                                Text("${c.displayName} (${c.symbol})", Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showCurrency = false }) { Text("Close") } },
            )
        }

        if (showStartDay) {
            AlertDialog(
                onDismissRequest = { showStartDay = false },
                title = { Text("Month starts on day") },
                text = {
                    Column {
                        Text(
                            "Use your salary day to budget by pay cycle. The month of October then runs from day N of October to day N−1 of November.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            modifier = Modifier.heightIn(max = 260.dp).padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items((1..28).toList()) { day ->
                                FilterChip(
                                    selected = s.monthStartDay == day,
                                    onClick = { vm.setMonthStartDay(day); showStartDay = false },
                                    label = { Text("$day") },
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showStartDay = false }) { Text("Close") } },
            )
        }

        if (confirmRestore) {
            AlertDialog(
                onDismissRequest = { confirmRestore = false },
                title = { Text("Restore backup?") },
                text = { Text("All current transactions, categories and plans will be replaced by the backup. This can't be undone. Consider backing up first.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRestore = false
                        restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }) { Text("Choose file") }
                },
                dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
    )
}
