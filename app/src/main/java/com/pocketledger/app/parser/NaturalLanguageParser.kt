package com.pocketledger.app.parser

import com.pocketledger.app.model.EntryType
import com.pocketledger.app.model.LedgerAccount
import com.pocketledger.app.model.LedgerCategory
import com.pocketledger.app.model.LedgerEntry
import java.time.*
import java.util.Locale

object NaturalLanguageParser {
    data class ParseResult(val entry: LedgerEntry, val missing: Set<String>)

    private val incomeWords = listOf(
        "收入", "收到", "入帳", "入账", "工資", "工资", "薪水", "獎學金", "奖学金", "退款",
        "earned", "received", "salary", "income", "refund", "got a", "recibí", "reçu", "erhalten", "recebi", "収入", "월급", "راتب"
    )

    private val currencyAliases = linkedMapOf(
        "HKD" to listOf("hkd", "港幣", "港币", "hk$"),
        "USD" to listOf("usd", "dollar", "dollars", "美元", "us$", "$"),
        "EUR" to listOf("eur", "euro", "euros", "歐元", "欧元", "€"),
        "GBP" to listOf("gbp", "pound", "pounds", "英鎊", "英镑", "£"),
        "JPY" to listOf("jpy", "日圓", "日元", "円"),
        "KRW" to listOf("krw", "韓圜", "韩元", "원", "₩"),
        "SGD" to listOf("sgd", "新加坡元"), "AUD" to listOf("aud", "澳元"), "CAD" to listOf("cad", "加元"),
        "CNY" to listOf("cny", "rmb", "人民幣", "人民币", "元", "￥", "¥"),
    )

    private val purposeAliases = linkedMapOf(
        "apple" to listOf("apple"), "coffee" to listOf("coffee"), "lunch" to listOf("lunch"),
        "breakfast" to listOf("breakfast"), "dinner" to listOf("dinner"), "bus" to listOf("bus"),
        "metro" to listOf("metro", "subway"), "taxi" to listOf("taxi", "uber"),
        "medicine" to listOf("medicine", "medication"), "doctor" to listOf("doctor"),
        "book" to listOf("book"), "tuition" to listOf("tuition"), "salary" to listOf("salary"),
        "scholarship" to listOf("scholarship"), "refund" to listOf("refund"),
        "蘋果" to listOf("蘋果"), "苹果" to listOf("苹果"), "咖啡" to listOf("咖啡"),
        "午飯" to listOf("午飯"), "午餐" to listOf("午餐"), "午饭" to listOf("午饭"),
        "早餐" to listOf("早餐"), "晚飯" to listOf("晚飯"), "晚餐" to listOf("晚餐"),
        "地鐵" to listOf("地鐵"), "地铁" to listOf("地铁"), "巴士" to listOf("巴士"), "公交" to listOf("公交"),
        "藥品" to listOf("藥品"), "药品" to listOf("药品"), "學費" to listOf("學費"), "学费" to listOf("学费"),
        "工資" to listOf("工資"), "工资" to listOf("工资"), "獎學金" to listOf("獎學金"), "奖学金" to listOf("奖学金"), "退款" to listOf("退款"),
    )

    private val categoryByPurpose = mapOf(
        "apple" to "Food", "coffee" to "Food", "lunch" to "Food", "breakfast" to "Food", "dinner" to "Food",
        "蘋果" to "Food", "苹果" to "Food", "咖啡" to "Food", "午飯" to "Food", "午餐" to "Food", "午饭" to "Food", "早餐" to "Food", "晚飯" to "Food", "晚餐" to "Food",
        "bus" to "Transport", "metro" to "Transport", "taxi" to "Transport", "地鐵" to "Transport", "地铁" to "Transport", "巴士" to "Transport", "公交" to "Transport",
        "medicine" to "Health", "doctor" to "Health", "藥品" to "Health", "药品" to "Health",
        "book" to "Education", "tuition" to "Education", "學費" to "Education", "学费" to "Education",
        "salary" to "Salary", "工資" to "Salary", "工资" to "Salary",
        "scholarship" to "Scholarship", "獎學金" to "Scholarship", "奖学金" to "Scholarship",
        "refund" to "Refund", "退款" to "Refund",
    )

    private data class AccountMatch(val id: String?, val name: String)

