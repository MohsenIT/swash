package logic

import com.koloboke.collect.map.hash.HashObjObjMaps
import dao.G
import dao.edge.E
import dao.edge.TokenE
import dao.vertex.ElementV
import dao.vertex.RefV
import dao.vertex.V

import java.io.FileNotFoundException
import java.io.PrintWriter
import java.io.UnsupportedEncodingException
import java.util.*


class MessagePassing(private val g: G) {
    private var currentPosition: MutableMap<Message, V> = emptyMap<Message, V>().toMutableMap()
    private var currentLevel = 0

    //region Traversal & Message Passing Steps
    fun refVs(): MessagePassing {
        val sourceVs = g.getRefVs()
        currentPosition = HashObjObjMaps.newMutableMap(sourceVs.size)
        sourceVs.forEach { currentPosition[Message(it)] = it }
        currentLevel = 0
        return this
    }

    fun sendOuts(): MessagePassing {
        val nextPosition: MutableMap<Message, V> = HashObjObjMaps.newMutableMap()
        val types = E.Type.getTypesByLevels(currentLevel, currentLevel + 1)
        for ((key, value) in currentPosition) {
            val outEs = types.map { value.getOutE(it) }.flatten()
            for (e in outEs) {
                val elementV = e.outV as ElementV
                val m = key.clone()
                m.incMaxLevel()
                m.similarity = m.similarity / elementV.clusterCount
                if (elementV.type === V.Type.TOKEN)
                    m.tokenE = e as TokenE
                nextPosition[m] = elementV
            }
        }
        currentPosition = nextPosition
        currentLevel++
        return this
    }

    fun sendIns(): MessagePassing {
        val nextPosition: MutableMap<Message, V> = HashObjObjMaps.newMutableMap()
        val types = E.Type.getTypesByLevels(currentLevel - 1, currentLevel)

        for ((key, value) in currentPosition) {
            val inVs = types.map { value.getInV(it) }.flatten()
            for (v in inVs) {
                val m = key.clone()
                when {
                    v.type === V.Type.REFERENCE -> m.destRefV = v as RefV
                    else -> m.similarity = m.similarity / (v as ElementV).clusterCount
                }
                nextPosition[m] = v
            }
        }
        currentPosition = nextPosition
        currentLevel--
        return this
    }

    fun aggMsgsToCandidates(commonMsgTh: Int = 1, relSimTh: Float = 0.5f): Map<RefV, List<Candidate>> {
        val result = currentPosition.keys
                .groupBy { it.destRefV!! }.mapValues { it.value.groupBy(Message::originRefV) }

        val strongMap: MutableMap<RefV, List<Candidate>> = HashObjObjMaps.newMutableMap()
        for (dst in result.entries) {
            val simThreshold = getSimilarityThreshold(dst, relSimTh)
            val candidateList = ArrayList<Candidate>()

            for ((key, value) in dst.value) {
                val can = Candidate(dst.key, key, value)
                if (can.sumSimilarity >= simThreshold && can.messageList.size >= commonMsgTh)
                    candidateList.add(can)
            }
            if (candidateList.size > 1)
                strongMap[dst.key] = candidateList.sortedByDescending { it.sumSimilarity }
        }
        return strongMap
    }

    private fun getSimilarityThreshold(receivedMessages: Map.Entry<RefV, Map<RefV, List<Message>>>, relativeSimTh: Float): Double {
        val selfSim = receivedMessages.value[receivedMessages.key]!!.sumByDouble { it.similarity.toDouble() }
        return selfSim * relativeSimTh
    }
    //endregion

    //region Clustering Candidates


    /**
     * Get a collection of candidates and cluster them based on a rule-based greedy approach
     *
     * @param candidates Map of the V vertex to their candidates
     * @return Map of representative V (currently most frequent) to connected components Vs
     */
    @Throws(FileNotFoundException::class, UnsupportedEncodingException::class)
    fun clusterCandidates(candidates: Map<RefV, List<Candidate>>) {
        candidates.values.flatten().forEach { g.addE(it.destRefV, it.originRefV, E.Type.REF_REF, it.sumSimilarity) }

        // collect REFs and prioritize them
        val isVisited = g.getRefVs().filter { it.hasInOutE(E.Type.REF_REF) }
                .sortedWith(compareBy<RefV> { it.getOutE(E.Type.REF_TKN).size }
                        .thenBy { it.getOutE(E.Type.REF_TKN).filter { e -> (e as TokenE).isAbbr }.count() }
                        .thenByDescending { it.weight })
                .associateBy({ it }, { false }).toMutableMap()


        // BFS traversal on REF_REF edges
        for (notVisitedRefV in isVisited.keys) {
            if (isVisited[notVisitedRefV]!!)
                continue
            val queue = LinkedList(listOf(notVisitedRefV))
            isVisited[notVisitedRefV] = true
            val clusterProfile = notVisitedRefV.refClusterV!!.profile
            while (!queue.isEmpty()) {
                val r = queue.remove()!!
                val adjacentList = r.getInOutV(E.Type.REF_REF).map { it as RefV }.filter { !isVisited[it]!! }
                for (adj in adjacentList) {
                    val result = clusterProfile.align(adj)
                    var isConsistent = result.isConsistent
                    if (!isConsistent) isConsistent = result.canBecomeConsistent()
                    if (isConsistent) {
                        queue.add(adj)
                        isVisited[adj] = true
                        adj.replaceReferenceCluster(r, g)
                        clusterProfile.merge(result)
                    }
                }
            }
        }
        g.updateAncestorClusterCnt()
    }
    //endregion


    class Message(var originRefV: RefV, var similarity: Float = 1F, var maxLayer: Int = 0) : Cloneable {
        var destRefV: RefV? = null
        var tokenE: TokenE? = null

        constructor(originRefV: RefV, similarity: Float, maxLayer: Int, destRefV: RefV?, firstToken: TokenE?) : this(originRefV, similarity, maxLayer) {
            this.destRefV = destRefV
            this.tokenE = firstToken
        }

        fun incMaxLevel() {
            this.maxLayer++
        }

        public override fun clone(): Message {
            return Message(this.originRefV, this.similarity, this.maxLayer, this.destRefV, this.tokenE)
        }

        override fun toString() = "MSG{origin=$originRefV, sim=$similarity, tokenE=$tokenE}"
    }

    class Candidate(val destRefV: RefV, val originRefV: RefV, val messageList: List<Message>) {

        /**
         * Gets sum of all messages similarity from the destination V.
         *
         * @return Float number of Messages similarity
         */
        var sumSimilarity: Float = messageList.sumByDouble { it.similarity.toDouble() }.toFloat()

        /**
         * Gets count of message from the origin that received to the current V.
         *
         * @return Integer Message Count
         */
        val cntMessage: Int
            get() = messageList.size

        override fun toString(): String = "CAN{origin=%s, cnt=%d, sim=%.3f, messages=%s}"
                .format(originRefV, cntMessage, sumSimilarity, messageList.map { it.tokenE })
    }
}
