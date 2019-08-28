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
package org.onos.dcnet;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.*;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.core.GroupId;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.group.*;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eclipsesource.json.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * ONOS App implementing DCnet forwarding scheme.
 */
@Component(immediate = true)
public class DCnet {
    /** Service used to register DCnet application in ONOS. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private CoreService coreService;

    /** Service used to manage flow rules installed on switches. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private FlowRuleService flowRuleService;

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

    /** Service used to register and obtain group information. */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private GroupService groupService;

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
        /** Human readable name for the switch. */
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
    private static Logger log = LoggerFactory.getLogger(DCnet.class);

    /** Location where configuration information can be found.
     * Change this as necessary if configuration JSONs are stored elsewhere */
    private static String configLoc =
            System.getProperty("user.home") + "/dcnet-source/config/mininet/";

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

    /** Number of data centers. */
    private int dcCount = 0;

    /** Number of links going down for each data center egress. */
    private List<Integer> dcRadixDown = new ArrayList<>();

    /** Number of links going down for each super spine in each data center. */
    private List<Integer> ssRadixDown = new ArrayList<>();

    /** Number of links going up for each spine in each data center. */
    private List<Integer> spRadixUp = new ArrayList<>();

    /** Number of links going down for each spine in each data center. */
    private List<Integer> spRadixDown = new ArrayList<>();

    /** Number of links going up for each leaf in each data center. */
    private List<Integer> lfRadixUp = new ArrayList<>();

    /** Number of links going down for each leaf in each data center. */
    private List<Integer> lfRadixDown = new ArrayList<>();

    /** Keeps track of which ports should be ECMP'ed for
     * leaf switches in each data center. */
    private List<List<GroupBucket>> leafBuckets = new ArrayList<>();

    /** Keeps track of which ports should be ECMP'ed for
     * spine switches in each data center. */
    private List<List<GroupBucket>> spineBuckets = new ArrayList<>();

    /** Keeps track of which ports should be ECMP'ed for
     * egress switches in each data center. */
    private List<List<GroupBucket>> dcBuckets = new ArrayList<>();

    /** Counter for number of groups created to guarantee unique ids. */
    private int groupCount = 0;

    /** Maps Chassis ID to a switch entry. */
    private Map<String, SwitchEntry> switchDB = new TreeMap<>();

    /** Maps IP address to a host entry. */
    private Map<Integer, HostEntry> hostDB = new TreeMap<>();

    /** List of currently active flow rules for DCnet. */
    private List<FlowRule> installedFlows = new ArrayList<>();

    /** List of devices that have been added to DCnet. */
    private List<DeviceId> addedDevices = new ArrayList<>();

    /** Used to identify flow rules belonging to DCnet. */
    private ApplicationId appId;

    /** Allows deviceIDs to be hashed for creating group keys. */
    private static KryoNamespace appKryo = new KryoNamespace.Builder()
            .register(Integer.class)
            .register(DeviceId.class)
            .build("dcnet-app");

    /** Listens for switches that are added to the network. */
    private final DeviceListener deviceListener = new InternalDeviceListener();

    /** Listens for hosts that are added to the network. */
    private final HostListener hostListener = new InternalHostListener();

    /** Handler for packets that are passed to controller by switches. */
    private final PacketProcessor packetProcessor = new DCnetPacketProcessor();

    /** Selector for IPv4 traffic to intercept. */
    private final TrafficSelector intercept = DefaultTrafficSelector.
            builder()
            .matchEthType(Ethernet.TYPE_IPV4)
            .build();

