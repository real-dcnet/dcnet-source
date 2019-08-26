/*
 * Copyright 2015 Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onos.dcnet;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;
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
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.group.*;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.PathService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eclipsesource.json.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * ONOS App implementing DCnet forwarding scheme
 */
@Component(immediate = true)
public class DCnet {
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private PathService pathService;

    public class SwitchEntry {
        private String name;
        private int level;
        private int dc;
        private int pod;
        private int leaf;
        private boolean joined;
        private Device device;

        public SwitchEntry(String name, int level, int dc, int pod, int leaf) {
            this.name = name;
            this.level = level;
            this.dc = dc;
            this.pod = pod;
            this.leaf = leaf;
            this.joined = false;
            this.device = null;
        }

        public String getName() {
            return this.name;
        }

        public int getLevel() {
            return this.level;
        }

        public int getDc() {
            return this.dc;
        }

        public int getPod() {
            return this.pod;
        }

        public int getLeaf() {
            return this.leaf;
        }

        public Device getDevice() {
            return this.device;
        }

        public void setDevice(Device device) {
            this.device = device;
        }

        public boolean isJoined() {
            return this.joined;
        }

        public void setJoined() {
            this.joined = true;
        }
    }

    public class HostEntry {
        private String name;
        private byte[] rmac;
        private byte[] idmac;

        public HostEntry(String name, byte[] rmac, byte[] idmac) {
            this.name = name;
            this.rmac = rmac;
            this.idmac = idmac;
        }

        public String getName() {
            return this.name;
        }

        public byte[] getRmac() {
            return this.rmac;
        }

        public byte[] getIdmac() {
            return this.idmac;
        }

    }

    private static Logger log = LoggerFactory.getLogger(DCnet.class);

    private static final String configLoc = System.getProperty("user.home") + "/dcnet-source/config/mininet/";

    private static final int DC = 0;
    private static final int SUPER = 1;
    private static final int SPINE = 2;
    private static final int LEAF = 3;
    private static final int BASE_PRIO = 50000;

    private int dcCount = 0;
    private List<Integer> dcRadixDown = new ArrayList<>();
    private List<Integer> ssRadixDown = new ArrayList<>();
    private List<Integer> spRadixUp = new ArrayList<>();
    private List<Integer> spRadixDown = new ArrayList<>();
    private List<Integer> lfRadixUp = new ArrayList<>();
    private List<Integer> lfRadixDown = new ArrayList<>();

    private List<List<GroupBucket>> leafBuckets = new ArrayList<>();
    private List<List<GroupBucket>> spineBuckets = new ArrayList<>();
    private List<List<GroupBucket>> dcBuckets = new ArrayList<>();

    private int groupCount = 0;

    /* Maps Chassis ID to a switch entry */
    private Map<String, SwitchEntry> switchDB = new TreeMap<>();

    /* Maps IP address to a host entry */
    private Map<Integer, HostEntry> hostDB = new TreeMap<>();

    /* List of currently active flow rules */
    private List<FlowRule> installedFlows = new ArrayList<>();

    private List<DeviceId> addedDevices = new ArrayList<>();

    private ApplicationId appId;

    protected static KryoNamespace appKryo = new KryoNamespace.Builder()
            .register(Integer.class)
            .register(DeviceId.class)
            .build("group-fwd-app");

    private final SetMultimap<GroupKey, FlowRule> pendingFlows = HashMultimap.create();

    private final DeviceListener deviceListener = new InternalDeviceListener();

    private final HostListener hostListener = new InternalHostListener();

    private final PacketProcessor packetProcessor = new DCnetPacketProcessor();

    /* Selector for IPv4 traffic to intercept */
    private final TrafficSelector intercept = DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build();

