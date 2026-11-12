package com.spendplan.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spendplan.app.AppContainer
import com.spendplan.app.data.CategoryDeleteResult
import com.spendplan.app.data.db.CategoryEntity
import com.spendplan.app.data.db.TransactionType
import com.spendplan.app.ui.LocalAppMessenger
import com.spendplan.app.ui.appViewModel
import com.spendplan.app.ui.components.CategoryBadge
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val CategoryEmojis = listOf(
    "🍔", "🍜", "☕", "🍺", "🛒", "🥦", "🚌", "🚗", "⛽", "🚕", "🏍️", "✈️",
    "🏠", "💡", "💧", "📱", "🌐", "🔧", "💊", "🏥", "🎓", "📚", "🎬", "🎮",
    "🎵", "🏋️", "🛍️", "👕", "💄", "🎁", "🐶", "👶", "💳", "🏦", "📦", "🙏",
    "💼", "💻", "💰", "📈", "🧾", "⭐",
)

val CategoryColors = listOf(
    0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF7986CB, 0xFF64B5F6,
    0xFF4FC3F7, 0xFF4DD0E1, 0xFF4DB6AC, 0xFF81C784, 0xFFAED581, 0xFFDCE775,
    0xFFFFD54F, 0xFFFFB74D, 0xFFFF8A65, 0xFFA1887F, 0xFF90A4AE, 0xFFBDBDBD,
).map { it.toInt() }

class CategoriesViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.categoryRepository
    val categories: StateFlow<List<CategoryEntity>> = repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun validate(name: String, type: TransactionType, exceptId: Long?) = repo.validateName(name, type, exceptId)

    fun save(existing: CategoryEntity?, name: String, type: TransactionType, emoji: String, color: Int) = viewModelScope.launch {
        if (existing == null) repo.add(name, type, emoji, color) else repo.update(existing.copy(name = name, emoji = emoji, color = color))
    }

    fun delete(category: CategoryEntity, onInUse: (Int) -> Unit, onDeleted: () -> Unit) = viewModelScope.launch {
        when (val r = repo.delete(category)) {
            is CategoryDeleteResult.InUse -> onInUse(r.usageCount)
            CategoryDeleteResult.Deleted -> onDeleted()
        }
    }

    fun reassignAndDelete(category: CategoryEntity, targetId: Long, onDone: () -> Unit) = viewModelScope.launch {
        repo.reassignAndDelete(category, targetId)
        container.onDataChanged()
        onDone()
    }
}

private data class EditorTarget(val existing: CategoryEntity?, val type: TransactionType)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val vm = appViewModel { CategoriesViewModel(it) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    var tab by rememberSaveable { mutableStateOf(0) }
    val type = if (tab == 0) TransactionType.EXPENSE else TransactionType.INCOME
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    var reassign by remember { mutableStateOf<Pair<CategoryEntity, Int>?>(null) }
    var confirmDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editor = EditorTarget(null, type) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add category")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Expense") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Income") })
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(categories.filter { it.type == type }, key = { it.id }) { c ->
                    ListItem(
                        modifier = Modifier.clickable { editor = EditorTarget(c, c.type) },
                        leadingContent = { CategoryBadge(c) },
                        headlineContent = { Text(c.name) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editor = EditorTarget(c, c.type) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${c.name}")
                                }
                                IconButton(onClick = { confirmDelete = c }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${c.name}")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    editor?.let { target ->
        CategoryEditorDialog(
            target = target,
            validate = vm::validate,
            onDismiss = { editor = null },
            onSave = { name, emoji, color ->
                vm.save(target.existing, name, target.type, emoji, color)
                editor = null
            },
        )
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete “${c.name}”?") },
            text = { Text("Its budgets in all plans will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    vm.delete(c, onInUse = { count -> reassign = c to count }, onDeleted = { messenger.show("Category deleted") })
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    reassign?.let { (category, count) ->
        val targets = categories.filter { it.type == category.type && it.id != category.id }
        ReassignDialog(
            category = category,
            usageCount = count,
            targets = targets,
            onDismiss = { reassign = null },
            onConfirm = { targetId ->
                reassign = null
                vm.reassignAndDelete(category, targetId) { messenger.show("Moved items and deleted “${category.name}”") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    target: EditorTarget,
    validate: suspend (String, TransactionType, Long?) -> String?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(target.existing?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(target.existing?.emoji ?: CategoryEmojis.first()) }
    var color by rememberSaveable { mutableStateOf(target.existing?.color ?: CategoryColors.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target.existing == null) "New ${target.type.name.lowercase()} category" else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(CategoryEntity(name = name, type = target.type, emoji = emoji, color = color), 48.dp)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40); error = null },
                        label = { Text("Name") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { message -> @Composable { Text(message) } },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    Modifier.heightIn(max = 220.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CategoryEmojis.forEach { e ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(
                                    if (e == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .selectable(selected = e == emoji, onClick = { emoji = e }, role = Role.RadioButton),
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryColors.forEach { c ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(c), CircleShape)
                                .then(
                                    if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                                )
                                .selectable(selected = c == color, onClick = { color = c }, role = Role.RadioButton),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val problem = validate(name, target.type, target.existing?.id)
                    if (problem != null) error = problem else onSave(name.trim(), emoji, color)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReassignDialog(
    category: CategoryEntity,
    usageCount: Int,
    targets: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("“${category.name}” is in use") },
        text = {
            Column {
                if (targets.isEmpty()) {
                    Text("It's used by $usageCount item(s) and it's the only ${category.type.name.lowercase()} category, so it can't be deleted. Add another category first.")
                } else {
                    Text("It's used by $usageCount transaction(s), recurring rules or planned payments. Move them to:")
                    LazyColumn(Modifier.heightIn(max = 300.dp).padding(top = 8.dp)) {
                        items(targets, key = { it.id }) { t ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = selected == t.id, onClick = { selected = t.id }, role = Role.RadioButton)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected == t.id, onClick = null)
                                Text("${t.emoji} ${t.name}", Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onConfirm) }, enabled = selected != null) { Text("Move & delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
