package util

import com.google.common.graph.*
import com.koloboke.collect.map.hash.HashObjObjMaps
import dao.vertex.RefV
import logic.MessagePassing

import java.util.*

object GuavaGraphs {
    /**
     * Compute Connected components of a [com.google.common.graph.ValueGraph] using BFS.
     *
     * @param g the input [com.google.common.graph.ValueGraph]
     * @return a components collection of the [com.google.common.graph.ImmutableValueGraph]
     */
    fun connectedComponents(g: ValueGraph<Long, Float>): Collection<ImmutableValueGraph<Long, Double>> {
        val components = ArrayList<ImmutableValueGraph<Long, Double>>()
        val refIdToNotVisited = g.nodes().map { it!! to true }.toMap().toMutableMap()
        for (r in refIdToNotVisited.keys) {
            if (!refIdToNotVisited[r]!!)
                continue
            val queue = LinkedList<Long>()
            val componentEs = ArrayList<weightedEdge>()
            queue.add(r)
            refIdToNotVisited[r] = false
            while (!queue.isEmpty()) {
                val u = queue.remove()
                g.adjacentNodes(u).filter{ refIdToNotVisited[it]!! }.forEach{
                    queue.add(it)
                    refIdToNotVisited[it] = false
                    componentEs.add(weightedEdge(u!!, it.toLong(), g.edgeValueOrDefault(u, it, 0f)!!.toDouble()))
                }
            }
            components.add(createGraph(componentEs))
        }
        return components
    }

    /**
     * Create a [com.google.common.graph.ValueGraph] using a collection of weighted edges
     * @param es a collection of weightedEdges. A weightedEdge consist of u and v nodes and the weight of an edge
     * @return a new [com.google.common.graph.ImmutableValueGraph] with the offered edges structure
     */
    private fun createGraph(es: Collection<weightedEdge>): ImmutableValueGraph<Long, Double> {
        val sg: MutableValueGraph<Long, Double> = ValueGraphBuilder.directed().build()
        for (e in es)
            sg.putEdgeValue(e.u, e.v, e.w)
        return ImmutableValueGraph.copyOf(sg)
    }

    /**
     * Extract a Guava [com.google.common.graph.ValueGraph] using offered edges (es)
     *
     * @param g [com.google.common.graph.ValueGraph] which subgraph should be extracted from it
     * @param es edges collections of subgraph
     * @return a new [com.google.common.graph.ImmutableValueGraph] with the offered edges structure
     */
    private fun extractSubGraph(g: ValueGraph<Long, Double>, es: Collection<EndpointPair<Long>>): ImmutableValueGraph<Long, Double> {
        val sg:  MutableValueGraph<Long, Double> = ValueGraphBuilder.directed().build()
        for (e in es)
            sg.putEdgeValue(e.nodeU(), e.nodeV(), g.edgeValueOrDefault(e.nodeU(), e.nodeV(), 0.0))
        return ImmutableValueGraph.copyOf(sg)
    }

    /**
     * Compute Connected components of CandidateLists using BFS.
     * CandidateLists convert to a guava graph and use BFS on this graph to compute Connected Components.
     *
     * @param candidates Map of Candidate Collections
     * @return Collection of [com.google.common.graph.ImmutableValueGraph]s
     */
    fun MessagePassing.connectedCandidatesGuavaGraphs(candidates: Map<RefV, List<MessagePassing.Candidate>>): Collection<ImmutableValueGraph<Long, Double>> {
        val graph: MutableValueGraph<Long, Float> = ValueGraphBuilder.directed().build()
        candidates.values.stream().flatMap { it.stream() }.forEach { c ->
            if (c.destRefV != c.originRefV)
                graph.putEdgeValue(c.destRefV.id, c.originRefV.id, c.sumSimilarity)
        }
        return GuavaGraphs.connectedComponents(graph)
    }

    /**
     * Get a collection of [com.google.common.graph.ImmutableValueGraph]s and return the corresponding
     * vertices of nodes in the ValueGraph by their ids.
     *
     * @param componentGraphs Collection of [com.google.common.graph.ImmutableValueGraph]s
     * @return Map of representative V (currently most frequent) to connected components Vs
     */
    fun MessagePassing.graphsToClusters(componentGraphs: Collection<ImmutableValueGraph<Long, Double>>): Map<RefV, Collection<RefV>> {
        val components: MutableMap<RefV, Collection<RefV>> = HashObjObjMaps.newMutableMap()
        for (componentGraph in componentGraphs) {
            val componentVs = componentGraph.nodes().map{ id -> g.getV(id.toLong()) as RefV }
            components[componentVs.maxBy { it.weight }!!] = componentVs
        }
        return components
    }

    class weightedEdge(var u: Long, var v: Long, var w: Double)
}
