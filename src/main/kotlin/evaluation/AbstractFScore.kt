package evaluation

import dao.G

abstract class AbstractFScore(private val g: G) {
    protected val logger = mu.KotlinLogging.logger {}
    var precision: Double = 0.0
    var recall: Double = 0.0
    private val f1: Double
        get() = 2.0 * precision * recall / (precision + recall)

    abstract fun evaluate()

    override fun toString() = "{F1=$f1, Precision=$precision, Recall=$recall}"
}