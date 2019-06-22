package dao.vertex

import dao.edge.E
import dao.edge.ElementE
import dao.vertex.V.Type.HIERARCHY
import logic.Alignment

class HierarchyV(id: Long, alignment: Alignment) : NameV(id, alignment.getName(), HIERARCHY, 0) {

    init {
        alignment.entries.filter { it.matchedV != null}.forEach {
            ElementE(this, it.getElementV(), E.Type.HRC_ELM, it.order.toFloat(), it.pon)
        }
    }

    override val elementEs: List<ElementE>
        get() = getOutE(E.Type.HRC_ELM).map { it as ElementE }.sortedBy { it.order }

    fun getParents(): Collection<HierarchyV> = getInV(E.Type.HRC_HRC).map { it as HierarchyV }

    fun getChildren() = getChildEs().map { it.outV as NameV }

    fun getChildEs() = (getOutE(E.Type.HRC_HRC) + getOutE(E.Type.HRC_HRC))

    enum class Relation { CHILD, PARENT, NONE, NA }
}
