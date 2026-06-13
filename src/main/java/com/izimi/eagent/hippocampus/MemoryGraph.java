package com.izimi.eagent.hippocampus;

import com.izimi.eagent.bayesian.BayesianModule;
import com.izimi.eagent.util.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MemoryGraph {

    private static final double SALIENCE_THRESHOLD = 0.6;
    private static final int MAX_INFER_PER_CALL = 5;
    private static final double EDGE_THRESHOLD = 0.7;
    private static final long DEFAULT_AVERAGE_INTERVAL_MS = 60000;

    private final Map<String, MemoryNode> nodes = new LinkedHashMap<>();
    private final List<MemoryEdge> edges = new ArrayList<>();
    private transient long lastMemoryTimestamp = 0;
    private transient long totalIntervalMs = 0;
    private transient int intervalCount = 0;
    private transient final Map<String, List<String>> reflexToNodes = new HashMap<>();
    private int lastSavedDay = 0;

    // ── Node CRUD ──

    public void addNode(MemoryEntry entry) {
        if (entry == null || entry.id == null) return;
        nodes.put(entry.id, new MemoryNode(entry.id, entry.summary, entry.timestamp, entry.gameDay));
        if (entry.relatedSkills != null) {
            for (String skill : entry.relatedSkills) {
                reflexToNodes.computeIfAbsent(skill, k -> new ArrayList<>()).add(entry.id);
            }
        }
    }

    public void removeNode(String memoryId) {
        if (memoryId == null) return;
        nodes.remove(memoryId);
        edges.removeIf(e -> e.fromId().equals(memoryId) || e.toId().equals(memoryId));
        reflexToNodes.values().forEach(list -> list.remove(memoryId));
        reflexToNodes.values().removeIf(List::isEmpty);
    }

    public MemoryNode getNode(String memoryId) {
        return nodes.get(memoryId);
    }

    public Collection<MemoryNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    // ── Edge CRUD ──

    public void addEdge(String fromId, String toId, MemoryEdge.RelationType type, double weight) {
        if (!nodes.containsKey(fromId) || !nodes.containsKey(toId)) return;
        edges.add(new MemoryEdge(fromId, toId, type, Math.max(0, Math.min(1, weight))));
    }

    public List<MemoryEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public List<MemoryEdge> getEdges(String memoryId) {
        return edges.stream()
                .filter(e -> e.fromId().equals(memoryId) || e.toId().equals(memoryId))
                .collect(Collectors.toList());
    }

    // ── Salience ──

    public double computeSalience(MemoryEntry entry) {
        if (entry == null) return 0.0;
        double score = 0.0;

        if (entry.keyLearnings != null && !entry.keyLearnings.isEmpty()) {
            score += 0.3;
        }

        if (entry.relatedSkills != null && !entry.relatedSkills.isEmpty()) {
            score += Math.min(entry.relatedSkills.size() * 0.1, 0.2);
        }

        if (entry.preferencesUpdated != null && !entry.preferencesUpdated.isEmpty()) {
            score += 0.2;
        }

        if (lastMemoryTimestamp > 0) {
            long gap = entry.timestamp - lastMemoryTimestamp;
            if (gap > getAverageMemoryInterval() * 2) {
                score += 0.3;
            }
        }
        lastMemoryTimestamp = entry.timestamp;

        return Math.min(score, 1.0);
    }

    private long getAverageMemoryInterval() {
        if (intervalCount == 0) return DEFAULT_AVERAGE_INTERVAL_MS;
        return totalIntervalMs / intervalCount;
    }

    // ── Edge inference ──

    public void inferEdges(List<MemoryEntry> recentEntries, MemoryEntry newEntry, BayesianModule bayes) {
        if (newEntry == null || newEntry.id == null) return;

        double salience = computeSalience(newEntry);
        if (salience < SALIENCE_THRESHOLD) return;

        if (recentEntries == null || recentEntries.isEmpty()) return;

        List<MemoryEntry> candidates = recentEntries.stream()
                .limit(MAX_INFER_PER_CALL)
                .collect(Collectors.toList());

        for (MemoryEntry existing : candidates) {
            if (existing == null || existing.id == null || existing.id.equals(newEntry.id)) continue;

            inferTemporalEdge(newEntry, existing);
            inferCausalEdge(newEntry, existing);
            inferSimilarityEdge(newEntry, existing, bayes);
            inferContrastEdge(newEntry, existing);
        }
    }

    private void inferTemporalEdge(MemoryEntry newEntry, MemoryEntry existing) {
        if (newEntry.gameDay == existing.gameDay) {
            double weight = 1.0 - Math.min(
                    Math.abs(newEntry.timestamp - existing.timestamp) / (double) getAverageMemoryInterval(), 0.95);
            addEdge(newEntry.id, existing.id, MemoryEdge.RelationType.TEMPORAL, weight);
        }
    }

    private void inferCausalEdge(MemoryEntry newEntry, MemoryEntry existing) {
        Set<String> newLearnings = new HashSet<>();
        if (newEntry.keyLearnings != null) newLearnings.addAll(newEntry.keyLearnings);

        Set<String> existingSkills = new HashSet<>();
        if (existing.relatedSkills != null) existingSkills.addAll(existing.relatedSkills);

        long overlap = newLearnings.stream()
                .filter(l -> existingSkills.stream().anyMatch(s -> l.contains(s) || s.contains(l)))
                .count();

        if (overlap > 0) {
            double weight = Math.min(0.5 + overlap * 0.15, 1.0);
            addEdge(existing.id, newEntry.id, MemoryEdge.RelationType.CAUSAL, weight);
        }
    }

    private void inferSimilarityEdge(MemoryEntry newEntry, MemoryEntry existing, BayesianModule bayes) {
        if (bayes == null) return;
        double similarity = bayes.predictRelevance(newEntry, existing);
        if (similarity > EDGE_THRESHOLD) {
            addEdge(newEntry.id, existing.id, MemoryEdge.RelationType.SIMILARITY, similarity);
        }
    }

    private void inferContrastEdge(MemoryEntry newEntry, MemoryEntry existing) {
        Set<String> newWords = extractWords(newEntry.summary);
        Set<String> existingWords = extractWords(existing.summary);

        boolean hasFail = newWords.contains("失败") || existingWords.contains("失败");
        boolean hasSuccess = newWords.contains("成功") || existingWords.contains("成功");

        if (hasFail && hasSuccess) {
            addEdge(newEntry.id, existing.id, MemoryEdge.RelationType.CONTRAST, 0.3);
        }
    }

    private Set<String> extractWords(String text) {
        if (text == null) return Collections.emptySet();
        return Arrays.stream(text.split("[\\s:,;.，；：、]+"))
                .filter(w -> w.length() >= 2)
                .collect(Collectors.toSet());
    }

    // ── Graph traversal ──

    public List<MemoryNode> traverse(String startId, MemoryEdge.RelationType type, int maxDepth) {
        if (!nodes.containsKey(startId) || maxDepth <= 0) return List.of();

        Set<String> visited = new HashSet<>();
        List<MemoryNode> result = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startId);
        visited.add(startId);

        int depth = 0;
        while (!queue.isEmpty() && depth < maxDepth) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                String current = queue.poll();
                for (MemoryEdge edge : edges) {
                    String neighbor = null;
                    if (edge.fromId().equals(current) && (type == null || edge.type() == type)) {
                        neighbor = edge.toId();
                    } else if (edge.toId().equals(current) && (type == null || edge.type() == type)) {
                        neighbor = edge.fromId();
                    }
                    if (neighbor != null && !visited.contains(neighbor)) {
                        visited.add(neighbor);
                        MemoryNode node = nodes.get(neighbor);
                        if (node != null) result.add(node);
                        queue.add(neighbor);
                    }
                }
            }
            depth++;
        }

        return result;
    }

    public List<MemoryNode> findSimilar(String memoryId, int topK) {
        if (!nodes.containsKey(memoryId)) return List.of();

        return edges.stream()
                .filter(e -> e.type() == MemoryEdge.RelationType.SIMILARITY
                        && (e.fromId().equals(memoryId) || e.toId().equals(memoryId)))
                .sorted((a, b) -> Double.compare(b.weight(), a.weight()))
                .limit(topK > 0 ? topK : 5)
                .map(e -> {
                    String targetId = e.fromId().equals(memoryId) ? e.toId() : e.fromId();
                    return nodes.get(targetId);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<MemoryNode> traceCausalChain(String memoryId, boolean upstream) {
        if (!nodes.containsKey(memoryId)) return List.of();

        List<MemoryNode> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(memoryId);
        visited.add(memoryId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (MemoryEdge edge : edges) {
                if (edge.type() != MemoryEdge.RelationType.CAUSAL) continue;
                String neighbor = upstream
                        ? (edge.toId().equals(current) ? edge.fromId() : null)
                        : (edge.fromId().equals(current) ? edge.toId() : null);
                if (neighbor != null && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    MemoryNode node = nodes.get(neighbor);
                    if (node != null) result.add(node);
                    queue.add(neighbor);
                }
            }
        }

        return result;
    }

    public List<MemoryNode> getTimeline(int gameDay) {
        return nodes.values().stream()
                .filter(n -> n.gameDay() == gameDay)
                .sorted(Comparator.comparingLong(MemoryNode::timestamp))
                .collect(Collectors.toList());
    }

    // ── Edge ranking (Bayesian) ──

    public List<MemoryEdge> rankEdges(List<MemoryEdge> inputEdges, String queryContext, BayesianModule bayes) {
        if (inputEdges == null || inputEdges.isEmpty()) return List.of();
        if (queryContext == null || bayes == null) return new ArrayList<>(inputEdges);

        return inputEdges.stream()
                .sorted((a, b) -> {
                    MemoryNode nodeA = nodes.get(a.toId());
                    MemoryNode nodeB = nodes.get(b.toId());
                    double scoreA = nodeA != null ? bayes.predictRelevance(queryContext, nodeA.summary()) : 0.0;
                    double scoreB = nodeB != null ? bayes.predictRelevance(queryContext, nodeB.summary()) : 0.0;
                    return Double.compare(scoreB, scoreA);
                })
                .collect(Collectors.toList());
    }

    // ── Persistence ──

    public void load(Path path) {
        if (!Files.exists(path)) return;

        Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
        if (data == null) return;

        Object versionObj = data.get("version");
        if (!"1.0".equals(versionObj)) return;

        if (data.containsKey("lastSavedDay")) {
            lastSavedDay = ((Number) data.get("lastSavedDay")).intValue();
        }

        nodes.clear();
        Object rawNodes = data.get("nodes");
        if (rawNodes instanceof List) {
            for (Object raw : (List<?>) rawNodes) {
                if (raw instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) raw;
                    String id = (String) map.get("memoryId");
                    String summary = (String) map.get("summary");
                    long ts = map.containsKey("timestamp") ? ((Number) map.get("timestamp")).longValue() : 0;
                    int gd = map.containsKey("gameDay") ? ((Number) map.get("gameDay")).intValue() : 0;
                    if (id != null) {
                        nodes.put(id, new MemoryNode(id, summary, ts, gd));
                    }
                }
            }
        }

        edges.clear();
        Object rawEdges = data.get("edges");
        if (rawEdges instanceof List) {
            for (Object raw : (List<?>) rawEdges) {
                if (raw instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) raw;
                    String from = (String) map.get("fromId");
                    String to = (String) map.get("toId");
                    String typeStr = (String) map.get("type");
                    double w = map.containsKey("weight") ? ((Number) map.get("weight")).doubleValue() : 0.5;
                    long ca = map.containsKey("createdAt") ? ((Number) map.get("createdAt")).longValue() : 0;
                    long ra = map.containsKey("lastReinforcedAt") ? ((Number) map.get("lastReinforcedAt")).longValue() : 0;
                    if (from != null && to != null && typeStr != null) {
                        try {
                            MemoryEdge.RelationType type = MemoryEdge.RelationType.valueOf(typeStr);
                            MemoryEdge edge = ca > 0
                                    ? new MemoryEdge(from, to, type, w, ca, ra > 0 ? ra : ca)
                                    : new MemoryEdge(from, to, type, w);
                            edges.add(edge);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        }

    }

    public void save(Path path) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", "1.0");
        data.put("lastSavedDay", lastSavedDay);

        List<Map<String, Object>> nodeList = new ArrayList<>();
        for (MemoryNode node : nodes.values()) {
            Map<String, Object> nm = new LinkedHashMap<>();
            nm.put("memoryId", node.memoryId());
            nm.put("summary", node.summary());
            nm.put("timestamp", node.timestamp());
            nm.put("gameDay", node.gameDay());
            nodeList.add(nm);
        }
        data.put("nodes", nodeList);

        List<Map<String, Object>> edgeList = new ArrayList<>();
        for (MemoryEdge edge : edges) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("fromId", edge.fromId());
            em.put("toId", edge.toId());
            em.put("type", edge.type().name());
            em.put("weight", edge.weight());
            em.put("createdAt", edge.createdAt());
            em.put("lastReinforcedAt", edge.lastReinforcedAt());
            edgeList.add(em);
        }
        data.put("edges", edgeList);

        JsonUtil.writeToFileSafeAtomic(path, data);
    }

    public void setLastSavedDay(int day) {
        this.lastSavedDay = day;
    }

    public int getLastSavedDay() {
        return lastSavedDay;
    }

    public void updateTimestamps(long timestamp) {
        if (lastMemoryTimestamp > 0 && timestamp > lastMemoryTimestamp) {
            long gap = timestamp - lastMemoryTimestamp;
            totalIntervalMs += gap;
            intervalCount++;
        }
        lastMemoryTimestamp = timestamp;
    }

    // ── Reflex-Memory Index (Phase 1) ──

    public void rebuildReflexToNodesIndex(MemoryManager memoryManager) {
        reflexToNodes.clear();
        if (memoryManager == null) return;
        for (MemoryNode node : nodes.values()) {
            MemoryEntry entry = memoryManager.getEntry(node.memoryId());
            if (entry != null && entry.relatedSkills != null) {
                for (String skill : entry.relatedSkills) {
                    reflexToNodes.computeIfAbsent(skill, k -> new ArrayList<>()).add(node.memoryId());
                }
            }
        }
    }

    public List<String> findNodeIdsByReflex(String reflexId) {
        String reflexBody = reflexId.startsWith("reflex_") ? reflexId.substring(7) : reflexId;
        List<String> result = new ArrayList<>();
        for (var entry : reflexToNodes.entrySet()) {
            String skill = entry.getKey();
            if (reflexBody.equals(skill) || reflexBody.startsWith(skill + "_")) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    // ── Hebbian Reinforcement (Phase 1) ──

    public void reinforcePath(List<String> nodeIds, double delta) {
        if (nodeIds == null || nodeIds.size() < 2) return;
        double edgeDelta = delta * 0.1;
        for (int i = 0; i < nodeIds.size() - 1; i++) {
            String from = nodeIds.get(i);
            String to = nodeIds.get(i + 1);
            MemoryEdge existing = findEdge(from, to);
            if (existing != null) {
                existing.updateWeight(edgeDelta);
            } else {
                double initWeight = Math.max(0.1, Math.min(1.0, 0.3 + delta * 0.05));
                addEdge(from, to, MemoryEdge.RelationType.SIMILARITY, initWeight);
            }
        }
    }

    private MemoryEdge findEdge(String fromId, String toId) {
        for (MemoryEdge e : edges) {
            if (e.fromId().equals(fromId) && e.toId().equals(toId)) return e;
            if (e.fromId().equals(toId) && e.toId().equals(fromId)) return e;
        }
        return null;
    }

    // ── Diffusion Activation (Phase 2) ──

    public Set<String> traverse(String startId, MemoryEdge.RelationType type, int maxDepth, double minWeight) {
        if (!nodes.containsKey(startId) || maxDepth <= 0) return Set.of();

        Set<String> activated = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();
        Map<String, Integer> depthMap = new HashMap<>();
        queue.add(startId);
        depthMap.put(startId, 0);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int curDepth = depthMap.get(current);
            if (curDepth >= maxDepth) continue;

            for (MemoryEdge edge : edges) {
                if (edge.weight() < minWeight) continue;
                if (type != null && edge.type() != type) continue;

                String neighbor;
                if (edge.fromId().equals(current)) {
                    neighbor = edge.toId();
                } else if (edge.toId().equals(current)) {
                    neighbor = edge.fromId();
                } else {
                    continue;
                }

                if (!depthMap.containsKey(neighbor)) {
                    int neighborDepth = curDepth + 1;
                    depthMap.put(neighbor, neighborDepth);
                    if (neighborDepth <= maxDepth) {
                        activated.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }

        return activated;
    }

    // ── Skeleton Export/Import (Phase 3) ──

    private static final double SKELETON_EDGE_WEIGHT = 0.5;
    private static final int SKELETON_MIN_INCIDENT_EDGES = 2;
    private static final int SKELETON_VERSION = 1;

    public Map<String, Object> exportSkeleton() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", SKELETON_VERSION);

        Map<String, Integer> incidentCount = new HashMap<>();
        for (MemoryEdge edge : edges) {
            if (edge.weight() >= SKELETON_EDGE_WEIGHT) {
                incidentCount.merge(edge.fromId(), 1, Integer::sum);
                incidentCount.merge(edge.toId(), 1, Integer::sum);
            }
        }

        Set<String> selectedIds = incidentCount.entrySet().stream()
                .filter(e -> e.getValue() >= SKELETON_MIN_INCIDENT_EDGES)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        List<Map<String, Object>> skeletonNodes = new ArrayList<>();
        for (MemoryNode node : nodes.values()) {
            if (!selectedIds.contains(node.memoryId())) continue;
            Map<String, Object> sn = new LinkedHashMap<>();
            sn.put("label", deinstanceLabel(node.summary()));
            skeletonNodes.add(sn);
        }
        result.put("skeleton_nodes", skeletonNodes);

        List<Map<String, Object>> skeletonEdges = new ArrayList<>();
        for (MemoryEdge edge : edges) {
            if (edge.weight() < SKELETON_EDGE_WEIGHT) continue;
            if (!selectedIds.contains(edge.fromId()) || !selectedIds.contains(edge.toId())) continue;
            Map<String, Object> se = new LinkedHashMap<>();
            se.put("from", edge.fromId());
            se.put("to", edge.toId());
            se.put("type", edge.type().name());
            se.put("weight", edge.weight());
            skeletonEdges.add(se);
        }
        result.put("skeleton_edges", skeletonEdges);

        return result;
    }

    @SuppressWarnings("unchecked")
    public void importSkeleton(Map<String, Object> skeleton) {
        if (skeleton == null) return;

        Object rawNodes = skeleton.get("skeleton_nodes");
        if (rawNodes instanceof List) {
            for (Object raw : (List<?>) rawNodes) {
                if (raw instanceof Map) {
                    Map<String, Object> sn = (Map<String, Object>) raw;
                    String label = (String) sn.get("label");
                    if (label != null) {
                        String sid = "skel_" + label.hashCode();
                        if (!nodes.containsKey(sid)) {
                            nodes.put(sid, new MemoryNode(sid, label, System.currentTimeMillis(), 0));
                        }
                    }
                }
            }
        }

        Object rawEdges = skeleton.get("skeleton_edges");
        if (rawEdges instanceof List) {
            for (Object raw : (List<?>) rawEdges) {
                if (raw instanceof Map) {
                    Map<String, Object> se = (Map<String, Object>) raw;
                    String from = (String) se.get("from");
                    String to = (String) se.get("to");
                    String typeStr = (String) se.get("type");
                    double weight = se.containsKey("weight") ? ((Number) se.get("weight")).doubleValue() : 0.5;
                    if (from != null && to != null && typeStr != null && nodes.containsKey(from) && nodes.containsKey(to)) {
                        try {
                            MemoryEdge.RelationType type = MemoryEdge.RelationType.valueOf(typeStr);
                            MemoryEdge existing = findEdge(from, to);
                            if (existing != null) {
                                if (weight > existing.weight()) existing.setWeight(weight);
                            } else {
                                addEdge(from, to, type, weight);
                            }
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        }
    }

    static String deinstanceLabel(String summary) {
        if (summary == null) return "";
        String result = summary
                .replaceAll(" at \\([^)]+\\)", "")
                .replaceAll(" \\d+:\\d+:\\d+", "")
                .replaceAll(" \\d{4}-\\d{2}-\\d{2}", "")
                .replaceAll("\\[.*?\\]", "")
                .trim();
        return result.isEmpty() ? summary : result;
    }

    void setReflexToNodes(Map<String, List<String>> index) {
        reflexToNodes.clear();
        if (index != null) reflexToNodes.putAll(index);
    }
}
