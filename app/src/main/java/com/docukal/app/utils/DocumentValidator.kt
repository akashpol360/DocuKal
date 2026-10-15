package com.docukal.app.utils

/**
 * The kinds of documents DocuKal recognises. Used for the DigiLocker-style type
 * picker, the icon/badge shown for each document, and for format validation.
 *
 * No data model change: a document's kind is stored in the existing free-text
 * `type` column (as [label]) and is otherwise re-detected from name/number, so
 * documents saved by older versions keep working.
 */
enum class DocKind(
    val label: String,
    val shortLabel: String,
    val badge: String,
    val numberLabel: String,
    val suggestedCategory: String?,
    /** Number must be entered and must pass format validation. */
    val numberRequired: Boolean = false,
    /** A number should only exist once in the vault (identity documents). */
    val uniqueNumber: Boolean = false
) {
    AADHAAR("Aadhaar Card", "Aadhaar", "AADHAAR", "Aadhaar number (12 digits)", "Personal", true, true),
    PAN("PAN Card", "PAN", "PAN", "PAN number (e.g. ABCDE1234F)", "Personal", true, true),
    PASSPORT("Passport", "Passport", "PASSPORT", "Passport number (e.g. A1234567)", "Personal", true, true),
    DRIVING_LICENCE("Driving Licence", "Driving Licence", "DL", "Licence number (e.g. MH12 20110012345)", "Personal", true, true),
    VOTER_ID("Voter ID", "Voter ID", "EPIC", "EPIC number (e.g. ABC1234567)", "Personal", true, true),
    ELECTRICITY_BILL("Electricity / Light Bill", "Light Bill", "BILL", "Consumer number", "Home"),
    AFFIDAVIT("Affidavit", "Affidavit", "AFFIDAVIT", "Document number", "Personal"),
    RESUME("Resume / CV", "Resume", "CV", "Document number", "Work"),
    BANK("Bank Document", "Bank", "BANK", "Account / reference number", "Finance"),
    INSURANCE("Insurance Policy", "Insurance", "POLICY", "Policy number", "Insurance"),
    EDUCATION("Education Certificate", "Education", "EDU", "Certificate / roll number", "Education"),
    OTHER("Other Document", "Other", "DOC", "Document number", null);

    companion object {
        fun fromLabel(text: String): DocKind? {
            val t = text.trim()
            if (t.isEmpty()) return null
            return entries.firstOrNull { it.label.equals(t, ignoreCase = true) }
        }

        /**
         * Works out what kind of document this is. Order: an exact stored type label,
         * then keywords in the type/name, then (for saved identity numbers) the number
         * format itself. Falls back to [OTHER].
         */
        fun detect(name: String, type: String = "", number: String = ""): DocKind {
            fromLabel(type)?.let { return it }
            val text = "$type $name".replace('_', ' ').replace('-', ' ')
            KIND_RULES.firstOrNull { (_, rx) -> rx.containsMatchIn(text) }?.let { return it.first }
            if (number.isNotBlank()) {
                if (DocumentValidator.checkNumber(PAN, number).state == NumberState.VALID) return PAN
                // Aadhaar by number alone only when it is in the grouped "1234 5678 9012" form,
                // because roughly 1 in 10 random 12-digit numbers would otherwise pass the checksum.
                if (GROUPED_AADHAAR.matches(number.trim()) &&
                    DocumentValidator.checkNumber(AADHAAR, number).state == NumberState.VALID
                ) return AADHAAR
            }
            return OTHER
        }
    }
}

private val GROUPED_AADHAAR = Regex("^[2-9][0-9]{3} [0-9]{4} [0-9]{4}$")

