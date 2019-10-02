package org.onos.dcnet;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.onlab.graph.Weight;
import org.onosproject.net.Device;
import org.onosproject.net.Path;
import org.onosproject.net.topology.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;

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

    public static void analyzeTopology(TopologyService topologyService) {
        Topology topo = topologyService.currentTopology();
        TopologyGraph topoGraph = topologyService.getGraph(topo);
        Graph<TopologyVertex, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        for (TopologyVertex v : topoGraph.getVertexes()) {
            graph.addVertex(v);
        }
        for (TopologyEdge e : topoGraph.getEdges()) {
            graph.addEdge(e.src(), e.dst());
        }
        log.info(graph.toString());
        //createLinearTopos(graph, 3);
    }
/*
    public static void createLinearTopos(Graph<TopologyVertex, DefaultEdge> graph, int size) {
        Topology topo = topologyService.currentTopology();
        TopologyGraph graph = topologyService.getGraph(topo);
        Weight max = null;
        Path longest = null;
        for (TopologyVertex v : graph.getVertexes()) {
            for (TopologyVertex u : graph.getVertexes()) {
                Set<Path> paths = topologyService.getPaths(topo, v.deviceId(), u.deviceId());
                if(!paths.isEmpty()) {
                    Path p = paths.iterator().next();
                    if(max == null || p.weight().compareTo(max) > 0) {
                        max = p.weight();
                        longest = p;
                    }
                }
            }
        }
    }
*/
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
