package org.onos.dclab;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.apache.felix.scr.annotations.*;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.link.LinkAdminService;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.topology.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * ONOS App implementing DCLab forwarding scheme.
 */
@Component(immediate = true)
public class DClab {
    /** Logs information, errors, and warnings during runtime. */
    private static Logger log = LoggerFactory.getLogger(DClab.class);

    /** Service used to manage flow rules installed on switches. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private CoreService coreService;

    /** Service used to manage flow rules installed on switches. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceService deviceService;

    /** Service used to manage flow rules installed on switches. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private TopologyService topologyService;

    /** Service used to manage flow rules installed on switches. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceAdminService deviceAdminService;

    /** Service used to manage flow rules installed on switches. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private LinkAdminService linkAdminService;

    /** Used to identify flow rules belonging to DCnet. */
    private ApplicationId appId;

    public static class RestPaths {
        private static final String PROTO = "http://";
        private static final String AUTH = "/cgi-bin/luci/rpc/auth";
        private static final String UCI = "/cgi-bin/luci/rpc/uci";
        private static final String SYS = "/cgi-bin/luci/rpc/sys";
    }

    public static class QueueEntry implements Comparable<QueueEntry> {
        private int key;
        private int value;

        public QueueEntry(int key, int value) {
            this.key = key;
            this.value = value;
        }

        public int getKey() {
            return this.key;
        }

        public int getValue() {
            return this.value;
        }

        public int compareTo(QueueEntry entry) {
            return this.key - entry.getKey();
        }
    }

    /** Allows application to be started by ONOS controller. */
    @Activate
    public void activate() {
        appId = coreService.registerApplication("org.onosproject.dclab");
        analyzeTopology();
        log.info("Started");
    }

    /** Allows application to be stopped by ONOS controller. */
    @Deactivate
    public void deactivate() {
        log.info("Stopped");
    }

    public void analyzeTopology() {
        Topology topo = topologyService.currentTopology();
        TopologyGraph topoGraph = topologyService.getGraph(topo);
        Graph<TopologyVertex, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        for (TopologyVertex v : topoGraph.getVertexes()) {
            graph.addVertex(v);
        }
        for (TopologyEdge e : topoGraph.getEdges()) {
            if (DijkstraShortestPath.findPathBetween(graph, e.src(), e.dst()) == null) {
                graph.addEdge(e.src(), e.dst());
            }
        }
        log.info(graph.toString());
        //List<Graph<TopologyVertex, DefaultEdge>> topos = createLinearTopos(graph, 3);
        List<Graph<TopologyVertex, DefaultEdge>> topos = createStarTopos(graph, 3);
        log.info(topos.toString());
        disablePorts(topoGraph, topos);
    }

    public void disablePorts(TopologyGraph graphOld, List<Graph<TopologyVertex, DefaultEdge>> graphNew) {
        for (TopologyVertex v : graphOld.getVertexes()) {
            boolean exit = false;
            for (Graph<TopologyVertex, DefaultEdge> g : graphNew) {
                for (TopologyVertex u : g.vertexSet()) {
                    if (v.equals(u)) {
                        for (TopologyEdge e : graphOld.getEdgesFrom(v)) {
                            boolean exitTwo = false;
                            for (DefaultEdge f : g.outgoingEdgesOf(u)) {
                                if (e.dst().equals(g.getEdgeTarget(f))) {
                                    exitTwo = true;
                                    break;
                                }
                            }
                            if (!exitTwo) {
                                linkAdminService.removeLink(e.link().src(), e.link().dst());
                            }
                        }
                        exit = true;
                        break;
                    }
                }
                if (exit) {
                    break;
                }
            }
            if (!exit) {
                linkAdminService.removeLinks(v.deviceId());
            }
        }
    }

