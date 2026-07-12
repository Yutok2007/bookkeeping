package com.pocketledger.app.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transactions", indices = [Index("accountId"), Index("categoryId"), Index("occurredAt")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String,
    val amountMinor: Long,
    val currency: String,
    val purpose: String,
    val categoryId: String?,
    val categoryNameSnapshot: String,
    val accountId: String?,
    val accountNameSnapshot: String,
    val occurredAt: Long,
    val note: String,
    val originalText: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "accounts", indices = [Index(value = ["name", "userId"])])
data class AccountEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val type: String,
    val currency: String,
    val initialBalanceMinor: Long,
    val currentBalanceMinor: Long,
    val includeInTotalAssets: Boolean,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "categories", indices = [Index(value = ["name", "type", "userId"])])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val type: String,
    val icon: String,
    val color: Long,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "budgets", indices = [Index("categoryId")])
data class BudgetEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val period: String,
    val categoryId: String?,
    val categoryNameSnapshot: String?,
    val amountMinor: Long,
    val currency: String,
    val createdAt: Long,
    val updatedAt: Long,
)
