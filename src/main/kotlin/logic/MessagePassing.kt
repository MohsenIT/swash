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


class MessagePassing(val g: G) {
    private var currentPosition: MutableMap<Message, V> = emptyMap<Message, V>().toMutableMap()


    //region Traversal & Message Passing Steps
    fun V(sourceVs: Collection<V>): MessagePassing {
        currentPosition = HashObjObjMaps.newMutableMap(sourceVs.size)
        sourceVs.forEach { v -> currentPosition[Message(v as RefV, 1.0f)] = v }
        return this
    }

    fun V(type: V.Type): MessagePassing {
        return this.V(g.getVs(type))
    }

    fun out(type: E.Type): MessagePassing {
        val nextPosition: MutableMap<Message, V> = HashObjObjMaps.newMutableMap()
        for ((key, value) in currentPosition) {
            for (v in value.getOutV(type)) {
                val elementV = v as ElementV
                val m = key.clone()
                m.incMaxLevel()
                m.similarity = m.similarity / elementV.clusterCount
                if (elementV.type === V.Type.TOKEN)
                    m.firstToken = elementV
                nextPosition[m] = elementV
            }
        }
        currentPosition = nextPosition
        return this
    }

    fun `in`(type: E.Type): MessagePassing {
        val nextPosition: MutableMap<Message, V> = HashObjObjMaps.newMutableMap()
        for ((key, value) in currentPosition) {
            for (v in value.getInV(type)) {
                val m = key.clone()
                when {
                    v.type === V.Type.REFERENCE -> m.destRefV = v as RefV
                    else -> m.similarity = m.similarity / (v as ElementV).clusterCount
                }
                nextPosition[m] = v
            }
        }
        currentPosition = nextPosition
        return this
    }

    fun aggRefVsTerminal(commonMsgTh: Int, relSimTh: Float): Map<RefV, List<Candidate>> {
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
        // add similarity edges between tokens (REF_REF edges)
        candidates.values.flatMap { it }.forEach { g.addE(it.destRefV, it.originRefV, E.Type.REF_REF, it.sumSimilarity) }

        // collect REFs and prioritize them
        val isVisited = g.getRefVs().filter { it.hasInOutE(E.Type.REF_REF) }
                .sortedWith(compareBy<RefV> { it.getOutE(E.Type.REF_TKN).size }
                        .thenBy { it.getOutE(E.Type.REF_TKN).filter { e -> (e as TokenE).isAbbr }.count() }
                        .thenByDescending { it.weight })
                .associateBy({ it }, { false }).toMutableMap()


        // BFS traversal on REF_REF edges
        val fpWriter = PrintWriter("/home/ofogh/uni/PHDResearch/Dev/FP.tsv", "UTF-8")
        val fnWriter = PrintWriter("/home/ofogh/uni/PHDResearch/Dev/FN.tsv", "UTF-8")
        var all = 0
        var notConsistentCount = 0
        var inAndChange = 0
        val inAndChangeAndTP = 0
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
                    all++
                    if (!isConsistent) {
                        isConsistent = result.canBecomeConsistent()
                        notConsistentCount++
                        if (isConsistent) inAndChange++
                        //if(isConsistent && u.getRefResolvedId().equals(adj.getRefResolvedId()))inAndChangeAndTP++;
                    }
                    if (isConsistent) {
                        queue.add(adj)
                        isVisited[adj] = true
                        adj.replaceReferenceCluster(r, g)
                        clusterProfile.merge(result)
                    }
                    if(isConsistent && r.refResolvedIdV != adj.refResolvedIdV)
                        fpWriter.printf("%s\t%s\t%s%n", r.value, adj.value, clusterProfile)
                    else if(!isConsistent && r.refResolvedIdV == adj.refResolvedIdV)
                        fnWriter.printf("%s\t%s\t%s%n", r.value, adj.value, clusterProfile)
                }
            }
        }
        fnWriter.close()
        fpWriter.close()
        g.updateAncestorClusterCnt()
    }
    //endregion


    inner class Message(var originRefV: RefV, var similarity: Float, var maxLayer: Int = 0) : Cloneable {
        var destRefV: RefV? = null
        var firstToken: ElementV? = null

        constructor(originRefV: RefV, similarity: Float, maxLayer: Int, destRefV: RefV?, firstToken: ElementV?) : this(originRefV, similarity, maxLayer) {
            this.destRefV = destRefV
            this.firstToken = firstToken
        }

        fun incMaxLevel() {
            this.maxLayer++
        }

        public override fun clone(): Message {
            return Message(this.originRefV, this.similarity, this.maxLayer, this.destRefV, this.firstToken)
        }

        override fun toString() = "MSG{origin=$originRefV, sim=$similarity, firstToken=$firstToken}"
    }

    inner class Candidate(val destRefV: RefV, val originRefV: RefV, val messageList: List<Message>) {

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
                .format(originRefV, cntMessage, sumSimilarity, messageList.map { it.firstToken })
    }
}
