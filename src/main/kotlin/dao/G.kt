package dao

import com.koloboke.collect.map.hash.HashLongObjMaps
import com.koloboke.collect.set.hash.HashObjSets
import dao.edge.E
import dao.edge.TokenE
import dao.edge.TokenE.PON
import dao.vertex.ClusterV
import dao.vertex.ElementV
import dao.vertex.RefV
import dao.vertex.V
import de.zedlitz.phonet4java.Phonet2
import util.IOs

import java.util.*

private val logger = mu.KotlinLogging.logger {}

class G(expectedVertexCount: Int = 40000, expectedEdgesCount: Int = 80000) {
    private val vs: MutableMap<Long, V> = HashLongObjMaps.newMutableMap(expectedVertexCount)
    private val es: MutableSet<E> = HashObjSets.newMutableSet(expectedEdgesCount)

    //region Getters & Setters

    fun getVs(type: V.Type): List<V> = vs.values.filter { it.type == type }

    fun getRefVs(): List<RefV> = getVs(V.Type.REFERENCE).map { it as RefV }

    fun removeV(v: V?) = vs.remove(v?.id)

    fun getV(id: Long): V? = vs[id]

    fun addE(inV: V, outV: V, type: E.Type, weight: Float) {
        if (inV === outV) return
        val e = E(inV, outV, type, weight)
        this.es.add(e)
    }

    //endregion

    //region Read Graph Files
    fun readGraph(vertexCsvFilePath: String, edgeCsvFilePath: String) {
        readVerticesFile(vertexCsvFilePath)
        readEdgesFile(edgeCsvFilePath)
        logger.info { "the vertex list and edge list files are read and the graph is initiated successfully." }

        initPONs()
        initClusters()
    }

    private fun readEdgesFile(edgeCsvFilePath: String) {
        val edges = IOs.readCSVLines(edgeCsvFilePath)
        for (i in 1 until edges.size) {
            val l = edges[i]
            val inV = vs[l[0].toLong()]!!
            val outV = vs[l[1].toLong()]!!
            val e = if (l[4] == "REF_TKN")
                TokenE(inV as RefV, outV as ElementV, l[4], l[5])
            else
                E(inV, outV, l[4], l[5])
            es.add(e)
        }
    }

    private fun readVerticesFile(vertexCsvFilePath: String) {
        val vertices: List<Array<String>> = IOs.readCSVLines(vertexCsvFilePath)
        for (i in 1 until vertices.size) {
            val l = vertices[i]
            val v = when {
                V.Type.isReference(l[2]) -> RefV(l[0], l[1], l[3])
                V.Type.isElement(l[2]) -> ElementV(l[0], l[1], l[2], l[3], 0)
                else -> V(l[0], l[1], l[2], l[3])
            }
            vs[l[0].toLong()] = v
        }
    }
    //endregion


    /**
     * Assign a cluster vertex to each REFERENCE vertices. The clusters change during matching.
     */
    private fun initClusters() {
        var topId = getMaxIdV()
        for (refV in getRefVs()) {
            val clusV = ClusterV(++topId, refV)
            val clusE = E(clusV, refV, E.Type.CLS_REF)
            this.es.add(clusE)
            this.vs[clusV.id] = clusV
        }
        logger.info { "initializing cluster vertices: a cluster vertex is assigned to each REF vertex." }
    }

    fun getMaxIdV() = vs.keys.max() ?: 0

    /**
     * Assign initial PON (firstname, lastname , ...) to the REF_TKN edges
     */
    private fun initPONs() {
        for (refV in getRefVs()) {
            val tokenEs= refV.tokenEs.sortedWith(compareBy<TokenE> { it.isAbbr }.thenByDescending { it.order }).toMutableList()
            val lname: TokenE = tokenEs[0]
            lname.pon = PON.LASTNAME
            tokenEs.remove(lname)
            val fname: TokenE? = tokenEs.sortedBy { it.order }.firstOrNull()
            if (fname != null) {
                fname.pon = PON.FIRSTNAME
                tokenEs.remove(fname)
                for (e in tokenEs) {
                    when {
                        e.order > lname.order -> e.pon = PON.SUFFIX
                        e.order > fname.order && e.order < lname.order -> e.pon = PON.MIDDLENAME
                        else -> e.pon = PON.PREFIX
                    }
                }
            }
        }
        logger.info { "Initial PON's is assigned to REF_TKN edges." }
    }

