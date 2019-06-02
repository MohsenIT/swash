package dao.vertex

import logic.matching.ClusterProfile

class ClusterV (id: Long, refV: RefV) : V(id, refV.value, Type.CLUSTER, 1L) {
    var profile: ClusterProfile = buildClusterProfile(refV)

    private fun buildClusterProfile(refV: RefV): ClusterProfile {
        val profile = ClusterProfile()
        refV.elementEs.forEach { profile.addEntry(ClusterProfile.Entry(it)) }
        return profile
    }
}
