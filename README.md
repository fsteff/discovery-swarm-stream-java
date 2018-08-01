# discovery-swarm-stream-java
server-side java implementation of [discovery-swarm-stream](https://github.com/RangerMauve/discovery-swarm-stream)<br>
Works with java 1.7 and on android 5.1 upwards.<br>
Android demo: [DatDiscoveryWebview](https://github.com/fsteff/DatDiscoveryWebview)

## features/limitations
- TCP connections to the peers (no uTP)
- DNS and mDNS lookup (no dht)
- routing between websocket peers
- currently does not announce to the DNS (need to find a way to get dnsjava to send additional data)
- does not listen for incoming connections (coming soon)

## dependencies
- dnsjava.dnsjava (v2.1.8)
- net.iharder.base64 (v2.3.9)
- org.java-websocket.Java-Websocket (v1.3.8)
- com.google.protobuf.protobuf.java (v3.5.1)
- net.posick.mdnsjava (v2.2.0)
