package util

fun <K, V> MutableMap<K, MutableList<V>>.putOrAddListValue(k: K, v: V) {
    if (this.containsKey(k))
        this[k]?.add(v)
    else {
        this[k] = mutableListOf(v)}
}