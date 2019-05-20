package dao.vertex

import com.koloboke.collect.map.hash.HashObjObjMaps
import com.koloboke.collect.set.hash.HashObjSets
import dao.edge.E
import util.putOrAddListValue
import java.util.*

open class V(val id: Long, val value: String, val type: Type, var weight: Long) {

    val inE: MutableMap<E.Type, MutableList<E>> = HashObjObjMaps.newMutableMap()
    val outE: MutableMap<E.Type, MutableList<E>> = HashObjObjMaps.newMutableMap()

    constructor(id: String, value: String, type: String, weight: String) : this(id.toLong(), value, Type.toType(type)!!, weight.toLong())

    //region Traversal Functions

    fun getInE(type: E.Type): List<E> = inE.getOrDefault(type, mutableListOf())

    fun getOutE(type: E.Type): List<E> = outE.getOrDefault(type, mutableListOf())

    fun hasInOutE(type: E.Type) = getInOutV(type).isNotEmpty()

    fun getInOutE(type: E.Type): List<E> {
        val list = ArrayList<E>(getInE(type))
        list.addAll(getOutE(type))
        return list
    }

    fun getInV(type: E.Type): Set<V> = getInE(type).map { it.inV }.toSet()

    fun getOutV(type: E.Type): Set<V> = getOutE(type).map { it.outV }.toSet()

    fun getInOutV(type: E.Type): Set<V> {
        val set = HashObjSets.newMutableSet<V>(getInV(type))
        set.addAll(getOutV(type))
        return set
    }

    fun getOutNextLevelV(): Set<V> = outE.keys.filter { it.outLevel == it.inLevel + 1 }.flatMap { getOutV(it) }.toSet()

    fun Collection<V>.outVsUntil(destLevel: Int): Set<V> {
        require(this.map { it.type.level }.distinct().count() == 1) {"All vertices should be in the same levels."}
        var level: Int = this.map { it.type.level }.firstOrNull() ?: Int.MAX_VALUE
        var vs: Set<V> = HashObjSets.newMutableSet(this)
        while (level < destLevel) vs = vs.flatMap{ it.getOutV(E.Type.getTypeByLevels(level, ++level))}.toSet()
        return vs
    }

    //endregion


    //region Add or Remove Edges
    fun addE(e: E) {
        if(e.inV === this) outE.putOrAddListValue(e.type, e)
        else if(e.outV === this) inE.putOrAddListValue(e.type, e)
    }

    fun removeE(e: E) {
        if(e.inV === this) outE[e.type]?.remove(e)
        else if(e.outV === this) inE[e.type]?.remove(e)
    }

    //endregion


    override fun toString() = String.format("V[%s] %s: %d", type.text, value, weight)

    enum class Type(val text: String, val level: Int) {
        RESOLVED_ID("RID", -1),
        CLUSTER("CLS", -1),
        REFERENCE("REF", 0),
        HIERARCHY("HRC", 0),
        TOKEN("TKN", 1),
        SIMILAR("SIM", 2),
        NICKNAME("NCK", 2),
        ABBREVIATED("ABR", 3);

        companion object {
            val MAX_LEVEL = values().map { it.level }.max() ?: 3

            fun toType(text: String): Type? = values().first { it.text.equals(text, ignoreCase = true)}

            fun isElement(type: String) = toType(type)!!.level > 0

            fun isReference(type: String) = toType(type) == REFERENCE
        }
    }
}