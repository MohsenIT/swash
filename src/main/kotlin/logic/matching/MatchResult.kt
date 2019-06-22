package logic.matching

import dao.edge.ElementE
import dao.vertex.ElementV

import java.util.ArrayList

import dao.edge.ElementE.PON.*

class MatchResult(var clusterProfile: ClusterProfile, tokenEs: List<ElementE>) {

    val sortedTokenEs: List<ElementE> = tokenEs.sortedBy { it.order }
    var matchedEntries: MutableList<Matched> = ArrayList(3)

    fun addMatchedEntries(matched: Matched) {
        this.matchedEntries.add(matched)
    }

    /**
     * Gets cluster profile entries that is not isMatched with RefV.
     *
     * @return List of `ClusterProfile.Entry`
     */
    val notMatchedProfileEntries: List<ClusterProfile.Entry>
        get() = clusterProfile.entries.filter { e -> matchedEntries.none{ it.profileEntry == e } }

    /**
     * Gets RefV tokenEs that is not isMatched with the cluster profile.
     *
     * @return List of TokenEs that does not isMatched with profile entries.
     */
    val notMatchedTokenEs: List<ElementE>
        get() = sortedTokenEs.filter { e -> matchedEntries.none { m -> m.refTokenE == e } }

    fun setTokenEsNamesPart(pons: Array<ElementE.PON>) {
        var i = 0
        while (i < sortedTokenEs.size) sortedTokenEs[i].pon = pons[i++]
    }

    /**
     * @return an array of `PON`s from tokenEs that Shifted to Left.
     */
    private val shiftedLeftPONs: Array<ElementE.PON>?
        get() {
            val sortedTokenEsSize = sortedTokenEs.size
            val pons = Array(sortedTokenEsSize) { UNKNOWN }
            var isShiftStarted = false
            var i = 0
            while (i < sortedTokenEsSize) {
                val pon: ElementE.PON = sortedTokenEs[i].pon
                if (pon === MIDDLENAME && sortedTokenEs[i + 1].pon === LASTNAME)
                    isShiftStarted = true
                pons[i] = if (isShiftStarted) pon.nextRankedPON() else pon
                i++
            }
            return if (isShiftStarted) pons else null
        }

    /**
     * @return an array of `PON`s from tokenEs that Shifted to Right.
     */
    val shiftedRightPONs: Array<ElementE.PON>?
        get() {
            val pons = Array(sortedTokenEs.size) { UNKNOWN }
            var isShiftStarted = false
            for (i in sortedTokenEs.indices.reversed()) {
                val part = sortedTokenEs[i].pon
                if (part === MIDDLENAME && sortedTokenEs[i - 1].pon === FIRSTNAME)
                    isShiftStarted = true
                pons[i] = if (isShiftStarted) part.previousRankedPON() else part
            }
            return if (isShiftStarted) pons else null
        }

    /**
     * @return an array of `PON`s from tokenEs that Shifted to Right.
     */
    private val reversedFirstnameAndLastname: Array<ElementE.PON>?
        get() {
            val pons = Array(sortedTokenEs.size) { UNKNOWN }
            var hasLastname = false
            var hasFirstname = false
            var i = 0
            val sortedTokenEsSize = sortedTokenEs.size
            while (i < sortedTokenEsSize) {
                val part = sortedTokenEs[i].pon
                if (part === LASTNAME) {
                    pons[i] = FIRSTNAME
                    hasLastname = true
                }
                if (part === FIRSTNAME) {
                    pons[i] = LASTNAME
                    hasFirstname = true
                }
                i++
            }
            return if (hasLastname && hasFirstname) pons else null
        }

    //region Methods

    /**
     * check if the refV is consistent with the profile or not?
     *
     * @return a boolean that is false if not consistent and true otherwise.
     */
    val isConsistent: Boolean
        get() = isConsistent(this.sortedTokenEs.map{ it.pon }.toTypedArray())

