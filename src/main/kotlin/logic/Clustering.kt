package logic

import dao.G
import dao.edge.E
import dao.edge.ElementE
import dao.vertex.RefV
import java.util.*

class Clustering(private val g: G) {

    fun clusterCandidates(candidates: Map<RefV, List<MessagePassing.Candidate>>) {
        candidates.values.flatten().forEach { g.addE(it.destRefV, it.originRefV, E.Type.REF_REF, it.sumSimilarity) }

        val isVisited = prioritizeRefVs()
        for (notVisitedRefV in isVisited.keys) {
            if (isVisited[notVisitedRefV]!!)
                continue
            val queue = LinkedList(listOf(notVisitedRefV))
            isVisited[notVisitedRefV] = true
            while (queue.isNotEmpty()) {
                val ref = queue.remove()!!
                for (adj in ref.getAdjacentCandidates().filterNot { isVisited[it]!! }) {
                    val alignment = ref.align(adj)
                    if (!alignment.isConsistent && alignment.canTransformBeUseful) {
                        alignment.transformPONs() // TODO: 2019-06-03 should be implemented.
                    }
                    if (alignment.isConsistent) {
                        queue.add(adj)
                        isVisited[adj] = true
                        //adj.replaceReferenceCluster(r, g)
                        alignment.addToHierarchy(g)
                    }
                }
            }
        }
        //g.updateAncestorClusterCnt()
    }


    private fun prioritizeRefVs(): MutableMap<RefV, Boolean> {
        return g.getRefVs().filter { it.hasInOutE(E.Type.REF_REF) }
                .sortedWith(compareBy<RefV> { it.elementCount }
                        .thenBy { it.elementEs.filter(ElementE::isAbbr).count() }
                        .thenByDescending { it.weight })
                .associateWith { false }.toMutableMap()
    }
}


