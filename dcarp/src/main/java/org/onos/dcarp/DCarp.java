/*
 * Copyright 2015 Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onos.dcarp;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.*;
import org.onosproject.app.ApplicationService;
import org.onosproject.core.Application;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * ONOS App implementing DCnet ARP resolution.
 */
@Component(immediate = true)
public class DCarp {
    /** Service used to register DCnet application in ONOS. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private CoreService coreService;

    /** Service used to request and manage packets punted
     * by leaf and data center switches. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private PacketService packetService;

    /** Service used to register and obtain device information. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceService deviceService;

    /** Service used to register and obtain host information. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private HostService hostService;

    /** Service used to manage flow rules installed on switches. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private FlowRuleService flowRuleService;

    /** Service used to manage flow rules installed on switches. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private ApplicationService applicationService;

    /** Holds information about switches parsed from JSON. */
    private static final class SwitchEntry {
        /** Human readable name for the switch. */
        private String name;

        /** MAC address of the switch. */
        private byte[] mac;

        /** Location of switch on data center hierarchy. */
        private int level;

        /** Identifier for data center that contains switch. */
        private int dc;

        /** Identifier for pod that contains switch. */
        private int pod;

        /** Identifier at leaf level for a switch if it is one. */
        private int leaf;

        private SwitchEntry(final String switchName,
                            final byte[] switchMac,
                            final int switchLevel,
                            final int dcLoc,
                            final int podLoc,
                            final int leafLoc) {
            this.name = switchName;
            this.mac = switchMac;
            this.level = switchLevel;
            this.dc = dcLoc;
            this.pod = podLoc;
            this.leaf = leafLoc;
        }

        private byte[] getMac() {
            return this.mac;
        }

        private int getLevel() {
            return this.level;
        }

        private int getDc() {
            return this.dc;
        }

        private int getPod() {
            return this.pod;
        }

        private int getLeaf() {
            return this.leaf;
        }
    }

    private static final class HostEntry {
        /** Human readable name for the host. */
        private String name;

        /** Location based mac address to use in forwarding. */
        private byte[] rmac;

        /** Real mac address of the switch. */
        private byte[] idmac;

        private HostEntry(final String hostName,
                          final byte[] hostRmac,
                          final byte[] hostIdmac) {
            this.name = hostName;
            this.rmac = hostRmac;
            this.idmac = hostIdmac;
        }

        private byte[] getRmac() {
            return this.rmac;
        }

        private byte[] getIdmac() {
            return this.idmac;
        }

    }

    /** Logs information, errors, and warnings during runtime. */
    private static Logger log = LoggerFactory.getLogger(DCarp.class);

    /** Location where configuration information can be found.
     * Change this as necessary if configuration JSONs are stored elsewhere */
    private static String configLoc =
            System.getProperty("user.home") + "/dcnet-source/config/mininet/";

    private static String controllerIP = "10.0.0.8";

    /** Macro for data center egress switches. */
    private static final int DC = 0;

    /** Macro for super spine switches. */
    private static final int SUPER = 1;

    /** Macro for spine switches. */
    private static final int SPINE = 2;

    /** Macro for leaf switches. */
    private static final int LEAF = 3;

    /** Priority to use when installing flow rules.
     * Should be higher than reactive forwarding rules */
    private static final int BASE_PRIO = 50000;

    /** Number of links going up for each leaf in each data center. */
    private List<Integer> lfRadixUp = new ArrayList<>();

    /** Maps Chassis ID to a switch entry. */
    private Map<String, SwitchEntry> switchDB = new TreeMap<>();

    /** Maps IP address to a host entry. */
    private Map<Integer, HostEntry> hostDB = new TreeMap<>();

    /** Used to identify flow rules belonging to DCnet. */
    private ApplicationId appId;

    /** Listens for hosts that are added to the network. */
    private final HostListener hostListener = new DCarp.InternalHostListener();

    /** Handler for packets that are passed to controller by switches. */
    private final PacketProcessor packetProcessor = new ArpPacketProcessor();

    /** Selector for IPv4 traffic to intercept. */
    private final TrafficSelector intercept = DefaultTrafficSelector.
            builder()
            .matchEthType(Ethernet.TYPE_ARP)
            .build();