    fun parse(
        text: String,
        defaultCurrency: String,
        accounts: List<LedgerAccount>,
        categories: List<LedgerCategory> = emptyList(),
        now: ZonedDateTime = ZonedDateTime.now(),
    ): ParseResult {
        val clean = text.trim()
        val normalized = clean.lowercase(Locale.ROOT)
        val type = if (incomeWords.any(normalized::contains)) EntryType.INCOME else EntryType.EXPENSE
        val amount = extractAmount(clean)
        val currency = extractCurrency(normalized) ?: defaultCurrency
        val purpose = extractPurpose(clean, normalized)
        val category = resolveCategory(normalized, purpose, type, categories)
        val account = resolveAccount(normalized, accounts)
        val occurredAt = extractDateTime(normalized, now).toInstant().toEpochMilli()
        val entry = LedgerEntry(
            type = type,
            amount = amount ?: 0.0,
            currency = currency,
            purpose = purpose,
            categoryId = category?.id,
            category = category?.name.orEmpty(),
            accountId = account?.id,
            accountName = account?.name.orEmpty(),
            occurredAt = occurredAt,
            note = clean,
            rawText = clean,
        )
        return ParseResult(entry, buildSet {
            if (amount == null) add("amount")
            if (purpose.isBlank()) add("purpose")
            if (category == null) add("category")
            if (account == null || account.id == null) add("account")
        })
    }

    private fun extractCurrency(text: String): String? = currencyAliases.entries.firstOrNull { (_, aliases) -> aliases.any(text::contains) }?.key

