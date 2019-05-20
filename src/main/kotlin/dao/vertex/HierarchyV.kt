package dao.vertex

import dao.edge.E
import dao.edge.TokenE
import dao.vertex.V.Type.HIERARCHY

class HierarchyV(id: String, value: String, weight: String) : NameV(id.toLong(), value, HIERARCHY, weight.toLong()) {

    override val tokenEs: List<TokenE>
        get() = getOutE(E.Type.REF_TKN).map { it as TokenE }.sortedBy { it.order }

}
