package com.pocketledger.app.model

import java.time.Instant
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode

enum class EntryType { INCOME, EXPENSE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class TextSize(val scale: Float) { SMALL(0.88f), STANDARD(1f), LARGE(1.14f), EXTRA_LARGE(1.28f) }

data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "local",
    val type: EntryType = EntryType.EXPENSE,
    val amount: Double = 0.0,
    val currency: String = "CNY",
    val purpose: String = "",
    val categoryId: String? = null,
    val category: String = "",
    val accountId: String? = null,
    val accountName: String = "",
    val occurredAt: Long = Instant.now().toEpochMilli(),
    val note: String = "",
    val rawText: String = "",
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli(),
)

data class LedgerAccount(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "local",
    val name: String = "",
    val type: String = "Cash",
    val initialBalance: Double = 0.0,
    val balance: Double = 0.0,
    val currency: String = "CNY",
    val includeInAssets: Boolean = true,
    val archived: Boolean = false,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli(),
)

data class LedgerCategory(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "local",
    val name: String = "",
    val type: EntryType = EntryType.EXPENSE,
    val icon: String = "·",
    val color: Long = 0xFF718078,
    val archived: Boolean = false,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli(),
)

data class Budget(
    val total: Double = 5000.0,
    val categoryAmounts: Map<String, Double> = mapOf("Food" to 1200.0, "Transport" to 500.0),
    val currency: String = "CNY",
)

data class UserProfile(
    val id: String = "local",
    val name: String = "",
    val contact: String = "",
    val provider: String = "",
)

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val textSize: TextSize = TextSize.STANDARD,
    val currency: String = "CNY",
    val language: String = "zh-TW",
)

data class StoredState(
    val entries: List<LedgerEntry> = emptyList(),
    val accounts: List<LedgerAccount> = emptyList(),
    val categories: List<LedgerCategory> = emptyList(),
    val budget: Budget = Budget(),
    val settings: AppSettings = AppSettings(),
    val profile: UserProfile = UserProfile(),
)

val expenseCategories = listOf("Food", "Health", "Education", "Transport", "Shopping", "Entertainment", "Housing", "Travel", "Communication", "Subscriptions", "Other")
val incomeCategories = listOf("Salary", "Scholarship", "Refund", "Transfer In", "Investment Income", "Part-time Job", "Other Income")
val currencies = listOf("CNY", "HKD", "USD", "EUR", "GBP", "JPY", "KRW", "SGD", "AUD", "CAD")
val accountTypes = listOf("Cash", "Bank Account", "Credit Card", "E-wallet", "Transport Card", "Investment Account", "Other")

object Money {
    fun fractionDigits(currency: String): Int = if (currency in setOf("JPY", "KRW")) 0 else 2

    fun toMinor(amount: Double, currency: String): Long = BigDecimal.valueOf(amount)
        .movePointRight(fractionDigits(currency))
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()

    fun fromMinor(amountMinor: Long, currency: String): Double = BigDecimal.valueOf(amountMinor)
        .movePointLeft(fractionDigits(currency))
        .toDouble()
}

fun defaultCategories(now: Long = Instant.now().toEpochMilli()): List<LedgerCategory> =
    expenseCategories.map { name -> LedgerCategory(name = name, type = EntryType.EXPENSE, icon = defaultCategoryIcon(name), createdAt = now, updatedAt = now) } +
        incomeCategories.map { name -> LedgerCategory(name = name, type = EntryType.INCOME, icon = defaultCategoryIcon(name), createdAt = now, updatedAt = now) }

fun defaultCategoryIcon(name: String): String = when (name) {
    "Food" -> "●"; "Health" -> "+"; "Education" -> "A"; "Transport" -> "→"
    "Shopping" -> "◇"; "Entertainment" -> "☆"; "Housing" -> "⌂"; "Travel" -> "✦"
    "Communication" -> "⌁"; "Subscriptions" -> "↻"
    "Salary", "Scholarship", "Refund", "Transfer In", "Investment Income", "Part-time Job" -> "+"
    else -> "·"
}
