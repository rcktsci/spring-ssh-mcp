---
name: spring-ssh-mcp-usage
description: |
    Using the Spring-SSH-MCP MCP server to run commands on remote servers.
    Activate when the user wants to: connect via SSH, run a command, check connectivity.
    Activate **ONLY IF THE FOLLOWING TOOLS ARE AVAILABLE**:
    - (.*)_generate_session_id
    - (.*)_execute
---

# Spring SSH MCP Server

## Usage

### Session ID

`sessionId` is a constant for the ENTIRE conversation. The same ID for all `execute` calls.

**Getting an ID:**

```
generate_session_id() → <20 lowercase alphanumeric chars>
```

**Using the ID:** If a sessionId is already known — reuse it. Otherwise — obtain it via `generate_session_id`.

```
# ✅ CORRECT — one sessionId for the whole conversation
sess = generate_session_id()  # "abc123def456ghij7890"
execute(reasoningAndExpectations="Non-destructive check of server state", name="server", command="whoami", sessionId=sess)
execute(reasoningAndExpectations="Non-destructive check of server uptime", name="server", command="uptime", sessionId=sess)
```

```
# ❌ INCORRECT — a new sessionId per call
execute(reasoningAndExpectations="test", name="server", command="whoami", sessionId="abc123")
execute(reasoningAndExpectations="test", name="server", command="uptime", sessionId="def456")
```

`reasoningAndExpectations` is a required parameter: describe briefly what the command does, whether it is destructive, and what you expect.

### Timeouts

- 0 or negative → 30 seconds by default
- On timeout `timedOut=true`, output may be partial
- Exit code = -1 if the command did not finish

## Troubleshooting

If an error reproduces consistently, contact the MCP server administrator.

Example problems:

### "Exhausted available authentication methods"

The SSH server rejected all methods. Common causes:

- Key in OpenSSH format instead of PEM
- PasswordAuth disabled and the key not added to authorized_keys
- Key not in the user's authorized_keys

### "Connection refused"

- Check the IP — often a typo (e.g., 192.168.1.42 vs 192.168.10.142)

### "Authentication failed"

- Key is in PEM format, not OpenSSH — convert: `ssh-keygen -p -m PEM -f key`