    public List<Graph<TopologyVertex, DefaultEdge>> createLinearTopos(Graph<TopologyVertex, DefaultEdge> graph, int size) {
        while(true) {
            int max = 0;
            GraphPath longest = null;
            for (TopologyVertex v : graph.vertexSet()) {
                for (TopologyVertex u : graph.vertexSet()) {
                    GraphPath path = DijkstraShortestPath.findPathBetween(graph, v, u);
                    if (path != null && path.getLength() > max) {
                        max = path.getLength();
                        longest = path;
                    }
                }
            }
            if(max <= size || longest == null) {
                break;
            }
            int count = 1;
            for(Object e : longest.getEdgeList()) {
                if(count == size) {
                    graph.removeEdge((DefaultEdge) e);
                    count = 1;
                }
                else {
                    count++;
                }
            }
        }
        List<Graph<TopologyVertex, DefaultEdge>> topos = new ArrayList<>();
        List<TopologyVertex> addedVertices = new ArrayList<>();
        for (TopologyVertex v : graph.vertexSet()) {
            for (TopologyVertex u : graph.vertexSet()) {
                GraphPath path = DijkstraShortestPath.findPathBetween(graph, v, u);
                if (path != null && path.getLength() == size) {
                    boolean exit = false;
                    for (Object k : path.getVertexList()) {
                        if (addedVertices.contains(k)) {
                            exit = true;
                            break;
                        }
                    }
                    if (exit) {
                        break;
                    }
                    addedVertices.addAll(path.getVertexList());
                    topos.add(path.getGraph());
                }
            }
        }
        return topos;
    }

