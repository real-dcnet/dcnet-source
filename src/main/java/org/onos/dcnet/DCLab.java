package org.onos.dcnet;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.onosproject.net.Device;
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
public class DCLab {
    /** Logs information, errors, and warnings during runtime. */
    private static Logger log = LoggerFactory.getLogger(DCLab.class);

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

    public static void analyzeTopology(TopologyService topologyService) {
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
        List<List<TopologyVertex>> topos = createLinearTopos(graph, 3);
        log.info(topos.toString());
    }

    public static List<List<TopologyVertex>> createLinearTopos(Graph<TopologyVertex, DefaultEdge> graph, int size) {
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
        List<List<TopologyVertex>> topos = new ArrayList<>();
        List<TopologyVertex> addedVertices = new ArrayList<>();
        for (TopologyVertex v : graph.vertexSet()) {
            for (TopologyVertex u : graph.vertexSet()) {
                GraphPath path = DijkstraShortestPath.findPathBetween(graph, v, u);
                if (path.getLength() == size) {
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
                    topos.add(path.getVertexList());
                }
            }
        }
        return topos;
    }

    public static List<List<TopologyVertex>> createStarTopos(Graph<TopologyVertex, DefaultEdge> graph, int size) {
        List<List<TopologyVertex>> components = new ArrayList<>();
        List<Integer> points = new ArrayList<>();
        for (TopologyVertex v : graph.vertexSet()) {
            if (graph.degreeOf(v) == 1) {
                List<TopologyVertex> component = new ArrayList<>();
                component.add(v);
                components.add(component);
                points.add(1);
            }
        }
        while (true) {
            List<List<Integer>> compDist = new ArrayList<>();
            for (int i = 0; i < components.size(); i++) {
                compDist.add(new ArrayList<>());
                for (int j = 0; j < components.size(); j++) {
                    compDist.get(i).add(Integer.MAX_VALUE);
                }
            }

            // TODO: Use a temporary graph so that edges can be removed during Gale-Shapley
            /* Find shortest distance between all pairs of components */
            for (int i = 0; i < components.size(); i++) {
                for (int j = i + 1; j < components.size(); j++) {
                    for (TopologyVertex v : components.get(i)) {
                        for (TopologyVertex u : components.get(j)) {
                            // TODO: Store closest nodes for each pair of components so they do not need to be found again
                            int dist = DijkstraShortestPath.findPathBetween(graph, v, u).getLength();
                            if (dist < compDist.get(i).get(j)) {
                                compDist.get(i).set(j, dist);
                            }
                            if (dist < compDist.get(j).get(i)) {
                                compDist.get(j).set(i, dist);
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

            List<Boolean> matched = new ArrayList<>();
            for (int i = 0; i < components.size(); i++) {
                matched.add(false);
            }
            List<List<TopologyVertex>> tempComp = new ArrayList<>();
            List<Integer> tempPoints = new ArrayList<>();
            // TODO: Gale-Shapley Matching
            while (true) {
                int minDist = Integer.MAX_VALUE;
                for (int i = 0; i < compQueue.size(); i++) {
                    if (compQueue.get(i).peek() != null && compQueue.get(i).peek().getKey() < minDist) {

                    }
                }
            }
        }
    }

    public static void configureSwitch(final Device device) {
        String token = getToken();
        log.info(token);
    }

    public static String getToken() {
        String[] params = {"admin", "admin"};
        JsonObject ret = restCall(RestPaths.PROTO + "10.0.1.99" + RestPaths.AUTH, params, "login", null);
        if (ret != null) {
            return ret.getString("result", "");
        }
        return "";
    }

    public static JsonObject restCall(String path, String[] params, String method, String token) {
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
