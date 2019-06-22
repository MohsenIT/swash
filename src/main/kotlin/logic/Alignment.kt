package logic

import dao.G
import dao.edge.E.Type.HRC_HRC
import dao.edge.E.Type.HRC_REF
import dao.edge.ElementE
import dao.edge.ElementE.PON.*
import dao.vertex.*
import dao.vertex.HierarchyV.Relation.*
import logic.Alignment.DominanceType.*
import logic.Alignment.Entry.Type.*
import util.inc

class Alignment(val n1: NameV, val n2: NameV, entries: List<Entry>) {
    val entries: List<Entry> = entries.sortedWith(compareBy({ it.pon.rank }, { it.order })) // TODO: 2019-06-02 select correct entries && order
    val canTransformBeUseful get() = entries.any { it.pon == UNKNOWN }
    var dominanceType: DominanceType = NOT_CONSISTENT
    var isConsistent: Boolean = checkConsistency()

    fun transformPONs() {
        // TODO: 2019-06-02 implement this
        isConsistent = checkConsistency()
    }

    fun addToHierarchy(g: G) {
        check(isConsistent) { "$n1 and $n2 should be consistent." }
        check(n1 is RefV && n2 is RefV) { "$n1 and $n2 should be instances of RefV class." }

        var newV = HierarchyV(g.getMaxIdV(), this)
        val vMap: MutableMap<NameV, HierarchyV.Relation> = listOf(n1, n2).associateWith { CHILD }.toMutableMap()
        vMap.putAll((n1.refHierarchyVs + n2.refHierarchyVs).associateWith { NA })

        while (vMap.values.any { it == NA }) {
            for ((v , _) in vMap.filter { it.value == NA }) { v as HierarchyV
                val align = newV.align(v)
                when (align.dominanceType) {
                    NOT_CONSISTENT -> vMap[v] = NONE
                    EQUIVALENT -> { newV = v; vMap[v] = NONE }
                    FIRST_IS_GENERAL -> {
                        for ((child, _) in vMap.filter { it.value == CHILD }) {
                            val childAlign = v.align(child)
                            if (childAlign.dominanceType.isEquOrFirstIsGeneral()) {
                                vMap.replace(child, NONE)
                                g.addE(v, child, if(child.isRef) HRC_REF else HRC_HRC )
                            }
                        }
                        vMap[v] = CHILD
                        vMap.putAll(v.getParents().associateWith { NA })
                    }
                    SECOND_IS_GENERAL -> {
                        vMap[v] = PARENT
                        for (childE in v.getChildEs()) {
                            val child = childE.outV as NameV
                            val childAlign = newV.align(child)
                            if (childAlign.dominanceType.isEquOrFirstIsGeneral()) {
                                vMap.replace(child, NONE)
                                v.removeE(childE).also { child.removeE(childE) }
                                g.addE(newV, child, if(child.isRef) HRC_REF else HRC_HRC )
                            } else if (childAlign.dominanceType == SECOND_IS_GENERAL)
                                vMap[child] = PARENT
                        }
                        vMap.putAll(v.getParents().associateWith { NA })
                    }
                    NEITHER_FIRST_NOR_SECOND -> vMap[v] = NONE
                }
            }
        }
        val parentsAndChildren = vMap.entries.groupBy { it.value }.mapValues { it.value.map { e -> e.key } }
        g.addHierarchyE(newV, parentsAndChildren[PARENT], parentsAndChildren[CHILD])
    }

    private fun checkConsistency(): Boolean {
        val ponToTypes = entries.groupBy { it.pon }.mapValues { it.value.map { e -> e.matchedType } }

        ponToTypes[UNKNOWN]?.let { return false }
        ponToTypes[LASTNAME]?.forEach { if (it.absDegree > 1) return false }
        ponToTypes[FIRSTNAME]?.none { (it == E1_OMITTED && n1.elementCount > 1) || (it == E2_OMITTED && n2.elementCount > 1) }?.let { if (!it) return false }
        ponToTypes[MIDDLENAME]?.containsAll(listOf(E1_OMITTED, E2_OMITTED))?.let { if (it) return false }
        ponToTypes[SUFFIX]?.containsAll(listOf(E1_OMITTED, E2_OMITTED))?.let { if (it) return false }
        ponToTypes[PREFIX]?.containsAll(listOf(E1_OMITTED, E2_OMITTED))?.let { if (it) return false }

        dominanceType = checkDominance()
        return true
    }

