package evaluation

import dao.G
import dao.edge.E
import dao.vertex.V

class ClustersFScore(val g: G) : AbstractFScore(g) {

    private val resolvedIdCntMap: Map<String, Long> = g.getVs(V.Type.RESOLVED_ID).map { it.value to it.weight }.toMap()

    override fun evaluate() {
        var fp: Long = 0
        var fn: Long = 0
        var tp: Long = 0
        for (clusterV in g.getVs(V.Type.CLUSTER)) {
            val clusterRefVs = clusterV.getOutV(E.Type.CLS_REF)
            val clusterResolvedIdCntMap = clusterRefVs.flatMap { it.getInE(E.Type.RID_REF) }
                    .groupBy { it.inV.value }.mapValues { it.value.map(E::weight).sum().toLong() }

            val clusterRefCnt = clusterResolvedIdCntMap.values.sum()
            for ((key, value) in clusterResolvedIdCntMap) {
                tp += value * (value - 1) / 2
                fn += value * (resolvedIdCntMap[key]!! - value)
                fp += value * (clusterRefCnt - value)
            }
        }
        precision = tp / (tp + fp).toDouble()
        recall = tp / (tp + fn).toDouble()

        logger.info { this }
    }
}
