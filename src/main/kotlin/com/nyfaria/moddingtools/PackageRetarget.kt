package com.nyfaria.moddingtools

object PackageRetarget {

    fun apply(text: String, oldPackage: String, newPackage: String): String {
        if (oldPackage.isBlank() || oldPackage == newPackage) return text
        if (!text.contains(oldPackage)) return text

        if (!newPackage.startsWith("$oldPackage.")) {
            return text.replace(oldPackage, newPackage)
        }

        val tail = newPackage.removePrefix(oldPackage)
        val alreadyMigrated = Regex(Regex.escape(oldPackage) + "(?=" + Regex.escape(tail) + ")")
        val pending = Regex(Regex.escape(oldPackage) + "(?!" + Regex.escape(tail) + ")")
        return if (alreadyMigrated.containsMatchIn(text) && !pending.containsMatchIn(text)) {
            text
        } else {
            pending.replace(text, newPackage)
        }
    }

    fun occurrence(oldPackage: String, newPackage: String): String {
        val base = Regex.escape(oldPackage)
        if (!newPackage.startsWith("$oldPackage.")) return base
        return base + "(?!" + Regex.escape(newPackage.removePrefix(oldPackage)) + ")"
    }

    fun repair(text: String, newPackage: String): String {
        val segment = newPackage.substringAfterLast('.')
        if (segment.isEmpty()) return text
        val repeated = Regex("(?:" + Regex.escape(".$segment") + "){2,}")
        return repeated.replace(text, ".$segment")
    }
}
