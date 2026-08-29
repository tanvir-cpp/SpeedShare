# SpeedShare High-Speed Local Sharing Protocol Specification

## 1. Network Discovery (UDP Port: 53317)
- **Transport**: UDP Multi-Adapter Subnet Broadcast & Global Broadcast (`255.255.255.255:53317`)
- **Heartbeat Interval**: 1.5 seconds
- **Stale Peer Timeout**: 6 seconds (peers not refreshed within this window are dropped)
- **Beacon Message Format** (JSON):
```json
{
  "type": "BEACON",
  "deviceId": "8-hex-chars",
  "deviceName": "Asus-Laptop",
  "deviceType": "WINDOWS" | "ANDROID",
  "port": 53318,
  "nonce": 42,
  "version": 1
}
```
The `nonce` field is a monotonically increasing counter that lets receivers
ignore packets echoed back by their own network stack.

- **Discovery Ping** (Sent upon app startup to immediately discover peers):
```json
{
  "type": "DISCOVER",
  "deviceId": "8-hex-chars",
  "deviceName": "Asus-Laptop",
  "deviceType": "WINDOWS" | "ANDROID",
  "port": 53318,
  "nonce": 43,
  "version": 1
}
```
Peers receiving a `DISCOVER` MUST respond once with a `BEACON`.

---

## 2. Transfer Negotiation & Streaming (TCP Port: 53318)
All TCP messages in the control phase are framed by a 4-byte big-endian integer
indicating the length of the UTF-8 JSON payload.

### Step A: Transfer Request (Sender → Receiver)
```json
{
  "action": "TRANSFER_REQUEST",
  "sessionId": "uuid-v4-hex",
  "senderDevice": "Asus-Laptop",
  "deviceType": "WINDOWS",
  "files": [
    {
      "id": "0",
      "name": "sample_video.mp4",
      "size": 104857600,
      "mime": "video/mp4"
    }
  ],
  "totalSize": 104857600
}
```
`name` MAY be a relative path (e.g. `"subdir/photo.jpg"`). Receivers MUST
sanitize any path components (`..`, absolute paths, illegal characters) before
writing to disk.

### Step B: Receiver Decision (Receiver → Sender)
- **If Accepted**:
```json
{ "action": "TRANSFER_ACCEPT", "sessionId": "uuid-v4-hex" }
```
- **If Declined**:
```json
{ "action": "TRANSFER_DECLINE", "sessionId": "uuid-v4-hex", "reason": "User rejected the transfer request" }
```
- **If the request is malformed** (e.g. zero files, oversized header), the
receiver SHOULD respond with `TRANSFER_DECLINE` and a descriptive `reason`
rather than just dropping the connection.

### Step C: High-Throughput Binary Streaming (Sender → Receiver)
Once accepted, for each file in the transfer list:
1. Sender writes 4-byte big-endian file index (`int`).
2. Sender writes 8-byte big-endian file length (`long` / `int64`).
3. Sender writes file content in 1MB (`1048576` bytes) binary chunks directly
   over the TCP socket until all bytes are sent.
4. Receiver reads bytes and writes directly to target output stream.
5. If the sender's local file is shorter than the declared size, the sender
   MUST fail the transfer with a `TRANSFER_ERROR` and the receiver MUST discard
   the partial file.

Files of size zero are valid: the sender writes the 12-byte header and nothing
else, and the receiver creates an empty file.

### Step D: Transfer Completion (Receiver → Sender)
When all files are fully received and written to disk:
```json
{ "action": "TRANSFER_COMPLETE", "sessionId": "uuid-v4-hex", "status": "SUCCESS" }
```
Both sides update their UI and close the transfer session cleanly.

### Error Path (Sender → Receiver)
If the sender encounters an unrecoverable error mid-stream, it MAY send:
```json
{ "action": "TRANSFER_ERROR", "sessionId": "uuid-v4-hex", "reason": "Local read failed" }
```
The receiver treats any TCP close without `TRANSFER_COMPLETE` as a failure.

---

## 3. Future / Reserved
- **Integrity hashes**: An optional `"sha256"` field per file is reserved for
  a future revision. Receivers that don't recognize it MUST ignore it.
- **Mutual authentication**: A 6-digit PIN prompt is reserved as a future
  option to prevent transfers from being silently accepted by unattended
  devices.
