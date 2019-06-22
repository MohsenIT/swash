package dao.vertex

import dao.edge.ElementE
import dao.edge.E.Type.*
import logic.Alignment
import util.partitionToPairs

/**
 * An abstract super class of [RefV] and [HierarchyV] to use in [ElementE] as source vertex
 */
abstract class NameV(id: Long, value: String, type: Type, weight: Long) : V(id, value, type, weight) {
    abstract val elementEs: List<ElementE>
    val isRef = (type == Type.REFERENCE)

    val elementCount: Int
        get() = elementEs.size

    // TODO: 2019-06-17 out of HierarchyV is not necessarily ElementV
    override fun getOutNextLayerV(): Set<ElementV> {
        return super.getOutNextLayerV() as Set<ElementV>
    }

    fun isChildOf(hierarchyV: HierarchyV) = getInE(if (this.isRef) HRC_REF else HRC_HRC).any { it.inV == hierarchyV }

    //region align methods
    fun align(name: NameV): Alignment {
        var rMap = listOf(
                this.elementEs.map { Pair(it.outV, Alignment.Entry(it, null, null)) },
                name.elementEs.map { Pair(it.outV, Alignment.Entry(null, it, null)) }
        ).flatten().groupBy { it.first }.mapValues { it.value.map { p -> p.second }.toMutableSet() }.toMutableMap()

        val aligned = mutableSetOf<Alignment.Entry>() // Set avoids adding a new alignment entry with the same e1 and e2
        for (layer in 1..Type.MAX_LAYER) {
            rMap = if (layer == 1) rMap else rMap.outElementVsTo(layer)
            for ((elementV, aligns) in rMap) {
                for ((a1, a2) in aligns.partitionToPairs { it.e1 != null }) {
                    val a = Alignment.Entry(a1.e1, a2.e2, elementV)
                    if (a.isValidMatch() && !aligned.contains(a)) {
                        aligned += a
                        if (a.pon != ElementE.PON.UNKNOWN) // if UNKNOWN, it can be matched in the upper layers.
                            aligns.removeAll(arrayOf(a1, a2))
                    }
                }
            }
        }
        aligned.removeAll(unknownsMatchedInUpperLayer(aligned)) // TODO: 2019-06-03 what if transforms make them consistent?
        aligned += rMap.values.flatten()
        return Alignment(this, name, aligned.toList())
    }

    private fun unknownsMatchedInUpperLayer(aligned: MutableSet<Alignment.Entry>): List<Alignment.Entry> {
        val (unknowns, knowns) = aligned.partition { it.pon == ElementE.PON.UNKNOWN }
        return unknowns.filter { it.e1 in knowns.map(Alignment.Entry::e1) || it.e2 in knowns.map(Alignment.Entry::e2) }
    }

    /**
     * traverse out [ElementV]s to next layer and return an object similar to the input.
     */
    private fun MutableMap<ElementV, MutableSet<Alignment.Entry>>.outElementVsToNext(): MutableMap<ElementV, MutableSet<Alignment.Entry>> {
        return this.filter { it.value.isNotEmpty() }
                .flatMap { it.key.getOutNextLayerV().map { e -> Pair(e, it.value) } }
                .groupBy { it.first }.mapValues { it.value.flatMap { p -> p.second }.toMutableSet() }.toMutableMap()
    }

    /**
     * traverse out [ElementV]s to next layer if it's layer is decedent of the [layer]
     */
    private fun MutableMap<ElementV, MutableSet<Alignment.Entry>>.outElementVsTo(layer: Int): MutableMap<ElementV, MutableSet<Alignment.Entry>> {
        return this.filter { it.value.isNotEmpty() }
                .flatMap { it.key.getOutToLayer(layer).map { e -> Pair(e, it.value) } }
                .groupBy { it.first }.mapValues { it.value.flatMap { p -> p.second }.toMutableSet() }.toMutableMap()
    }
    //endregion

}