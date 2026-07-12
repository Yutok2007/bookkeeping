@file:OptIn(ExperimentalMaterial3Api::class)

package com.pocketledger.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketledger.app.i18n.categoryLabel
import com.pocketledger.app.i18n.languages
import com.pocketledger.app.i18n.tr
import com.pocketledger.app.model.*
import com.pocketledger.app.viewmodel.LedgerUiState
import com.pocketledger.app.viewmodel.LedgerViewModel
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4C), onPrimary = Color.White, primaryContainer = Color(0xFF8CF7C8),
    secondary = Color(0xFF4C6358), tertiary = Color(0xFF3D6473), background = Color(0xFFF7FBF7),
    surface = Color(0xFFF7FBF7), surfaceVariant = Color(0xFFDCE5DE), error = Color(0xFFBA1A1A)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF70DBAD), onPrimary = Color(0xFF003825), primaryContainer = Color(0xFF005139),
    secondary = Color(0xFFB3CCBF), tertiary = Color(0xFFA5CDDD), background = Color(0xFF101512),
    surface = Color(0xFF101512), surfaceVariant = Color(0xFF404943), error = Color(0xFFFFB4AB)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LedgerRoot() }
    }
}

private enum class MainPage { HOME, CHART, REPORT, PROFILE }

@Composable
private fun LedgerRoot(viewModel: LedgerViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.data.settings
    val systemDark = isSystemInDarkTheme()
    val dark = settings.theme == ThemeMode.DARK || (settings.theme == ThemeMode.SYSTEM && systemDark)
    val baseDensity = LocalDensity.current
    val direction = if (settings.language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * settings.textSize.scale),
        androidx.compose.ui.platform.LocalLayoutDirection provides direction
    ) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
            var page by rememberSaveable { mutableStateOf(MainPage.HOME) }
            var adding by rememberSaveable { mutableStateOf(false) }
            var editing by remember { mutableStateOf<LedgerEntry?>(null) }
            Surface(Modifier.fillMaxSize()) {
                if (adding || editing != null) {
                    AddEntryScreen(viewModel, editing, onClose = { adding = false; editing = null })
                } else {
                    Scaffold(
                        bottomBar = {
                            MainNavigation(page, settings.language, onPage = { page = it }, onAdd = { adding = true })
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding).fillMaxSize()) {
                            when (page) {
                                MainPage.HOME -> HomeScreen(viewModel, state, onEdit = { editing = it })
                                MainPage.CHART -> ChartScreen(viewModel, state)
                                MainPage.REPORT -> ReportScreen(viewModel, state)
                                MainPage.PROFILE -> ProfileScreen(viewModel, state)
                            }
                        }
                    }
                }
            }
            state.userMessage?.let { message ->
                AlertDialog(
                    onDismissRequest = viewModel::clearMessage,
                    title = { Text("Pocket Ledger") },
                    text = { Text(message) },
                    confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text("OK") } },
                )
            }
        }
    }
}

@Composable
private fun MainNavigation(page: MainPage, language: String, onPage: (MainPage) -> Unit, onAdd: () -> Unit) {
    NavigationBar(tonalElevation = 4.dp) {
        NavItem(page == MainPage.HOME, "⌂", tr(language, "home")) { onPage(MainPage.HOME) }
        NavItem(page == MainPage.CHART, "▥", tr(language, "chart")) { onPage(MainPage.CHART) }
        NavigationBarItem(
            selected = false, onClick = onAdd,
            icon = {
                Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        )
        NavItem(page == MainPage.REPORT, "≡", tr(language, "report")) { onPage(MainPage.REPORT) }
        NavItem(page == MainPage.PROFILE, "●", tr(language, "profile")) { onPage(MainPage.PROFILE) }
    }
}

@Composable
private fun RowScope.NavItem(selected: Boolean, symbol: String, label: String, onClick: () -> Unit) {
    NavigationBarItem(selected = selected, onClick = onClick, icon = { Text(symbol, style = MaterialTheme.typography.titleLarge) }, label = { Text(label, maxLines = 1) })
}

@Composable
private fun PageHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun HomeScreen(viewModel: LedgerViewModel, state: LedgerUiState, onEdit: (LedgerEntry) -> Unit) {
    val language = state.data.settings.language
    var selectedMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val selectedMonth = YearMonth.parse(selectedMonthText)
    val (income, expense, balance) = viewModel.monthTotals(selectedMonth.atDay(1))
    val entries = viewModel.filteredEntries().filter { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault())) == selectedMonth }
    var selected by remember { mutableStateOf<LedgerEntry?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selectedMonthText = selectedMonth.minusMonths(1).toString() }) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr(language, "app"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(formatMonthTitle(language, selectedMonth), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { selectedMonthText = selectedMonth.plusMonths(1).toString() }) { Text("›", style = MaterialTheme.typography.headlineMedium) }
        }
        SummaryRow(language, state.data.settings.currency, income, expense, balance)
        OutlinedTextField(
            value = state.search, onValueChange = viewModel::setSearch, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(tr(language, "search")) }, singleLine = true,
            leadingIcon = { Text("⌕") }, trailingIcon = if (state.search.isNotBlank()) ({ Text("×", Modifier.clickable { viewModel.setSearch("") }) }) else null
        )
        FilterStrip(viewModel, state)
        if (entries.isEmpty()) {
            EmptyState(tr(language, "no_entries"), tr(language, "add_first"), Modifier.weight(1f))
        } else {
            val grouped = entries.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                grouped.toSortedMap(compareByDescending { it }).forEach { (date, dateEntries) ->
                    item { DateHeader(date) }
                    items(dateEntries, key = { it.id }) { entry ->
                        EntryRow(entry, language, onClick = { selected = entry }, category = state.data.categories.firstOrNull { it.id == entry.categoryId })
                    }
                }
            }
        }
    }
    selected?.let { entry ->
        EntryDetailDialog(entry, language, onDismiss = { selected = null }, onEdit = { selected = null; onEdit(entry) }, onDelete = { viewModel.deleteEntry(entry); selected = null })
    }
}