    private fun extractAmount(text: String): Double? {
        val normalized = text.lowercase(Locale.ROOT)
        val verbAmount = Regex("(?:spent|paid|cost|received|earned|花了|花費|支付|收到|收入)[^0-9]{0,16}([0-9]+(?:[.,][0-9]{1,2})?)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)
        val numeric = verbAmount ?: listOf(
            Regex("(?:cny|rmb|hkd|usd|eur|gbp|jpy|krw|sgd|aud|cad|[¥￥$€£₩])\\s*([0-9]+(?:[.,][0-9]{1,2})?)", RegexOption.IGNORE_CASE),
            Regex("([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:元|圓|dollars?|euros?|pounds?|yuan|hkd|usd|cny|eur|gbp|jpy|krw|sgd|aud|cad)", RegexOption.IGNORE_CASE),
        ).asSequence().mapNotNull { it.find(text)?.groupValues?.getOrNull(1) }.firstOrNull()
        if (numeric != null) return numeric.replace(",", ".").toDoubleOrNull()

        val chinese = Regex("([零〇一二兩两三四五六七八九十百千萬万]+)\\s*(?:美元|港幣|港币|人民幣|人民币|元|圓)")
            .find(normalized)?.groupValues?.getOrNull(1)
        return chinese?.let(::parseChineseNumber)?.toDouble()
    }

    private fun extractPurpose(original: String, normalized: String): String {
        purposeAliases.forEach { (canonical, aliases) ->
            if (aliases.any(normalized::contains)) return if (canonical.firstOrNull()?.isLetter() == true && canonical.all { it.code < 128 }) canonical.replaceFirstChar { it.titlecase(Locale.ENGLISH) } else canonical
        }
        val patterns = listOf(
            Regex("\\bfor\\s+(?:an?|the)?\\s*([a-z][a-z-]*)", RegexOption.IGNORE_CASE),
            Regex("\\bon\\s+([a-z][a-z-]*)", RegexOption.IGNORE_CASE),
            Regex("\\bbought\\s+(?:an?|the)?\\s*([a-z][a-z-]*)", RegexOption.IGNORE_CASE),
            Regex("\\bas\\s+([a-z][a-z-]*)", RegexOption.IGNORE_CASE),
        )
        val ignored = setOf("my", "the", "a", "an", "using", "today", "yesterday")
        return patterns.asSequence().mapNotNull { it.find(original)?.groupValues?.getOrNull(1) }
            .firstOrNull { it.lowercase(Locale.ROOT) !in ignored && it.toDoubleOrNull() == null }
            ?.replaceFirstChar { it.titlecase(Locale.ENGLISH) }.orEmpty()
    }

    private fun resolveCategory(text: String, purpose: String, type: EntryType, categories: List<LedgerCategory>): LedgerCategory? {
        val active = categories.filter { !it.archived && it.type == type }
        active.firstOrNull { text.contains(it.name.lowercase(Locale.ROOT)) }?.let { return it }
        val desired = categoryByPurpose[purpose.lowercase(Locale.ROOT)] ?: categoryByPurpose[purpose]
        if (desired != null) active.firstOrNull { it.name.equals(desired, true) }?.let { return it }
        val otherNames = if (type == EntryType.INCOME) setOf("Other Income", "Other income") else setOf("Other")
        return if (purpose.isNotBlank()) active.firstOrNull { it.name in otherNames } else null
    }

    private fun resolveAccount(text: String, accounts: List<LedgerAccount>): AccountMatch? {
        val active = accounts.filterNot { it.archived }
        active.sortedByDescending { it.name.length }.firstOrNull { text.contains(it.name.lowercase(Locale.ROOT)) }
            ?.let { return AccountMatch(it.id, it.name) }

        val requested = when {
            listOf("微信支付", "微信", "wechat").any(text::contains) -> "WeChat Pay"
            listOf("支付寶", "支付宝", "alipay").any(text::contains) -> "Alipay"
            listOf("信用卡", "credit card").any(text::contains) -> "Credit Card"
            listOf("現金", "现金", "cash").any(text::contains) -> "Cash"
            listOf("八達通", "八达通", "octopus").any(text::contains) -> "Octopus"
            "paypal" in text -> "PayPal"
            listOf("中國銀行", "中国银行", "bank of china").any(text::contains) -> "中國銀行"
            else -> return null
        }
        val actual = active.firstOrNull { it.name.equals(requested, true) }
            ?: when (requested) {
                "Credit Card" -> active.firstOrNull { it.type == "Credit Card" }
                "Cash" -> active.firstOrNull { it.type == "Cash" }
                "Octopus" -> active.firstOrNull { it.type == "Transport Card" && it.name.contains("Octopus", true) }
                else -> null
            }
        return AccountMatch(actual?.id, actual?.name ?: requested)
    }

    private fun extractDateTime(text: String, now: ZonedDateTime): ZonedDateTime {
        var date = now.toLocalDate()
        var time = now.toLocalTime().withSecond(0).withNano(0)

        date = when {
            listOf("前天", "day before yesterday", "anteayer", "avant-hier", "vorgestern", "一昨日", "그저께").any(text::contains) -> date.minusDays(2)
            listOf("昨天", "昨日", "yesterday", "ayer", "hier", "gestern", "ontem", "어제", "أمس").any(text::contains) -> date.minusDays(1)
            listOf("明天", "tomorrow", "mañana", "demain", "morgen", "amanhã", "내일", "غد").any(text::contains) -> date.plusDays(1)
            else -> date
        }

        Regex("(20\\d{2})\\s*(?:年|[-/.])\\s*(\\d{1,2})\\s*(?:月|[-/.])\\s*(\\d{1,2})\\s*(?:日)?").find(text)?.let {
            date = runCatching { LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrDefault(date)
        } ?: Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日").find(text)?.let {
            date = runCatching { LocalDate.of(now.year, it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrDefault(date)
        } ?: extractEnglishDate(text, now.year)?.let { date = it }

        Regex("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)").find(text)?.let {
            time = LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        } ?: extractChineseTime(text)?.let { time = it } ?: run {
            time = when {
                listOf("this morning", "早上", "上午").any(text::contains) -> LocalTime.of(9, 0)
                listOf("this afternoon", "中午", "下午").any(text::contains) -> LocalTime.of(15, 0)
                listOf("tonight", "晚上", "今晚").any(text::contains) -> LocalTime.of(20, 0)
                else -> time
            }
        }
        return ZonedDateTime.of(date, time, now.zone)
    }

    private fun extractEnglishDate(text: String, defaultYear: Int): LocalDate? {
        val months = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
        val match = Regex("\\b(${months.joinToString("|")})\\s+(\\d{1,2})(?:,\\s*(20\\d{2}))?", RegexOption.IGNORE_CASE).find(text) ?: return null
        val month = months.indexOf(match.groupValues[1].lowercase(Locale.ROOT)) + 1
        val year = match.groupValues[3].toIntOrNull() ?: defaultYear
        return runCatching { LocalDate.of(year, month, match.groupValues[2].toInt()) }.getOrNull()
    }

    private fun extractChineseTime(text: String): LocalTime? {
        val match = Regex("(早上|上午|中午|下午|晚上|今晚)?\\s*([零〇一二兩两三四五六七八九十百千0-9]+)\\s*[點点時时]\\s*(?:([零〇一二兩两三四五六七八九十百千0-9]+)\\s*分?)?").find(text) ?: return null
        val period = match.groupValues[1]
        var hour = match.groupValues[2].toIntOrNull() ?: parseChineseNumber(match.groupValues[2])
        val minuteRaw = match.groupValues[3]
        val minute = if (minuteRaw.isBlank()) 0 else minuteRaw.toIntOrNull() ?: parseChineseNumber(minuteRaw)
        if (period in listOf("下午", "晚上", "今晚") && hour < 12) hour += 12
        if (period == "中午" && hour < 11) hour += 12
        if (period in listOf("早上", "上午") && hour == 12) hour = 0
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }

    internal fun parseChineseNumber(value: String): Int {
        if (value.all(Char::isDigit)) return value.toIntOrNull() ?: 0
        val digits = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '兩' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        val units = mapOf('十' to 10, '百' to 100, '千' to 1000)
        var total = 0
        var section = 0
        var number = 0
        value.forEach { char ->
            when {
                char in digits -> number = digits.getValue(char)
                char in units -> {
                    if (number == 0) number = 1
                    section += number * units.getValue(char)
                    number = 0
                }
                char == '萬' || char == '万' -> {
                    section += number
                    total += section * 10_000
                    section = 0
                    number = 0
                }
            }
        }
        return total + section + number
    }
}
