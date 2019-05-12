import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import dao.G
import dao.edge.E
import dao.vertex.V
import evaluation.AbstractFScore
import evaluation.ClustersFScore
import logic.MessagePassing
import util.MyArgParser
import java.io.IOException

object Main {
    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) = mainBody {
        val arg = ArgParser(args).parseInto { MyArgParser(it, "arxiv") }
        val g = G(arg.expectedVertexCount, arg.expectedEdgeCount)
        g.readGraph(arg.vertexFilePath, arg.edgeFilePath)

        val eval: AbstractFScore = ClustersFScore(g)
        // eval.evaluate()
        val mp = MessagePassing(g)
        val candidates = mp.V(V.Type.REFERENCE)
                .out(E.Type.REF_TKN).`in`(E.Type.REF_TKN).aggRefVsTerminal(1, 0.5f)
        //util.IOs.writeSimilarityGraph(candidates, "out/", true, g)

        mp.clusterCandidates(candidates)
        util.IOs.writeVlists(candidates, "out/", true, g)
        eval.evaluate()
    }
}