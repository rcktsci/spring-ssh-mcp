---
name: spring-ssh-mcp-managing
description: |
    Activate when user asks for:
    - Add/edit/check/remove a connection to a remote SSH server
    - Grant SSH access to another AI agent, manage key-based auth and permissions
    To run commands via SSH on remote servers, activate the other skill: `spring-ssh-mcp-usage`.
---

When this skill is activated, the usage skill must also be activated: `spring-ssh-mcp-usage`.

# Spring SSH MCP Server

## Creating a Connection

### Authentication

**IMPORTANT**: Before adding a connection, ALWAYS fetch the list of current servers first
to understand their naming convention, for example:

- `ds/host-01` - hypervisor
- `vm/server-01` - virtual machine

**Password:**

```
add_server_connection(name="prod", host="192.168.1.1", username="root",
           password="secret")
```

**Key without passphrase:**

```
add_server_connection(name="prod", host="192.168.1.1", username="root",
          privateKey="-----BEGIN RSA PRIVATE KEY-----\n...")
```

**Key with passphrase:**

```
add_server_connection(name="prod", host="192.168.1.1", username="root",
          privateKey="-----BEGIN RSA PRIVATE KEY-----...",
          privateKeySecret="keypass")
```

`overwrite` (optional, default `false`) controls whether an existing server with the same name is replaced.

### PEM vs OpenSSH key format

sshj (the MCP library) supports **ONLY PEM**:

✓ `-----BEGIN RSA PRIVATE KEY-----`
✓ `-----BEGIN ENCRYPTED PRIVATE KEY-----` (may be encrypted)
✗ `-----BEGIN OPENSSH PRIVATE KEY-----` — NOT supported

If a key does not work — it is most likely in OpenSSH format.

**Generating a PEM key with passphrase:**

```bash
ssh-keygen -t rsa -b 2048 -f /tmp/mykey -N "passphrase" -m PEM
```

### Setting up the user and key on the server

```bash
# Generate a key ON THE SERVER
ssh-keygen -t rsa -b 2048 -f /tmp/mykey -N "passphrase" -m PEM

# Add the public key to authorized_keys
mkdir -p /home/username/.ssh
chmod 700 /home/username/.ssh
cat /tmp/mykey.pub >> /home/username/.ssh/authorized_keys
chmod 600 /home/username/.ssh/authorized_keys
chown -R username:username /home/username/.ssh
```

## Access Tokens (auth_tokens)

The spring-ssh MCP backend runs on Postgres in a Docker container (typically the `postgres` service from docker-compose).
The MCP API tokens live in the `ssh-mcp.auth_tokens` table.

**MCP endpoint:** `http://<hostname>:3111/mcp`

Tokens are used by MCP clients as **Bearer tokens** in the `Authorization` header when connecting to the MCP server:

```
Authorization: Bearer <token-uuid>
```

One token = one set of permissions (`can_edit`, `can_execute`, `execute_only`) and an owner binding via `comment`.
The server authorizes the client by the token, after which the client can call the tools (`add_server_connection`, `execute`,
`list_servers`, `remove_server_connection`, `rename_server_connection`, `generate_session_id`).

### Privilege model

spring-ssh MCP serves **other AI agents**: they connect to the server, authenticate with a token,
and execute commands on the target VMs via MCP tools. Humans do not call MCP directly — each person works
through their own AI agent.

- **`can_edit = true`** — only for the infrastructure AI admin.
  Allows adding/removing SSH servers via MCP.
- **`can_edit = false`** — granted to other AI agents that only need to run commands.
  Almost always paired with an `execute_only` glob restriction — it pins down which VMs the agent can access.
  `comment` holds the human/system that owns the agent.

### Connecting

```bash
# Container: postgres, user: <db-user>, schema: ssh-mcp (has a hyphen — needs "quoting")
docker exec postgres psql -U <db-user> -d <database> -c ...
```

### `ssh-mcp.auth_tokens` table schema

| Column                      | Type            | Purpose                                                           |
|-----------------------------|-----------------|-------------------------------------------------------------------|
| `id`                        | bigint identity | PK                                                                |
| `token`                     | uuid            | API token                                                         |
| `can_edit`                  | boolean         | allowed to edit the server list                                   |
| `can_execute`               | boolean         | allowed to run commands                                           |
| `execute_only`              | varchar(255)[]  | glob whitelist (if empty — all servers, otherwise — only matches) |
| `created_at` / `updated_at` | timestamp       | now() by default                                                  |
| `comment`                   | varchar(255)    | free-form description (usually owner identification)              |

### Glob in `execute_only`

`execute_only` is matched against the server name in MCP (`ds/host-01`, `vm/server-01`, `vm/*`, etc.).
Supports `*` as a wildcard. An empty array `{}` = no restrictions.

Examples:

- `{}` — all servers
- `{vm/*}` — all VMs in the cluster
- `{vm/*,ds/host-01}` — all VMs plus a specific hypervisor
- `{*db*}` — anything containing "db"
- `{vm/server-01}` — only a specific VM

### INSERTing a token (read-write operation, on user request)

**Always use `psql -c`, not stdin-heredoc:**
heredoc via `docker exec ... psql << EOF` can silently finish with empty stdout without inserting.
With the `-c` flag the behavior is predictable.

```bash
docker exec postgres psql -U <db-user> -d <database> -c "
INSERT INTO \"ssh-mcp\".auth_tokens (token, can_execute, execute_only, comment)
VALUES (gen_random_uuid(), TRUE, ARRAY['vm/server-01'], 'Owner Name')
RETURNING id, token, can_execute, can_edit, execute_only, comment;"
```

**Important:**

- The `ssh-mcp` schema must be wrapped in double quotes (otherwise a syntax error occurs on the hyphen).
- `ARRAY[...]` or `'{...}'::varchar[]` — both forms are valid for Postgres.
- Only `gen_random_uuid()` (no need for `uuid-ossp` — it is built-in since PG 13+).

### Verification after INSERT

```bash
# Immediately check that the row appeared
docker exec postgres psql -U <db-user> -d <database> -c 'SELECT id, can_execute, can_edit, execute_only, comment FROM "ssh-mcp".auth_tokens ORDER BY id DESC LIMIT 5;'
```

### Useful SELECTs

```bash
# All tokens
docker exec postgres psql -U <db-user> -d <database> -c 'SELECT id, can_execute, can_edit, execute_only, comment, created_at FROM "ssh-mcp".auth_tokens;'

# Table structure
docker exec postgres psql -U <db-user> -d <database> -c '\d "ssh-mcp".auth_tokens'

# List of servers (separate table)
docker exec postgres psql -U <db-user> -d <database> -c 'SELECT id, name FROM "ssh-mcp".servers;'
```

## Troubleshooting

### "Exhausted available authentication methods"

The SSH server rejected all methods. Common causes:

- Key in OpenSSH format instead of PEM
- PasswordAuth disabled and the key not added to authorized_keys
- Key not in the user's authorized_keys

### "Connection refused"

- Check the IP — often a typo (e.g., 192.168.1.42 vs 192.168.10.142)

### "Authentication failed"

- Key is in PEM format, not OpenSSH — convert: `ssh-keygen -p -m PEM -f key`
