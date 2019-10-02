package org.onos.dcnet;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.net.Device;
import org.onosproject.net.host.HostService;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * ONOS App implementing DCLab forwarding scheme.
 */
public class DCLab {
    /** Logs information, errors, and warnings during runtime. */
    private static Logger log = LoggerFactory.getLogger(DCLab.class);

    /** Service used to register and obtain host information. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private TopologyService topologyService;

    public class RestPaths {
        private static final String PROTO = "http://";
        private static final String AUTH = "/cgi-bin/luci/rpc/auth";
        private static final String UCI = "/cgi-bin/luci/rpc/uci";
        private static final String SYS = "/cgi-bin/luci/rpc/sys";
    }

    public void analyzeTopology() {
        Topology topo = topologyService.currentTopology();
        TopologyGraph graph = topologyService.getGraph(topo);
        log.info(graph.toString());
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