    //region Update clusters
    /**
     * update cluster edges according to the clustering result
     *
     * @param clusters a map of cluster Vs and their representative
     */
    fun updateClusters(clusters: Map<RefV, Collection<RefV>>) {
        clusters.entries.forEach { it.value.forEach { v -> v.replaceReferenceCluster(it.key, this)} }
    }

    /**
     * update cluster edges according to the their actual resolved_id to calculate max achievable F1.
     *
     * @param allCandidatesVs all vertices in the candidates collection
     */
    fun updateClustersToRealClusters(allCandidatesVs: Collection<RefV>) {
        val queue = LinkedList(allCandidatesVs)
        while (!queue.isEmpty()) {
            val refV = queue.peek()
            val vsInRID = refV.getInV(E.Type.RID_REF).elementAt(0).getOutV(E.Type.RID_REF)
                    .filter{ queue.contains(it) }.map { it as RefV }
            vsInRID.forEach {it.replaceReferenceCluster(refV, this) }
            queue.removeAll(vsInRID)
        }
    }

    /**
     * update cluster edges according to the their actual resolved_id to calculate max achievable F1.
     */
    fun updateToMaxAchievableRecall() {
        val refsToNotVisited = getRefVs().map { it to true }.toMap().toMutableMap()
        for (v in refsToNotVisited.keys) {
            if (!refsToNotVisited[v]!!) continue
            val queue = LinkedList(listOf(v))
            refsToNotVisited[v] = false
            while (!queue.isEmpty()) {
                val u = queue.remove()!!
                val resIdV = u.refResolvedIdV
                val coResAdjs = u.getInOutV(E.Type.REF_REF).stream().map { it as RefV }
                        .filter { refsToNotVisited[it]!! && it.refResolvedIdV == resIdV }
                for (adj in coResAdjs) {
                    queue.add(adj)
                    refsToNotVisited[adj] = false
                    adj.replaceReferenceCluster(v, this)
                }
            }
        }
    }


    /**
     * update cluster edges according to the their actual resolved_id to calculate max achievable F1.
     *
     * @param g whole graph
     * @param goldPairsFilePath
     */
    fun updateToMaxAchievableRecallPairwise(g: G, goldPairsFilePath: String) {
        var a = 0
        val goldPairs = IOs.readCSVLines(goldPairsFilePath).drop(1)
        for (gold in goldPairs) {
            val s = g.getV(gold[0].toLong()) as RefV
            val t = g.getV(gold[1].toLong()) as RefV
            val refsToNotVisited = getRefVs().map { it to true }.toMap().toMutableMap()

            val queue = LinkedList(listOf(s))
            refsToNotVisited[s] = false
            while (!queue.isEmpty()) {
                val u = queue.remove()
                val coResAdjs = u.getInOutV(E.Type.REF_REF).stream().map { it as RefV }.filter{ refsToNotVisited[it]!! }
                for (adj in coResAdjs) {
                    if (adj == t) {
                        t.replaceReferenceCluster(s, this)
                        a++
                        while (!queue.isEmpty()) queue.remove()
                        break
                    }
                    queue.add(adj)
                    refsToNotVisited.put(adj, false)
                }
            }
        }
    }

    fun updateClustersToStringMatches() {
        val c = Phonet2()
        val phoneMap = getRefVs().groupBy { c.code(it.value) }
        for (refVs in phoneMap.values) {
            refVs.forEach { it.replaceReferenceCluster(refVs[0], this) }
        }
    }

    /**
     * update the clusterCnt field of all `ElementV`s
     *
     * @param maxUpdateLevel max level to update cluster count in the graph, 0 is REF, 1 is TKN , and etc.
     */
    @JvmOverloads
    fun updateAncestorClusterCnt(maxUpdateLevel: Int = V.Type.MAX_LEVEL) {
        require(maxUpdateLevel in 1..3) {"maxUpdateLevel must be between [1, 3]."}
        val elementVs = vs.values.filter { it.type.level in 1..maxUpdateLevel }
                .map { it as ElementV }.sortedBy { it.type.level }
        for (v in elementVs) {
            v.clusterCount = v.inE.entries.filter { it.key.isInterLevel() }
                    .flatMap { it.value.map { x -> if (x.inV is RefV) 1 else (x.inV as ElementV).clusterCount }}.sum()
        }
        logger.info {"ClusterCount property of level 1 to $maxUpdateLevel  are updated."}
    }
    //endregion
}