    public List<Graph<TopologyVertex, DefaultEdge>> createStarTopos(Graph<TopologyVertex, DefaultEdge> graph, int size) {
        List<List<TopologyVertex>> components = new ArrayList<>();
        List<List<DefaultEdge>> compEdges = new ArrayList<>();
        List<List<TopologyVertex>> finalComp = new ArrayList<>();
        List<List<DefaultEdge>> finalEdges = new ArrayList<>();
        List<Integer> points = new ArrayList<>();
        for (TopologyVertex v : graph.vertexSet()) {
            if (graph.degreeOf(v) == 1) {
                List<TopologyVertex> component = new ArrayList<>();
                component.add(v);
                components.add(component);
                compEdges.add(new ArrayList<>());
                points.add(1);
            }
        }
        Graph<TopologyVertex, DefaultEdge> partitions = new SimpleGraph<>(DefaultEdge.class);
        for (TopologyVertex v : graph.vertexSet()) {
            partitions.addVertex(v);
        }
        for (DefaultEdge e : graph.edgeSet()) {
            partitions.addEdge(graph.getEdgeSource(e), graph.getEdgeTarget(e));
        }
        while (true) {
            List<List<Integer>> compDist = new ArrayList<>();
            List<List<List<TopologyVertex>>> closestVert = new ArrayList<>();
            for (int i = 0; i < components.size(); i++) {
                compDist.add(new ArrayList<>());
                closestVert.add(new ArrayList<>());
                for (int j = 0; j < components.size(); j++) {
                    compDist.get(i).add(Integer.MAX_VALUE);
                    closestVert.get(i).add(new ArrayList<>());
                }
            }

            /* Find shortest distance between all pairs of components */
            for (int i = 0; i < components.size() - 1; i++) {
                for (int j = i + 1; j < components.size(); j++) {
                    for (TopologyVertex v : components.get(i)) {
                        for (TopologyVertex u : components.get(j)) {
                            GraphPath path = DijkstraShortestPath.findPathBetween(partitions, v, u);
                            if (path == null) {
                                continue;
                            }
                            int dist = path.getLength();
                            if (dist < compDist.get(i).get(j)) {
                                compDist.get(i).set(j, dist);
                                if (closestVert.get(i).get(j).size() > 0) {
                                    closestVert.get(i).get(j).set(0, v);
                                    closestVert.get(i).get(j).set(1, u);
                                }
                                else {
                                    closestVert.get(i).get(j).add(v);
                                    closestVert.get(i).get(j).add(u);
                                }
                            }

                            if (dist < compDist.get(j).get(i)) {
                                compDist.get(j).set(i, dist);
                                if (closestVert.get(j).get(i).size() > 0) {
                                    closestVert.get(j).get(i).set(0, u);
                                    closestVert.get(j).get(i).set(1, v);
                                }
                                else {
                                    closestVert.get(j).get(i).add(u);
                                    closestVert.get(j).get(i).add(v);
                                }
                            }
                        }
                    }
                }
            }

            /* Put distances into a minheap */
            List<PriorityQueue<QueueEntry>> compQueue = new ArrayList<>();
            for (int i = 0; i < components.size(); i++) {
                compQueue.add(new PriorityQueue<>());
                for (int j = 0; j < components.size(); j++) {
                    compQueue.get(i).add(new QueueEntry(compDist.get(i).get(j), j));
                }
            }

            Map<TopologyVertex, Boolean> matched = new HashMap<>();
            List<List<TopologyVertex>> tempComp = new ArrayList<>();
            List<List<DefaultEdge>> tempEdges = new ArrayList<>();
            List<Integer> tempPoints = new ArrayList<>();
            // TODO: Gale-Shapley Matching
            boolean changed = false;
            while (true) {
                int minDist = Integer.MAX_VALUE;
                GraphPath minPath = null;
                int minI = 0;
                int minJ = 0;
                for (int i = 0; i < compQueue.size(); i++) {
                    if (compQueue.get(i).peek() != null && compQueue.get(i).peek().getKey() < minDist) {
                        TopologyVertex v = closestVert.get(i).get(compQueue.get(i).peek().getValue()).get(0);
                        TopologyVertex u = closestVert.get(i).get(compQueue.get(i).peek().getValue()).get(1);
                        GraphPath path = DijkstraShortestPath.findPathBetween(partitions, v, u);
                        boolean exit = false;
                        for (Object x : path.getVertexList()) {
                            if (matched.containsKey((TopologyVertex) x)) {
                                exit = true;
                                break;
                            }
                        }
                        if (!exit) {
                            minDist = compQueue.get(i).peek().getKey();
                            minPath = path;
                            minI = i;
                            minJ = compQueue.get(i).peek().getValue();
                            changed = true;
                        }
                    }
                }
                if (minPath == null) {
                    break;
                }
                int newPoints = points.get(minI) + points.get(minJ);
                if (newPoints >= size) {
                    finalComp.add(new ArrayList<>());
                    finalEdges.add(new ArrayList<>());
                    for (Object x : minPath.getVertexList()) {
                        Set<DefaultEdge> edges = new HashSet<>(partitions.edgesOf((TopologyVertex) x));
                        partitions.removeAllEdges(edges);
                        partitions.removeVertex((TopologyVertex) x);
                        finalComp.get(finalComp.size() - 1).add((TopologyVertex) x);
                        matched.put((TopologyVertex) x, true);
                    }
                    for (Object e : minPath.getEdgeList()) {
                        finalEdges.get(finalEdges.size() - 1).add((DefaultEdge) e);
                    }
                    for (TopologyVertex x : components.get(minI)) {
                        if (!partitions.containsVertex(x)) {
                            continue;
                        }
                        Set<DefaultEdge> edges = new HashSet<>(partitions.edgesOf(x));
                        partitions.removeAllEdges(edges);
                        partitions.removeVertex(x);
                        finalComp.get(finalComp.size() - 1).add(x);
                        matched.put(x, true);
                    }
                    for (DefaultEdge e : compEdges.get(minI)) {
                        finalEdges.get(finalEdges.size() - 1).add(e);
                    }
                    for (TopologyVertex x : components.get(minJ)) {
                        if (!partitions.containsVertex(x)) {
                            continue;
                        }
                        Set<DefaultEdge> edges = new HashSet<>(partitions.edgesOf(x));
                        partitions.removeAllEdges(edges);
                        partitions.removeVertex(x);
                        finalComp.get(finalComp.size() - 1).add(x);
                        matched.put(x, true);
                    }
                    for (DefaultEdge e : compEdges.get(minJ)) {
                        finalEdges.get(finalEdges.size() - 1).add(e);
                    }
                }
                else {
                    tempComp.add(new ArrayList<>());
                    tempEdges.add(new ArrayList<>());
                    for (Object x : minPath.getVertexList()) {
                        tempComp.get(tempComp.size() - 1).add((TopologyVertex) x);
                        matched.put((TopologyVertex) x, true);
                    }
                    for (Object e : minPath.getEdgeList()) {
                        tempEdges.get(tempEdges.size() - 1).add((DefaultEdge) e);
                    }
                    for (TopologyVertex x : components.get(minI)) {
                        tempComp.get(tempComp.size() - 1).add(x);
                        matched.put(x, true);
                    }
                    for (DefaultEdge e : compEdges.get(minI)) {
                        tempEdges.get(tempEdges.size() - 1).add(e);
                    }
                    for (TopologyVertex x : components.get(minJ)) {
                        tempComp.get(tempComp.size() - 1).add(x);
                        matched.put(x, true);
                    }
                    for (DefaultEdge e : compEdges.get(minJ)) {
                        tempEdges.get(tempEdges.size() - 1).add(e);
                    }
                    tempPoints.add(newPoints);
                }
                for (int i = 0; i < components.size(); i++) {
                    if (i != minI && i != minJ) {
                        tempComp.add(components.get(i));
                        tempEdges.add(compEdges.get(i));
                        tempPoints.add(points.get(i));
                    }
                }
                components = tempComp;
                compEdges = tempEdges;
                points = tempPoints;
                break;
            }
            if (!changed) {
                break;
            }
        }
        // TODO: Connect vertexes in final components with edges
        List<Graph<TopologyVertex, DefaultEdge>> topos = new ArrayList<>();
        for (int i = 0; i < finalComp.size(); i++) {
            topos.add(new SimpleGraph<>(DefaultEdge.class));
            log.info(finalComp.get(i).toString() + "\n\n");
            for (TopologyVertex v : finalComp.get(i)) {
                topos.get(i).addVertex(v);
            }
            for (DefaultEdge e : finalEdges.get(i)) {
                topos.get(i).addEdge(graph.getEdgeSource(e), graph.getEdgeTarget(e));
            }
        }
        return topos;
    }

