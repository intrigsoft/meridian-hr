# Wiring Meridian into DioscHub

After the adapter is running (`mcp-adapter`, port 5175), configure the hub:

1. **Register the MCP server** — MCP servers → add, URL `http://<host>:5175/mcp`
   (Streamable HTTP), no static auth. Credential-less = the hub forwards each
   session's bound artifact (the adapter's JWT) as the `Authorization` header.
2. **Create/attach the Assistant** and grant the adapter's tools to the roles you
   want (Meridian's own RBAC still gates every action through the front door).
3. **Approval-gate** the destructive write tools you consider irreversible in the
   role's approval policy.
4. Mint an admin capability key with the `auth:bind` scope — it lives on the
   **adapter** (`DIOSC_HUB_API_KEY`), since the adapter performs the hub bind.

The adapter's own README documents the full tool catalog and the broker flow.
