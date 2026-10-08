package com.cinemawatch.domain

import app.fieldwatch.domain.SignatureClass
import java.util.Locale

/** These are hypotheses about radio evidence, not verified devices or people. */
enum class SignalGroup { ACCESS_POINT, IPHONE, APPLE, HUAWEI, XIAOMI, COMPUTER, PHONE, AUDIO, WEARABLE, IOT, UNKNOWN }
enum class GuessEvidence { RADIO_TYPE, ADVERTISED_NAME, COMPANY_ID, OUI, SIGNATURE, NONE }
data class SignalGuess(val group: SignalGroup, val evidence: GuessEvidence, val confidence: String)

object SignalGrouping {
    fun guess(kind: String, name: String, vendor: String?, company: String?, signature: SignatureClass?): SignalGuess {
        val n = name.lowercase(Locale.ROOT)
        val brand = listOfNotNull(company, vendor).joinToString(" ").lowercase(Locale.ROOT)
        val evidence = if (company != null) GuessEvidence.COMPANY_ID else GuessEvidence.OUI
        if (Regex("(^|[^a-z])iphone([^a-z]|$)").containsMatchIn(n)) return SignalGuess(SignalGroup.IPHONE, GuessEvidence.ADVERTISED_NAME, "LOW")
        if (Regex("macbook|imac|thinkpad|latitude|desktop[- ]|laptop[- ]|computer|电脑|電腦").containsMatchIn(n)) return SignalGuess(SignalGroup.COMPUTER, GuessEvidence.ADVERTISED_NAME, "LOW")
        if ("huawei" in brand || "华为" in brand || "華為" in brand) return SignalGuess(SignalGroup.HUAWEI, evidence, "MEDIUM")
        if ("xiaomi" in brand || "小米" in brand) return SignalGuess(SignalGroup.XIAOMI, evidence, "MEDIUM")
        if ("apple" in brand) return SignalGuess(SignalGroup.APPLE, evidence, "MEDIUM")
        if (Regex("huawei|华为|華為|honor|荣耀|榮耀").containsMatchIn(n)) return SignalGuess(SignalGroup.HUAWEI, GuessEvidence.ADVERTISED_NAME, "LOW")
        if (Regex("xiaomi|redmi|poco|小米|红米|紅米").containsMatchIn(n)) return SignalGuess(SignalGroup.XIAOMI, GuessEvidence.ADVERTISED_NAME, "LOW")
        val category = when (signature) {
            SignatureClass.PHONE -> SignalGroup.PHONE
            SignatureClass.AUDIO -> SignalGroup.AUDIO
            SignatureClass.WEARABLE, SignatureClass.GLASSES -> SignalGroup.WEARABLE
            SignatureClass.BEACON, SignatureClass.THERMOSTAT, SignatureClass.HOME, SignatureClass.LOCK -> SignalGroup.IOT
            else -> null
        }
        // Catalog rules may be name-only; never represent a match as verified identity.
        if (category != null) return SignalGuess(category, GuessEvidence.SIGNATURE, "LOW")
        if (kind == "WIFI") return SignalGuess(SignalGroup.ACCESS_POINT, GuessEvidence.RADIO_TYPE, "MEDIUM")
        return SignalGuess(SignalGroup.UNKNOWN, GuessEvidence.NONE, "UNKNOWN")
    }
}