    public void configureSwitch(final Device device) {
        String token = getToken();
        log.info(token);
    }

    public String getToken() {
        String[] params = {"admin", "admin"};
        JsonObject ret = restCall(RestPaths.PROTO + "10.0.1.99" + RestPaths.AUTH, params, "login", null);
        if (ret != null) {
            return ret.getString("result", "");
        }
        return "";
    }

    public JsonObject restCall(String path, String[] params, String method, String token) {
        JsonObject request = new JsonObject()
                .add("jsonrpc", "2.0")
                .add("id", 1)
                .add("method", method);
        if (params.length == 1) {
            request.add("params", params[0]);
        }
        else {
            JsonArray parameters = new JsonArray();
            for (String param : params) {
                parameters.add(param);
            }
            request.add("params", parameters);
        }
        try {
            String target = path;
            if (token != null) {
                target += "?auth=" + token;
            }
            URL url = new URL(target);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");

            String input = request.toString();
            log.info(input);

            OutputStream os = conn.getOutputStream();
            os.write(input.getBytes());
            os.flush();

            /* Don't wait for response for apply methods since switch must restart its network processes */
            if (!method.equals("apply")) {
                BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

                String output;
                StringBuilder result = new StringBuilder();
                while ((output = br.readLine()) != null) {
                    log.info(output);
                    result.append(output);
                }
                conn.disconnect();

                return Json.parse(result.toString()).asObject();
            }
            return null;
        }
        catch(Exception e) {
            log.error("URL Exception");
            return null;
        }

    }
}