private fun kw(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

private val KIND_RULES: List<Pair<DocKind, Regex>> = listOf(
    DocKind.AADHAAR to kw("aadhaar|aadhar|adhaar|adhar|uidai"),
    DocKind.PAN to kw("\\bpan\\b|pancard"),
    DocKind.PASSPORT to kw("passport"),
    DocKind.DRIVING_LICENCE to kw("driving|driver|\\bdl\\b"),
    DocKind.VOTER_ID to kw("voter|\\bepic\\b"),
    DocKind.ELECTRICITY_BILL to kw("electricity|electric|light bill|lightbill|power bill|\\bmseb\\b|msedcl|bijli"),
    DocKind.AFFIDAVIT to kw("affidavit|notary"),
    DocKind.RESUME to kw("resume|\\bcv\\b|curriculum"),
    DocKind.BANK to kw("bank|passbook|cheque|ifsc"),
    DocKind.INSURANCE to kw("insurance|policy|\\blic\\b"),
    DocKind.EDUCATION to kw("degree|marksheet|mark sheet|diploma|university|college|school|\\bssc\\b|\\bhsc\\b")
)

enum class NumberState { EMPTY, VALID, INVALID, NOT_CHECKED }

data class NumberCheck(val state: NumberState, val message: String = "")

/**
 * Offline FORMAT validation of identity numbers. This does not contact UIDAI, the
 * Income Tax Department, etc. - it can only catch typos and wrong formats, so the UI
 * must never describe a number as "verified".
 */
object DocumentValidator {

    private val PAN_REGEX = Regex("^[A-Z]{3}[ABCFGHLJPTK][A-Z][0-9]{4}[A-Z]$")
    private val PASSPORT_REGEX = Regex("^[A-PR-WY][1-9][0-9]{5}[1-9]$")
    private val DL_REGEX = Regex("^[A-Z]{2}[0-9]{2}[0-9A-Z]{6,12}$")
    private val VOTER_REGEX = Regex("^[A-Z]{3}[0-9]{7}$")

    /** Strips separators / fixes case so numbers compare and validate consistently. */
    fun normalizeNumber(kind: DocKind, raw: String): String = when (kind) {
        DocKind.AADHAAR -> raw.filter { !it.isWhitespace() && it != '-' }
        DocKind.PAN, DocKind.PASSPORT, DocKind.VOTER_ID ->
            raw.filter { !it.isWhitespace() }.uppercase()
        DocKind.DRIVING_LICENCE ->
            raw.filter { !it.isWhitespace() && it != '-' && it != '/' }.uppercase()
        else -> raw.trim()
    }

    /** Canonical text to store: Aadhaar as "1234 5678 9012", other IDs upper-cased. */
    fun formatNumber(kind: DocKind, raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return when (kind) {
            DocKind.AADHAAR -> {
                val digits = normalizeNumber(kind, trimmed)
                if (digits.length == 12 && digits.all { it.isDigit() }) digits.chunked(4).joinToString(" ") else trimmed
            }
            DocKind.PAN, DocKind.PASSPORT, DocKind.VOTER_ID, DocKind.DRIVING_LICENCE -> trimmed.uppercase()
            else -> trimmed
        }
    }

    fun checkNumber(kind: DocKind, raw: String): NumberCheck {
        if (raw.isBlank()) return NumberCheck(NumberState.EMPTY)
        val n = normalizeNumber(kind, raw)
        return when (kind) {
            DocKind.AADHAAR -> checkAadhaar(n)
            DocKind.PAN ->
                if (PAN_REGEX.matches(n)) valid()
                else invalid("PAN must be 10 characters like ABCDE1234F (5 letters, 4 digits, 1 letter)")
            DocKind.PASSPORT ->
                if (PASSPORT_REGEX.matches(n)) valid()
                else invalid("Passport number must be 1 letter followed by 7 digits, like A1234567")
            DocKind.DRIVING_LICENCE ->
                if (DL_REGEX.matches(n)) valid()
                else invalid("Licence number should start with the 2-letter state code and RTO number, like MH12 20110012345")
            DocKind.VOTER_ID ->
                if (VOTER_REGEX.matches(n)) valid()
                else invalid("Voter ID (EPIC) must be 3 letters followed by 7 digits, like ABC1234567")
            else -> NumberCheck(NumberState.NOT_CHECKED)
        }
    }

    private fun valid() = NumberCheck(NumberState.VALID, "Format looks valid")
    private fun invalid(msg: String) = NumberCheck(NumberState.INVALID, msg)

    private fun checkAadhaar(digits: String): NumberCheck {
        if (!digits.all { it in '0'..'9' }) return invalid("Aadhaar number must contain digits only")
        if (digits.length != 12) return invalid("Aadhaar number must be exactly 12 digits (you entered ${digits.length})")
        if (digits[0] == '0' || digits[0] == '1') return invalid("Aadhaar number cannot start with 0 or 1")
        if (!verhoeffValid(digits)) return invalid("This Aadhaar number looks incorrect (checksum failed). Please re-check the digits")
        return valid()
    }

    private val VERHOEFF_D = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
        intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
        intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
        intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
        intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
    )

    private val VERHOEFF_P = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
        intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
        intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
    )

    /** Verhoeff checksum, the scheme used by the last digit of an Aadhaar number. */
    fun verhoeffValid(digits: String): Boolean {
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return false
        var c = 0
        val reversed = digits.reversed()
        for (i in reversed.indices) {
            c = VERHOEFF_D[c][VERHOEFF_P[i % 8][reversed[i] - '0']]
        }
        return c == 0
    }

    private val PAN_IN_TEXT = Regex("\\b[A-Z]{3}[ABCFGHLJPTK][A-Z][0-9]{4}[A-Z]\\b")
    private val AADHAAR_IN_TEXT = Regex("\\b[2-9][0-9]{3}[ -]?[0-9]{4}[ -]?[0-9]{4}\\b")

    /** Looks for a PAN or a checksum-valid Aadhaar number inside free text (file names, text files). */
    fun findKnownNumber(text: String): String? {
        PAN_IN_TEXT.find(text.uppercase())?.let { return it.value }
        for (m in AADHAAR_IN_TEXT.findAll(text)) {
            val digits = m.value.filter { it.isDigit() }
            if (verhoeffValid(digits)) return digits.chunked(4).joinToString(" ")
        }
        return null
    }
}