@Composable
private fun SummaryRow(language: String, currency: String, income: Double, expense: Double, balance: Double) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard(tr(language, "income"), money(income, currency), Color(0xFF16865B), Modifier.weight(1f))
        SummaryCard(tr(language, "expense"), money(expense, currency), MaterialTheme.colorScheme.error, Modifier.weight(1f))
        SummaryCard(tr(language, "balance"), money(balance, currency), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun FilterStrip(viewModel: LedgerViewModel, state: LedgerUiState) {
    val language = state.data.settings.language
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = state.categoryFilter == null && state.accountFilter == null && state.dateFilter == null, onClick = viewModel::clearFilters, label = { Text(tr(language, "all")) })
        FilterChip(selected = state.dateFilter == LocalDate.now(), onClick = { viewModel.setDateFilter(if (state.dateFilter == LocalDate.now()) null else LocalDate.now()) }, label = { Text("Today") })
        FilterChip(selected = state.dateFilter == LocalDate.now().minusDays(1), onClick = { viewModel.setDateFilter(if (state.dateFilter == LocalDate.now().minusDays(1)) null else LocalDate.now().minusDays(1)) }, label = { Text("Yesterday") })
        state.data.categories.filterNot { it.archived }.forEach { category ->
            FilterChip(selected = state.categoryFilter == category.name, onClick = { viewModel.setCategoryFilter(if (state.categoryFilter == category.name) null else category.name) }, label = { Text(categoryLabel(language, category.name)) })
        }
        viewModel.activeAccounts().forEach { account ->
            FilterChip(selected = state.accountFilter == account.id, onClick = { viewModel.setAccountFilter(if (state.accountFilter == account.id) null else account.id) }, label = { Text(account.name) })
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 18.dp, vertical = 12.dp))
}

@Composable
private fun EntryRow(entry: LedgerEntry, language: String, onClick: (() -> Unit)? = null, category: LedgerCategory? = null) {
    val sign = if (entry.type == EntryType.INCOME) "+" else "−"
    val color = if (entry.type == EntryType.INCOME) Color(0xFF16865B) else MaterialTheme.colorScheme.error
    val time = Instant.ofEpochMilli(entry.occurredAt).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    val rowModifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Row(rowModifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background((category?.let { Color(it.color.toULong()) } ?: categoryColor(entry.category)).copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
            Text(category?.icon ?: categorySymbol(entry.category))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(entry.purpose, fontWeight = FontWeight.SemiBold)
            Text(listOf(categoryLabel(language, entry.category), entry.accountName, time).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("$sign${money(entry.amount, entry.currency)}", fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun EntryDetailDialog(entry: LedgerEntry, language: String, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.purpose) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine(tr(language, "type"), tr(language, if (entry.type == EntryType.INCOME) "income" else "expense"))
                DetailLine(tr(language, "amount"), money(entry.amount, entry.currency))
                DetailLine(tr(language, "category"), categoryLabel(language, entry.category))
                DetailLine(tr(language, "account"), entry.accountName.ifBlank { "—" })
                DetailLine(tr(language, "date_time"), formatDateTime(entry.occurredAt))
                if (entry.note.isNotBlank()) DetailLine(tr(language, "note"), entry.note)
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text(tr(language, "edit")) } },
        dismissButton = { Row { TextButton(onClick = onDelete) { Text(tr(language, "delete"), color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text(tr(language, "close")) } } }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(104.dp)); Text(value, Modifier.weight(1f)) }
}

@Composable
private fun AddEntryScreen(viewModel: LedgerViewModel, editing: LedgerEntry?, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val language = state.data.settings.language
    var mode by rememberSaveable(editing?.id) { mutableStateOf(if (editing == null) "sentence" else "manual") }
    var sentence by rememberSaveable(editing?.id) { mutableStateOf(editing?.rawText.orEmpty()) }
    var reviewing by rememberSaveable(editing?.id) { mutableStateOf(editing != null) }
    var missing by remember { mutableStateOf(emptySet<String>()) }
    var draft by remember(editing?.id) {
        mutableStateOf(editing ?: LedgerEntry(currency = state.data.settings.currency))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (reviewing) tr(language, "confirm") else tr(language, "add_entry"), fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onClose) { Text("←", style = MaterialTheme.typography.titleLarge) } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!reviewing) {
                PrimaryTabRow(selectedTabIndex = if (mode == "sentence") 0 else 1) {
                    Tab(selected = mode == "sentence", onClick = { mode = "sentence" }, text = { Text(tr(language, "sentence")) })
                    Tab(selected = mode == "manual", onClick = { mode = "manual" }, text = { Text(tr(language, "manual")) })
                }
            }
            if (mode == "sentence" && !reviewing) {
                SentenceInput(
                    text = sentence, onTextChange = { sentence = it }, language = language,
                    onParse = {
                        val result = viewModel.parse(sentence)
                        draft = result.entry
                        missing = result.missing
                        reviewing = true
                    }
                )
            } else {
                EntryForm(
                    entry = draft,
                    language = language,
                    accounts = viewModel.activeAccounts(),
                    categories = viewModel.activeCategories(draft.type),
                    missing = missing,
                    onChange = { draft = it },
                    onSave = {
                        if (draft.amount > 0 && draft.purpose.isNotBlank()) {
                            viewModel.saveEntry(draft)
                            onClose()
                        }
                    },
                    onCancel = if (reviewing && editing == null && mode == "sentence") ({ reviewing = false }) else onClose
                )
            }
        }
    }
}

