package com.pocketledger.app.model

import org.junit.Assert.*
import org.junit.Test

class LedgerRulesTest {
    private val account = LedgerAccount(id = "cash", name = "Cash", currency = "CNY", initialBalance = 100.0, balance = 100.0)
    private val food = LedgerCategory(id = "food", name = "Food", type = EntryType.EXPENSE)

    @Test fun expenseAndIncomeUpdateBalanceInMinorUnits() {
        val entries = listOf(
            LedgerEntry(id = "expense", type = EntryType.EXPENSE, amount = 10.25, currency = "CNY", accountId = "cash"),
            LedgerEntry(id = "income", type = EntryType.INCOME, amount = 20.10, currency = "CNY", accountId = "cash"),
        )
        val result = LedgerRules.recalculateBalances(listOf(account), entries, 0).single()
        assertEquals(109.85, result.balance, 0.001)
    }

    @Test fun editingTransactionRecalculatesWithoutDoubleApplying() {
        val old = LedgerEntry(id = "entry", type = EntryType.EXPENSE, amount = 10.0, currency = "CNY", accountId = "cash")
        val edited = old.copy(amount = 25.0)
        assertEquals(90.0, LedgerRules.recalculateBalances(listOf(account), listOf(old), 0).single().balance, 0.001)
        assertEquals(75.0, LedgerRules.recalculateBalances(listOf(account), listOf(edited), 0).single().balance, 0.001)
    }

    @Test fun deletingTransactionRestoresBalance() {
        assertEquals(100.0, LedgerRules.recalculateBalances(listOf(account), emptyList(), 0).single().balance, 0.001)
    }

    @Test fun differentCurrencyDoesNotChangeAccountBalance() {
        val usd = LedgerEntry(type = EntryType.EXPENSE, amount = 50.0, currency = "USD", accountId = "cash")
        assertEquals(100.0, LedgerRules.recalculateBalances(listOf(account), listOf(usd), 0).single().balance, 0.001)
    }

    @Test fun usedAccountAndCategoryCannotBeSilentlyDeleted() {
        val entry = LedgerEntry(accountId = account.id, categoryId = food.id)
        assertFalse(LedgerRules.canDeleteAccount(account.id, listOf(entry)))
        assertFalse(LedgerRules.canDeleteCategory(food.id, listOf(entry)))
        assertTrue(LedgerRules.canDeleteAccount(account.id, emptyList()))
        assertTrue(LedgerRules.canDeleteCategory(food.id, emptyList()))
    }

    @Test fun archivedItemsAreExcludedFromNewEntryLists() {
        assertTrue(LedgerRules.activeAccounts(listOf(account.copy(archived = true))).isEmpty())
        assertTrue(LedgerRules.activeCategories(listOf(food.copy(archived = true)), EntryType.EXPENSE).isEmpty())
        assertTrue(LedgerRules.activeCategories(listOf(food), EntryType.INCOME).isEmpty())
        assertEquals(listOf(food), LedgerRules.activeCategories(listOf(food), EntryType.EXPENSE))
    }

    @Test fun accountCanBeAddedEditedAndArchived() {
        val added = LedgerRules.upsertAccount(emptyList(), account)
        assertEquals(account, added.single())
        val edited = LedgerRules.upsertAccount(added, account.copy(name = "Wallet"))
        assertEquals("Wallet", edited.single().name)
        assertTrue(LedgerRules.activeAccounts(edited.map { it.copy(archived = true) }).isEmpty())
    }

    @Test fun categoryCanBeAddedEditedAndArchived() {
        val added = LedgerRules.upsertCategory(emptyList(), food)
        assertEquals(food, added.single())
        val edited = LedgerRules.upsertCategory(added, food.copy(name = "Dining"))
        assertEquals("Dining", edited.single().name)
        assertTrue(LedgerRules.activeCategories(edited.map { it.copy(archived = true) }, EntryType.EXPENSE).isEmpty())
    }

    @Test fun moneyUsesCurrencyMinorUnits() {
        assertEquals(1050L, Money.toMinor(10.50, "CNY"))
        assertEquals(11L, Money.toMinor(10.6, "JPY"))
    }

    @Test fun customCategoryAppearsInAnalyticsWithoutBeingHardcoded() {
        val entries = listOf(LedgerEntry(amount = 12.34, currency = "CNY", category = "Pets", type = EntryType.EXPENSE))
        assertEquals(12.34, LedgerAnalytics.categoryTotals(entries, EntryType.EXPENSE, "CNY").getValue("Pets"), 0.001)
    }

    @Test fun analyticsDoesNotCombineCurrencies() {
        val entries = listOf(
            LedgerEntry(amount = 10.0, currency = "CNY", type = EntryType.EXPENSE),
            LedgerEntry(amount = 20.0, currency = "USD", type = EntryType.EXPENSE),
        )
        assertEquals(10.0, LedgerAnalytics.totals(entries, "CNY").second, 0.001)
        assertEquals(20.0, LedgerAnalytics.totals(entries, "USD").second, 0.001)
    }
}
