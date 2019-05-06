package util

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default

class MyArgParser(parser: ArgParser, datasetName: String = "arxiv") {

    val datasetName by parser.storing("-n", "--datasetName", help = "dataset datasetName").default { datasetName } // TODO: 2019-04-28 should be removed later.

    val vertexFilePath by parser.storing("-v", "--vertices", help = "the node file path")
            .default { "/home/ofogh/uni/PHDResearch/Dev/r/name-matching-data-preparation/out/${this.datasetName}_node_list.csv" } // TODO: 2019-04-28 should be removed later.

    val edgeFilePath by parser.storing("-e", "--edges", help = "the edge file path")
            .default { "/home/ofogh/uni/PHDResearch/Dev/r/name-matching-data-preparation/out/${this.datasetName}_edge_list.csv" } // TODO: 2019-04-28 should be removed later.

    val expectedVertexCount by parser.storing("-V", "--expectedVertexCount"
            , help = "an upper bound for the number of vertices.") { toInt() }.default { 40000 }

    val expectedEdgeCount by parser.storing("-E", "--expectedEdgeCount"
            , help = "an upper bound for the number of edges.") { toInt() }.default { 80000 }

}