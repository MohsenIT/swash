package dao.vertex

/**
 * store clusters count of each element vertices. For element vertex types (positive layers) this field is valid.
 *
 * @param clusterCount Int value of cluster count
 */
class ElementV(
        id: String,
        value: String,
        type: String,
        weight: String,
        var clusterCount: Int = 0
) : V(id, value, type, weight)
