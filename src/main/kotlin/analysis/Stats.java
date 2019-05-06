package analysis;

import dao.G;
import dao.edge.E;
import dao.vertex.V;

public class Stats {
    public static void calcStats(G g) {
        // count of REF = 58515
        g.getRefVs().stream().mapToLong(V::getWeight).sum();

        // count of distinct NAME in REFs = 12872
        g.getRefVs().size();

        //count of NAMEs with more than 1 representations = 3089
        g.getRefVs().stream().filter(v -> v.getOutE(E.Type.RID_REF).size()>1).count();
    }
}
