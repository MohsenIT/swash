package dao

import dao.edge.TokenE

class FullName (val tokens: List<Token>) {

    fun getTokens(pon: TokenE.PON) : Set<Token> =  tokens.filter { it.pon == pon }.toSet()

    fun getTokensOrdered(pon: TokenE.PON) : List<Token> =  tokens.filter { it.pon == pon }.sortedBy { it.order }


    class Token (
            val value: String,
            var pon: TokenE.PON,
            var order: Int,
            var preToken: Token?,
            var nextToken: Token?
    )
}
