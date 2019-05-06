package dao.vertex

import logic.matching.ClusterProfile

class ClusterV (id: Long, refV: RefV) : V(id, refV.value, V.Type.CLUSTER, 1L) {
    var profile: ClusterProfile = refV.buildClusterProfile()
}
