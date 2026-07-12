package com.pocketledger.app.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PocketLedgerDatabaseTest {
    private lateinit var database: PocketLedgerDatabase

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PocketLedgerDatabase::class.java).build()
    }

    @After fun closeDatabase() = database.close()

    @Test fun storesAccountCategoryAndTransactionWithoutForeignKeyDataLoss() = runBlocking {
        val account = AccountEntity("account", "local", "Cash", "Cash", "CNY", 10_000, 9_000, true, false, 1, 1)
        val category = CategoryEntity("category", "local", "Food", "EXPENSE", "●", 0xFF718078, false, 1, 1)
        val transaction = TransactionEntity("transaction", "local", "EXPENSE", 1_000, "CNY", "Lunch", category.id, category.name, account.id, account.name, 1, "", "", 1, 1)
        database.ledgerDao().replaceAll(listOf(transaction), listOf(account), listOf(category), emptyList())
        assertEquals(1_000, database.ledgerDao().getTransactions().single().amountMinor)
        assertEquals("Cash", database.ledgerDao().getAccounts().single().name)
        assertEquals("Food", database.ledgerDao().getCategories().single().name)
    }
}
