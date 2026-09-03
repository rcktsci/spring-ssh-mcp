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

| Tool                       | Purpose                                                     | Required role |
|----------------------------|-------------------------------------------------------------|---------------|
| `list_servers`             | List servers                                                |               |
| `add_server_connection`    | Add a server connection                                     | EDIT          |
| `rename_server_connection` | Rename a connection                                         | EDIT          |
| `remove_server_connection` | Remove a connection                                         | EDIT          |
| `generate_session_id`      | Generate a `sessionId` for a subsequent `execute` call      |               |
| `execute`                  | Execute a command                                           | EXECUTE       |
| `list_access_tokens`       | List all access tokens (full UUIDs, roles, executeOnly)     | TOKEN_ADMIN   |
| `upsert_access_token`      | Create or update an access token (returns full token value) | TOKEN_ADMIN   |
| `delete_access_token`      | Delete an access token                                      | TOKEN_ADMIN   |

## Access tokens

Tokens are bearer UUIDs stored in the `auth_tokens` table. Each token carries:

- `can_edit` — allows managing server connections (`add_server_connection`, `rename_server_connection`, `remove_server_connection`).
- `can_execute` — allows running `execute`. Restricted by `execute_only` glob patterns over server names; empty array means unrestricted.
- `is_token_admin` — allows managing access tokens via `list_access_tokens`, `upsert_access_token`, `delete_access_token`.

A token cannot modify or delete itself.

### Bootstrapping the first token-admin

The first `TOKEN_ADMIN` must be created directly in the database (no token exists yet to call the tools):

```sql
INSERT INTO "ssh-mcp".auth_tokens (token, can_edit, can_execute, is_token_admin)
VALUES (gen_random_uuid(), true, true, true)
RETURNING token;
```

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
