package dao.vertex

import dao.G
import dao.edge.E
import dao.edge.ElementE

import dao.vertex.V.Type.REFERENCE

class RefV(id: String, value: String, weight: String) : NameV(id.toLong(), value, REFERENCE, weight.toLong()) {

    override val elementEs: List<ElementE>
        get() = getOutE(E.Type.REF_TKN).map { it as ElementE }.sortedBy { it.order }

    val refResolvedIdV: V?
        get() = getInV(E.Type.RID_REF).firstOrNull()

    val refClusterV: ClusterV?
        get() = getInV(E.Type.CLS_REF).firstOrNull() as ClusterV


    /**
     * replace [ClusterV] of [RefV] by the [ClusterV] of [targetV] parameter.
     *
     * @param targetV reference vertex that its cluster should be replaced by old one.
     */
    fun replaceReferenceCluster(targetV: RefV, g: G) {
        val oldE = this.getInE(E.Type.CLS_REF).firstOrNull()
        val clusterV = targetV.refClusterV ?: ClusterV(g.getMaxIdV(), targetV)
        if (clusterV === oldE?.inV)
            return

        if (oldE != null) { // remove oldE references
            oldE.inV.removeE(oldE)
            this.removeE(oldE)
            if(oldE.inV.getOutE(E.Type.CLS_REF).isEmpty()) g.removeV(oldE.inV)
        }

        E(clusterV, this, E.Type.CLS_REF)
    }
}
