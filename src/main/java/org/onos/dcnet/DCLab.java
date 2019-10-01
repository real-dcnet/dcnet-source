package org.onos.dcnet;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import org.onosproject.net.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DCLab {
    /** Logs information, errors, and warnings during runtime. */
    private static Logger log = LoggerFactory.getLogger(DCLab.class);

    public static void configureSwitch(final Device device) {
        Client client = ClientBuilder.newClient();
        JsonObject request = new JsonObject()
                .add("params", new JsonArray().add("admin").add("admin"))
                .add("jsonrpc", "2.0")
                .add("id", 1)
                .add("method", "login");
        /*
        Response response = client
                .target("http://10.0.1.99/cgi-bin/luci/rpc/auth")
                .queryParam("params", new JsonArray().add("admin").add("admin"))
                .queryParam("jsonrpc", "2.0")
                .queryParam("id", 1)
                .queryParam("method", "login")
                .request(MediaType.APPLICATION_JSON)
                .get();
        String token = response.readEntity(String.class);
        log.info(Integer.toString(response.getStatus()));
        log.info(token);
        */
        try {
            URL url = new URL("http://10.0.1.99/cgi-bin/luci/rpc/auth");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");

            String input = request.toString();
            log.info(input);

            OutputStream os = conn.getOutputStream();
            os.write(input.getBytes());
            os.flush();
            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

            String output;
            log.info("Output from Server .... \n");
            while ((output = br.readLine()) != null) {
                log.info(output);
            }

            conn.disconnect();
        }
        catch(Exception e) {
            log.error("URL Exception");
        }
    }
}
