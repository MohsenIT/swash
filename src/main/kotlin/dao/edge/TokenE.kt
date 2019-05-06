package dao.edge

import dao.vertex.ElementV
import dao.vertex.RefV
import util.Strings

class TokenE(inV: RefV, outV: ElementV, type: String, weight: String) : E(inV, outV, type, weight), Cloneable {
    //region fields
    val order: Int = weight.toInt() - 1
    val isAbbr: Boolean = Strings.isAbbreviated(outV.value.length)
    val isBeforeDot: Boolean = Strings.isBeforeDot(inV.value, order)
    var pon: PON = PON.UNKNOWN

    override val outV: ElementV
        get() = super.outV as ElementV

    override val inV: RefV
        get() = super.inV as RefV
    //endregion

    //region methods
    /**
     * Increase the rank of pon, for example if namepart is LASTNAME change it to SUFFIX
     */
    fun incPONRank() {
        pon = pon.nextRankedNamePart()
    }

    /**
     * Decrease the rank of pon, for example if namepart is FIRSTNAME change it to PREFIX
     */
    fun decPONRank() {
        pon = pon.previousRankedNamePart()
    }

    override fun toString(): String = "E[${super.type}] ${super.inV.value} -$pon-> ${super.outV.value}"

    override fun equals(obj: Any?): Boolean {
        if (obj === this) return true
        if (obj !is TokenE) return false
        val e = obj as TokenE?
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

        fun nextRankedNamePart(): PON = if (this.rank == 5) SUFFIX else values()[this.rank + 1]

        fun previousRankedNamePart(): PON = if (this.rank == 1) PREFIX else values()[this.rank - 1]
    }
}
