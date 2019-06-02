package logic

import dao.edge.ElementE
import dao.edge.ElementE.PON.*
import dao.vertex.ElementV
import dao.vertex.RefV
import dao.vertex.V
import logic.Alignment.Entry.Type.*
import java.lang.IllegalStateException

class Alignment(val r1: RefV, val r2: RefV, alignedEs : List<Entry>, onlyR1s : List<ElementE>, onlyR2s : List<ElementE>) {
    val entries = mutableListOf<Entry>()

    init {


    }

    fun add(e: Entry) { entries.add(e) }

    fun transformPONs() {

    }

    fun isConsistent(pons: List<ElementE.PON>): Boolean {
        require(pons.size == entries.size) { "$pons and [entries] should has the same size." }
        val ponToTypes = entries.groupBy { it.pon }.mapValues { it.value.map { e -> e.matchedType } }

        ponToTypes[UNKNOWN]?.let { return false }
        ponToTypes[LASTNAME]?.forEach { if (it != EQUAL_OR_SIMILAR) return false }
        ponToTypes[FIRSTNAME]?.none { it == E1_OMITTED || it == E2_OMITTED }?.let { if(!it) return false }
        ponToTypes[MIDDLENAME]?.containsAll(listOf(E1_OMITTED, E2_OMITTED))?.let { if(it) return false }
        ponToTypes[SUFFIX]?.containsAll(listOf(E1_OMITTED, E2_OMITTED))?.let { if(it) return false }
        ponToTypes[PREFIX]?.containsAll(listOf(E1_OMITTED, E2_OMITTED))?.let { if(it) return false }
        return true
    }

    fun inferRelation(){
//        check(isConsistent) { "$r1 and $r2 should be consistent." }
    }


    class Entry(
            val e1: ElementE?,
            val e2: ElementE?,
            val matchedV: ElementV?,
            var order: Int = 0
            ) {
        val matchedType: Type = inferType()
        val pon: ElementE.PON = inferPON()

        private fun inferType(): Type {
            return when {
                e1 == null && e2 != null -> E1_OMITTED
                e1 != null && e2 == null -> E2_OMITTED
                e1 != null && e2 != null && matchedV != null -> when {
                    matchedV.type.level in 1..2 -> EQUAL_OR_SIMILAR
                    matchedV.type.level == 3 -> if (e1.isAbbr) E1_ABBREVIATED else E2_ABBREVIATED
                    else -> throw IllegalStateException("$e1 and $e2 is matched in an illegal level.")
                }
                else -> throw IllegalStateException("illegal alignment of $e1 and $e2.")
            }

        }

        private fun inferPON(): ElementE.PON {
            return when {
                e1 != null && e2 != null -> if(e1.pon == e2.pon) e1.pon else UNKNOWN
                e1 == null && e2 != null -> e2.pon
                e1 != null && e2 == null -> e1.pon
                else -> throw IllegalStateException("$e1 and $e2 should not be null at the same time.")
            }
        }

//        private fun inferOrder(): Int {
//            e1?.inV?.elementEs?.filter { it. }
//        }

        enum class Type (val degree: Int) {
            E1_OMITTED(-2),
            E1_ABBREVIATED(-1),
            EQUAL_OR_SIMILAR(0),
            E2_ABBREVIATED(+1),
            E2_OMITTED(+2);
        }
    }
}