    /** Initializes application by reading configuration files for hosts,
     * switches, and topology design. */
    private void init() {

        dcRadixDown = new ArrayList<>();
        ssRadixDown = new ArrayList<>();
        spRadixUp = new ArrayList<>();
        spRadixDown = new ArrayList<>();
        lfRadixUp = new ArrayList<>();
        lfRadixDown = new ArrayList<>();

        leafBuckets = new ArrayList<>();
        spineBuckets = new ArrayList<>();
        dcBuckets = new ArrayList<>();

        switchDB = new TreeMap<>();
        hostDB = new TreeMap<>();
        installedFlows = new ArrayList<>();
        addedDevices = new ArrayList<>();

        groupCount = 0;

        try {
            /* Setup switch database by reading fields in switch config JSON */
            JsonObject config = Json.parse(new BufferedReader(
                    new FileReader(configLoc + "switch_config.json"))
            ).asObject();
            addSwitchConfigs(config.get("dcs").asArray(), DC);
            addSwitchConfigs(config.get("supers").asArray(), SUPER);
            addSwitchConfigs(config.get("spines").asArray(), SPINE);
            addSwitchConfigs(config.get("leaves").asArray(), LEAF);

            /* Setup host database by reading fields in host config JSON */
            config = Json.parse(new BufferedReader(
                    new FileReader(configLoc + "host_config.json"))
            ).asObject();
            addHostConfigs(config.get("hosts").asArray());

            /* Setup topology by reading fields in topology config JSON */
            config = Json.parse(new BufferedReader(
                    new FileReader(configLoc + "top_config.json"))
            ).asObject();
            dcCount = config.get("dc_count").asInt();
            addDcConfigs(config.get("config").asArray());

            /* Create buckets for each switch type in each data center
                describing ports to use for ECMP */
            for (int d = 0; d < dcCount; d++) {
                leafBuckets.add(new ArrayList<>());
                for (int i = 1; i <= lfRadixUp.get(d); i++) {
                    TrafficTreatment.Builder treatment = DefaultTrafficTreatment
                            .builder()
                            .setOutput(PortNumber.portNumber(
                                    lfRadixDown.get(d) + i));
                    leafBuckets.get(d).add(DefaultGroupBucket
                            .createSelectGroupBucket(treatment.build()));
                }
                spineBuckets.add(new ArrayList<>());
                for (int i = 1; i <= spRadixUp.get(d); i++) {
                    TrafficTreatment.Builder treatment = DefaultTrafficTreatment
                            .builder()
                            .setOutput(PortNumber.portNumber(
                                    spRadixDown.get(d) + i));
                    spineBuckets.get(d).add(DefaultGroupBucket
                            .createSelectGroupBucket(treatment.build()));
                }
                dcBuckets.add(new ArrayList<>());
                for (int i = 1; i <= dcRadixDown.get(d); i++) {
                    TrafficTreatment.Builder treatment = DefaultTrafficTreatment
                            .builder()
                            .setOutput(PortNumber.portNumber(i));
                    dcBuckets.get(d).add(DefaultGroupBucket
                            .createSelectGroupBucket(treatment.build()));
                }
            }
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
     * Parses the JSONs describing each host.
     * @param configs   Array of JSONs that hold config information
     */
    private void addHostConfigs(final JsonArray configs) {
        for (JsonValue obj : configs) {
            JsonObject config = obj.asObject();
            HostEntry entry = new HostEntry(
                    config.get("name").asString(),
                    strToMac(config.get("rmac").asString()),
                    strToMac(config.get("idmac").asString()));
            hostDB.put(ipStrtoInt(config.get("ip").asString()), entry);
        }
    }

    /**
     * Parses the JSONs describing each data center topology.
     * @param configs   Array of JSONs that hold config information
     */
    private void addDcConfigs(final JsonArray configs) {
        for (JsonValue obj : configs) {
            JsonObject config = obj.asObject();
            dcRadixDown.add(config.get("dc_radix_down").asInt());
            ssRadixDown.add(config.get("ss_radix_down").asInt());
            spRadixUp.add(config.get("sp_radix_up").asInt());
            spRadixDown.add(config.get("sp_radix_down").asInt());
            lfRadixUp.add(config.get("lf_radix_up").asInt());
            lfRadixDown.add(config.get("lf_radix_down").asInt());
        }
    }

    /** Allows application to be started by ONOS controller. */
    @Activate
    public void activate() {
        init();
        appId = coreService.registerApplication("org.onosproject.dcnet");
        packetService.addProcessor(packetProcessor, BASE_PRIO);
        packetService.requestPackets(
                intercept,
                PacketPriority.CONTROL,
                appId,
                Optional.empty());
        deviceService.addListener(deviceListener);
        hostService.addListener(hostListener);
        log.info("Started");
    }

    /** Allows application to be stopped by ONOS controller. */
    @Deactivate
    public void deactivate() {

        packetService.removeProcessor(packetProcessor);
        flowRuleService.removeFlowRulesById(appId);
        deviceService.removeListener(deviceListener);
        hostService.removeListener(hostListener);
        for (DeviceId d : addedDevices) {
            groupService.purgeGroupEntries(d);
        }
        log.info("Stopped");
    }

    /**
     * Translates an IP String into integer representation.
     * @param ip    String representation of IP address
     * @return      Integer representation of IP address
     */
    private int ipStrtoInt(final String ip) {
        String[] bytes = ip.split("\\.");
        return (Integer.parseInt(bytes[0]) << 24)
                + (Integer.parseInt(bytes[1]) << 16)
                + (Integer.parseInt(bytes[2]) << 8)
                + Integer.parseInt(bytes[3]);
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
     * Creates rules for new packets received by leaf switch.
     * @param context   Contains extra information sent to controller
     * @param eth       Packet that was sent to controller
     * @param device    Switch that sent the packet
     * @param entry     Information about switch that sent packet
     */
    private void processPacketLeaf(
            final PacketContext context,
            final Ethernet eth,
            final Device device,
            final SwitchEntry entry) {

        IPv4 ip = (IPv4) (eth.getPayload());

        int ipDst = ip.getDestinationAddress();
        int ipSrc = ip.getSourceAddress();
        HostEntry hostDst = hostDB.get(ipDst);
        HostEntry hostSrc = hostDB.get(ipSrc);
        TrafficSelector.Builder selectorDst = DefaultTrafficSelector
                .builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(IpPrefix.valueOf(ipDst, 32));
        TrafficSelector.Builder selectorSrc = DefaultTrafficSelector
                .builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(IpPrefix.valueOf(ipSrc, 32));

        byte[] bytesDst = null;
        int dcDst = 0;
        int podDst = 0;
        int leafDst = 0;

        byte[] bytesSrc = null;
        int dcSrc = 0;
        int podSrc = 0;
        int leafSrc = 0;

        if (hostDst != null) {

            /* Obtain location information from RMAC address if it exists */
            bytesDst = hostDst.getRmac();
            dcDst = (((int) bytesDst[0]) << 4) + (bytesDst[1] >> 4);
            podDst = ((bytesDst[1] & 0xF) << 8) + bytesDst[2];
            leafDst = (((int) bytesDst[3]) << 4) + (bytesDst[4] >> 4);
        }

        if (hostDst != null && dcDst == entry.getDc()
                && podDst == entry.getPod() && leafDst == entry.getLeaf()) {
            /* If recipient is directly connected to leaf, translate ethernet
            destination back to recipients's and forward to it */
            int port = ((bytesDst[4] & 0xF) << 8) + bytesDst[5] + 1;
            MacAddress hostDstMac = new MacAddress(hostDst.getIdmac());
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment
                    .builder()
                    .setEthDst(hostDstMac)
                    .setOutput(PortNumber.portNumber(port));
            FlowRule flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .makePermanent()
                    .withSelector(selectorDst.build())
                    .withTreatment(treatment.build())
                    .forDevice(device.id())
                    .withPriority(BASE_PRIO + 1000)
                    .build();
            flowRuleService.applyFlowRules(flowRule);
            installedFlows.add(flowRule);

            /* Send packet to destination host */
            Ethernet modifiedMac = new Ethernet();
            modifiedMac.setEtherType(Ethernet.TYPE_IPV4)
                    .setSourceMACAddress(context.inPacket()
                            .parsed().getSourceMACAddress())
                    .setDestinationMACAddress(hostDstMac)
                    .setPayload(context.inPacket().parsed().getPayload());
            treatment = DefaultTrafficTreatment
                    .builder()
                    .setOutput(PortNumber.portNumber(port));
            OutboundPacket packet = new DefaultOutboundPacket(
                    device.id(),
                    treatment.build(),
                    ByteBuffer.wrap(modifiedMac.serialize()));
            packetService.emit(packet);
        } else {
            /* If recipient is connected elsewhere, translate ethernet
                destination to RMAC and forward to spines */
            GroupDescription groupDescription = null;
            for (GroupDescription g : groupService.getGroups(device.id())) {
                groupDescription = g;
            }
            GroupKey key = new DefaultGroupKey(appKryo
                    .serialize(Objects.hash(device)));
            if (groupDescription == null) {
                groupDescription = new DefaultGroupDescription(
                        device.id(),
                        GroupDescription.Type.SELECT,
                        new GroupBuckets(leafBuckets.get(entry.getDc())),
                        key,
                        groupCount++,
                        appId);
                groupService.addGroup(groupDescription);
            }
            MacAddress hostDstMac;
            if (hostDst != null) {
                hostDstMac = new MacAddress(hostDst.getRmac());
            } else {
                hostDstMac = new MacAddress(new byte[]{
                        (byte) 0x3F, (byte) 0xFF, (byte) 0xFF,
                        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
            }
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment
                    .builder()
                    .setEthDst(hostDstMac)
                    .group(new GroupId(groupDescription.givenGroupId()));
            FlowRule flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .makePermanent()
                    .withSelector(selectorDst.build())
                    .withTreatment(treatment.build())
                    .forDevice(device.id())
                    .withPriority(BASE_PRIO + 500)
                    .build();
            flowRuleService.applyFlowRules(flowRule);
            installedFlows.add(flowRule);

            /* Send packet to a random spine switch */
            Ethernet modifiedMac = new Ethernet();
            modifiedMac.setEtherType(Ethernet.TYPE_IPV4)
                    .setSourceMACAddress(context.inPacket()
                            .parsed().getSourceMACAddress())
                    .setDestinationMACAddress(hostDstMac)
                    .setPayload(context.inPacket().parsed().getPayload());
            treatment = DefaultTrafficTreatment
                    .builder()
                    .setOutput(PortNumber.portNumber(
                            lfRadixDown.get(entry.getDc()) + 1 + (int) (Math
                                    .random() * lfRadixUp.get(entry.getDc()))));
            OutboundPacket packet = new DefaultOutboundPacket(
                    device.id(),
                    treatment.build(),
                    ByteBuffer.wrap(modifiedMac.serialize()));
            packetService.emit(packet);
        }

        /* Handle translation for reverse traffic, towards source host*/
        if (hostSrc != null) {
            /* Obtain location information from RMAC address if it exists */
            bytesSrc = hostSrc.getRmac();
            dcSrc = (((int) bytesSrc[0]) << 4) + (bytesSrc[1] >> 4);
            podSrc = ((bytesSrc[1] & 0xF) << 8) + bytesSrc[2];
            leafSrc = (((int) bytesSrc[3]) << 4) + (bytesSrc[4] >> 4);
        }

        if (hostSrc != null && dcSrc == entry.getDc()
                && podSrc == entry.getPod() && leafSrc == entry.getLeaf()) {
            /* If sender is directly connected to leaf, translate ethernet
            destination back to recipients's and forward to it */
            int port = ((bytesSrc[4] & 0xF) << 8) + bytesSrc[5] + 1;
            MacAddress hostSrcMac = new MacAddress(hostSrc.getIdmac());
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment
                    .builder()
                    .setEthDst(hostSrcMac)
                    .setOutput(PortNumber.portNumber(port));
            FlowRule flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .makePermanent()
                    .withSelector(selectorSrc.build())
                    .withTreatment(treatment.build())
                    .forDevice(device.id())
                    .withPriority(BASE_PRIO + 1000)
                    .build();
            flowRuleService.applyFlowRules(flowRule);
            installedFlows.add(flowRule);
        } else {
            /* If sender is connected to another leaf, translate ethernet
                destination to RMAC and forward to spines */
            GroupDescription groupDescription = null;
            for (GroupDescription g : groupService.getGroups(device.id())) {
                groupDescription = g;
            }
            GroupKey key = new DefaultGroupKey(appKryo
                    .serialize(Objects.hash(device)));
            if (groupDescription == null) {
                groupDescription = new DefaultGroupDescription(
                        device.id(),
                        GroupDescription.Type.SELECT,
                        new GroupBuckets(leafBuckets.get(entry.getDc())),
                        key,
                        groupCount++,
                        appId);
                groupService.addGroup(groupDescription);
            }
            MacAddress hostSrcMac;
            if (hostSrc != null) {
                hostSrcMac = new MacAddress(hostSrc.getRmac());
            } else {
                hostSrcMac = new MacAddress(new byte[]{
                        (byte) 0x3F, (byte) 0xFF, (byte) 0xFF,
                        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
            }
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment
                    .builder()
                    .setEthDst(hostSrcMac)
                    .group(new GroupId(groupDescription.givenGroupId()));
            FlowRule flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .makePermanent()
                    .withSelector(selectorSrc.build())
                    .withTreatment(treatment.build())
                    .forDevice(device.id())
                    .withPriority(BASE_PRIO + 500)
                    .build();
            flowRuleService.applyFlowRules(flowRule);
            installedFlows.add(flowRule);
        }
        context.block();
    }

    /* Creates rules for packets with new IPv4 destination that a data center switch receives */
    private void processPacketDc(PacketContext context, Ethernet eth, Device device, SwitchEntry entry) {

        IPv4 ipv4 = (IPv4) (eth.getPayload());

        int ip = ipv4.getDestinationAddress();
        HostEntry host = hostDB.get(ip);
        if (host == null) {
            return;
        }

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder().matchInPort(PortNumber.portNumber(dcRadixDown.get(entry.getDc()) + dcCount))
                .matchEthType(Ethernet.TYPE_IPV4).matchIPDst(IpPrefix.valueOf(ip, 32));

        byte[] bytes = host.getRmac();
        int dc = (((int)bytes[0]) << 4) + (bytes[1] >> 4);
        MacAddress hostMac = new MacAddress(host.getRmac());

        if (dc == entry.getDc()) {
            GroupDescription groupDescription = null;
            for (GroupDescription g : groupService.getGroups(device.id())) {
                groupDescription = g;
            }
            GroupKey key = new DefaultGroupKey(appKryo.serialize(Objects.hash(device)));
            if (groupDescription == null) {
                groupDescription = new DefaultGroupDescription(device.id(), GroupDescription.Type.SELECT, new GroupBuckets(dcBuckets.get(entry.getDc())), key, groupCount++, appId);
                groupService.addGroup(groupDescription);
            }
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setEthDst(hostMac).group(new GroupId(groupDescription.givenGroupId()));
            FlowRule flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .makePermanent()
                    .withSelector(selector.build())
                    .withTreatment(treatment.build())
                    .forDevice(device.id())
                    .withPriority(BASE_PRIO + 500)
                    .build();
            flowRuleService.applyFlowRules(flowRule);
            installedFlows.add(flowRule);

            /* Send packet to a random super spine switch */
            Ethernet modifiedMac = new Ethernet();
            modifiedMac.setEtherType(Ethernet.TYPE_IPV4)
                    .setSourceMACAddress(context.inPacket().parsed().getSourceMACAddress())
                    .setDestinationMACAddress(hostMac)
                    .setPayload(context.inPacket().parsed().getPayload());
            treatment = DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(1 + (int) (Math.random() * dcRadixDown.get(entry.getDc()))));
            OutboundPacket packet = new DefaultOutboundPacket(device.id(), treatment.build(), ByteBuffer.wrap(modifiedMac.serialize()));
            packetService.emit(packet);
        }

        else {
            int temp = 1 + dc + dcRadixDown.get(entry.getDc()) ;
            if (entry.getDc() < dc) {
                temp--;
            }
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setEthDst(hostMac).setOutput(PortNumber.portNumber(temp));
            FlowRule flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .makePermanent()
                    .withSelector(selector.build())
                    .withTreatment(treatment.build())
                    .forDevice(device.id())
                    .withPriority(BASE_PRIO + 500)
                    .build();
            flowRuleService.applyFlowRules(flowRule);
            installedFlows.add(flowRule);

            /* Send packet to correct data center */
            Ethernet modifiedMac = new Ethernet();
            modifiedMac.setEtherType(Ethernet.TYPE_IPV4)
                    .setSourceMACAddress(context.inPacket().parsed().getSourceMACAddress())
                    .setDestinationMACAddress(hostMac)
                    .setPayload(context.inPacket().parsed().getPayload());
            treatment = DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(temp));
            OutboundPacket packet = new DefaultOutboundPacket(device.id(), treatment.build(), ByteBuffer.wrap(modifiedMac.serialize()));
            packetService.emit(packet);
        }
    }

    /* Intercepts packets sent to controller */
    private class DCnetPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            Ethernet eth = context.inPacket().parsed();
            if (eth.getEtherType() == Ethernet.TYPE_IPV4) {
                Device device = deviceService.getDevice(context.inPacket().receivedFrom().deviceId());
                String id = device.chassisId().toString();
                SwitchEntry entry = switchDB.get(id);
                if (entry != null) {
                    if (entry.getLevel() == LEAF) {
                        log.info("Leaf received packet with destination: " + eth.getDestinationMAC().toString());
                        processPacketLeaf(context, eth, device, entry);
                    } else if (entry.getLevel() == DC) {
                        log.info("DC received packet with destination: " + eth.getDestinationMAC().toString());
                        processPacketDc(context, eth, device, entry);
                    }
                }
            }
        }
    }

    /* Initializes flow rules for switch based on its level in topology */
    private synchronized void setupFlows(Device device) {

        String id = device.chassisId().toString();
        log.info("Chassis " + id + " connected");
        if (switchDB.containsKey(id)) {
            SwitchEntry entry = switchDB.get(id);
            log.info("Switch " + id + " connected");
            log.info("Level: " + entry.getLevel());
            log.info("DC: " + entry.getDc());
            log.info("Pod: " + entry.getPod());
            log.info("Leaf: " + entry.getLeaf());

            switch (entry.getLevel()) {
                case DC:
                    addFlowsDC(device);
                    break;
                case SUPER:
                    addFlowsSuper(device);
                    break;
                case SPINE:
                    addFlowsSpine(device);
                    break;
                case LEAF:
                    addFlowsLeaf(device);
                    break;
                default:
                    break;
            }
            addedDevices.add(device.id());
        }
    }

    /* Adds flows for data center switch to forward down to super spines and towards other data center switches */
    private void addFlowsDC(Device device) {

        String id = device.chassisId().toString();
        SwitchEntry entry = switchDB.get(id);

        /* Add rule to ECMP packets belonging in this data center towards super spines */
        int dc = entry.getDc();
        byte[] bytes = new byte[6];
        bytes[0] = (byte)((dc >> 4) & 0x3F);
        bytes[1] = (byte)((dc & 0xF) << 4);
        MacAddress eth = new MacAddress(bytes);
        MacAddress mask = new MacAddress(new byte[]{(byte) 0xFF, (byte) 0xF0, 0x00, 0x00, 0x00, 0x00});
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder().matchEthDstMasked(eth, mask).matchEthType(Ethernet.TYPE_IPV4);
        GroupDescription groupDescription = null;
        for(GroupDescription g : groupService.getGroups(device.id())) {
            groupDescription = g;
        }
        GroupKey key = new DefaultGroupKey(appKryo.serialize(Objects.hash(device)));
        if(groupDescription == null) {
            groupDescription = new DefaultGroupDescription(device.id(), GroupDescription.Type.SELECT, new GroupBuckets(dcBuckets.get(entry.getDc())), key, groupCount++, appId);
            groupService.addGroup(groupDescription);
        }
        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().group(new GroupId(groupDescription.givenGroupId()));
        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .makePermanent()
                .withSelector(selector.build())
                .withTreatment(treatment.build())
                .forDevice(device.id())
                .withPriority(BASE_PRIO + 1000)
                .build();
        flowRuleService.applyFlowRules(flowRule);

        /* Add rules to forward packets belonging to another data center to the correct one */
        for (int d = 0; d < dcCount; d++) {
            int port = d;
            if (d > dc) {
                port--;
            }
            else if (d == dc) {
                continue;
            }
            bytes = new byte[6];
            bytes[0] = (byte)((d >> 4) & 0x3F);
            bytes[1] = (byte)((d & 0xF) << 4);
            eth = new MacAddress(bytes);
            selector = DefaultTrafficSelector.builder().matchEthDstMasked(eth, mask).matchEthType(Ethernet.TYPE_IPV4);
            treatment = DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(dcRadixDown.get(dc) + port + 1));
            flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .makePermanent()
                    .withSelector(selector.build())
                    .withTreatment(treatment.build())
                    .forDevice(device.id())
                    .withPriority(BASE_PRIO + 500)
                    .build();
            flowRuleService.applyFlowRules(flowRule);
        }

        // TODO: Forward all other traffic to internet

        /* Adds default rule to let controller handle packets for connections to internet */
        selector = DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4);
        treatment = DefaultTrafficTreatment.builder().punt();
        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .makePermanent()
                .withSelector(selector.build())
                .withTreatment(treatment.build())
                .forDevice(device.id())
                .withPriority(BASE_PRIO + 100)
                .build();
        flowRuleService.applyFlowRules(flowRule);

        /* Adds rule to forward packets to internet if they have a reserved RMAC address */
        MacAddress reserved = new MacAddress(new byte[]{(byte) 0x3F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        MacAddress test = new MacAddress(new byte[]{(byte) 0xDC, (byte) 0xDC, (byte) 0xDC, (byte) 0x00, (byte) 0x00, (byte) 0x31});
        selector = DefaultTrafficSelector.builder().matchEthDst(reserved).matchEthType(Ethernet.TYPE_IPV4);
        treatment = DefaultTrafficTreatment.builder().setEthSrc(new MacAddress(entry.getMac())).setEthDst(test).setOutput(PortNumber.portNumber(dcRadixDown.get(dc) + dcCount));
        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .makePermanent()
                .withSelector(selector.build())
                .withTreatment(treatment.build())
                .forDevice(device.id())
                .withPriority(BASE_PRIO + 1500)
                .build();
        flowRuleService.applyFlowRules(flowRule);
    }

    /* Adds flows for super spine switches to forward down to spines and up to the data center switch */
    private void addFlowsSuper(Device device) {

        String id = device.chassisId().toString();
        SwitchEntry entry = switchDB.get(id);
        int dc = entry.getDc();

        /* Add rules to forward packets belonging in this data center down towards the correct spine based on pod destination */
        for (int p = 0; p < ssRadixDown.get(dc); p++) {
            byte[] bytes = new byte[6];
            bytes[0] = (byte) ((dc >> 4) & 0x3F);
            bytes[1] = (byte) (((dc & 0xF) << 4) + ((p >> 8) & 0xF));
            bytes[2] = (byte) (p & 0xFF);
            MacAddress eth = new MacAddress(bytes);
            MacAddress mask = new MacAddress(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00});
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder().matchEthDstMasked(eth, mask).matchEthType(Ethernet.TYPE_IPV4);
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(p + 1));
            FlowRule flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .makePermanent()
                    .withSelector(selector.build())
                    .withTreatment(treatment.build())
                    .forDevice(device.id())
                    .withPriority(BASE_PRIO + 1000)
                    .build();
            flowRuleService.applyFlowRules(flowRule);
        }

        /* Add rule to forward packets belonging to another data center up to the data center switch */
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4);
        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(ssRadixDown.get(dc) + 1));
        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .makePermanent()
                .withSelector(selector.build())
                .withTreatment(treatment.build())
                .forDevice(device.id())
                .withPriority(BASE_PRIO + 500)
                .build();
        flowRuleService.applyFlowRules(flowRule);
    }

    /* Adds flows for spine switches to forward down to leaves and up to super spines */
    private void addFlowsSpine(Device device) {
        String id = device.chassisId().toString();
        SwitchEntry entry = switchDB.get(id);
        int dc = entry.getDc();
        int pod = entry.getPod();

        /* Add rules to forward packets belonging in this pod down towards the correct leaf based on ToR destination */
        for (int l = 0; l < spRadixDown.get(dc); l++) {
            byte[] bytes = new byte[6];
            bytes[0] = (byte) ((dc >> 4) & 0x3F);
            bytes[1] = (byte) (((dc & 0xF) << 4) + ((pod >> 8) & 0xF));
            bytes[2] = (byte) (pod & 0xFF);
            bytes[3] = (byte) ((l >> 4) & 0xFF);
            bytes[4] = (byte) ((l & 0xF) << 4);
            MacAddress eth = new MacAddress(bytes);
            MacAddress mask = new MacAddress(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xF0, 0x00});
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder().matchEthDstMasked(eth, mask).matchEthType(Ethernet.TYPE_IPV4);
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(l + 1));
            FlowRule flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .makePermanent()
                    .withSelector(selector.build())
                    .withTreatment(treatment.build())
                    .forDevice(device.id())
                    .withPriority(BASE_PRIO + 1000)
                    .build();
            flowRuleService.applyFlowRules(flowRule);
        }

        /* Add rule to ECMP packets belonging to another pod up to super spines */
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4);
        GroupDescription groupDescription = null;
        for (GroupDescription g : groupService.getGroups(device.id())) {
            groupDescription = g;
        }
        GroupKey key = new DefaultGroupKey(appKryo.serialize(Objects.hash(device)));
        if (groupDescription == null) {
            groupDescription = new DefaultGroupDescription(device.id(), GroupDescription.Type.SELECT, new GroupBuckets(spineBuckets.get(entry.getDc())), key, groupCount++, appId);
            groupService.addGroup(groupDescription);
        }
        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().group(new GroupId(groupDescription.givenGroupId()));
        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .makePermanent()
                .withSelector(selector.build())
                .withTreatment(treatment.build())
                .forDevice(device.id())
                .withPriority(BASE_PRIO + 500)
                .build();
        flowRuleService.applyFlowRules(flowRule);
    }

    /* Adds default flows for leaf to hand all IPv4 packets to controller if it hasn't seen the IP destination before */
    private void addFlowsLeaf(Device device) {

        String id = device.chassisId().toString();
        SwitchEntry entry = switchDB.get(id);
        int dc = entry.getDc();

        for (int h = 1; h <= lfRadixDown.get(dc) + lfRadixUp.get(dc); h++) {
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder().matchInPort(PortNumber.portNumber(h)).matchEthType(Ethernet.TYPE_IPV4);
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().punt();
            FlowRule flowRule = DefaultFlowRule.builder()
                    .fromApp(appId)
                    .makePermanent()
                    .withSelector(selector.build())
                    .withTreatment(treatment.build())
                    .forDevice(device.id())
                    .withPriority(BASE_PRIO + 100)
                    .build();
            flowRuleService.applyFlowRules(flowRule);
        }
    }

    private void removeSwitch(Device device) {

    }

    /* Invalidate all flows that use the IP address of a host that was moved */
    private void removeHostFlows(Host host) {
        Set<IpAddress> ips = host.ipAddresses();
        List<FlowRule> temp = new ArrayList<>(installedFlows);
        for (IpAddress ip : ips) {
            for (FlowRule flow : installedFlows) {
                if (((IPCriterion)flow.selector().getCriterion(Criterion.Type.IPV4_DST)).ip().address().equals(ip)) {
                    flowRuleService.removeFlowRules(flow);
                    temp.remove(flow);
                }
            }
        }
        installedFlows = temp;
    }

    /* Listen for switches that are added to topology */
    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent deviceEvent) {
            switch (deviceEvent.type()) {
                case DEVICE_ADDED:
                case DEVICE_UPDATED:
                    setupFlows(deviceEvent.subject());
                    break;
                case DEVICE_REMOVED:
                case DEVICE_SUSPENDED:
                    removeSwitch(deviceEvent.subject());
                    break;
                default:
                    break;
            }
        }
    }

    /* Listed for hosts that are moved or removed from network */
    private class InternalHostListener implements HostListener {
        @Override
        public void event(HostEvent hostEvent) {
            switch (hostEvent.type()) {
                case HOST_MOVED:
                case HOST_REMOVED:
                    removeHostFlows(hostEvent.subject());
                    break;
                default:
                    break;
            }
        }
    }
}
