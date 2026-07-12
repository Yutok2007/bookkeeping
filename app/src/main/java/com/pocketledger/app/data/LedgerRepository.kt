package com.pocketledger.app.data

import android.content.Context
import com.pocketledger.app.data.room.*
import com.pocketledger.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class LedgerRepository(context: Context) {
    companion object {
        private const val BACKUP_FORMAT = "pocket-ledger-backup"
        private const val BACKUP_VERSION = 1
    }

    private val preferences = context.getSharedPreferences("pocket_ledger", Context.MODE_PRIVATE)
    private val dao = PocketLedgerDatabase.getInstance(context).ledgerDao()
    private val writeMutex = Mutex()

    fun load(): StoredState = runBlocking(Dispatchers.IO) { loadAndImportIfNeeded() }

    suspend fun save(state: StoredState) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            saveToRoom(state)
            preferences.edit()
                .putString("state", encode(state).toString())
                .putBoolean("room_import_complete", true)
                .apply()
        }
    }

    private suspend fun loadAndImportIfNeeded(): StoredState {
        val legacyRaw = preferences.getString("state", null)
        val legacy = legacyRaw?.let { raw -> runCatching { decode(JSONObject(raw)) }.getOrNull() }
        val roomHasData = dao.accountCount() > 0 || dao.transactionCount() > 0 || dao.getCategories().isNotEmpty()
        if (!roomHasData) {
            val imported = prepareForRoom(legacy ?: initialState())
            saveToRoom(imported)
            preferences.edit().putString("state", encode(imported).toString()).putBoolean("room_import_complete", true).apply()
            return imported
        }
        val roomState = readRoomState(legacy?.settings ?: AppSettings())
        return if (roomState.categories.isEmpty()) {
            roomState.copy(categories = defaultCategories()).also { saveToRoom(it) }
        } else roomState
    }

    private fun prepareForRoom(source: StoredState): StoredState {
        val categories = source.categories.ifEmpty { defaultCategories() }
        val resolvedEntries = source.entries.map { entry ->
            val category = categories.firstOrNull { it.type == entry.type && it.name.equals(entry.category, true) }
            entry.copy(categoryId = category?.id, category = category?.name ?: entry.category)
        }
        val accounts = source.accounts.map { account ->
            val impact = resolvedEntries.filter { it.accountId == account.id && it.currency == account.currency }
                .sumOf { if (it.type == EntryType.INCOME) it.amount else -it.amount }
            account.copy(
                type = normalizeAccountType(account.type),
                initialBalance = if (account.initialBalance != 0.0) account.initialBalance else account.balance - impact,
            )
        }
        return source.copy(
            entries = resolvedEntries,
            accounts = accounts,
            categories = categories,
        )
    }

    fun exportBackup(state: StoredState): String = JSONObject().apply {
        put("format", BACKUP_FORMAT)
        put("version", BACKUP_VERSION)
        put("exportedAt", Instant.now().toEpochMilli())
        put("data", encode(state))
    }.toString(2)

    suspend fun importBackup(raw: String): StoredState = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val root = JSONObject(raw)
            require(root.optString("format") == BACKUP_FORMAT) { "This is not a Pocket Ledger backup file." }
            val version = root.optInt("version", 0)
            require(version in 1..BACKUP_VERSION) { "This backup version is not supported by this app." }
            val data = root.optJSONObject("data") ?: error("The backup file does not contain ledger data.")
            require(data.has("entries") && data.has("accounts") && data.has("categories")) { "The backup file is incomplete." }
            val restored = prepareForRoom(decode(data))
            validateBackup(restored)
            saveToRoom(restored)
            preferences.edit()
                .putString("state", encode(restored).toString())
                .putBoolean("room_import_complete", true)
                .apply()
            restored
        }
    }

    private fun validateBackup(state: StoredState) {
        require(state.entries.map { it.id }.distinct().size == state.entries.size) { "The backup contains duplicate transaction IDs." }
        require(state.accounts.map { it.id }.distinct().size == state.accounts.size) { "The backup contains duplicate account IDs." }
        require(state.categories.map { it.id }.distinct().size == state.categories.size) { "The backup contains duplicate category IDs." }
        require(state.entries.all { it.amount.isFinite() && it.amount >= 0.0 }) { "The backup contains an invalid transaction amount." }
        require(state.accounts.all { it.balance.isFinite() && it.initialBalance.isFinite() }) { "The backup contains an invalid account balance." }
        require(state.budget.total.isFinite() && state.budget.categoryAmounts.values.all(Double::isFinite)) { "The backup contains an invalid budget." }
    }

    private fun initialState(): StoredState {
        val now = Instant.now().toEpochMilli()
        return StoredState(
            accounts = listOf(
                LedgerAccount(name = "Cash", type = "Cash", currency = "CNY", createdAt = now, updatedAt = now),
                LedgerAccount(name = "WeChat Pay", type = "E-wallet", currency = "CNY", createdAt = now, updatedAt = now),
                LedgerAccount(name = "Alipay", type = "E-wallet", currency = "CNY", createdAt = now, updatedAt = now),
            ),
            categories = defaultCategories(now),
        )
    }

    private suspend fun saveToRoom(state: StoredState) {
        val now = Instant.now().toEpochMilli()
        val budgets = buildList {
            add(BudgetEntity("monthly-total", "local", "MONTHLY", null, null, Money.toMinor(state.budget.total, state.budget.currency), state.budget.currency, now, now))
            state.budget.categoryAmounts.forEach { (name, amount) ->
                val category = state.categories.firstOrNull { it.name == name }
                add(BudgetEntity("category:${category?.id ?: name}", "local", "MONTHLY", category?.id, name, Money.toMinor(amount, state.budget.currency), state.budget.currency, now, now))
            }
        }
        dao.replaceAll(
            state.entries.map { it.toEntity() },
            state.accounts.map { it.toEntity() },
            state.categories.map { it.toEntity() },
            budgets,
        )
    }

    private suspend fun readRoomState(settings: AppSettings): StoredState {
        val categories = dao.getCategories().map { it.toModel() }
        val budgets = dao.getBudgets()
        val total = budgets.firstOrNull { it.categoryId == null }
        val budgetCurrency = total?.currency ?: settings.currency
        return StoredState(
            entries = dao.getTransactions().map { it.toModel() },
            accounts = dao.getAccounts().map { it.toModel() },
            categories = categories,
            budget = Budget(
                total = total?.let { Money.fromMinor(it.amountMinor, it.currency) } ?: 5000.0,
                categoryAmounts = budgets.filter { it.categoryId != null || it.categoryNameSnapshot != null }
                    .associate { entity -> (categories.firstOrNull { it.id == entity.categoryId }?.name ?: entity.categoryNameSnapshot.orEmpty()) to Money.fromMinor(entity.amountMinor, entity.currency) },
                currency = budgetCurrency,
            ),
            settings = settings,
        )
    }

    private fun LedgerEntry.toEntity() = TransactionEntity(
        id, userId, type.name, Money.toMinor(amount, currency), currency, purpose, categoryId, category,
        accountId, accountName, occurredAt, note, rawText, createdAt, updatedAt,
    )

    private fun TransactionEntity.toModel() = LedgerEntry(
        id = id, userId = userId, type = enumValueOrDefault(type, EntryType.EXPENSE),
        amount = Money.fromMinor(amountMinor, currency), currency = currency, purpose = purpose,
        categoryId = categoryId, category = categoryNameSnapshot, accountId = accountId,
        accountName = accountNameSnapshot, occurredAt = occurredAt, note = note, rawText = originalText,
        createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun LedgerAccount.toEntity() = AccountEntity(
        id, userId, name, normalizeAccountType(type), currency, Money.toMinor(initialBalance, currency),
        Money.toMinor(balance, currency), includeInAssets, archived, createdAt, updatedAt,
    )

    private fun AccountEntity.toModel() = LedgerAccount(
        id = id, userId = userId, name = name, type = type,
        initialBalance = Money.fromMinor(initialBalanceMinor, currency),
        balance = Money.fromMinor(currentBalanceMinor, currency), currency = currency,
        includeInAssets = includeInTotalAssets, archived = archived, createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun LedgerCategory.toEntity() = CategoryEntity(id, userId, name, type.name, icon, color, archived, createdAt, updatedAt)
    private fun CategoryEntity.toModel() = LedgerCategory(id, userId, name, enumValueOrDefault(type, EntryType.EXPENSE), icon, color, archived, createdAt, updatedAt)

    private fun normalizeAccountType(type: String): String = when (type) {
        "Savings" -> "Bank Account"
        "Credit card" -> "Credit Card"
        "Transit card" -> "Transport Card"
        "Investment" -> "Investment Account"
        else -> type
    }

    private fun encode(state: StoredState) = JSONObject().apply {
        put("entries", JSONArray().apply { state.entries.forEach { put(entryToJson(it)) } })
        put("accounts", JSONArray().apply { state.accounts.forEach { put(accountToJson(it)) } })
        put("categories", JSONArray().apply { state.categories.forEach { put(categoryToJson(it)) } })
        put("budget", JSONObject().apply {
            put("total", state.budget.total); put("currency", state.budget.currency); put("categories", JSONObject(state.budget.categoryAmounts))
        })
        put("settings", JSONObject().apply {
            put("theme", state.settings.theme.name); put("textSize", state.settings.textSize.name)
            put("currency", state.settings.currency); put("language", state.settings.language)
        })
    }

    private fun decode(json: JSONObject): StoredState {
        val entries = json.optJSONArray("entries") ?: JSONArray()
        val accounts = json.optJSONArray("accounts") ?: JSONArray()
        val categories = json.optJSONArray("categories") ?: JSONArray()
        val budget = json.optJSONObject("budget") ?: JSONObject()
        val settings = json.optJSONObject("settings") ?: JSONObject()
        val categoryJson = budget.optJSONObject("categories") ?: JSONObject()
        return StoredState(
            entries = (0 until entries.length()).map { jsonToEntry(entries.getJSONObject(it)) },
            accounts = (0 until accounts.length()).map { jsonToAccount(accounts.getJSONObject(it)) },
            categories = (0 until categories.length()).map { jsonToCategory(categories.getJSONObject(it)) },
            budget = Budget(budget.optDouble("total", 5000.0), categoryJson.keys().asSequence().associateWith { categoryJson.optDouble(it) }, budget.optString("currency", "CNY")),
            settings = AppSettings(enumValueOrDefault(settings.optString("theme"), ThemeMode.SYSTEM), enumValueOrDefault(settings.optString("textSize"), TextSize.STANDARD), settings.optString("currency", "CNY"), settings.optString("language", "zh-TW")),
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T = enumValues<T>().firstOrNull { it.name == value } ?: default

    private fun entryToJson(entry: LedgerEntry) = JSONObject().apply {
        put("id", entry.id); put("userId", entry.userId); put("type", entry.type.name); put("amount", entry.amount)
        put("currency", entry.currency); put("purpose", entry.purpose); put("categoryId", entry.categoryId); put("category", entry.category)
        put("accountId", entry.accountId); put("accountName", entry.accountName); put("occurredAt", entry.occurredAt)
        put("note", entry.note); put("rawText", entry.rawText); put("createdAt", entry.createdAt); put("updatedAt", entry.updatedAt)
    }

    private fun jsonToEntry(json: JSONObject) = LedgerEntry(
        id = json.getString("id"), userId = json.optString("userId", "local"), type = enumValueOrDefault(json.optString("type"), EntryType.EXPENSE),
        amount = json.optDouble("amount"), currency = json.optString("currency", "CNY"), purpose = json.optString("purpose"),
        categoryId = if (json.isNull("categoryId")) null else json.optString("categoryId").ifBlank { null }, category = json.optString("category"),
        accountId = if (json.isNull("accountId")) null else json.optString("accountId").ifBlank { null }, accountName = json.optString("accountName"),
        occurredAt = json.optLong("occurredAt", Instant.now().toEpochMilli()), note = json.optString("note"), rawText = json.optString("rawText"),
        createdAt = json.optLong("createdAt", Instant.now().toEpochMilli()), updatedAt = json.optLong("updatedAt", Instant.now().toEpochMilli()),
    )

    private fun accountToJson(account: LedgerAccount) = JSONObject().apply {
        put("id", account.id); put("userId", account.userId); put("name", account.name); put("type", account.type)
        put("initialBalance", account.initialBalance); put("balance", account.balance); put("currency", account.currency)
        put("includeInAssets", account.includeInAssets); put("archived", account.archived); put("createdAt", account.createdAt); put("updatedAt", account.updatedAt)
    }

    private fun jsonToAccount(json: JSONObject) = LedgerAccount(
        id = json.getString("id"), userId = json.optString("userId", "local"), name = json.optString("name"), type = json.optString("type", "Cash"),
        initialBalance = json.optDouble("initialBalance", 0.0), balance = json.optDouble("balance"), currency = json.optString("currency", "CNY"),
        includeInAssets = json.optBoolean("includeInAssets", true), archived = json.optBoolean("archived"),
        createdAt = json.optLong("createdAt", Instant.now().toEpochMilli()), updatedAt = json.optLong("updatedAt", Instant.now().toEpochMilli()),
    )

    private fun categoryToJson(category: LedgerCategory) = JSONObject().apply {
        put("id", category.id); put("userId", category.userId); put("name", category.name); put("type", category.type.name)
        put("icon", category.icon); put("color", category.color); put("archived", category.archived); put("createdAt", category.createdAt); put("updatedAt", category.updatedAt)
    }

    private fun jsonToCategory(json: JSONObject) = LedgerCategory(
        id = json.getString("id"), userId = json.optString("userId", "local"), name = json.optString("name"),
        type = enumValueOrDefault(json.optString("type"), EntryType.EXPENSE), icon = json.optString("icon", "·"),
        color = json.optLong("color", 0xFF718078), archived = json.optBoolean("archived"),
        createdAt = json.optLong("createdAt", Instant.now().toEpochMilli()), updatedAt = json.optLong("updatedAt", Instant.now().toEpochMilli()),
    )
}
