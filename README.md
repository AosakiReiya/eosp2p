# Introduction
This mod aims to add P2P connection for both client and dedicated server through Epic Online Service, in order to add a multiplayer connection method whenever direct access to public IP is not available (for hosting player or server), e.g. behind [Nedwork Address Translation](https://en.wikipedia.org/wiki/Network_address_translation).

# Requirements
This mod can only operate on the following platforms:
- Windows (x86_64)
- Windows (arm64) (not yet tested)
- Linux (x86_64), with GNU C Library version 2.17+_*_ (not yet tested)
- Linux (arm64), with GNU C Library version 2.17+_*_
- MacOS (x86_64), with system version at least OS X 10.15 (Catalina)
- MacOS (arm64) (not yet tested)

_*_ System bundled with Musl C can try [gcompat](https://github.com/Stantheman/gcompat)  
_*_ Android is not supported due to technical issues

# Details
## Client
### Joining
If the mod successfully loaded and operate, input a connection address with correct format will both works as like normal server address in "Add Server" and "Direct Connection"

Domain address format `EOS:yourdomain.com` is also supported — the client resolves the EOS connection key by querying the domain's TXT record via DNS-over-HTTPS (multi-provider fallback).

![Direct Connection](https://cdn.modrinth.com/data/cached_images/c7b86033211eb01c02796a5497aa2cd935b475d1.png)
![Add Server](https://cdn.modrinth.com/data/cached_images/87bbab8059e6d404e9d3e86bfb73eba5977f11fb.png)
### Hosting
If the mod successfully loaded and operate, after Open To LAN operation, the connection address through P2P will also printed in chat. It is able to copy through clicking, just like the original LAN port.
![Connection address in chatbox after Open to LAN](https://cdn.modrinth.com/data/cached_images/4edd32d0be0bbeb5147c43c9b6b08818a29f39f4.png)
## Server
The connection address through P2P will be printed just after "Start serving on..". Note that the connection address do not include brackets.
![Connection address in dedicated server log](https://cdn.modrinth.com/data/cached_images/74b7a7d9ea3211e6d54adfd45b9599914ee77388_0.webp)

# Commands
The following commands works on both dedicated server and client.
- `/eosp2p status`: Show if mod operates or not, with reason.
- `/eosp2p eosaddress`: Show the connection address of the current world. Error thrown if not yet published.
- `/eosp2p ddns update`: Manually push current EOS connection key to Cloudflare DNS TXT record.
- `/eosp2p ddns status`: Show last DDNS update time and current key.

# Cloudflare DDNS
The server can automatically update a DNS TXT record with its EOS connection key.

## Configuration (`config/eosp2p-server.toml`)
- `cloudflare.enabled`: Enable DDNS (default: false)
- `cloudflare.api_token`: Cloudflare API Token (needs DNS:Edit permission)
- `cloudflare.zone_id`: Cloudflare Zone UUID (not domain name)
- `cloudflare.ddns_record`: TXT record name, e.g. `eosmc.example.com`

The mod writes a TXT record with value `eos-v1=<base64key>` to Cloudflare on server start (or manual trigger via `/eosp2p ddns update`).

Players can then connect using `EOS:eosmc.example.com` — the client queries the domain's TXT record to obtain the connection key automatically.

# Known Issues (that I failed to solve)
- Frequent connect and disconnect may causes issues.
- The correct close reason may not shown up (as the connection close before the close reason arrived)
- Server PING function will shows that server not accessible instead of latency

# Credits
I've use the following libraries in this project
- [skywind3000/kcp](https://github.com/skywind3000/kcp)
- [skarupke/flat_hash_map](https://github.com/skarupke/flat_hash_map)
- [Neargye/magic_enum](https://github.com/Neargye/magic_enum)
- [DarrenLevine/cppcrc](https://github.com/DarrenLevine/cppcrc)
- [fmtlib/fmt](https://github.com/fmtlib/fmt) 