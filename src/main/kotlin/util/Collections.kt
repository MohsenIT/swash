package util

import com.koloboke.collect.map.hash.HashObjObjMaps
import dao.vertex.ElementV

fun <K, V> MutableMap<K, MutableList<V>>.putOrAddListValue(k: K, v: V) {
    if (this.containsKey(k))
        this[k]?.add(v)
    else {
        this[k] = mutableListOf(v)
    }
}

fun <T> Collection<T>.partitionToPairs(predicate: (T) -> Boolean): List<Pair<T, T>> {
    val (first, second) = this.partition { predicate(it) }
    val result = mutableListOf<Pair<T,T>>()
    first.forEach { f -> second.forEach { s -> result.add(Pair(f, s)) } }
    return result
}


/**
 * Splits the original collection into pair of lists,
 * where *first* list contains elements for which [predicate] yielded `true`,
 * while *second* list contains elements for which [predicate] yielded `false`.
 */
inline fun <T> Iterable<T>.partition(predicate: (T) -> Boolean): Pair<List<T>, List<T>> {
    val first = ArrayList<T>()
    val second = ArrayList<T>()
    for (element in this) {
        if (predicate(element)) {
            first.add(element)
        } else {
            second.add(element)
        }
    }
    return Pair(first, second)
}


private fun <K, V> MutableMap<K, MutableSet<V>>.removeFromValue(key: K, value: V?): MutableMap<K, MutableSet<V>> {
    val collection = this[key] ?: return this
    if (collection.remove(value) && collection.isEmpty()) this.remove(key)
    return this
}

private inline fun <K : Any, V : Any> List<K>.joinByKey(collection: List<V>, filter: (Pair<K, V>) -> Boolean): List<Pair<K, List<V>>> = map { t ->
    val filtered = collection.filter { filter(Pair(t, it)) }
    Pair(t, filtered)
}

private fun <K : Any, V : Any> Map<K, V>.joinByKey(rightMap: Map<K, V>): Map<K, Pair<V?, V?>> =
        keys.union(rightMap.keys).map { it to Pair(this[it], rightMap[it]) }.toMap()

private fun <K : Any, V1 : Any, V2 : Any> Map<K, Collection<V1>>.joinByKey(rightMap: Map<K, Collection<V2>>): MutableList<Triple<K, V1?, V2?>> {
    val res = mutableListOf<Triple<K, V1?, V2?>>()
    for (k in keys.union(rightMap.keys))
        for (v1 in this[k] ?: listOf(null))
            for (v2 in rightMap[k] ?: listOf(null)) res.add(Triple(k, v1, v2))
    return res
}

fun <T, S> Collection<T>.cartesianProduct(other: Iterable<S>): List<Pair<T, S>> =
        flatMap { first -> other.map { second -> Pair(first, second) } }

