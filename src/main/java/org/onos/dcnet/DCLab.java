package org.onos.dcnet;

import org.onosproject.net.Device;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class DCLab {
    public static void configureSwitch(final Device device) {
        Client client = ClientBuilder.newClient();
        Response response = client
                .target("http://10.0.1.99/cgi-bin/luci/rpc/auth")
                .queryParam("params", "admin", "admin")
                .queryParam("jsonrpc", "2.0")
                .queryParam("id", 1)
                .queryParam("method", "login")
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get();
        String token = response.getEntity().toString();
        System.out.println(token);
    }
}
