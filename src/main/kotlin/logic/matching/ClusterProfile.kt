package logic.matching

import com.koloboke.collect.map.hash.HashObjObjMaps
import dao.edge.TokenE
import dao.edge.TokenE.PON
import dao.vertex.ElementV
import dao.vertex.RefV
import dao.vertex.V

class ClusterProfile {

    //region Fields
    internal var entries: MutableList<Entry> = ArrayList(4)

    fun addEntry(entry: Entry) { this.entries.add(entry) }

    fun addEntry(entry: Entry, index: Int) {
        entry.order = index
        this.entries.filter { it.order >= index }.forEach { it.incOrder() }
        this.entries.add(index, entry)
    }
    //endregion

    //region Methods
    fun align(refV: RefV): MatchResult {
        var refMap = refV.tokenEs.groupBy { it.outV }.mapValues { it.value.toMutableSet() }.toMutableMap()
        var profileMap = entries.groupBy { it.elementV }.mapValues { it.value.toMutableSet() }.toMutableMap()

        val result = MatchResult(this, refV.tokenEs)
        for (i in 1..V.Type.MAX_LEVEL) {
            refMap = refMap.outElementVsAtLeast(i)
            profileMap = profileMap.outElementVsAtLeast(i)

            val matchedEntriesToRemove = ArrayList<MatchResult.Matched>()
            for ((key, value) in refMap) {
                if (profileMap.containsKey(key))
                    profileMap[key]?.forEach { entry ->
                        value.forEach { tokenE ->
                            val matched = MatchResult.Matched(entry, tokenE, key)
                            if (!matched.isNonAbbrsMatchedInAbbrLevel && !matched.isAbbrsMatchedInNonTokenLevel) {
                                result.addMatchedEntries(matched)
                                if (entry.pon === tokenE.pon)
                                // If not, may be isMatched in upper level
                                    matchedEntriesToRemove.add(matched)
                            }
                        }
                    }
            }
            // elementVs is removed out of above loops to does not change Maps during iteration
            // if an entry contains more than 1 element on its set, remove only the same element (not whole entry)
            for (me in matchedEntriesToRemove) {
                val tokenEs: MutableSet<TokenE>? = refMap[me.matchedV]
                if (tokenEs != null && tokenEs.size > 1) tokenEs.remove(me.refTokenE) else refMap.remove(me.matchedV)
                val profEs = profileMap[me.matchedV]
                if (profEs != null && profEs.size > 1) profEs.remove(me.profileEntry) else profileMap.remove(me.matchedV)
            }
        }
        return result
    }

    /**
     * traverse out vertices until all of them at least reached to `minLevel`.
     * if the level of a vertex is greater than `minLevel`, this vertex does not traversed.
     *
     * @param minLevel minimum level of output `ElementV`s.
     * @param <T> A generic type of my parameter
     * @return set of vertices in the minLevel
    </T> */
    private fun <T> MutableMap<ElementV, MutableSet<T>>.outElementVsAtLeast(minLevel: Int): MutableMap<ElementV, MutableSet<T>> {
        val resultMap: MutableMap<ElementV, MutableSet<T>> = HashObjObjMaps.newMutableMap(this.size)
        for ((key, value) in this) {
            var currentLevel = key.type.level
            if (currentLevel >= minLevel)
                resultMap.put(key, value)
            else {
                var vs = setOf(key)
                while (currentLevel++ < minLevel) {
                    vs = vs.flatMap { it.getOutNextLevelV() }.map { it as ElementV}.toSet()
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

    fun merge(matchResult: MatchResult) {
        matchResult.notMatchedTokenEs.stream().map { Entry(it) }.forEach { entry ->
            val index = matchResult.matchedEntries.filter { it.refTokenE.order < entry.order }
                    .map { it.profileEntry.order }.max() ?: 0 + 1
            addEntry(entry, index)
        }

        // TODO: 27/08/2018 Is this merge is valid? We should change 'Ali A Raeesi' to 'Ali Akbar Raeesi'?
        matchResult.matchedEntries.filter { it.isProfileAbbrAndRefNonAbbr }
                .forEach { matched -> matched.profileEntry = Entry(matched.refTokenE) }

        // TODO: 27/08/2018 if isMatched on 2nd level?
    }

    override fun toString() = "${entries.size} [entries=${entries.joinToString { it.toString() }}]"
    //endregion


    class Entry (tokenE: TokenE) {
        val elementV: ElementV = tokenE.outV
        val isAbbr: Boolean = tokenE.isAbbr
        val isBeforeDot: Boolean = tokenE.isBeforeDot
        val pon: PON = tokenE.pon
        var order: Int = tokenE.order

        fun incOrder() {
            this.order++
        }

        override fun toString() = "$elementV {${if (isAbbr) "ABBR " else ""}$pon, order=$order}"
    }
}