@Composable
private fun SentenceInput(text: String, onTextChange: (String) -> Unit, language: String, onParse: () -> Unit) {
    val speakLabel = tr(language, "speak")
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showImeHint by remember { mutableStateOf(false) }
    var imeRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(imeRequest) {
        if (imeRequest > 0) {
            focusRequester.requestFocus()
            withFrameNanos { }
            keyboardController?.show()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f))) {
            Text(tr(language, "sentence_hint"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).focusRequester(focusRequester),
            label = { Text(tr(language, "sentence")) }, placeholder = { Text(tr(language, "sentence_hint")) }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    imeRequest += 1
                    showImeHint = true
                }, modifier = Modifier.weight(1f)
            ) { Text("⌨  $speakLabel") }
            Button(onClick = onParse, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) { Text(tr(language, "parse")) }
        }
        if (showImeHint) {
            Text(tr(language, "ime_voice_hint"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Text("中文 · English · 日本語 · 한국어 · Español · Français · Deutsch · Português · العربية", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EntryForm(
    entry: LedgerEntry,
    language: String,
    accounts: List<LedgerAccount>,
    categories: List<LedgerCategory>,
    missing: Set<String>,
    onChange: (LedgerEntry) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var saveAttempted by remember(entry.id) { mutableStateOf(false) }
    var amountText by remember(entry.id) { mutableStateOf(if (entry.amount > 0) cleanNumber(entry.amount) else "") }
    var dateText by remember(entry.id) { mutableStateOf(Instant.ofEpochMilli(entry.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()) }
    var timeText by remember(entry.id) { mutableStateOf(Instant.ofEpochMilli(entry.occurredAt).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    val compatibleAccounts = accounts.filter { it.currency == entry.currency }
    val valid = entry.amount > 0 && entry.purpose.isNotBlank()

    fun updateDateTime() {
        runCatching {
            val date = LocalDate.parse(dateText)
            val time = LocalTime.parse(timeText, DateTimeFormatter.ofPattern("H:mm"))
            onChange(entry.copy(occurredAt = ZonedDateTime.of(date, time, ZoneId.systemDefault()).toInstant().toEpochMilli()))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = entry.type == EntryType.EXPENSE, onClick = { onChange(entry.copy(type = EntryType.EXPENSE, categoryId = null, category = "")) }, label = { Text(tr(language, "expense")) })
            FilterChip(selected = entry.type == EntryType.INCOME, onClick = { onChange(entry.copy(type = EntryType.INCOME, categoryId = null, category = "")) }, label = { Text(tr(language, "income")) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = amountText, onValueChange = { value -> amountText = value; onChange(entry.copy(amount = value.replace(",", ".").toDoubleOrNull() ?: 0.0)) },
                modifier = Modifier.weight(1.3f), label = { Text(tr(language, "amount")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = saveAttempted && entry.amount <= 0
            )
            DropdownField(entry.currency, currencies, tr(language, "currency"), Modifier.weight(1f)) { currency ->
                val selected = accounts.firstOrNull { it.id == entry.accountId }
                onChange(entry.copy(currency = currency, accountId = if (selected?.currency == currency) entry.accountId else null, accountName = if (selected?.currency == currency) entry.accountName else ""))
            }
        }
        OutlinedTextField(
            value = entry.purpose, onValueChange = { onChange(entry.copy(purpose = it)) }, modifier = Modifier.fillMaxWidth(),
            label = { Text(tr(language, "purpose")) }, singleLine = true, isError = saveAttempted && entry.purpose.isBlank(),
            supportingText = if (saveAttempted && entry.purpose.isBlank()) ({ Text("Purpose could not be identified. Please enter it manually.") }) else null
        )
        DropdownField(
            value = entry.category, options = listOf("") + categories.map { it.name }, label = tr(language, "category"), modifier = Modifier.fillMaxWidth(),
            display = { categoryLabel(language, it).ifBlank { "—" } }, onSelected = { name ->
                val category = categories.firstOrNull { it.name == name }
                onChange(entry.copy(categoryId = category?.id, category = category?.name.orEmpty()))
            }
        )
        DropdownField(
            value = entry.accountName, options = listOf("") + compatibleAccounts.map { it.name }, label = tr(language, "account"), modifier = Modifier.fillMaxWidth(),
            display = { it.ifBlank { "—" } }, onSelected = { name ->
                val account = compatibleAccounts.firstOrNull { it.name == name }
                onChange(entry.copy(accountId = account?.id, accountName = account?.name.orEmpty()))
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = dateText, onValueChange = { dateText = it; updateDateTime() }, label = { Text("YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1.4f))
            OutlinedTextField(value = timeText, onValueChange = { timeText = it; updateDateTime() }, label = { Text("HH:MM") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = entry.note, onValueChange = { onChange(entry.copy(note = it)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp), label = { Text(tr(language, "note")) })
        if (saveAttempted && !valid) Text(tr(language, "required"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(tr(language, "cancel")) }
            Button(onClick = { saveAttempted = true; if (valid) onSave() }, modifier = Modifier.weight(1f)) { Text(tr(language, "save")) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DropdownField(
    value: String,
    options: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    display: (String) -> String = { it },
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(56.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(display(value).ifBlank { "—" }, maxLines = 1)
            }
            Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.distinct().forEach { option ->
                DropdownMenuItem(text = { Text(display(option)) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

private data class TrendBucket(val label: String, val income: Double, val expense: Double)

@Composable
private fun ChartScreen(viewModel: LedgerViewModel, state: LedgerUiState) {
    val language = state.data.settings.language
    val currency = state.data.settings.currency
    var range by rememberSaveable { mutableStateOf("month") }
    val entries = viewModel.entriesInRange(range)
    val previousDate = when (range) { "week" -> LocalDate.now().minusWeeks(1); "year" -> LocalDate.now().minusYears(1); else -> LocalDate.now().minusMonths(1) }
    val previousEntries = viewModel.entriesInRange(range, previousDate)
    val income = entries.filter { it.type == EntryType.INCOME }.sumOf { it.amount }
    val expense = entries.filter { it.type == EntryType.EXPENSE }.sumOf { it.amount }
    val buckets = remember(entries, range) { trendBuckets(entries, range) }
    val categoryTotals = LedgerAnalytics.categoryTotals(entries, EntryType.EXPENSE, currency).toList().sortedByDescending { it.second }
    val previousCategoryTotals = LedgerAnalytics.categoryTotals(previousEntries, EntryType.EXPENSE, currency)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader(tr(language, "chart"), tr(language, "trend")) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                listOf("week", "month", "year").forEachIndexed { index, item ->
                    SegmentedButton(selected = range == item, onClick = { range = item }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(tr(language, item)) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(tr(language, "income"), money(income, currency), Color(0xFF16865B), Modifier.weight(1f))
                SummaryCard(tr(language, "expense"), money(expense, currency), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                SummaryCard(tr(language, "net"), money(income - expense, currency), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }
        if (entries.isEmpty()) {
            item { EmptyState(tr(language, "no_data"), tr(language, "add_first"), Modifier.height(260.dp)) }
        } else {
            item {
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr(language, "trend"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))
                        TrendChart(buckets)
                    }
                }
            }
            item {
                Text(tr(language, "categories"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
            }
            items(categoryTotals, key = { it.first }) { (category, total) ->
                val previous = previousCategoryTotals[category]
                val change = if (previous != null && previous > 0) (total - previous) / previous * 100 else null
                CategoryBar(categoryLabel(language, category), total, expense, currency, state.data.categories.firstOrNull { it.name == category }?.let { Color(it.color.toULong()) } ?: categoryColor(category), change)
            }
            categoryTotals.firstOrNull()?.let { top ->
                item {
                    AssistChip(onClick = {}, label = { Text("${tr(language, "top_category")}: ${categoryLabel(language, top.first)} · ${money(top.second, currency)}") }, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TrendChart(buckets: List<TrendBucket>) {
    val maxValue = max(1.0, buckets.maxOfOrNull { max(it.income, it.expense) } ?: 1.0)
    Column {
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            val groupWidth = size.width / max(1, buckets.size)
            val barWidth = groupWidth * .25f
            val chartHeight = size.height - 8.dp.toPx()
            buckets.forEachIndexed { index, bucket ->
                val x = index * groupWidth + groupWidth * .2f
                val incomeHeight = (bucket.income / maxValue * chartHeight).toFloat()
                val expenseHeight = (bucket.expense / maxValue * chartHeight).toFloat()
                drawRect(Color(0xFF35A875), Offset(x, chartHeight - incomeHeight), Size(barWidth, incomeHeight))
                drawRect(Color(0xFFE56C65), Offset(x + barWidth + 3.dp.toPx(), chartHeight - expenseHeight), Size(barWidth, expenseHeight))
            }
            drawLine(Color.Gray.copy(alpha = .4f), Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            buckets.forEach { bucket -> Text(bucket.label, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1) }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(Color(0xFF35A875), "Income")
            LegendDot(Color(0xFFE56C65), "Expense")
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).background(color, CircleShape)); Spacer(Modifier.width(5.dp)); Text(text, style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun CategoryBar(label: String, amount: Double, total: Double, currency: String, color: Color, changePercent: Double? = null) {
    val fraction = if (total <= 0) 0f else (amount / total).toFloat().coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row {
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("${money(amount, currency)} · ${(fraction * 100).toInt()}%" + (changePercent?.let { " · ${if (it >= 0) "+" else ""}${it.toInt()}%" } ?: ""))
        }
        Spacer(Modifier.height(7.dp))
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp), color = color, trackColor = color.copy(alpha = .14f))
    }
}

private fun trendBuckets(entries: List<LedgerEntry>, range: String): List<TrendBucket> {
    val zone = ZoneId.systemDefault()
    val now = LocalDate.now()
    return when (range) {
        "week" -> {
            val start = now.minusDays((now.dayOfWeek.value - 1).toLong())
            (0..6).map { offset ->
                val date = start.plusDays(offset.toLong())
                val values = entries.filter { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() == date }
                TrendBucket(date.dayOfWeek.name.take(1), values.filter { it.type == EntryType.INCOME }.sumOf { it.amount }, values.filter { it.type == EntryType.EXPENSE }.sumOf { it.amount })
            }
        }
        "year" -> (1..12).map { month ->
            val values = entries.filter { Instant.ofEpochMilli(it.occurredAt).atZone(zone).monthValue == month }
            TrendBucket(month.toString(), values.filter { it.type == EntryType.INCOME }.sumOf { it.amount }, values.filter { it.type == EntryType.EXPENSE }.sumOf { it.amount })
        }
        else -> (0..4).map { week ->
            val values = entries.filter { (Instant.ofEpochMilli(it.occurredAt).atZone(zone).dayOfMonth - 1) / 7 == week }
            TrendBucket("${week * 7 + 1}", values.filter { it.type == EntryType.INCOME }.sumOf { it.amount }, values.filter { it.type == EntryType.EXPENSE }.sumOf { it.amount })
        }
    }
}

@Composable
private fun ReportScreen(viewModel: LedgerViewModel, state: LedgerUiState) {
    val language = state.data.settings.language
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PageHeader(tr(language, "report"))
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr(language, "analytics")) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr(language, "accounts")) })
        }
        if (tab == 0) AnalyticsScreen(viewModel, state, Modifier.weight(1f)) else AccountsScreen(viewModel, state, Modifier.weight(1f))
    }
}

@Composable
private fun AnalyticsScreen(viewModel: LedgerViewModel, state: LedgerUiState, modifier: Modifier = Modifier) {
    val language = state.data.settings.language
    val currency = state.data.settings.currency
    val now = LocalDate.now()
    val weekStart = now.minusDays((now.dayOfWeek.value - 1).toLong())
    val week = viewModel.spendingSince(weekStart, weekStart.plusWeeks(1))
    val month = viewModel.spendingSince(now.withDayOfMonth(1), now.withDayOfMonth(1).plusMonths(1))
    val year = viewModel.spendingSince(now.withDayOfYear(1), now.withDayOfYear(1).plusYears(1))
    val totals = viewModel.monthTotals()
    val budget = if (state.data.budget.currency == currency) state.data.budget.total else 0.0
    val progress = if (budget <= 0) 0f else (month / budget).toFloat()
    val monthCategorySpending = LedgerAnalytics.categoryTotals(viewModel.entriesInRange("month"), EntryType.EXPENSE, currency).toList().sortedByDescending { it.second }
    var budgetDialog by remember { mutableStateOf(false) }
    var categoryBudgetDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(tr(language, "expense"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(tr(language, "week_spend"), money(week, currency), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                SummaryCard(tr(language, "month_spend"), money(month, currency), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                SummaryCard(tr(language, "year_spend"), money(year, currency), MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }
        }
        item {
            Text(tr(language, "this_month"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            SummaryRow(language, currency, totals.first, totals.second, totals.third)
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(language, "budget"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(onClick = { budgetDialog = true }) { Text(tr(language, "set_budget")) }
                            TextButton(onClick = { categoryBudgetDialog = true }) { Text("Category budgets") }
                        }
                    }
                    DetailLine(tr(language, "budget"), money(budget, currency))
                    DetailLine(tr(language, "used"), money(month, currency))
                    DetailLine(tr(language, "remaining"), money((budget - month).coerceAtLeast(0.0), currency))
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp), color = if (progress > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = if (progress > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    if (progress > 1) Text("⚠ ${tr(language, "over_budget")}: ${money(month - budget, currency)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
        (if (state.data.budget.currency == currency) state.data.budget.categoryAmounts else emptyMap()).forEach { (category, limit) ->
            val spent = viewModel.entriesInRange("month").filter { it.type == EntryType.EXPENSE && it.category == category }.sumOf { it.amount }
            item { CategoryBudgetRow(categoryLabel(language, category), spent, limit, currency) }
        }
        if (monthCategorySpending.isNotEmpty()) {
            item { Text(tr(language, "categories"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            items(monthCategorySpending, key = { it.first }) { (category, spent) ->
                CategoryBar(categoryLabel(language, category), spent, month, currency, state.data.categories.firstOrNull { it.name == category }?.let { Color(it.color.toULong()) } ?: categoryColor(category))
            }
        }
    }
    if (budgetDialog) BudgetDialog(budget, language, currency, onDismiss = { budgetDialog = false }) { viewModel.setBudget(it); budgetDialog = false }
    if (categoryBudgetDialog) CategoryBudgetDialog(
        categories = viewModel.activeCategories(EntryType.EXPENSE),
        currency = currency,
        onDismiss = { categoryBudgetDialog = false },
        onSave = { name, amount -> viewModel.setCategoryBudget(name, amount); categoryBudgetDialog = false },
    )
}

@Composable
private fun CategoryBudgetRow(label: String, spent: Double, limit: Double, currency: String) {
    val fraction = if (limit <= 0) 0f else (spent / limit).toFloat()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row { Text(label, Modifier.weight(1f)); Text("${money(spent, currency)} / ${money(limit, currency)}", color = if (fraction > 1) MaterialTheme.colorScheme.error else LocalContentColor.current) }
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = if (fraction > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun BudgetDialog(current: Double, language: String, currency: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember { mutableStateOf(cleanNumber(current)) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr(language, "set_budget")) }, text = {
        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("${tr(language, "amount")} ($currency)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
    }, confirmButton = { Button(onClick = { onSave(value.toDoubleOrNull() ?: current) }) { Text(tr(language, "save")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr(language, "cancel")) } })
}

@Composable
private fun CategoryBudgetDialog(categories: List<LedgerCategory>, currency: String, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var categoryName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set category budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField(categoryName, listOf("") + categories.map { it.name }, "Category", Modifier.fillMaxWidth()) { categoryName = it }
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount ($currency)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(categoryName, amount.toDoubleOrNull() ?: 0.0) }, enabled = categoryName.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AccountsScreen(viewModel: LedgerViewModel, state: LedgerUiState, modifier: Modifier = Modifier) {
    val language = state.data.settings.language
    val currency = state.data.settings.currency
    var editing by remember { mutableStateOf<LedgerAccount?>(null) }
    var creating by remember { mutableStateOf(false) }
    var historyAccount by remember { mutableStateOf<LedgerAccount?>(null) }
    var deleting by remember { mutableStateOf<LedgerAccount?>(null) }
    val assetTotals = state.data.accounts.filter { it.includeInAssets && !it.archived }.groupBy { it.currency }.mapValues { (_, accounts) -> accounts.sumOf { it.balance } }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(language, "total_assets"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (assetTotals.isEmpty()) Text(money(0.0, currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        assetTotals.toSortedMap().forEach { (assetCurrency, total) -> Text(money(total, assetCurrency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        if (assetTotals.size > 1) Text("Currencies are shown separately because no exchange-rate service is configured.", style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(onClick = { creating = true }) { Text("+ ${tr(language, "add_account")}") }
                }
            }
        }
        items(state.data.accounts.sortedWith(compareBy<LedgerAccount> { it.archived }.thenBy { it.name }), key = { it.id }) { account ->
            val recent = state.data.entries.filter { it.accountId == account.id }.maxByOrNull { it.occurredAt }
            ElevatedCard(Modifier.fillMaxWidth().clickable { historyAccount = account }, colors = if (account.archived) CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) else CardDefaults.elevatedCardColors()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) { Text(accountSymbol(account.type)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(account.name, fontWeight = FontWeight.Bold)
                            if (account.archived) Text("  · ${tr(language, "archive")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(account.type + (recent?.let { " · ${it.purpose}" } ?: "") + if (account.includeInAssets) " · Assets" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(account.balance, account.currency), fontWeight = FontWeight.Bold, color = if (account.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(account.currency, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    if (creating) AccountDialog(null, language, currency, onDismiss = { creating = false }, onSave = { viewModel.addAccount(it); creating = false })
    editing?.let { account ->
        AccountDialog(account, language, currency, onDismiss = { editing = null }, onSave = { viewModel.addAccount(it); editing = null },
            onArchive = { viewModel.addAccount(account.copy(archived = !account.archived)); editing = null },
            onDelete = {
                if (!viewModel.deleteUnusedAccount(account)) deleting = account
                editing = null
            })
    }
    historyAccount?.let { account ->
        AccountHistoryDialog(
            account = account,
            entries = state.data.entries.filter { it.accountId == account.id },
            language = language,
            onDismiss = { historyAccount = null },
            onEdit = { historyAccount = null; editing = account },
        )
    }
    deleting?.let { account ->
        AccountDeleteDialog(
            account = account,
            targets = state.data.accounts.filter { it.id != account.id && !it.archived && it.currency == account.currency },
            usageCount = viewModel.accountUsageCount(account.id),
            onDismiss = { deleting = null },
            onArchive = { viewModel.addAccount(account.copy(archived = true)); deleting = null },
            onKeepHistory = { viewModel.deleteAccountKeepingHistory(account); deleting = null },
            onMove = { target -> viewModel.moveAccountTransactions(account, target); deleting = null },
        )
    }
}

@Composable
private fun AccountDialog(
    account: LedgerAccount?,
    language: String,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (LedgerAccount) -> Unit,
    onArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var draft by remember(account?.id) { mutableStateOf(account ?: LedgerAccount(currency = defaultCurrency)) }
    var initialBalance by remember(account?.id) { mutableStateOf(if (account == null) "" else cleanNumber(account.initialBalance)) }
    var currentBalance by remember(account?.id) { mutableStateOf(if (account == null) "" else cleanNumber(account.balance)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth().padding(20.dp).heightIn(max = 700.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (account == null) tr(language, "add_account") else tr(language, "edit"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = draft.name, onValueChange = { draft = draft.copy(name = it) }, label = { Text(tr(language, "account_name")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                DropdownField(draft.type, accountTypes, tr(language, "account_type"), Modifier.fillMaxWidth()) { draft = draft.copy(type = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = initialBalance, onValueChange = {
                        initialBalance = it
                        val value = it.replace(",", ".").toDoubleOrNull() ?: 0.0
                        draft = if (account == null) draft.copy(initialBalance = value, balance = value) else draft.copy(initialBalance = value)
                        if (account == null) currentBalance = it
                    }, label = { Text("Initial balance") }, modifier = Modifier.weight(1.4f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    DropdownField(draft.currency, currencies, tr(language, "currency"), Modifier.weight(1f)) { draft = draft.copy(currency = it) }
                }
                if (account != null) {
                    OutlinedTextField(value = currentBalance, onValueChange = {
                        currentBalance = it
                        draft = draft.copy(balance = it.replace(",", ".").toDoubleOrNull() ?: 0.0)
                    }, label = { Text("Current balance") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(language, "include_assets"), Modifier.weight(1f))
                    Switch(checked = draft.includeInAssets, onCheckedChange = { draft = draft.copy(includeInAssets = it) })
                }
                if (account != null) {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        onDelete?.let { TextButton(onClick = it) { Text(tr(language, "delete"), color = MaterialTheme.colorScheme.error) } }
                        onArchive?.let { TextButton(onClick = it) { Text(if (account.archived) "Unarchive" else tr(language, "archive")) } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, Modifier.weight(1f)) { Text(tr(language, "cancel")) }
                    Button(onClick = { onSave(draft) }, enabled = draft.name.isNotBlank(), modifier = Modifier.weight(1f)) { Text(tr(language, "save")) }
                }
            }
        }
    }
}

@Composable
private fun AccountHistoryDialog(
    account: LedgerAccount,
    entries: List<LedgerEntry>,
    language: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(18.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(account.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${account.type} · ${money(account.balance, account.currency)}${if (account.archived) " · Archived" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onEdit) { Text(tr(language, "edit")) }
                    TextButton(onClick = onDismiss) { Text("×") }
                }
                HorizontalDivider()
                if (entries.isEmpty()) {
                    EmptyState("No transactions", "Transactions using this account will appear here.", Modifier.weight(1f))
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp)) {
                        items(entries.sortedByDescending { it.occurredAt }, key = { it.id }) { EntryRow(it, language, null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDeleteDialog(
    account: LedgerAccount,
    targets: List<LedgerAccount>,
    usageCount: Int,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
    onKeepHistory: () -> Unit,
    onMove: (LedgerAccount) -> Unit,
) {
    var targetName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Account is in use") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${account.name} is used by $usageCount transaction(s). Existing transactions will never be deleted.")
                OutlinedButton(onClick = onArchive, Modifier.fillMaxWidth()) { Text("Archive account") }
                if (targets.isNotEmpty()) {
                    DropdownField(targetName, listOf("") + targets.map { it.name }, "Move transactions to", Modifier.fillMaxWidth()) { targetName = it }
                    Button(onClick = { targets.firstOrNull { it.name == targetName }?.let(onMove) }, enabled = targetName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Move transactions and delete") }
                }
                TextButton(onClick = onKeepHistory, Modifier.fillMaxWidth()) { Text("Keep transactions as Deleted Account", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProfileScreen(viewModel: LedgerViewModel, state: LedgerUiState) {
    val language = state.data.settings.language
    var loginDialog by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { PageHeader(tr(language, "profile"), tr(language, "provider_note")) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .65f))) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr(language, "external_sign_in"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tr(language, "provider_note"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { loginDialog = true }, modifier = Modifier.fillMaxWidth()) { Text(tr(language, "external_sign_in")) }
                }
            }
        }
        item { SettingsSection(viewModel, state) }
    }
    if (loginDialog) LoginDialog(language, onDismiss = { loginDialog = false })
}

@Composable
private fun SettingsSection(viewModel: LedgerViewModel, state: LedgerUiState) {
    val settings = state.data.settings
    val language = settings.language
    val context = LocalContext.current
    var manageCategories by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(tr(language, "settings"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(tr(language, "appearance"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                val key = when (mode) { ThemeMode.SYSTEM -> "system"; ThemeMode.LIGHT -> "light"; ThemeMode.DARK -> "dark" }
                SegmentedButton(selected = settings.theme == mode, onClick = { viewModel.updateSettings { it.copy(theme = mode) } }, shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)) { Text(tr(language, key), maxLines = 1) }
            }
        }
        Text(tr(language, "text_size"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextSize.entries.forEach { size ->
                val key = when (size) { TextSize.SMALL -> "small"; TextSize.STANDARD -> "standard"; TextSize.LARGE -> "large"; TextSize.EXTRA_LARGE -> "extra_large" }
                FilterChip(selected = settings.textSize == size, onClick = { viewModel.updateSettings { it.copy(textSize = size) } }, label = { Text(tr(language, key)) })
            }
        }
        HorizontalDivider()
        DropdownField(settings.currency, currencies, tr(language, "currency"), Modifier.fillMaxWidth()) { currency -> viewModel.updateSettings { it.copy(currency = currency) } }
        DropdownField(
            value = settings.language, options = languages.map { it.code }, label = tr(language, "language"), modifier = Modifier.fillMaxWidth(),
            display = { code -> languages.firstOrNull { it.code == code }?.label ?: code },
            onSelected = { code -> viewModel.updateSettings { it.copy(language = code) } }
        )
        HorizontalDivider()
        FilledTonalButton(onClick = { manageCategories = true }, modifier = Modifier.fillMaxWidth()) { Text("Manage Categories") }
        Text("Manage Accounts: Report → Accounts", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Pocket Ledger export")
                putExtra(Intent.EXTRA_TEXT, exportLedgerCsv(state.data.entries))
            }
            runCatching { context.startActivity(Intent.createChooser(share, "Export Pocket Ledger data")) }
        }, modifier = Modifier.fillMaxWidth()) { Text("Data Export (CSV)") }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Privacy & data", fontWeight = FontWeight.Bold)
                Text("Your ledger is stored only on this device. Android backup is enabled for app preferences.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Pocket Ledger 1.0.0", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (manageCategories) CategoryManagerDialog(viewModel, state, onDismiss = { manageCategories = false })
}

@Composable
private fun CategoryManagerDialog(viewModel: LedgerViewModel, state: LedgerUiState, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<LedgerCategory?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LedgerCategory?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(16.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Manage Categories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { creating = true }) { Text("+ Add") }
                    TextButton(onClick = onDismiss) { Text("×") }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    listOf(EntryType.EXPENSE to "Expense Categories", EntryType.INCOME to "Income Categories").forEach { (type, title) ->
                        item { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp)) }
                        items(state.data.categories.filter { it.type == type }.sortedWith(compareBy<LedgerCategory> { it.archived }.thenBy { it.name }), key = { it.id }) { category ->
                            ListItem(
                                headlineContent = { Text(category.name, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(if (category.archived) "Archived" else "Available for new transactions") },
                                leadingContent = { Box(Modifier.size(38.dp).background(Color(category.color.toULong()).copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) { Text(category.icon) } },
                                trailingContent = { TextButton(onClick = { editing = category }) { Text("Edit") } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
    if (creating) CategoryEditDialog(null, onDismiss = { creating = false }, onSave = { viewModel.addCategory(it); creating = false })
    editing?.let { category ->
        CategoryEditDialog(
            category,
            onDismiss = { editing = null },
            onSave = { viewModel.addCategory(it); editing = null },
            onArchive = { viewModel.archiveCategory(category); editing = null },
            onDelete = {
                if (!viewModel.deleteUnusedCategory(category)) deleting = category
                editing = null
            },
        )
    }
    deleting?.let { category ->
        CategoryDeleteDialog(
            category = category,
            targets = state.data.categories.filter { it.id != category.id && it.type == category.type && !it.archived },
            usageCount = viewModel.categoryUsageCount(category.id),
            onDismiss = { deleting = null },
            onArchive = { viewModel.archiveCategory(category); deleting = null },
            onKeepHistory = { viewModel.deleteCategoryKeepingHistory(category); deleting = null },
            onMove = { target -> viewModel.moveCategoryTransactions(category, target); deleting = null },
        )
    }
}

@Composable
private fun CategoryEditDialog(
    category: LedgerCategory?,
    onDismiss: () -> Unit,
    onSave: (LedgerCategory) -> Unit,
    onArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var draft by remember(category?.id) { mutableStateOf(category ?: LedgerCategory()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Add Category" else "Edit Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = draft.name, onValueChange = { draft = draft.copy(name = it) }, label = { Text("Category name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = draft.type == EntryType.EXPENSE, onClick = { draft = draft.copy(type = EntryType.EXPENSE) }, label = { Text("Expense") })
                    FilterChip(selected = draft.type == EntryType.INCOME, onClick = { draft = draft.copy(type = EntryType.INCOME) }, label = { Text("Income") })
                }
                OutlinedTextField(value = draft.icon, onValueChange = { draft = draft.copy(icon = it.take(3)) }, label = { Text("Icon") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(0xFFE99542, 0xFFE26A78, 0xFF6B7FD7, 0xFF3B9BA5, 0xFFAC6AC7, 0xFF16865B).forEach { colorValue ->
                        Surface(
                            modifier = Modifier.size(if (draft.color == colorValue) 38.dp else 32.dp).clickable { draft = draft.copy(color = colorValue) },
                            shape = CircleShape,
                            color = Color(colorValue.toULong()),
                            tonalElevation = if (draft.color == colorValue) 6.dp else 0.dp,
                        ) {}
                    }
                }
                if (category != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        onDelete?.let { TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                        onArchive?.let { TextButton(onClick = it) { Text(if (category.archived) "Unarchive" else "Archive") } }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(draft) }, enabled = draft.name.isNotBlank()) { Text(if (category == null) "Add" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CategoryDeleteDialog(
    category: LedgerCategory,
    targets: List<LedgerCategory>,
    usageCount: Int,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
    onKeepHistory: () -> Unit,
    onMove: (LedgerCategory) -> Unit,
) {
    var targetName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category is in use") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${category.name} is used by $usageCount transaction(s). Existing transactions will be preserved.")
                OutlinedButton(onClick = onArchive, Modifier.fillMaxWidth()) { Text("Archive category") }
                if (targets.isNotEmpty()) {
                    DropdownField(targetName, listOf("") + targets.map { it.name }, "Move transactions to", Modifier.fillMaxWidth()) { targetName = it }
                    Button(onClick = { targets.firstOrNull { it.name == targetName }?.let(onMove) }, enabled = targetName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Move transactions and delete") }
                }
                TextButton(onClick = onKeepHistory, Modifier.fillMaxWidth()) { Text("Keep historical category name and delete", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LoginDialog(language: String, onDismiss: () -> Unit) {
    var provider by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf("Login") }
    var countryCode by remember { mutableStateOf("+86") }
    var contact by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr(language, "sign_in"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Authentication is not configured in this build. No fake token or successful login will be created.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("Google" to "google", "WeChat" to "wechat", "Phone" to "phone", "Email" to "email").forEach { (value, key) ->
                    OutlinedButton(onClick = { provider = value }, modifier = Modifier.fillMaxWidth(), colors = if (provider == value) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()) {
                        Text(tr(language, key))
                    }
                }
                when (provider) {
                    "Google" -> Text("Requires Google OAuth Client ID, SHA certificate fingerprints, redirect URI, and a backend token-verification endpoint.", style = MaterialTheme.typography.bodySmall)
                    "WeChat" -> Text("Requires a WeChat Open Platform App ID, App Secret stored on a backend, universal link, redirect URI, and server-side code exchange.", style = MaterialTheme.typography.bodySmall)
                    "Phone" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(countryCode, { countryCode = it }, label = { Text("Country code") }, modifier = Modifier.weight(.7f), singleLine = true)
                            OutlinedTextField(contact, { contact = it }, label = { Text("Phone number") }, modifier = Modifier.weight(1.3f), singleLine = true)
                        }
                        OutlinedTextField(verificationCode, { verificationCode = it }, label = { Text("Verification code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Text("Requires an SMS provider and backend endpoints to send and verify one-time codes.", style = MaterialTheme.typography.bodySmall)
                    }
                    "Email" -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Register", "Login", "Forgot Password", "Reset Password").forEach { mode -> FilterChip(selected = authMode == mode, onClick = { authMode = mode }, label = { Text(mode) }) }
                        }
                        OutlinedTextField(contact, { contact = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (authMode != "Forgot Password") OutlinedTextField(password, { password = it }, label = { Text(if (authMode == "Reset Password") "New password" else "Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (authMode == "Reset Password") OutlinedTextField(verificationCode, { verificationCode = it }, label = { Text("Reset code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Text("Requires email delivery, password hashing, reset-token storage, and authenticated backend endpoints.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (provider.isNotBlank()) Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Requires external configuration") }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(tr(language, "cancel")) }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("◌", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
    }
}

private fun money(amount: Double, currency: String): String {
    val symbols = mapOf("CNY" to "¥", "HKD" to "HK$", "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "KRW" to "₩", "SGD" to "S$", "AUD" to "A$", "CAD" to "C$")
    val formatter = NumberFormat.getNumberInstance().apply { minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2; maximumFractionDigits = 2 }
    val sign = if (amount < 0) "−" else ""
    return "$sign${symbols[currency] ?: "$currency "}${formatter.format(kotlin.math.abs(amount))}"
}

private fun cleanNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.ROOT, value)

private fun formatDateTime(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

private fun formatMonthTitle(language: String, month: YearMonth = YearMonth.now()): String {
    val locale = when (language) { "zh-TW" -> Locale.TRADITIONAL_CHINESE; "zh-CN" -> Locale.SIMPLIFIED_CHINESE; else -> Locale.forLanguageTag(language) }
    return month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
}

private fun categoryColor(category: String): Color = when (category) {
    "Food" -> Color(0xFFE99542); "Health" -> Color(0xFFE26A78); "Education" -> Color(0xFF6B7FD7); "Transport" -> Color(0xFF3B9BA5)
    "Shopping" -> Color(0xFFAC6AC7); "Entertainment" -> Color(0xFFCB5F95); "Housing" -> Color(0xFF8D7456); "Travel" -> Color(0xFF4B8ACA)
    "Salary", "Scholarship", "Refund", "Investment" -> Color(0xFF16865B); else -> Color(0xFF718078)
}

private fun categorySymbol(category: String): String = when (category) {
    "Food" -> "●"; "Health" -> "+"; "Education" -> "A"; "Transport" -> "→"; "Shopping" -> "◇"; "Entertainment" -> "☆";
    "Housing" -> "⌂"; "Travel" -> "✦"; "Communication" -> "⌁"; "Salary", "Scholarship", "Refund", "Investment" -> "+"; else -> "·"
}

private fun accountSymbol(type: String): String = when (type) {
    "Cash" -> "¤"; "Bank Account" -> "▣"; "Credit Card" -> "▭"; "E-wallet" -> "◫"; "Transport Card" -> "↔"; "Investment Account" -> "↗"; else -> "○"
}

private fun exportLedgerCsv(entries: List<LedgerEntry>): String = buildString {
    appendLine("id,type,amount,currency,purpose,category,account,date_time,note,original_text")
    entries.sortedByDescending { it.occurredAt }.forEach { entry ->
        val values = listOf(entry.id, entry.type.name, cleanNumber(entry.amount), entry.currency, entry.purpose, entry.category, entry.accountName, formatDateTime(entry.occurredAt), entry.note, entry.rawText)
        appendLine(values.joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" })
    }
}
