import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import dao.G
import evaluation.AbstractFScore
import evaluation.ClustersFScore
import logic.Clustering
import logic.MessagePassing
import util.MyArgParser
import java.io.IOException

object Main {
    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) = mainBody {
        val arg = ArgParser(args).parseInto { MyArgParser(it, "citeseer") }
        val g = G(arg.expectedVertexCount, arg.expectedEdgeCount)
        g.readGraph(arg.vertexFilePath, arg.edgeFilePath)

        val eval: AbstractFScore = ClustersFScore(g)
        // eval.evaluate()
        val mp = MessagePassing(g)
        val candidates = mp.refVs().sendOuts().sendIns().aggMessagesToCandidates()

        val cls = Clustering(g)
        cls.clusterCandidates(candidates)
//        mp.clusterCandidates(candidates)
//        eval.evaluate()

        //util.IOs.writeSimilarityGraph(g, true, "out/", candidates)
        //util.IOs.writeVsClustersToTsv(g, true, "out/")
    }
}