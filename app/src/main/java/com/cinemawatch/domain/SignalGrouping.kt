package com.cinemawatch.domain

import app.fieldwatch.domain.SignatureClass
import java.util.Locale

/** These are hypotheses about radio evidence, not verified devices or people. */
enum class SignalGroup { ACCESS_POINT, IPHONE, APPLE, HUAWEI, XIAOMI, COMPUTER, PHONE, AUDIO, WEARABLE, IOT, UNKNOWN, HONOR, OPPO, VIVO, REALME, ONEPLUS, DJI, HIKVISION, DAHUA, TP_LINK, ZTE, ACCESS_CONTROL, CAMERA, DRONE, FINDER, GLASSES, HEALTH, MESH, BEACON, SIGNAGE, SURVEILLANCE, PENTEST, BODYWORN, PUBLIC_SAFETY, VEHICLE, THERMOSTAT, OTHER }
enum class GuessEvidence { RADIO_TYPE, ADVERTISED_NAME, COMPANY_ID, OUI, SIGNATURE, NONE }
data class SignalGuess(val group: SignalGroup, val evidence: GuessEvidence, val confidence: String)

object SignalGrouping {
    fun guess(kind: String, name: String, vendor: String?, company: String?, signature: SignatureClass?): SignalGuess {
        val n = name.lowercase(Locale.ROOT)
        if (Regex("(^|[^a-z])iphone([^a-z]|$)").containsMatchIn(n)) return SignalGuess(SignalGroup.IPHONE, GuessEvidence.ADVERTISED_NAME, "LOW")
        if (Regex("macbook|imac|thinkpad|latitude|desktop[- ]|laptop[- ]|computer|电脑|電腦").containsMatchIn(n)) return SignalGuess(SignalGroup.COMPUTER, GuessEvidence.ADVERTISED_NAME, "LOW")
        val ecosystems = listOf(
            SignalGroup.HONOR to "honor|荣耀|榮耀", SignalGroup.HUAWEI to "huawei|华为|華為",
            SignalGroup.XIAOMI to "xiaomi|redmi|poco|小米|红米|紅米|米家|mijia",
            SignalGroup.ONEPLUS to "oneplus|one plus|一加", SignalGroup.REALME to "realme|真我",
            SignalGroup.OPPO to "oppo", SignalGroup.VIVO to "vivo|iqoo",
            SignalGroup.DJI to "dji|大疆", SignalGroup.HIKVISION to "hikvision|海康",
            SignalGroup.DAHUA to "dahua|大华|大華", SignalGroup.TP_LINK to "tp-link|tp link|tplink|tapo|kasa",
            SignalGroup.ZTE to "zte|中兴|中興", SignalGroup.APPLE to "apple"
        )
        ecosystems.forEach { (group, pattern) ->
            val regex = Regex("(?i)(?<![a-z])(?:$pattern)(?![a-z])")
            if (company != null && regex.containsMatchIn(company)) return SignalGuess(group, GuessEvidence.COMPANY_ID, "MEDIUM")
            if (vendor != null && regex.containsMatchIn(vendor)) return SignalGuess(group, GuessEvidence.OUI, "MEDIUM")
        }
        ecosystems.forEach { (group, pattern) ->
            if (Regex("(?i)(?<![a-z])(?:$pattern)(?![a-z])").containsMatchIn(n)) return SignalGuess(group, GuessEvidence.ADVERTISED_NAME, "LOW")
        }
        val category = signature?.let { fromSignature(it) }
        // Catalog rules may be name-only; never represent a match as verified identity.
        if (category != null) return SignalGuess(category, GuessEvidence.SIGNATURE, "LOW")
        if (kind == "WIFI") return SignalGuess(SignalGroup.ACCESS_POINT, GuessEvidence.RADIO_TYPE, "MEDIUM")
        return SignalGuess(SignalGroup.UNKNOWN, GuessEvidence.NONE, "UNKNOWN")
    }

    val ecosystems = listOf(SignalGroup.APPLE, SignalGroup.IPHONE, SignalGroup.HUAWEI, SignalGroup.HONOR, SignalGroup.XIAOMI, SignalGroup.OPPO, SignalGroup.VIVO, SignalGroup.REALME, SignalGroup.ONEPLUS, SignalGroup.DJI, SignalGroup.HIKVISION, SignalGroup.DAHUA, SignalGroup.TP_LINK, SignalGroup.ZTE)
    val deviceTypes = listOf(SignalGroup.ACCESS_CONTROL, SignalGroup.AUDIO, SignalGroup.CAMERA, SignalGroup.DRONE, SignalGroup.FINDER, SignalGroup.GLASSES, SignalGroup.HEALTH, SignalGroup.IOT, SignalGroup.ACCESS_POINT, SignalGroup.MESH, SignalGroup.PHONE, SignalGroup.COMPUTER, SignalGroup.WEARABLE, SignalGroup.BEACON, SignalGroup.SIGNAGE, SignalGroup.SURVEILLANCE, SignalGroup.PENTEST, SignalGroup.BODYWORN, SignalGroup.PUBLIC_SAFETY, SignalGroup.VEHICLE, SignalGroup.THERMOSTAT, SignalGroup.OTHER, SignalGroup.UNKNOWN)
    fun fromSignature(value: SignatureClass): SignalGroup = when (value) {
        SignatureClass.LOCK -> SignalGroup.ACCESS_CONTROL
        SignatureClass.HOME -> SignalGroup.IOT
        SignatureClass.ISP -> SignalGroup.ACCESS_POINT
        SignatureClass.FINDER -> SignalGroup.FINDER
        SignatureClass.HACKING -> SignalGroup.PENTEST
        SignatureClass.LAW_ENFORCEMENT -> SignalGroup.PUBLIC_SAFETY
        else -> SignalGroup.valueOf(value.name)
    }
    fun memberships(kind: String, guess: SignalGuess, signatures: Set<SignatureClass>): Set<SignalGroup> = buildSet {
        addAll(signatures.map(::fromSignature))
        if (guess.group != SignalGroup.UNKNOWN) add(guess.group)
        if (kind == "WIFI") add(SignalGroup.ACCESS_POINT)
        if (none { it in deviceTypes && it != SignalGroup.UNKNOWN }) add(SignalGroup.UNKNOWN)
    }
}
