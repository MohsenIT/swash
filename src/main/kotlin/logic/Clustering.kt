package logic

import com.koloboke.collect.map.hash.HashObjObjMaps
import dao.vertex.ElementV
import dao.vertex.RefV
import dao.vertex.V

class Clustering {


    fun align(r1: RefV, r2: RefV) : Alignment{
        var r1Map = r1.elementEs.groupBy { it.outV }.mapValues { it.value.toMutableSet() }.toMutableMap()
        var r2Map = r2.elementEs.groupBy { it.outV }.mapValues { it.value.toMutableSet() }.toMutableMap()

        val aligned = mutableListOf<Alignment.Entry>()
        for (i in 1..V.Type.MAX_LEVEL) {
            r1Map = r1Map.outElementVsAtLeast(i)
            r2Map = r2Map.outElementVsAtLeast(i)

            val joinedMap = r1Map.joinByKey(r2Map)
            val matchedVs = joinedMap.filter { it.second != null && it.third != null }
            for ((v, e1, e2) in matchedVs) {
                // TODO: 2019-06-01 align with different pon and transform later
                if (e1?.pon != e2?.pon) continue
                if (i > 1 && e1!!.isAbbr && e2!!.isAbbr) continue // Abbreviations is matched In Non-Token Level
                if (i == 3 && !e1!!.isAbbr && !e2!!.isAbbr) continue // Non-Abbreviations is matched In Abbreviations Level

                aligned.add(Alignment.Entry(e1, e2, v))
                if(r1Map[v]!!.size > 1) r1Map[v]!!.remove(e1) else r1Map.remove(v)
                if(r2Map[v]!!.size > 1) r2Map[v]!!.remove(e2) else r2Map.remove(v)
            }
        }
        return Alignment(r1, r2, aligned, r1Map.values.flatten(), r2Map.values.flatten())
    }


}

private fun <K, V> MutableMap<K, MutableSet<V>>.removeFromValue(key: K, value: V?): MutableMap<K, MutableSet<V>> {
    val collection = this[key] ?: return this
    if(collection.remove(value) && collection.isEmpty()) this.remove(key)
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
        for (v1 in this[k]?: listOf(null))
            for (v2 in rightMap[k]?: listOf(null)) res.add(Triple(k, v1, v2))
    return res
}


fun <T, S> Collection<T>.cartesianProduct(other: Iterable<S>): List<Pair<T, S>> {
    return flatMap { first -> other.map { second -> Pair(first,  second) } }
}

/**
 * traverse out vertices until all of them at least reached to `minLevel`.
 * if the level of a vertex is greater than `minLevel`, this vertex does not traversed.
 *
 * @param minLevel minimum level of output `ElementV`s.
 * @param <T> A generic type of my parameter
 * @return set of vertices in the minLevel
</T> */
fun <T> MutableMap<ElementV, MutableSet<T>>.outElementVsAtLeast(minLevel: Int): MutableMap<ElementV, MutableSet<T>> {
    val resultMap: MutableMap<ElementV, MutableSet<T>> = HashObjObjMaps.newMutableMap(this.size)
    for ((key, value) in this) {
        var currentLevel = key.type.level
        if (currentLevel >= minLevel)
            resultMap.put(key, value)
        else {
            var vs = setOf(key)
            while (currentLevel++ < minLevel) {
                vs = vs.flatMap { it.getOutNextLevelV() }.map { it as ElementV }.toSet()
            }
            for (v in vs) {
                if (resultMap.containsKey(v))
                    resultMap[v]?.addAll(value)
                else
                    resultMap[v] = value
            }
        }
    }
    return resultMap
}

