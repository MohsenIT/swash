package util

import com.koloboke.collect.set.hash.HashObjSets
import dao.G
import dao.edge.E
import dao.vertex.RefV
import dao.vertex.V
import logic.MessagePassing
import java.io.File

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

object IOs {

    private val logger = mu.KotlinLogging.logger {}

    fun readCSVLines(csvFilePath: String, delimiter: String = "\t"): List<Array<String>> {
        val result: MutableList<Array<String>> = mutableListOf()
        File(csvFilePath).readLines().forEach {
            result.add(it.split(delimiter.toRegex()).dropLastWhile { e -> e.isEmpty() }.toTypedArray())
        }
        return result
    }

    @Throws(IOException::class)
    fun writeSimilarityGraph(candidates: Map<RefV, List<MessagePassing.Candidate>>, outPath: String, hasAllVs: Boolean, g: G, delimiter: String = "\t") {
        val candidateList = candidates.values.flatMap { it }
        val vertices: Set<V> = if(hasAllVs) g.getRefVs().toSet() else
            candidateList.flatMap { listOf(it.originRefV, it.destRefV) }.toSet()

        val vCsvRows = StringBuilder()
        vCsvRows.append(String.format("Id%1\$s Label%1\$s Weight%1\$s Res_Id\r\n", delimiter))
        for (v in vertices) {
            val firstResId = v.getInV(E.Type.RID_REF).first().value
            vCsvRows.append(String.format("%d%5\$s %s%5\$s %d%5\$s %s\r\n", v.id, v.value, v.weight, firstResId, delimiter))
        }
        Files.write(Paths.get("$outPath/vertices.tsv").toAbsolutePath(), vCsvRows.toString().toByteArray(), StandardOpenOption.CREATE)

        val rows : MutableSet<String> = HashObjSets.newMutableSet(candidateList.size / 2)
        for (c in candidateList) {
            val o = c.originRefV.id
            val d = c.destRefV.id
            rows.plusAssign(String.format("%d%5\$s %d%5\$s %.6f%5\$s %d\r\n", if (o < d) o else d, if (o >= d) o else d, c.sumSimilarity, c.cntMessage, delimiter))
        }
        val edgesColumnHeader = String.format("Source%2\$s Target%2\$s Weight%2\$s Common_Token_Cnt\r\n%s", rows.joinToString(""), delimiter)
        Files.write(Paths.get("$outPath/edges.tsv").toAbsolutePath(), edgesColumnHeader.toByteArray(), StandardOpenOption.CREATE)
    }

    @Throws(IOException::class)
    fun writeVlists(candidates: Map<RefV, List<MessagePassing.Candidate>>, outPath: String, hasAllVs: Boolean, g: G, delimiter: String = "\t") {
        val candidateList = candidates.values.flatten()
        val vertices: Set<V> = if(hasAllVs) g.getRefVs().toSet() else
            candidateList.flatMap { listOf(it.originRefV, it.destRefV) }.toSet()

        val vCsvRows = StringBuilder()
        vCsvRows.append(String.format("Id%1\$s Label%1\$s Weight%1\$s Res_Id%1\$s Cluster%1\$s Cluster_Size\r\n", delimiter))
        for (v in vertices) {
            val firstResId = v.getInV(E.Type.RID_REF).first().value
            val cluster = v.getInV(E.Type.CLS_REF).first()
            val clusterSize =  cluster.getOutV(E.Type.CLS_REF).size

            vCsvRows.append(String.format("%d%7\$s %s%7\$s %d%7\$s %s%7\$s %s%7\$s %d\r\n", v.id, v.value, v.weight, firstResId, cluster.value, clusterSize, delimiter))
        }
        Files.write(Paths.get("$outPath/vertices_clusters.tsv").toAbsolutePath(), vCsvRows.toString().toByteArray(), StandardOpenOption.CREATE)
    }
}
