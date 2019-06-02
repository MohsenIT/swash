package dao.vertex

import dao.edge.E
import dao.edge.ElementE
import dao.vertex.V.Type.HIERARCHY

class HierarchyV(id: String, value: String, weight: String) : NameV(id.toLong(), value, HIERARCHY, weight.toLong()) {

    override val elementEs: List<ElementE>
        get() = getOutE(E.Type.REF_TKN).map { it as ElementE }.sortedBy { it.order }

}
