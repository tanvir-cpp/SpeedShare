# SpeedShare High-Speed Local Sharing Protocol Specification

## 1. Network Discovery (UDP Port: 53317)
- **Transport**: UDP Multi-Adapter Subnet Broadcast & Global Broadcast (`255.255.255.255:53317`)
- **Heartbeat Interval**: 1.5 seconds
- **Message Format** (JSON):
```json
{
  "type": "BEACON",
  "deviceId": "uuid-v4",
  "deviceName": "Asus-Laptop",
  "deviceType": "WINDOWS" | "ANDROID",
  "port": 53318,
  "version": 1
}
```
- **Discovery Ping** (Sent upon app startup to immediately discover peers):
```json
{
  "type": "DISCOVER",
  "deviceId": "uuid-v4"
}
```

---

## 2. Transfer Negotiation & Streaming (TCP Port: 53318)
All TCP messages in the control phase are framed by a 4-byte big-endian integer indicating the length of the UTF-8 JSON payload.

### Step A: Transfer Request (Sender -> Receiver)
```json
{
  "action": "TRANSFER_REQUEST",
  "sessionId": "uuid-v4",
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

### Step B: Receiver Prompt Decision (Receiver -> Sender)
- **If Accepted**:
```json
{
  "action": "TRANSFER_ACCEPT",
  "sessionId": "uuid-v4"
}
```
- **If Declined**:
```json
{
  "action": "TRANSFER_DECLINE",
  "sessionId": "uuid-v4",
  "reason": "User rejected the transfer request"
}
```

### Step C: High-Throughput Binary Streaming (Sender -> Receiver)
Once accepted, for each file in the transfer list:
1. Sender writes 4-byte big-endian file index (`int`).
2. Sender writes 8-byte big-endian file length (`long` / `int64`).
3. Sender writes file content in 1MB (`1048576` bytes) binary chunks directly over the TCP socket until all bytes are sent.
4. Receiver reads bytes and writes directly to target output stream.

### Step D: Transfer Completion (Receiver -> Sender)
When all files are fully received and written to disk:
```json
{
  "action": "TRANSFER_COMPLETE",
  "sessionId": "uuid-v4",
  "status": "SUCCESS"
}
```
Both sides update their UI and close the transfer session cleanly.