    /**
     * check if the result shows that according to this result, the refV is consistent with profile or not?
     *
     * @return a boolean that is false if not consistent and true otherwise.
     */
    private fun isConsistent(pons: Array<ElementE.PON>): Boolean {
        for (profileEntry in clusterProfile.entries) {
            val matched = matchedEntries.filter { it.profileEntry === profileEntry }.sortedWith(
                    compareBy<Matched> { it.matchedV.type.layer } // 1) isMatched in lower layer has more priority
                            .thenBy { it.profileEntry.pon !== pons[it.refTokenE.order] } // 2) if a pon matches with multiple tokens, it consider the one that has the same pon
            ).firstOrNull()

            if (matched == null) {
                if (profileEntry.pon === LASTNAME || profileEntry.pon === FIRSTNAME)
                    return false
                if (notMatchedTokenEs.filter { pons[it.order] === profileEntry.pon }.count() > 0)
                    return false // if refV has any token with the same PON, it must be isMatched.
            } else if (matched.hasEqualPON(pons)) {
                if (profileEntry.pon === LASTNAME) {
                    if (matched.matchedV.type.layer > 2) return false
                } else { // TODO: 26/08/2018 it is possible that two middle names are matched that one is abbr and other not
                    if (matched.isNonAbbrsMatchedInAbbrLayer)
                        return false
                }
            } else {
                // TODO: 07/08/2018 update token types if increase consensus and cluster again
                if (profileEntry.pon === LASTNAME || profileEntry.pon === FIRSTNAME)
                    return false
            }
        }
        return true
    }

    fun canBecomeConsistent(): Boolean {
        val shiftedLeftPONs = this.shiftedLeftPONs
        var isConsistent = shiftedLeftPONs != null && this.isConsistent(shiftedLeftPONs)
        if (isConsistent) {
            this.setTokenEsNamesPart(shiftedLeftPONs!!)
            return true
        }
        //        TokenE.PON[] shiftedRightPONs = this.getShiftedRightPONs();
        //        isConsistent = shiftedRightPONs != null && this.isConsistent(shiftedRightPONs);
        //        if (isConsistent) {
        //            this.setTokenEsNamesPart(shiftedRightPONs);
        //            return true;
        //        }
        val reversed = this.reversedFirstnameAndLastname
        isConsistent = reversed != null && this.isConsistent(reversed)
        if (isConsistent) {
            this.setTokenEsNamesPart(reversed!!)
            return true
        }
        return false
    }

    //endregion

    class Matched(var profileEntry: ClusterProfile.Entry, var refTokenE: ElementE, var matchedV: ElementV) : Cloneable {

        /**
         * @param parts array of PON that should be used as PON of `refTokenE`
         * @return true if `profileEntry` and `refTokenE` has equal name parts.
         */
        fun hasEqualPON(parts: Array<ElementE.PON>): Boolean {
            return  parts.size > refTokenE.order && profileEntry.pon === parts[refTokenE.order]
        }

        /**
         * If a reference token and a clusterProfile entry are both Non-Abbreviated,
         * they should be isMatched in first layer (token as is) or at second layer (similar tokens).
         *
         * @return a boolean that Is the reference token and clusterProfile entry
         * are both non-abbreviated and isMatched in abbreviated layer?
         */
        val isNonAbbrsMatchedInAbbrLayer: Boolean
            get() = matchedV.type.layer == 3 && !refTokenE.isAbbr&& !profileEntry.isAbbr
                    && !refTokenE.isBeforeDot && !profileEntry.isBeforeDot

        /**
         * If a reference token and a clusterProfile entry are both Abbreviated,
         * they should be isMatched in first layer (token as is).
         *
         * @return a boolean that Is the reference token and clusterProfile entry
         * are both abbreviated and is not isMatched in token layer?
         */
        val isAbbrsMatchedInNonTokenLayer: Boolean
            get() =  matchedV.type.layer > 1 && refTokenE.isAbbr && profileEntry.isAbbr

        /**
         * Check if profile entry is abbreviated, while isMatched refV tokenE's is not?
         *
         * @return a boolean value, `true` if the above condition exist and `false` otherwise
         */
        val isProfileAbbrAndRefNonAbbr: Boolean
            get() = profileEntry.isAbbr && !refTokenE.isAbbr


        override fun toString(): String = String.format("Result.Matched{profEntry={%s: %s}, refTokenE=%s, matchedV=%s}"
                , profileEntry.pon, profileEntry.elementV, refTokenE, matchedV)

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is Matched) return false
            val e = other as Matched?
            return this.matchedV == e!!.matchedV && this.profileEntry === e.profileEntry && this.refTokenE === e.refTokenE
        }

        override fun hashCode(): Int {
            var result = profileEntry.hashCode()
            result = 31 * result + refTokenE.hashCode()
            result = 31 * result + matchedV.hashCode()
            return result
        }
    }
}