    /** Initializes application by reading configuration files for hosts,
     * switches, and topology design. */
    private void init() {
        lfRadixUp = new ArrayList<>();
        switchDB = new TreeMap<>();
        hostDB = new TreeMap<>();

        try {
            /* Setup switch database by reading fields in switch config JSON */
            JsonObject config = Json.parse(new BufferedReader(
                    new FileReader(configLoc + "switch_config.json"))
            ).asObject();
            addSwitchConfigs(config.get("dcs").asArray(), DC);
            addSwitchConfigs(config.get("supers").asArray(), SUPER);
            addSwitchConfigs(config.get("spines").asArray(), SPINE);
            addSwitchConfigs(config.get("leaves").asArray(), LEAF);

            /* Setup topology by reading fields in topology config JSON */
            config = Json.parse(new BufferedReader(
                    new FileReader(configLoc + "top_config.json"))
            ).asObject();
            addDcConfigs(config.get("config").asArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Parses the JSONs describing each switch.
     * @param configs   Array of JSONs that hold config information
     * @param level     Level in hierarchy of the switches being added
     */
    private void addSwitchConfigs(final JsonArray configs, final int level) {
        for (JsonValue obj : configs) {
            JsonObject config = obj.asObject();
            SwitchEntry entry = new SwitchEntry(
                    config.get("name").asString(),
                    strToMac(config.get("mac").asString()),
                    level,
                    config.get("dc").asInt(),
                    config.get("pod").asInt(),
                    config.get("leaf").asInt());
            switchDB.put(config.get("id").asString(), entry);
        }
    }

    /**
     * Parses the JSONs describing each data center topology.
     * @param configs   Array of JSONs that hold config information
     */
    private void addDcConfigs(final JsonArray configs) {
        for (JsonValue obj : configs) {
            JsonObject config = obj.asObject();
            lfRadixUp.add(config.get("lf_radix_up").asInt());
        }
    }

    /** Allows application to be started by ONOS controller. */
    @Activate
    public void activate() {
        init();
        appId = coreService.registerApplication("org.onosproject.dcarp");
        for (Host h : hostService.getHosts()) {
            configureHost(h);
        }
        packetService.addProcessor(packetProcessor, BASE_PRIO);
        packetService.requestPackets(
                intercept,
                PacketPriority.CONTROL,
                appId,
                Optional.empty());
        hostService.addListener(hostListener);
        log.info("Started");
    }

    /** Allows application to be stopped by ONOS controller. */
    @Deactivate
    public void deactivate() {
        packetService.removeProcessor(packetProcessor);
        log.info("Stopped");
    }

    /**
     * Translates a MAC address String into byte array representation.
     * @param address   String representation of MAC address
     * @return          Byte array representation of MAC address
     */
    private byte[] strToMac(final String address) {

        byte[] bytes = new byte[6];
        String[] octets = address.split(":");
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) (Integer.parseInt(octets[i], 16));
        }
        return bytes;
    }

    /**
     * Handles ARP packets received by leaf switch.
     * @param context   Contains extra information sent to controller
     * @param eth       Packet that was sent to controller
     * @param device    Switch that sent the packet
     * @param entry     Information about switch that sent packet
     */
    private void processPacketArp(
            final PacketContext context,
            final Ethernet eth,
            final Device device,
            final SwitchEntry entry) {
        ARP request = (ARP) (eth.getPayload());

        byte[] ip = request.getSenderProtocolAddress();
        Ip4Address ipAddr = Ip4Address.valueOf(ip);
        HostEntry hostSrc = hostDB.get(ipAddr.toInt());
        if (hostSrc == null) {
            return;
        }
        byte[] bytesSrc = hostSrc.getRmac();
        int dcSrc = (((int) bytesSrc[0]) << 4) + (bytesSrc[1] >> 4);
        int podSrc = ((bytesSrc[1] & 0xF) << 8) + bytesSrc[2];
        int leafSrc = (((int) bytesSrc[3]) << 4) + (bytesSrc[4] >> 4);
        int port = ((bytesSrc[4] & 0xF) << 8) + bytesSrc[5] + lfRadixUp.get(entry.getDc()) + 1;

        if(dcSrc != entry.getDc() || podSrc != entry.getPod() || leafSrc != entry.getLeaf()) {
            /* ARP request did not originate from a host connected to this leaf */
            return;
        }

        ip = request.getTargetProtocolAddress();
        ipAddr = Ip4Address.valueOf(ip);
        HostEntry hostDst = hostDB.get(ipAddr.toInt());
        if (hostDst == null) {
            return;
        }
        Ethernet reply = ARP.buildArpReply(ipAddr, new MacAddress(hostDst.getIdmac()), eth);

        TrafficTreatment.Builder treatment = DefaultTrafficTreatment
                .builder()
                .setOutput(PortNumber.portNumber(port));
        OutboundPacket packet = new DefaultOutboundPacket(
                device.id(),
                treatment.build(),
                ByteBuffer.wrap(reply.serialize()));
        packetService.emit(packet);
        context.block();
    }

    /** Handler for packets sent to controller by leaf or dc switch. */
    private class ArpPacketProcessor implements PacketProcessor {
        /**
         * Overrides function for processing an incoming packet.
         * @param context   Contains information pertaining to packet
         */
        @Override
        public void process(final PacketContext context) {
            Ethernet eth = context.inPacket().parsed();
            if (eth.getEtherType() == Ethernet.TYPE_ARP) {
                Device device = deviceService.getDevice(context.inPacket()
                        .receivedFrom().deviceId());
                String id = device.chassisId().toString();
                SwitchEntry entry = switchDB.get(id);
                if (entry != null && entry.getLevel() == LEAF) {
                    log.info("Leaf received ARP packet with destination: "
                            + eth.getDestinationMAC().toString());
                    processPacketArp(context, eth, device, entry);
                }
            }
            if (eth.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipv4 = (IPv4) (eth.getPayload());
                int ip = ipv4.getDestinationAddress();
                log.info("Processing IPv4");
                if (ip == Ip4Address.valueOf(controllerIP).toInt()) {
                    String message = new String(eth.getPayload().getPayload().getPayload().serialize());
                    String[] addrs = message.split(":");
                    log.info("Message " + message);
                    if (addrs[0].equals("reactive")) {
                        Ip4Address vmIP = IpPrefix.valueOf(addrs[1]).address().getIp4Address();
                        HostEntry host = hostDB.get(vmIP.toInt());
                        log.info("Erasing rules for IP " + addrs[1]);
                        for (FlowRule f : flowRuleService.getFlowEntriesById(applicationService.getId("org.onosproject.fwd"))) {
                            EthCriterion selector = (EthCriterion) f.selector().getCriterion(Criterion.Type.ETH_SRC);
                            log.info(f.toString());
                            if (selector != null && Arrays.equals(selector.mac().toBytes(), host.getIdmac())) {
                                flowRuleService.removeFlowRules(f);
                            }
                            selector = (EthCriterion) f.selector().getCriterion(Criterion.Type.ETH_DST);
                            if (selector != null && Arrays.equals(selector.mac().toBytes(), host.getIdmac())) {
                                flowRuleService.removeFlowRules(f);
                            }
                        }
                    }
                    context.block();
                }
            }
        }
    }

    /**
     * Configures RMAC for a new host based on leaf it attached to
     * @param host  Host that was added
     */
    private void configureHost(final Host host) {
        HostLocation location = host.location();
        Device device = deviceService.getDevice(location.deviceId());
        SwitchEntry leaf = switchDB.get(device.chassisId().toString());
        int port = (int)(location.port().toLong() - lfRadixUp.get(leaf.getDc()) - 1);
        byte[] rmac = new byte[6];
        rmac[0] = (byte) ((leaf.getDc() >> 4) & 0x3F);
        rmac[1] = (byte) (((leaf.getDc() & 0xF) << 4) + ((leaf.getPod() >> 8) & 0xF));
        rmac[2] = (byte) (leaf.getPod() & 0xFF);
        rmac[3] = (byte) ((leaf.getLeaf() >> 4) & 0xFF);
        rmac[4] = (byte) (((leaf.getLeaf() & 0xF) << 4) + ((port >> 8) & 0xF));
        rmac[5] = (byte) (port & 0xFF);
        HostEntry hostEntry = new HostEntry(host.id().toString(), rmac, host.mac().toBytes());
        for (IpAddress ip : host.ipAddresses()) {
            log.info("Host with MAC address " + host.mac().toString()
                    + " and ip address " + ip.getIp4Address().toString()
                    + " configured with RMAC address " + new MacAddress(rmac).toString());
            hostDB.put(ip.getIp4Address().toInt(), hostEntry);
        }
    }

    /** Listener for hosts that are moved or removed. */
    private class InternalHostListener implements HostListener {
        /**
         * Overrides handler for events related to hosts.
         * @param hostEvent     Event involving a host
         */
        @Override
        public void event(final HostEvent hostEvent) {
            switch (hostEvent.type()) {
                case HOST_MOVED:
                case HOST_ADDED:
                    configureHost(hostEvent.subject());
                    break;
                default:
                    break;
            }
        }
    }
}
