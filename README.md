# SSH MCP Server

MCP server for accessing remote SSH servers.

## Features

- Authentication with password or private key (optionally with passphrase)
- Passwords, keys, and passphrases are stored encrypted in PostgreSQL
- Bearer token authorization (two roles: EDIT/EXECUTE, wildcard-based server filtering), tokens stored in PostgreSQL
- Command history table keyed by `sessionId`
- Command execution timeouts
- Transport: Stateless Streamable HTTP

## Tools

| Tool                      | Purpose                                                        | Required role |
|---------------------------|----------------------------------------------------------------|---------------|
| `list_servers`            | List servers                                                   |               |
| `add_server_connection`   | Add a server connection                                        | EDIT          |
| `rename_server_connection`| Rename a connection                                            | EDIT          |
| `remove_server_connection`| Remove a connection                                            | EDIT          |
| `generate_session_id`     | Generate a `sessionId` for a subsequent `execute` call         |               |
| `execute`                 | Execute a command                                              | EXECUTE       |

## Running with Docker Compose

See [docker-compose.yml](docker-compose.yml). Run with `docker compose up -d`.

## Connecting to OpenCode

```json
{
    "mcp": {
        "ssh": {
            "type": "remote",
            "url": "http://<hostname>:3111/mcp",
            "headers": {
                "Authorization": "Bearer <token>"
            },
            "enabled": true
        }
    }
}
```
