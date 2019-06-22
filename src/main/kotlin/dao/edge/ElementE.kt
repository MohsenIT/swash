package dao.edge

import dao.vertex.ElementV
import dao.vertex.NameV
import util.Strings

class ElementE(inV: NameV, outV: ElementV, type: Type, weight: Float = 0F, var pon: PON = PON.UNKNOWN) : E(inV, outV, type, weight), Cloneable {

    constructor(inV: NameV, outV: ElementV, type: String, weight: String) : this(inV, outV, Type.valueOf(type), weight.toFloat())

    //region fields
    val order: Int = weight.toInt() - 1
    val isAbbr: Boolean = Strings.isAbbreviated(outV.value.length)
    val isBeforeDot: Boolean = Strings.isBeforeDot(inV.value, order)

    override val outV: ElementV
        get() = super.outV as ElementV

    override val inV: NameV
        get() = super.inV as NameV
    //endregion

    //region methods
    /**
     * Increase the rank of pon, for example if pon is LASTNAME change it to SUFFIX
     */
    fun incPONRank() {
        pon = pon.nextRankedPON()
    }

    /**
     * Decrease the rank of pon, for example if PON is FIRSTNAME change it to PREFIX
     */
    fun decPONRank() {
        pon = pon.previousRankedPON()
    }

    override fun toString(): String = "E[${super.type}] ${super.inV.value} -$pon-> ${super.outV.value}"

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is ElementE) return false
        val e = other as ElementE?
        return super.inV == e!!.inV && super.outV == e.outV && this.order == e.order
    }

    override fun hashCode(): Int {
        var result = 17
        result = 31 * result + super.inV.hashCode()
        result = 31 * result + super.outV.hashCode()
        result = 31 * result + this.order
        return result
    }
    //endregion

    enum class PON (var rank: Int) {
        UNKNOWN(-10), PREFIX(1), FIRSTNAME(2), MIDDLENAME(3), LASTNAME(4), SUFFIX(5);

        fun nextRankedPON(): PON = if (this.rank == 5) SUFFIX else values()[this.rank + 1]

        fun previousRankedPON(): PON = if (this.rank == 1) PREFIX else values()[this.rank - 1]
    }
}
