package util

object Strings {
    fun isAbbreviated(tokenLength: Int): Boolean = tokenLength == 1

    fun isBeforeDot(name: String, tokenOrder: Int): Boolean {
        val splits = name.split("[^\\W']+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return splits.size > tokenOrder + 1 && splits[tokenOrder + 1].startsWith(".")
    }
}
