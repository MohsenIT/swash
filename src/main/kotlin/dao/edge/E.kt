package dao.edge

import dao.vertex.V

open class E(open val inV: V, open val outV: V, val type: Type, var weight: Float = 1F) {
    // TODO: 2019-06-10 Number instead Float

    init {
        inV.addE(this)
        outV.addE(this)
    }

    constructor(inV: V, outV: V, type: String, weight: String) : this(inV, outV, Type.valueOf(type), weight.toFloat())

    override fun toString(): String = String.format("E[%s] %s --> %s", type, inV.value, outV.value)

    enum class Type(val inLayer: Int, val outLayer: Int) {
        RID_REF(-1, 0),
        CLS_REF(-1, 0),
        HRC_HRC(-1, -1),
        HRC_REF(-1, 0),
        HRC_ELM(-1, 2),
        REF_REF(0, 0),
        REF_TKN(0, 1),
        TKN_SIM(1, 2),
        TKN_NCK(1, 2),
        SIM_ABR(2, 3);

        fun isInterLayer() = inLayer != outLayer

        companion object {
            fun getNextLayerTypes(inLayer: Int): List<Type> {
                require(inLayer in 0..2) { "InLayer argument was $inLayer but expected in range [0, 2]." }
                return values().filter { it.inLayer == inLayer && it.outLayer == inLayer + 1 }
            }

            fun getNextLayerType(inLayer: Int) = getNextLayerTypes(inLayer)[0]

            fun getTypesByLayers(inLayer: Int, outLayer: Int): List<Type> {
                require(inLayer in 0..2) { "InLayer argument was $inLayer but expected in range [0, 2]." }
                require(inLayer + 1 == outLayer) { "OutLayer should be InLayer + 1." }
                return values().filter { it.inLayer == inLayer && it.outLayer == outLayer }
            }
        }
    }
}