    private fun checkDominance(): DominanceType {
        var hasN1Dominated = false
        var hasN2Dominated = false
        entries.map { it.matchedType.degree }.forEach { if (it < 0) hasN1Dominated++ else if (it > 0) hasN2Dominated++ }
        return when (hasN1Dominated) {
            false -> if (hasN2Dominated) SECOND_IS_GENERAL else EQUIVALENT
            true  -> if (hasN2Dominated) NEITHER_FIRST_NOR_SECOND else FIRST_IS_GENERAL
        }
    }

    override fun toString(): String = "Alignment(n1=${n1.value}, n2=${n2.value}, entries=$entries)"

    fun getName() = entries.filter { it.matchedV != null }.joinToString(" ") { it.getElementV().value }


    class Entry(val e1: ElementE?, val e2: ElementE?, val matchedV: ElementV?) {
        val matchedType: Type = inferType()
        val pon: ElementE.PON = inferPON()
        var order: Int = if (e1 != null && e2 != null) 0 else e1?.order ?: e2!!.order

        fun isValidMatch(): Boolean {
            if (e1 == null || e2 == null || matchedV == null) return false
            //if (matchedV.type.layer > 1 && e1.isAbbr && e2.isAbbr) return false // Abbreviations is matched In Non-Token Layer
            if (matchedV.type.layer == 3 && !e1.isAbbr && !e2.isAbbr) return false // Non-Abbreviations is matched In Abbreviations Layer
            return true
        }

        fun getElementV(): ElementV = matchedV ?: e2?.outV ?: e1?.outV!!

        private fun inferType(): Type {
            return when {
                e1 == null && e2 != null -> E1_OMITTED
                e1 != null && e2 == null -> E2_OMITTED
                e1 != null && e2 != null && matchedV != null -> when {
                    e1.outV == e2.outV -> EQUAL
                    matchedV.type.layer == 2 -> if (e2.outV.type == V.Type.TOKEN) E1_SIMILAR else E2_SIMILAR
                    matchedV.type.layer == 3 -> if (e1.outV.type == V.Type.ABBREVIATED) E1_ABBREVIATED else E2_ABBREVIATED
                    else -> throw IllegalStateException("$e1 and $e2 is matched in an illegal layer.")
                }
                else -> throw IllegalStateException("illegal alignment of $e1 and $e2.")
            }

        }

        private fun inferPON(): ElementE.PON {
            return when {
                e1 != null && e2 != null -> if (e1.pon == e2.pon) e1.pon else UNKNOWN
                e1 == null && e2 != null -> e2.pon
                e1 != null && e2 == null -> e1.pon
                else -> throw IllegalStateException("$e1 and $e2 should not be null at the same time.")
            }
        }

        override fun toString(): String = "Entry(e1=${e1?.outV?.value}, e2=${e2?.outV?.value}, matchedV=$matchedV, pon=$pon, order=$order)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Entry
            return (e1 == other.e1) || (e2 == other.e2)
        }

        override fun hashCode(): Int = 31 * (e1?.hashCode() ?: 0) + (e2?.hashCode() ?: 0)


        enum class Type(val degree: Int, val absDegree: Int = Math.abs(degree)) {
            E1_OMITTED(-3),
            E1_ABBREVIATED(-2),
            E1_SIMILAR(-1),
            EQUAL(0),
            E2_SIMILAR(+1),
            E2_ABBREVIATED(+2),
            E2_OMITTED(+3);
        }
    }

    enum class DominanceType {
        NOT_CONSISTENT, EQUIVALENT, FIRST_IS_GENERAL, SECOND_IS_GENERAL, NEITHER_FIRST_NOR_SECOND;

        fun isEquOrFirstIsGeneral() = this == EQUIVALENT || this == FIRST_IS_GENERAL
    }
}

