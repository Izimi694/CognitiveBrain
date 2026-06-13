package com.izimi.eagent.hippocampus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MemoryGraphTest {

    private MemoryGraph graph;

    @BeforeEach
    void setUp() {
        graph = new MemoryGraph();
    }

    private MemoryEntry entry(String id, String summary, long ts, int day, List<String> learnings, List<String> skills) {
        MemoryEntry e = new MemoryEntry(id, summary);
        e.timestamp = ts;
        e.gameDay = day;
        e.keyLearnings = learnings;
        e.relatedSkills = skills;
        e.preferencesUpdated = new java.util.HashMap<>();
        return e;
    }

    // ── Node CRUD ──

    @Test
    @DisplayName("addNode stores node")
    void addNodeStoresNode() {
        MemoryEntry e = entry("mem_001", "挖了5个铁矿", 1000, 1, List.of("矿产分布"), List.of("mine"));
        graph.addNode(e);
        assertEquals(1, graph.nodeCount());
        assertNotNull(graph.getNode("mem_001"));
    }

    @Test
    @DisplayName("addNode skips null entry")
    void addNodeSkipsNull() {
        graph.addNode(null);
        assertEquals(0, graph.nodeCount());
    }

    @Test
    @DisplayName("removeNode removes node and connected edges")
    void removeNodeRemovesEdges() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.TEMPORAL, 0.8);

        assertEquals(2, graph.nodeCount());
        assertEquals(1, graph.edgeCount());

        graph.removeNode("mem_001");
        assertEquals(1, graph.nodeCount());
        assertEquals(0, graph.edgeCount());
    }

    // ── Edge CRUD ──

    @Test
    @DisplayName("addEdge creates edge between valid nodes")
    void addEdgeCreatesEdge() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.CAUSAL, 0.9);

        assertEquals(1, graph.edgeCount());
        assertEquals(0.9, graph.getEdges().get(0).weight(), 0.001);
    }

    @Test
    @DisplayName("addEdge skips when node missing")
    void addEdgeSkipsMissingNode() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        graph.addNode(a);
        graph.addEdge("mem_001", "nonexistent", MemoryEdge.RelationType.TEMPORAL, 0.5);

        assertEquals(0, graph.edgeCount());
    }

    // ── Salience ──

    @Test
    @DisplayName("computeSalience zero for empty entry")
    void salienceZeroForEmpty() {
        MemoryEntry e = entry("mem_001", "x", 1000, 1, null, null);
        double s = graph.computeSalience(e);
        assertEquals(0.0, s, 0.001);
    }

    @Test
    @DisplayName("computeSalience with keyLearnings adds 0.3")
    void salienceWithLearnings() {
        MemoryEntry e = entry("mem_001", "发现钻石", 1000, 1, List.of("记录了矿产分布信息"), null);
        double s = graph.computeSalience(e);
        assertTrue(s >= 0.3);
    }

    @Test
    @DisplayName("computeSalience with skills and learnings adds correctly")
    void salienceWithSkillsAndLearnings() {
        MemoryEntry e = entry("mem_001", "战斗挖矿", 1000, 1, List.of("战斗"), List.of("mine", "combat", "craft"));
        double s = graph.computeSalience(e);
        assertTrue(s >= 0.5 && s <= 0.52);
    }

    @Test
    @DisplayName("computeSalience with time gap anomaly adds 0.3")
    void salienceWithTimeGap() {
        MemoryEntry first = entry("mem_001", "first", 1000, 1, null, null);
        graph.computeSalience(first);

        MemoryEntry second = entry("mem_002", "second", 500000, 1, List.of("事件"), null);
        double s = graph.computeSalience(second);
        assertTrue(s >= 0.6);
    }

    // ── Edge inference ──

    @Test
    @DisplayName("inferTemporalEdge for same gameDay")
    void inferTemporalSameDay() {
        MemoryEntry a = entry("mem_001", "挖矿", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "挖矿继续", 2000, 1, List.of("记录了行动"), List.of("mine", "combat", "craft"));
        b.preferencesUpdated.put("work", 0.8);
        graph.addNode(a);
        graph.addNode(b);
        graph.inferEdges(List.of(a), b, null);

        boolean found = graph.getEdges().stream()
                .anyMatch(e -> e.type() == MemoryEdge.RelationType.TEMPORAL);
        assertTrue(found);
    }

    @Test
    @DisplayName("inferCausalEdge with learning-skill overlap")
    void inferCausalOverlap() {
        MemoryEntry a = entry("mem_001", "craft iron_pickaxe", 1000, 1, null, List.of("craft"));
        MemoryEntry b = entry("mem_002", "use pickaxe to mine", 2000, 1, List.of("记录了craft技能优化"), List.of("mine", "combat"));
        b.preferencesUpdated.put("work", 0.8);
        graph.addNode(a);
        graph.addNode(b);
        graph.inferEdges(List.of(a), b, null);

        boolean found = graph.getEdges().stream()
                .anyMatch(e -> e.type() == MemoryEdge.RelationType.CAUSAL);
        assertTrue(found);
    }

    @Test
    @DisplayName("inferEdges skips below salience threshold")
    void inferEdgesSkipsLowSalience() {
        MemoryEntry a = entry("mem_001", "普通事件", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "普通事件2", 2000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.inferEdges(List.of(a), b, null);

        assertEquals(0, graph.edgeCount());
    }

    // ── Graph traversal ──

    @Test
    @DisplayName("traverse BFS along edges")
    void traverseBFS() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        MemoryEntry c = entry("mem_003", "C", 3000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.TEMPORAL, 0.8);
        graph.addEdge("mem_002", "mem_003", MemoryEdge.RelationType.TEMPORAL, 0.8);

        List<MemoryNode> result = graph.traverse("mem_001", MemoryEdge.RelationType.TEMPORAL, 3);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("traverse returns empty for unknown start")
    void traverseEmptyForUnknown() {
        List<MemoryNode> result = graph.traverse("unknown", MemoryEdge.RelationType.TEMPORAL, 3);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findSimilar returns edges sorted by weight")
    void findSimilarSorted() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        MemoryEntry c = entry("mem_003", "C", 3000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.SIMILARITY, 0.6);
        graph.addEdge("mem_001", "mem_003", MemoryEdge.RelationType.SIMILARITY, 0.9);

        List<MemoryNode> similar = graph.findSimilar("mem_001", 5);
        assertEquals(2, similar.size());
    }

    @Test
    @DisplayName("traceCausalChain upstream")
    void traceCausalUpstream() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.CAUSAL, 0.8);

        List<MemoryNode> upstream = graph.traceCausalChain("mem_002", true);
        assertEquals(1, upstream.size());
        assertEquals("mem_001", upstream.get(0).memoryId());
    }

    @Test
    @DisplayName("traceCausalChain downstream")
    void traceCausalDownstream() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.CAUSAL, 0.8);

        List<MemoryNode> downstream = graph.traceCausalChain("mem_001", false);
        assertEquals(1, downstream.size());
        assertEquals("mem_002", downstream.get(0).memoryId());
    }

    @Test
    @DisplayName("getTimeline returns memories for gameDay sorted")
    void getTimelineSorted() {
        MemoryEntry a = entry("mem_001", "早", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "晚", 3000, 1, null, null);
        MemoryEntry c = entry("mem_003", "其他天", 2000, 2, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);

        List<MemoryNode> day1 = graph.getTimeline(1);
        assertEquals(2, day1.size());
        assertEquals("mem_001", day1.get(0).memoryId());
        assertEquals("mem_002", day1.get(1).memoryId());
    }

    // ── Persistence ──

    @Test
    @DisplayName("save and load preserves nodes and edges")
    void saveAndLoad(@TempDir Path tempDir) {
        Path graphFile = tempDir.resolve("memory_graph.json");

        MemoryEntry a = entry("mem_001", "挖矿", 1000, 1, List.of("矿产分布"), List.of("mine"));
        MemoryEntry b = entry("mem_002", "战斗", 2000, 1, List.of("战斗经验"), List.of("combat"));
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.TEMPORAL, 0.85);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.CAUSAL, 0.7);

        graph.save(graphFile);
        assertTrue(tempDir.resolve("memory_graph.json").toFile().exists());

        MemoryGraph loaded = new MemoryGraph();
        loaded.load(graphFile);

        assertEquals(2, loaded.nodeCount());
        assertEquals(2, loaded.edgeCount());
        assertNotNull(loaded.getNode("mem_001"));
        assertEquals("挖矿", loaded.getNode("mem_001").summary());
    }

    @Test
    @DisplayName("load handles missing file gracefully")
    void loadMissingFile(@TempDir Path tempDir) {
        MemoryGraph loaded = new MemoryGraph();
        loaded.load(tempDir.resolve("nonexistent.json"));
        assertEquals(0, loaded.nodeCount());
        assertEquals(0, loaded.edgeCount());
    }

    @Test
    @DisplayName("save and load empty graph")
    void saveAndLoadEmpty(@TempDir Path tempDir) {
        Path graphFile = tempDir.resolve("empty_graph.json");
        graph.save(graphFile);

        MemoryGraph loaded = new MemoryGraph();
        loaded.load(graphFile);
        assertEquals(0, loaded.nodeCount());
        assertEquals(0, loaded.edgeCount());
    }

    @Test
    @DisplayName("load skips version mismatch")
    void loadVersionMismatch(@TempDir Path tempDir) {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        graph.addNode(a);
        graph.save(tempDir.resolve("memory_graph.json"));

        MemoryGraph loaded = new MemoryGraph();
        loaded.setLastSavedDay(42);
        loaded.load(tempDir.resolve("memory_graph.json"));
        assertEquals(1, loaded.nodeCount());
    }

    @Test
    @DisplayName("getEdges for specific memory returns all connected edges")
    void getEdgesForMemory() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        MemoryEntry c = entry("mem_003", "C", 3000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.TEMPORAL, 0.8);
        graph.addEdge("mem_003", "mem_001", MemoryEdge.RelationType.CAUSAL, 0.7);

        List<MemoryEdge> connected = graph.getEdges("mem_001");
        assertEquals(2, connected.size());
    }

    @Test
    @DisplayName("relationType enum has all four types")
    void relationTypeValues() {
        assertEquals(4, MemoryEdge.RelationType.values().length);
        assertNotNull(MemoryEdge.RelationType.valueOf("CAUSAL"));
        assertNotNull(MemoryEdge.RelationType.valueOf("TEMPORAL"));
        assertNotNull(MemoryEdge.RelationType.valueOf("SIMILARITY"));
        assertNotNull(MemoryEdge.RelationType.valueOf("CONTRAST"));
    }

    // ── Phase 1: reinforcePath ──

    @Test
    @DisplayName("reinforcePath strengthens existing edge with positive delta")
    void reinforcePathStrengthensExistingEdge() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.SIMILARITY, 0.5);

        graph.reinforcePath(List.of("mem_001", "mem_002"), 0.5);

        assertEquals(0.55, graph.getEdges().get(0).weight(), 0.001);
    }

    @Test
    @DisplayName("reinforcePath creates new SIMILARITY edge when none exists")
    void reinforcePathCreatesNewEdge() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);

        graph.reinforcePath(List.of("mem_001", "mem_002"), 0.5);

        assertEquals(1, graph.edgeCount());
        assertEquals(MemoryEdge.RelationType.SIMILARITY, graph.getEdges().get(0).type());
        assertEquals(0.325, graph.getEdges().get(0).weight(), 0.001);
        // 0.3 + 0.5*0.05 = 0.325
    }

    @Test
    @DisplayName("reinforcePath with negative delta weakens existing edge")
    void reinforcePathNegativeDelta() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.CAUSAL, 0.8);

        graph.reinforcePath(List.of("mem_001", "mem_002"), -0.5);

        assertEquals(0.75, graph.getEdges().get(0).weight(), 0.001);
    }

    @Test
    @DisplayName("reinforcePath with single element does nothing")
    void reinforcePathSingleElement() {
        graph.reinforcePath(List.of("mem_001"), 0.5);
        assertEquals(0, graph.edgeCount());
    }

    // ── Phase 1: findNodeIdsByReflex ──

    @Test
    @DisplayName("findNodeIdsByReflex returns ids from index")
    void findNodeIdsByReflexReturnsIds() {
        graph.setReflexToNodes(Map.of("mine", List.of("mem_001", "mem_002"),
                "craft", List.of("mem_002")));

        List<String> result = graph.findNodeIdsByReflex("reflex_mine_iron");
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("findNodeIdsByReflex returns empty list for unknown reflex")
    void findNodeIdsByReflexUnknown() {
        graph.setReflexToNodes(Map.of("mine", List.of("mem_001")));
        assertTrue(graph.findNodeIdsByReflex("reflex_unknown").isEmpty());
    }

    // ── Phase 2: traverse with activation decay ──

    @Test
    @DisplayName("traverse with minWeight respects depth limit")
    void traverseMinWeightDepthLimit() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        MemoryEntry c = entry("mem_003", "C", 3000, 1, null, null);
        MemoryEntry d = entry("mem_004", "D", 4000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addNode(d);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.TEMPORAL, 0.8);
        graph.addEdge("mem_002", "mem_003", MemoryEdge.RelationType.TEMPORAL, 0.8);
        graph.addEdge("mem_003", "mem_004", MemoryEdge.RelationType.TEMPORAL, 0.8);

        Set<String> result = graph.traverse("mem_001", MemoryEdge.RelationType.TEMPORAL, 2, 0.1);
        // depth=2 means 2 hops: mem_001 → mem_002 (hop1), mem_002 → mem_003 (hop2)
        // mem_004 at hop3, excluded
        assertEquals(2, result.size());
        assertTrue(result.contains("mem_002"));
        assertTrue(result.contains("mem_003"));
    }

    @Test
    @DisplayName("traverse with minWeight filters low-weight edges")
    void traverseMinWeightFilter() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        MemoryEntry c = entry("mem_003", "C", 3000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.SIMILARITY, 0.9);
        graph.addEdge("mem_002", "mem_003", MemoryEdge.RelationType.SIMILARITY, 0.2);

        Set<String> result = graph.traverse("mem_001", MemoryEdge.RelationType.SIMILARITY, 3, 0.5);
        assertEquals(1, result.size());
        assertTrue(result.contains("mem_002"));
    }

    @Test
    @DisplayName("traverse with minWeight returns empty for unknown start")
    void traverseMinWeightUnknown() {
        Set<String> result = graph.traverse("unknown", null, 3, 0.1);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("traverse with minWeight respects relation type filter")
    void traverseMinWeightTypeFilter() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        MemoryEntry c = entry("mem_003", "C", 3000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.CAUSAL, 0.8);
        graph.addEdge("mem_002", "mem_003", MemoryEdge.RelationType.SIMILARITY, 0.8);

        Set<String> result = graph.traverse("mem_001", MemoryEdge.RelationType.CAUSAL, 3, 0.1);
        assertEquals(1, result.size());
        assertTrue(result.contains("mem_002"));
    }

    // ── Phase 3: skeleton export/import ──

    @Test
    @DisplayName("exportSkeleton only keeps nodes with enough incident edges")
    void exportSkeletonFilters() {
        MemoryEntry a = entry("mem_001", "挖到钻石 at (100,64,200)", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "用钻石做工具", 2000, 1, null, null);
        MemoryEntry c = entry("mem_003", "孤立事件", 3000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.CAUSAL, 0.8);
        graph.addEdge("mem_002", "mem_001", MemoryEdge.RelationType.SIMILARITY, 0.6);
        // mem_003 has no edges → excluded

        Map<String, Object> skeleton = graph.exportSkeleton();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) skeleton.get("skeleton_nodes");
        assertNotNull(nodes);
        assertEquals(2, nodes.size());
    }

    @Test
    @DisplayName("importSkeleton merges edges with max weight")
    void importSkeletonMergesEdges() {
        MemoryEntry a = entry("mem_001", "挖矿", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "熔炼", 2000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.CAUSAL, 0.6);

        Map<String, Object> skeleton = Map.of(
                "skeleton_nodes", List.of(
                        Map.of("label", "挖矿"),
                        Map.of("label", "熔炼")
                ),
                "skeleton_edges", List.of(
                        Map.of("from", "mem_001", "to", "mem_002", "type", "CAUSAL", "weight", 0.9)
                )
        );

        graph.importSkeleton(skeleton);
        assertEquals(1, graph.edgeCount());
        assertEquals(0.9, graph.getEdges().get(0).weight(), 0.001);
    }

    @Test
    @DisplayName("deinstanceLabel removes position and timestamp from summary")
    void deinstanceLabelCleansSummary() {
        assertEquals("挖到钻石", MemoryGraph.deinstanceLabel("挖到钻石 at (100,64,200)"));
        assertEquals("发现矿洞", MemoryGraph.deinstanceLabel("发现矿洞 14:30:00"));
        assertEquals("完成目标", MemoryGraph.deinstanceLabel("完成目标 2024-01-15"));
        assertEquals("hello", MemoryGraph.deinstanceLabel("hello"));
    }

    @Test
    @DisplayName("reinforcePath keeps edge weight above minimum")
    void reinforcePathClampsBelowMinimum() {
        MemoryEntry a = entry("mem_001", "A", 1000, 1, null, null);
        MemoryEntry b = entry("mem_002", "B", 2000, 1, null, null);
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge("mem_001", "mem_002", MemoryEdge.RelationType.SIMILARITY, 0.5);

        // Large negative delta should not push below 0.1
        graph.reinforcePath(List.of("mem_001", "mem_002"), -10.0);

        assertEquals(0.1, graph.getEdges().get(0).weight(), 0.001);
    }
}
