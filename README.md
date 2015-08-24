# jrinetd

jrinetd is an open source (Apache License, Version 2.0) Java TCP port redirector proxy. Do not require any external lib.

### Current Stable Version is [1.1.1](https://maven-release.s3.amazonaws.com/release/org/javastack/jrinetd/1.1.1/jrinetd-1.1.1-bin.zip)

---

## DOC

#### Schema about Forward / Port Redirector:
    
![Forward / Port Redirector](https://raw.github.com/ggrandes/jrinetd/master/doc/forward_port.png "Forward / Port Redirector")

1. Machine-A (Client) init connection to Machine-B (jrinetd)
2. Machine-B init connection to Machine-C (Server)
3. Done: Machine-A is able to speak with Machine-C

###### Notes about security:

* Machine-A (Client) may be in Internal network.
* Machine-B (jrinetd) may be in DMZ.
* Machine-C (Server) may be in External network.

---

## System Properties (optional)

    # To redir stdout/stderr to (auto-daily-rotated) files you can use:
    -Dlog.stdOutFile=/var/log/jrinetd.out -Dlog.stdErrFile=/var/log/jrinetd.err
    # To log to stdout too:
    -Dlog.stdToo=true 

###### Filenames are a base-pattern, output files they will be: jrinetd.xxx.YEAR-MONTH-DAY (jrinetd.xxx.2015-08-01)

## Config (jrinetd.conf)

Config file must be in class-path `${JRINETD_HOME}/conf/`, general format is:

    #### Forward / Port Redirector
    ## forward <listen-addr>:<listen-port> <endpoint-list> [opts]
    
    # Note: <endpoint-list> can be a coma separated list of addresses, like "srv1:80,srv2:80,10.0.0.3:8080"
    
###### Options are comma separated:

* Options for outgoing connections
    * Loadbalancing (only one option can be used)
        * **LB=NONE**: disable LoadBalancing
        * **LB=ORDER**: active LoadBalancing in order (DNS resolved IP address are sorted, lower first { 10.0.0.1, 10.0.0.2, 192.168.0.1 })
        * **LB=RR**: active LoadBalancing in round-robin (DNS order)
        * **LB=RAND**: activate LoadBalancing in random order
        * **LB=RANDRR**: activate LoadBalancing in random order and round-robin
    * Failover (default disabled)
        * **FAILOVER**: enable FailOver (if connect fail, try next address)
    * Sticky Session
        * **STICKY=MEM:bitmask:elements:ttl:sticky-name[:cluster-name]**: activate Sticky session based on IP Source Address. Sessions are stored in MEMory, *bitmask* is a [CIDR](http://en.wikipedia.org/wiki/CIDR) to apply in source-ip-address (16=Class B, 24=Class C, 32=Unique host), *elements* for LRU cache, *ttl* is time to live of elements in cache (seconds), *sticky-name* and *cluster-name* in cluster environment is cluster identifier and replication identifier respectively. 
* Options for inbound connections
    * **PROXY=SEND**: use PROXY protocol (v1), generate header for remote server

##### Example config of Forward / Port Redirector:

    # <listen-addr>:<listen-port> <endpoint-list> [opts]
    forward 0.0.0.0:80 10.0.0.1:8080,10.0.0.2:8080
    forward 127.0.0.1:443 www.acme.com:443 LB=RR,STICKY=MEM:24:128:300:sticky1

* More examples in [sampleconf](https://github.com/ggrandes/jrinetd/blob/master/sampleconf/)

---

## Running (Linux)

    ./bin/jrinetd.sh <start|stop|restart|reload|status>

---

## TODOs

* Use Log4J
* Limit number of connections
* Limit absolute timeout/TTL of a connection
* Configurable retry-sleeps
* Thread pool/control
* Custom timeout by binding
* Audit threads / connections
* Statistics/Accounting
* JMX

## DONEs

* NIO (v1.0.0)
* BufferPool for reduce GC pressure (v1.0.0)
* Reload config (v1.0.0)
* Allow alternative config names (v1.0.0)
* Zip Packaging (Maven Assembly) (v1.0.0)
* Allow redir stdout/stderr to File, with auto daily-rotate (v1.0.0)
* PROXY protocol (v1) for Outgoing connections (v1.0.0)
* Multiple endpoint-list (not only DNS multi A-record) (v1.0.0)
* Sticky sessions in LoadBalancing (v1.0.0)
* Use multiple thread for multi-core machines (v1.0.0)
* Replicate Sticky Sessions over multiple jrinetd (HA) (v1.0.0)
* Improved support for embed -a little- (v1.1.0)

## MISC

Current harcoded values:

* Buffer Pool size: 8192buffers (per thread)
* Buffer-Length for I/O: 2048bytes
* DNS cache: 3seconds
* Reload config check time interval: 10seconds
* Shutdown/Reload timeout: 30seconds
* Cluster Connection timeout: 10seconds

---

## Latency Benchmark

<table>
  <tr align="right">
    <th>microsecs</th>
    <th>Direct</th>
    <th>Forward</th>
  </tr>
  <tr align="right">
    <th>min</th>
    <td>?</td>
    <td>?</td>
  </tr>
  <tr align="right">
    <th>max</th>
    <td>?</td>
    <td>?</td>
  </tr>
  <tr align="right">
    <th>avg</th>
    <td>?</td>
    <td>?</td>
  </tr>
</table>

## Throughput Benchmark

<table>
  <tr align="right">
    <th>(transfers)</th>
    <th>Direct (x2)</th>
    <th>Forward (x4)</th>
  </tr>
  <tr align="right">
    <th>Mbytes</th>
    <td>?</td>
    <td>?</td>
  </tr>
  <tr align="right">
    <th>Mbits</th>
    <td>?</td>
    <td>?</td>
  </tr>
</table>

###### All test run on localhost on a Laptop. Values are not accurate, but orientative. Latency { EchoServer, 1 byte write/read (end-to-end, round-trip), 100K iterations } Lower Better. Throughput { Chargen, 1024bytes read & write (full-duplex), total 512MBytes } Higher better.


---
Inspired in [rinetd](http://www.boutell.com/rinetd/), this is a Java-minimalistic version.
