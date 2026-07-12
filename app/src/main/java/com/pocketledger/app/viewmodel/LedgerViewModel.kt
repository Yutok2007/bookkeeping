package com.pocketledger.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pocketledger.app.data.LedgerRepository
import com.pocketledger.app.model.*
import com.pocketledger.app.parser.NaturalLanguageParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.*
import kotlinx.coroutines.launch

data class LedgerUiState(
    val data: StoredState = StoredState(),
    val search: String = "",
    val categoryFilter: String? = null,
    val accountFilter: String? = null,
    val dateFilter: LocalDate? = null,
    val userMessage: String? = null,
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LedgerRepository(application)
    private val _uiState = MutableStateFlow(LedgerUiState(repository.load()))
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    fun parse(text: String): NaturalLanguageParser.ParseResult = NaturalLanguageParser.parse(
        text, _uiState.value.data.settings.currency, activeAccounts(), activeCategories()
    )

    fun saveEntry(candidate: LedgerEntry) {
        if (candidate.amount <= 0 || candidate.purpose.isBlank()) return
        val data = _uiState.value.data
        val old = data.entries.firstOrNull { it.id == candidate.id }
        val saved = candidate.copy(updatedAt = Instant.now().toEpochMilli())
        val entries = if (old == null) data.entries + saved else data.entries.map { if (it.id == saved.id) saved else it }
        persist(data.copy(entries = entries.sortedByDescending { it.occurredAt }, accounts = recalculateBalances(data.accounts, entries)))
    }

    fun deleteEntry(entry: LedgerEntry) {
        val data = _uiState.value.data
        val entries = data.entries.filterNot { it.id == entry.id }
        persist(data.copy(entries = entries, accounts = recalculateBalances(data.accounts, entries)))
    }

    private fun recalculateBalances(accounts: List<LedgerAccount>, entries: List<LedgerEntry>): List<LedgerAccount> {
        return LedgerRules.recalculateBalances(accounts, entries)
    }

    fun addAccount(account: LedgerAccount) {
        val data = _uiState.value.data
        val existing = data.accounts.firstOrNull { it.id == account.id }
        val now = Instant.now().toEpochMilli()
        val transactionImpactMinor = data.entries.filter { it.accountId == account.id && it.currency == account.currency }.sumOf {
            val value = Money.toMinor(it.amount, it.currency)
            if (it.type == EntryType.INCOME) value else -value
        }
        val saved = if (existing == null) {
            account.copy(initialBalance = account.balance, createdAt = now, updatedAt = now)
        } else {
            val balanceChanged = Money.toMinor(account.balance, account.currency) != Money.toMinor(existing.balance, existing.currency)
            val initial = if (balanceChanged) Money.fromMinor(Money.toMinor(account.balance, account.currency) - transactionImpactMinor, account.currency) else account.initialBalance
            account.copy(initialBalance = initial, updatedAt = now)
        }
        val entries = if (existing != null && existing.name != saved.name) data.entries.map {
            if (it.accountId == saved.id) it.copy(accountName = saved.name, updatedAt = now) else it
        } else data.entries
        val accounts = LedgerRules.upsertAccount(data.accounts, saved)
        persist(data.copy(entries = entries, accounts = recalculateBalances(accounts, entries)))
    }

    fun archiveAccount(account: LedgerAccount) = addAccount(account.copy(archived = true))

    fun deleteUnusedAccount(account: LedgerAccount): Boolean {
        val data = _uiState.value.data
        if (!LedgerRules.canDeleteAccount(account.id, data.entries)) return false
        persist(data.copy(accounts = data.accounts.filterNot { it.id == account.id }))
        return true
    }

    fun accountUsageCount(accountId: String): Int = _uiState.value.data.entries.count { it.accountId == accountId }

    fun deleteAccountKeepingHistory(account: LedgerAccount) {
        val data = _uiState.value.data
        val entries = data.entries.map { if (it.accountId == account.id) it.copy(accountId = null, accountName = "Deleted Account", updatedAt = Instant.now().toEpochMilli()) else it }
        persist(data.copy(entries = entries, accounts = data.accounts.filterNot { it.id == account.id }))
    }

    fun moveAccountTransactions(source: LedgerAccount, target: LedgerAccount) {
        if (source.currency != target.currency) return
        val data = _uiState.value.data
        val entries = data.entries.map { if (it.accountId == source.id) it.copy(accountId = target.id, accountName = target.name, updatedAt = Instant.now().toEpochMilli()) else it }
        val accounts = data.accounts.filterNot { it.id == source.id }
        persist(data.copy(entries = entries, accounts = recalculateBalances(accounts, entries)))
    }

    fun addCategory(category: LedgerCategory) {
        val data = _uiState.value.data
        val existing = data.categories.firstOrNull { it.id == category.id }
        val now = Instant.now().toEpochMilli()
        val saved = category.copy(updatedAt = now)
        val entries = if (existing != null && existing.name != saved.name) data.entries.map {
            if (it.categoryId == saved.id) it.copy(category = saved.name, updatedAt = now) else it
        } else data.entries
        val categories = LedgerRules.upsertCategory(data.categories, saved)
        val budget = if (existing != null && existing.name != saved.name && existing.name in data.budget.categoryAmounts) {
            data.budget.copy(categoryAmounts = data.budget.categoryAmounts.toMutableMap().apply {
                val amount = remove(existing.name)
                if (amount != null) put(saved.name, amount)
            })
        } else data.budget
        persist(data.copy(entries = entries, categories = categories, budget = budget))
    }

    fun archiveCategory(category: LedgerCategory) = addCategory(category.copy(archived = !category.archived))

    fun deleteUnusedCategory(category: LedgerCategory): Boolean {
        val data = _uiState.value.data
        if (!LedgerRules.canDeleteCategory(category.id, data.entries)) return false
        persist(data.copy(categories = data.categories.filterNot { it.id == category.id }, budget = data.budget.copy(categoryAmounts = data.budget.categoryAmounts - category.name)))
        return true
    }

    fun categoryUsageCount(categoryId: String): Int = _uiState.value.data.entries.count { it.categoryId == categoryId }

    fun deleteCategoryKeepingHistory(category: LedgerCategory) {
        val data = _uiState.value.data
        val entries = data.entries.map { if (it.categoryId == category.id) it.copy(categoryId = null, category = category.name, updatedAt = Instant.now().toEpochMilli()) else it }
        persist(data.copy(entries = entries, categories = data.categories.filterNot { it.id == category.id }, budget = data.budget.copy(categoryAmounts = data.budget.categoryAmounts - category.name)))
    }

    fun moveCategoryTransactions(source: LedgerCategory, target: LedgerCategory) {
        if (source.type != target.type) return
        val data = _uiState.value.data
        val entries = data.entries.map { if (it.categoryId == source.id) it.copy(categoryId = target.id, category = target.name, updatedAt = Instant.now().toEpochMilli()) else it }
        val sourceBudget = data.budget.categoryAmounts[source.name]
        val budget = data.budget.copy(categoryAmounts = data.budget.categoryAmounts.toMutableMap().apply {
            remove(source.name)
            if (sourceBudget != null) put(target.name, (this[target.name] ?: 0.0) + sourceBudget)
        })
        persist(data.copy(entries = entries, categories = data.categories.filterNot { it.id == source.id }, budget = budget))
    }

    fun setBudget(total: Double) {
        val data = _uiState.value.data
        persist(data.copy(budget = data.budget.copy(total = total.coerceAtLeast(0.0), currency = data.settings.currency)))
    }

    fun setCategoryBudget(categoryName: String, amount: Double) {
        val data = _uiState.value.data
        persist(data.copy(budget = data.budget.copy(
            categoryAmounts = data.budget.categoryAmounts + (categoryName to amount.coerceAtLeast(0.0)),
            currency = data.settings.currency,
        )))
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val data = _uiState.value.data
        persist(data.copy(settings = transform(data.settings)))
    }

    fun setSearch(value: String) { _uiState.value = _uiState.value.copy(search = value) }
    fun setCategoryFilter(value: String?) { _uiState.value = _uiState.value.copy(categoryFilter = value) }
    fun setAccountFilter(value: String?) { _uiState.value = _uiState.value.copy(accountFilter = value) }
    fun setDateFilter(value: LocalDate?) { _uiState.value = _uiState.value.copy(dateFilter = value) }
    fun clearFilters() { _uiState.value = _uiState.value.copy(search = "", categoryFilter = null, accountFilter = null, dateFilter = null) }

    fun filteredEntries(): List<LedgerEntry> {
        val state = _uiState.value
        val query = state.search.trim().lowercase()
        return state.data.entries.filter { entry ->
            (query.isBlank() || listOf(entry.purpose, entry.note, entry.category, entry.accountName).any { it.lowercase().contains(query) }) &&
                (state.categoryFilter == null || entry.category == state.categoryFilter) &&
                (state.accountFilter == null || entry.accountId == state.accountFilter) &&
                (state.dateFilter == null || Instant.ofEpochMilli(entry.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() == state.dateFilter)
        }
    }

    fun activeAccounts() = _uiState.value.data.accounts.filterNot { it.archived }
    fun activeCategories(type: EntryType? = null) = _uiState.value.data.categories.filter { !it.archived && (type == null || it.type == type) }

    fun entriesInCurrency(currency: String = _uiState.value.data.settings.currency) = _uiState.value.data.entries.filter { it.currency == currency }

    fun entriesInRange(range: String, now: LocalDate = LocalDate.now()): List<LedgerEntry> {
        val zone = ZoneId.systemDefault()
        val start = when (range) {
            "week" -> now.minusDays((now.dayOfWeek.value - 1).toLong())
            "year" -> now.withDayOfYear(1)
            else -> now.withDayOfMonth(1)
        }
        val end = when (range) {
            "week" -> start.plusWeeks(1)
            "year" -> start.plusYears(1)
            else -> start.plusMonths(1)
        }
        return entriesInCurrency().filter {
            val date = Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
            !date.isBefore(start) && date.isBefore(end)
        }
    }

    fun monthTotals(now: LocalDate = LocalDate.now()): Triple<Double, Double, Double> {
        return LedgerAnalytics.totals(entriesInRange("month", now), _uiState.value.data.settings.currency)
    }

    fun spendingSince(start: LocalDate, endExclusive: LocalDate): Double {
        val zone = ZoneId.systemDefault()
        return entriesInCurrency().filter {
            val date = Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
            it.type == EntryType.EXPENSE && !date.isBefore(start) && date.isBefore(endExclusive)
        }.sumOf { it.amount }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(userMessage = null) }

    private fun persist(data: StoredState) {
        _uiState.value = _uiState.value.copy(data = data)
        viewModelScope.launch {
            runCatching { repository.save(data) }.onFailure {
                _uiState.value = _uiState.value.copy(userMessage = "Unable to save changes. Please try again.")
            }
        }
    }
}
