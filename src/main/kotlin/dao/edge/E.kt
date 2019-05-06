package dao.edge

import com.google.common.base.Preconditions.checkArgument
import dao.vertex.V
import java.util.*

open class E(open val inV: V, open val outV: V, val type: Type, var weight: Float = 1F) {

    init {
        inV.addE(this)
        outV.addE(this)
    }

    constructor(inV: V, outV: V, type: String, weight: String) : this(inV, outV, Type.valueOf(type), weight.toFloat())

    override fun toString(): String = String.format("E[%s] %s --> %s", type, inV.value, outV.value)

    //region E.Type enum
    enum class Type(
            val inLevel: Int,
            val outLevel: Int) {
        RID_REF(-1, 0),
        CLS_REF(-1, 0),
        REF_REF(0, 0),
        REF_TKN(0, 1),
        TKN_SIM(1, 2),
        TKN_NCK(1, 2),
        SIM_ABR(2, 3);

        fun isInterLevel()= inLevel != outLevel

        companion object {
            fun getTypeByLevels(inLevel: Int, outLevel: Int): Type? {
                checkArgument(inLevel in 0..2, "InLevel argument was %s but expected in range [0, 2].", inLevel)
                checkArgument(inLevel + 1 == outLevel, "OutLevel should be InLevel + 1.")
                return Arrays.stream(Type.values()).filter { t -> t.inLevel == inLevel && t.outLevel == outLevel }.findAny().orElse(null)
            }
        }
    }
    //endregion
}



