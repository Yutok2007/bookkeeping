package com.pocketledger.app.model

object LedgerAnalytics {
    fun categoryTotals(entries: List<LedgerEntry>, type: EntryType, currency: String): Map<String, Double> =
        entries.asSequence()
            .filter { it.type == type && it.currency == currency }
            .groupBy { it.category.ifBlank { "Uncategorized" } }
            .mapValues { (_, values) -> Money.fromMinor(values.sumOf { Money.toMinor(it.amount, it.currency) }, currency) }

    fun totals(entries: List<LedgerEntry>, currency: String): Triple<Double, Double, Double> {
        val matching = entries.filter { it.currency == currency }
        val incomeMinor = matching.filter { it.type == EntryType.INCOME }.sumOf { Money.toMinor(it.amount, currency) }
        val expenseMinor = matching.filter { it.type == EntryType.EXPENSE }.sumOf { Money.toMinor(it.amount, currency) }
        return Triple(Money.fromMinor(incomeMinor, currency), Money.fromMinor(expenseMinor, currency), Money.fromMinor(incomeMinor - expenseMinor, currency))
    }
}
