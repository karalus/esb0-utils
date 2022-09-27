# esb0-utils
Utilities for esb0 that are not mandatory to run esb0 or not of common use. This is also an incubator for stuff that will later make it into the esb0 main project.

__Features:__

### WS-Security ###
ESB0 actions to sign and verify SOAP messages according to [WS-Security](https://www.oasis-open.org/committees/tc_home.php?wg_abbrev=wss) using [WSS4J](https://ws.apache.org/wss4j/).

__Recommendation:__ Do not expose an esb0 directly to the internet. It is not security hardened. At least put it behind a reverse proxy into a DMZ and only allow outbound TCP traffic via HTTP proxy.

### Fast Infoset ###
Compress XML messages with [Fast Infoset](https://en.wikipedia.org/wiki/Fast_Infoset) even more by using external vocabularies.

### Miscellaneous ###
- Browse JMS queue (convert messages to JSON), purge queue 
- Dump contents of a cache as JSON, purge/delete cache
- Create a Heapdump/Threaddump inside ESB0 to be downloaded
- Convert a stream message into a [SwA](https://en.wikipedia.org/wiki/SOAP_with_Attachments) attachment
- Download an artifact from esb0 including all transitive dependencies as a ZIP
- Replace a BLOB in a JSON JDBC result with a CLOB taking code page and MIME type into account
- Initiate reconnect for a JMS Connection (for monkey tests)
- HTTP check alive test for JBoss Cluster setup with Apache mod_cluster
- Get state of JMSConnections in order to detect outages
- Get state of all JMSConsumers
- Provide rudimentary MBean API support (get & set attributes, list all attributes, invoke operations) via JSON. Can be used in a (admin) REST service.
