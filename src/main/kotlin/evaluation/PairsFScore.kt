package evaluation

import dao.G
import dao.vertex.RefV
import util.IOs

class PairsFScore (val g: G, val goldPairsFilePath: String) : AbstractFScore(g){
    override fun evaluate() {
        var fp = 0.0
        var fn = 0.0
        var tp = 0.0
        val goldPairs = IOs.readCSVLines(goldPairsFilePath)
        val golds = goldPairs.drop(1).map{GoldPairs(it)}
        for (gold in golds) {
            if (gold.inSameCluster()) when {
                gold.isMatched -> tp += gold.incValue.toDouble()
                else -> fp += gold.incValue.toDouble()
            } else when {
                !gold.isMatched -> tp += gold.incValue.toDouble()
                else -> {
                    fn += gold.incValue.toDouble()
                    System.out.printf("%s == %s\r\n", gold.refV1.value, gold.refV2.value)
                }
            }
        }
        precision = tp / (tp + fp)
        recall = tp / (tp + fn)

        logger.info { this }
    }

    private inner class GoldPairs(elements: Array<String>) {
        val refV1: RefV = g.getV(elements[0].toLong()) as RefV
        val refV2: RefV = g.getV(elements[1].toLong()) as RefV
        val isMatched: Boolean = elements.size <= 4 || elements[4].toInt() == 1

        val incValue: Long
            get() = refV1.weight * refV2.weight

        fun inSameCluster(): Boolean = refV1.refClusterV == refV2.refClusterV
    }
}
