package com.pocketledger.app.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    suspend fun getTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM accounts ORDER BY archived ASC, name COLLATE NOCASE ASC")
    suspend fun getAccounts(): List<AccountEntity>

    @Query("SELECT * FROM categories ORDER BY archived ASC, type ASC, name COLLATE NOCASE ASC")
    suspend fun getCategories(): List<CategoryEntity>

    @Query("SELECT * FROM budgets")
    suspend fun getBudgets(): List<BudgetEntity>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM accounts ORDER BY archived ASC, name COLLATE NOCASE ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM categories ORDER BY archived ASC, type ASC, name COLLATE NOCASE ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(items: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(items: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(items: List<BudgetEntity>)

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM budgets")
    suspend fun clearBudgets()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun transactionCount(): Int

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun accountCount(): Int

    @Transaction
    suspend fun replaceAll(
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
    ) {
        clearTransactions()
        clearAccounts()
        clearCategories()
        clearBudgets()
        if (transactions.isNotEmpty()) insertTransactions(transactions)
        if (accounts.isNotEmpty()) insertAccounts(accounts)
        if (categories.isNotEmpty()) insertCategories(categories)
        if (budgets.isNotEmpty()) insertBudgets(budgets)
    }
}
