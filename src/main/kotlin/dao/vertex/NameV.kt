package dao.vertex

import dao.edge.E
import dao.edge.TokenE

/**
 * An abstract super class of [RefV] and [HierarchyV] to use in [TokenE] as source vertex
 */
abstract class NameV(id: Long, value: String, type: Type, weight: Long) : V(id, value, type, weight) {
    abstract val tokenEs: List<TokenE>
}