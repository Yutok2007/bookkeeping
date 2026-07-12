package com.pocketledger.app.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TransactionEntity::class, AccountEntity::class, CategoryEntity::class, BudgetEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PocketLedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile private var instance: PocketLedgerDatabase? = null

        fun getInstance(context: Context): PocketLedgerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PocketLedgerDatabase::class.java,
                "pocket_ledger.db",
            ).build().also { instance = it }
        }
    }
}
