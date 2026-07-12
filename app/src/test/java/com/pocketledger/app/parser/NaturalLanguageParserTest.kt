package com.pocketledger.app.parser

import com.pocketledger.app.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class NaturalLanguageParserTest {
    private val accounts = listOf(
        LedgerAccount(id = "wechat", name = "WeChat Pay", type = "E-wallet", currency = "CNY"),
        LedgerAccount(id = "alipay", name = "Alipay", type = "E-wallet", currency = "CNY"),
        LedgerAccount(id = "cash", name = "Cash", type = "Cash", currency = "USD"),
        LedgerAccount(id = "card", name = "My Credit Card", type = "Credit Card", currency = "USD"),
        LedgerAccount(id = "octopus", name = "Octopus", type = "Transport Card", currency = "HKD"),
        LedgerAccount(id = "boc", name = "中國銀行", type = "Bank Account", currency = "CNY"),
        LedgerAccount(id = "paypal", name = "PayPal", type = "E-wallet", currency = "USD"),
    )
    private val categories = defaultCategories(0)
    private val now = ZonedDateTime.of(2026, 7, 11, 12, 34, 56, 0, ZoneId.of("Asia/Shanghai"))

    private fun parse(text: String) = NaturalLanguageParser.parse(text, "CNY", accounts, categories, now).entry
    private fun dateTime(entry: LedgerEntry) = ZonedDateTime.ofInstant(Instant.ofEpochMilli(entry.occurredAt), now.zone)

    @Test fun parsesEnglishAppleWithInTimeAndNoAccount() {
        val entry = parse("I spent 10 dollars for an apple in 20:28.")
        assertEquals(EntryType.EXPENSE, entry.type)
        assertEquals(10.0, entry.amount, 0.001)
        assertEquals("USD", entry.currency)
        assertEquals("Apple", entry.purpose)
        assertEquals("Food", entry.category)
        assertNull(entry.accountId)
        assertEquals("", entry.accountName)
        assertEquals(20, dateTime(entry).hour)
        assertEquals(28, dateTime(entry).minute)
    }

    @Test fun parsesEnglishCoffeeAndCreditCard() {
        val entry = parse("I bought coffee for 28 dollars using my credit card.")
        assertEquals(28.0, entry.amount, 0.001)
        assertEquals("Coffee", entry.purpose)
        assertEquals("Food", entry.category)
        assertEquals("card", entry.accountId)
    }

    @Test fun parsesEnglishSalaryIncome() {
        val entry = parse("I received 500 dollars as salary today.")
        assertEquals(EntryType.INCOME, entry.type)
        assertEquals("Salary", entry.purpose)
        assertEquals("Salary", entry.category)
        assertEquals(now.dayOfMonth, dateTime(entry).dayOfMonth)
    }

    @Test fun parsesEnglishBusWithCash() {
        val entry = parse("I paid 20 dollars for the bus with cash.")
        assertEquals("Bus", entry.purpose)
        assertEquals("Transport", entry.category)
        assertEquals("cash", entry.accountId)
    }

    @Test fun parsesEnglishRefundToPayPal() {
        val entry = parse("I got a 30 dollar refund to PayPal.")
        assertEquals(EntryType.INCOME, entry.type)
        assertEquals(30.0, entry.amount, 0.001)
        assertEquals("Refund", entry.purpose)
        assertEquals("Refund", entry.category)
        assertEquals("paypal", entry.accountId)
    }

    @Test fun parsesTraditionalChineseExpense() {
        val entry = parse("昨天用微信買午飯，32 元。")
        assertEquals(32.0, entry.amount, 0.001)
        assertEquals("午飯", entry.purpose)
        assertEquals("Food", entry.category)
        assertEquals("wechat", entry.accountId)
        assertEquals(10, dateTime(entry).dayOfMonth)
    }

    @Test fun parsesSimplifiedChineseAlipayCoffee() {
        val entry = parse("今天用支付宝买咖啡，花了 28 元。")
        assertEquals("咖啡", entry.purpose)
        assertEquals("Food", entry.category)
        assertEquals("alipay", entry.accountId)
    }

    @Test fun parsesChineseNumberAndSpokenTime() {
        val entry = parse("晚上八點二十八分買了一個蘋果，花了十美元。")
        assertEquals(10.0, entry.amount, 0.001)
        assertEquals("USD", entry.currency)
        assertEquals("蘋果", entry.purpose)
        assertEquals("Food", entry.category)
        assertEquals(20, dateTime(entry).hour)
        assertEquals(28, dateTime(entry).minute)
    }

    @Test fun parsesOctopusMetro() {
        val entry = parse("坐地鐵用了八達通，花了 12 港幣。")
        assertEquals("HKD", entry.currency)
        assertEquals("地鐵", entry.purpose)
        assertEquals("Transport", entry.category)
        assertEquals("octopus", entry.accountId)
    }

    @Test fun parsesChineseSalaryAndBank() {
        val original = "今天收到 5000 元工資，存入中國銀行。"
        val entry = parse(original)
        assertEquals(EntryType.INCOME, entry.type)
        assertEquals("工資", entry.purpose)
        assertEquals("Salary", entry.category)
        assertEquals("boc", entry.accountId)
        assertEquals(original, entry.note)
        assertEquals(original, entry.rawText)
    }

    @Test fun doesNotMistakeDateForAmount() {
        assertEquals(32.0, parse("7月10日午飯 32元").amount, 0.001)
    }
}
