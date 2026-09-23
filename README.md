# SSH MCP Server

MCP server for accessing remote SSH servers.

## Features

- Authentication with password or private key (optionally with passphrase)
- Passwords, keys, and passphrases are stored encrypted in PostgreSQL
- Bearer token authorization (two roles: EDIT/EXECUTE, wildcard-based server filtering), tokens stored in PostgreSQL
- Command history table keyed by `sessionId`
- Per-command environment variables (`environmentVariables` argument of `execute`)
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
| `list_access_tokens`       | List all access tokens (full UUIDs, roles, executeOnly, comment) | TOKEN_ADMIN   |
| `upsert_access_token`      | Create or update an access token (returns full token value) | TOKEN_ADMIN   |
| `delete_access_token`      | Delete an access token                                      | TOKEN_ADMIN   |

## Access tokens

Tokens are bearer UUIDs stored in the `auth_tokens` table. Each token carries:

- `can_edit` — allows managing server connections (`add_server_connection`, `rename_server_connection`, `remove_server_connection`).
- `can_execute` — allows running `execute`. Restricted by `execute_only` glob patterns over server names; empty array means unrestricted.
- `is_token_admin` — allows managing access tokens via `list_access_tokens`, `upsert_access_token`, `delete_access_token`.
- `comment` — free-form operator note (e.g. owner identification), max 255 chars. On `upsert_access_token`: `null` keeps the current value; empty string clears it.

A token cannot modify or delete itself.

### Bootstrapping the first token-admin

On startup, if no token with `is_token_admin = true` exists in the database, the server auto-generates one and prints it to the logs at `WARN` level:

```
BOOTSTRAP-ADMIN-TOKEN: <uuid> -- store this UUID now, it will not be shown again
```

Save the UUID immediately — it is the only time the token value is shown. Use it as the `Authorization: Bearer <uuid>` header to call `list_access_tokens` / `upsert_access_token` / `delete_access_token`, then create a permanent admin token through the MCP tools and delete the bootstrap one.

To disable auto-bootstrap (for example, in production where the first admin is provisioned by an external process), set `BOOTSTRAP_ADMIN_TOKEN=false` or `bootstrap.admin-token.enabled=false` in configuration.

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