    /* Initializes application by reading configuration files for hosts, switches, and topology design */
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
            /* Setup switch database by reading fields in switch configuration file */
            JsonObject config = Json.parse(new BufferedReader(new FileReader(configLoc + "switch_config.json"))).asObject();
            addSwitchConfigs(config.get("dcs").asArray(), DC);
            addSwitchConfigs(config.get("supers").asArray(), SUPER);
            addSwitchConfigs(config.get("spines").asArray(), SPINE);
            addSwitchConfigs(config.get("leaves").asArray(), LEAF);

            /* Setup host database by reading fields in host configuration file */
            config = Json.parse(new BufferedReader(new FileReader(configLoc + "host_config.json"))).asObject();
            addHostConfigs(config.get("hosts").asArray());

            /* Setup topology specifications by reading fields in topology configuration file */
            config = Json.parse(new BufferedReader(new FileReader(configLoc + "top_config.json"))).asObject();
            dcCount = config.get("dc_count").asInt();
            addDcConfigs(config.get("config").asArray());

            /* Create buckets for each switch type in each data center describing ports to use for ECMP */
            for (int d = 0; d < dcCount; d++) {
                leafBuckets.add(new ArrayList<>());
                for (int i = 1; i <= lfRadixUp.get(d); i++) {
                    TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(lfRadixDown.get(d) + i));
                    leafBuckets.get(d).add(DefaultGroupBucket.createSelectGroupBucket(treatment.build()));
                }
                spineBuckets.add(new ArrayList<>());
                for (int i = 1; i <= spRadixUp.get(d); i++) {
                    TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(spRadixDown.get(d) + i));
                    spineBuckets.get(d).add(DefaultGroupBucket.createSelectGroupBucket(treatment.build()));
                }
                dcBuckets.add(new ArrayList<>());
                for (int i = 1; i <= dcRadixDown.get(d); i++) {
                    TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(i));
                    dcBuckets.get(d).add(DefaultGroupBucket.createSelectGroupBucket(treatment.build()));
                }
            }
        }

        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addSwitchConfigs(JsonArray configs, int level) {
        for(JsonValue obj : configs) {
            JsonObject config = obj.asObject();
            SwitchEntry entry = new SwitchEntry(config.get("name").asString(), level, config.get("dc").asInt(), config.get("pod").asInt(), config.get("leaf").asInt());
            switchDB.put(config.get("mac").asString(), entry);
        }
    }

    private void addHostConfigs(JsonArray configs) {
        for(JsonValue obj : configs) {
            JsonObject config = obj.asObject();
            HostEntry entry = new HostEntry(config.get("name").asString(), strToMac(config.get("rmac").asString()), strToMac(config.get("idmac").asString()));
            hostDB.put(ipStrtoInt(config.get("ip").asString()), entry);
        }
    }

    private void addDcConfigs(JsonArray configs) {
        for(JsonValue obj : configs) {
            JsonObject config = obj.asObject();
            dcRadixDown.add(config.get("dc_radix_down").asInt());
            ssRadixDown.add(config.get("ss_radix_down").asInt());
            spRadixUp.add(config.get("sp_radix_up").asInt());
            spRadixDown.add(config.get("sp_radix_down").asInt());
            lfRadixUp.add(config.get("lf_radix_up").asInt());
            lfRadixDown.add(config.get("lf_radix_down").asInt());
        }
    }

    /* Allows application to be started by ONOS controller */
    @Activate
    public void activate() {

        init();
        appId = coreService.registerApplication("org.onosproject.dcnet");
        packetService.addProcessor(packetProcessor, BASE_PRIO);
        packetService.requestPackets(intercept, PacketPriority.CONTROL, appId, Optional.empty());
        deviceService.addListener(deviceListener);
        //hostService.addListener(hostListener);
        log.info("Started");
    }

    /* Allows application to be stopped by ONOS controller */
    @Deactivate
    public void deactivate() {

        packetService.removeProcessor(packetProcessor);
        flowRuleService.removeFlowRulesById(appId);
        deviceService.removeListener(deviceListener);
        //hostService.removeListener(hostListener);
        for (DeviceId d : addedDevices) {
            groupService.purgeGroupEntries(d);
        }
        log.info("Stopped");
    }

    /* Helper function to translate int version of IP (used by ONOS) into String (used in this application) */
    private int ipStrtoInt(String ip) {
        String[] bytes = ip.split(".");
        return (Integer.parseInt(bytes[0]) << 24) + (Integer.parseInt(bytes[1]) << 16)
                + (Integer.parseInt(bytes[2]) << 8) + Integer.parseInt(bytes[3]);
    }

    /* Helper function to translate String version of MAC (used in this application) into byte[] (used by ONOS) */
    private byte[] strToMac(String address) {

        byte[] bytes = new byte[6];
        String[] octets = address.split(":");
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte)(Integer.parseInt(octets[i], 16));
        }
        return bytes;
    }

    /* Creates rules for packets with new IPv4 destination that a leaf switch receives */
    private void processPacketLeaf(PacketContext context, Ethernet eth) {

        IPv4 ip = (IPv4) (eth.getPayload());
        Device device = deviceService.getDevice(context.inPacket().receivedFrom().deviceId());
        String id = device.chassisId().toString();
        SwitchEntry entry = switchDB.get(id);

        int ipDst = ip.getDestinationAddress();
        int ipSrc = ip.getSourceAddress();
        HostEntry hostDst = hostDB.get(ipDst);
        HostEntry hostSrc = hostDB.get(ipSrc);

        /* Handle translation for forward direction, ie where current packet is heading, if destination host is in data center */
        if (hostDst != null) {

            /* Obtain location information from RMAC address corresponding to IP destination */
            byte[] bytesDst = hostDst.getRmac();
            int dcDst = (((int)bytesDst[0]) << 4) + (bytesDst[1] >> 4);
            int podDst = ((bytesDst[1] & 0xF) << 8) + bytesDst[2];
            int leafDst = (((int)bytesDst[3]) << 4) + (bytesDst[4] >> 4);
            TrafficSelector.Builder selectorDst = DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).matchIPDst(IpPrefix.valueOf(ipDst, 32));

            /* If recipient is directly connected to leaf, translate ethernet destination back to recipients's and forward to it */
            if (dcDst == entry.getDc() && podDst == entry.getPod() && leafDst == entry.getLeaf()) {
                int port = ((bytesDst[4] & 0xF) << 8) + bytesDst[5] + 1;
                MacAddress hostDstMac = new MacAddress(hostDst.getIdmac());
                TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setEthDst(hostDstMac).setOutput(PortNumber.portNumber(port));
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
                        .setSourceMACAddress(context.inPacket().parsed().getSourceMACAddress())
                        .setDestinationMACAddress(hostDstMac)
                        .setPayload(context.inPacket().parsed().getPayload());
                treatment = DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(port));
                OutboundPacket packet = new DefaultOutboundPacket(device.id(), treatment.build(), ByteBuffer.wrap(modifiedMac.serialize()));
                packetService.emit(packet);
            }

            /* If recipient is connected to another leaf, translate ethernet destination to RMAC and forward to spines */
            else {
                GroupDescription groupDescription = null;
                for (GroupDescription g : groupService.getGroups(device.id())) {
                    groupDescription = g;
                }
                GroupKey key = new DefaultGroupKey(appKryo.serialize(Objects.hash(device)));
                if (groupDescription == null) {
                    groupDescription = new DefaultGroupDescription(device.id(), GroupDescription.Type.SELECT, new GroupBuckets(leafBuckets.get(entry.getDc())), key, groupCount++, appId);
                    groupService.addGroup(groupDescription);
                }
                MacAddress hostDstMac = new MacAddress(hostDst.getRmac());
                TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setEthDst(hostDstMac).group(new GroupId(groupDescription.givenGroupId()));
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
                        .setSourceMACAddress(context.inPacket().parsed().getSourceMACAddress())
                        .setDestinationMACAddress(hostDstMac)
                        .setPayload(context.inPacket().parsed().getPayload());
                treatment = DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(lfRadixDown.get(entry.getDc()) + 1 + (int) (Math.random() * lfRadixUp.get(entry.getDc()))));
                OutboundPacket packet = new DefaultOutboundPacket(device.id(), treatment.build(), ByteBuffer.wrap(modifiedMac.serialize()));
                packetService.emit(packet);
            }
        }

        /* Handle translation for reverse traffic, ie for any responses, if source host is in data center */
        if (hostSrc != null) {

            byte[] bytesSrc = hostSrc.getRmac();
            int dcSrc = (((int)bytesSrc[0]) << 4) + (bytesSrc[1] >> 4);
            int podSrc = ((bytesSrc[1] & 0xF) << 8) + bytesSrc[2];
            int leafSrc = (((int)bytesSrc[3]) << 4) + (bytesSrc[4] >> 4);
            TrafficSelector.Builder selectorSrc = DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).matchIPDst(IpPrefix.valueOf(ipSrc, 32));

            /* If sender is directly connected to leaf, translate ethernet destination back to recipients's and forward to it */
            if (dcSrc == entry.getDc() && podSrc == entry.getPod() && leafSrc == entry.getLeaf()) {
                int port = ((bytesSrc[4] & 0xF) << 8) + bytesSrc[5] + 1;
                MacAddress hostSrcMac = new MacAddress(hostSrc.getIdmac());
                TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setEthDst(hostSrcMac).setOutput(PortNumber.portNumber(port));
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
            }

            /* If sender is connected to another leaf, translate ethernet destination to RMAC and forward to spines */
            else {
                GroupDescription groupDescription = null;
                for (GroupDescription g : groupService.getGroups(device.id())) {
                    groupDescription = g;
                }
                GroupKey key = new DefaultGroupKey(appKryo.serialize(Objects.hash(device)));
                if (groupDescription == null) {
                    groupDescription = new DefaultGroupDescription(device.id(), GroupDescription.Type.SELECT, new GroupBuckets(leafBuckets.get(entry.getDc())), key, groupCount++, appId);
                    groupService.addGroup(groupDescription);
                }
                MacAddress hostSrcMac = new MacAddress(hostSrc.getRmac());
                TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().setEthDst(hostSrcMac).group(new GroupId(groupDescription.givenGroupId()));
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
    }

    /* Creates rules for packets with new IPv4 destination that a data center switch receives */
    private void processPacketDc(PacketContext context, Ethernet eth) {

        IPv4 ipv4 = (IPv4) (eth.getPayload());
        Device device = deviceService.getDevice(context.inPacket().receivedFrom().deviceId());
        String id = device.chassisId().toString();
        SwitchEntry entry = switchDB.get(id);

        int ip = ipv4.getDestinationAddress();
        HostEntry host = hostDB.get(ip);
        if (host == null) {
            return;
        }

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder().matchInPort(PortNumber.portNumber(dcRadixDown.get(entry.getDc()) + dcCount)).matchEthType(Ethernet.TYPE_IPV4).matchIPDst(IpPrefix.valueOf(ip, 32));

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
            int temp = dc + 1 + entry.getDc();
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
                        processPacketLeaf(context, eth);
                    } else if (entry.getLevel() == DC) {
                        log.info("DC received packet with destination: " + eth.getDestinationMAC().toString());
                        //processPacketDc(context, eth);
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

            entry.setDevice(device);
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
            entry.setJoined();
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

        /* Adds default rule to let controller handle packets that come in from the internet */
        /*
        selector = DefaultTrafficSelector.builder().matchInPort(PortNumber.portNumber(dcRadixDown.get(dc) + dcCount)).matchEthType(Ethernet.TYPE_IPV4);
        treatment = DefaultTrafficTreatment.builder().punt();
        flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .makePermanent()
                .withSelector(selector.build())
                .withTreatment(treatment.build())
                .forDevice(device.id())
                .withPriority(BASE_PRIO + 1500)
                .build();
        flowRuleService.applyFlowRules(flowRule);
        */

        // TODO: Forward all other traffic to internet
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
