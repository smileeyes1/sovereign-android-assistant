# Hakim model-host policy

## Governing rule
Hakim is model-host independent. A conversational host may be ChatGPT, another explicitly trusted MCP/OAuth client, or a future local/self-hosted reasoning host. The host supplies reasoning; Hakim supplies bounded execution tools.

- No OpenAI API key is required by the Hakim bridge.
- No ChatGPT session cookie, browser cookie, or private session token is copied into Hakim.
- ChatGPT is the current default host because it gives users the capabilities available to their own account, but it is not a technical single point of failure.
- Additional hosts are allowed only by an explicit HTTPS hostname allowlist (HAKIM_TRUSTED_OAUTH_HOSTS); there is no wildcard trust.
- Hakim never claims that a specific model is available to every account or host.
- Hakim does not bypass plan, rate, regional, workspace, safety, or product limits.
- The bridge does not select, unlock, imitate, or bill a language model.

## Architecture
authorized reasoning host → standard MCP tools → Hakim encrypted relay → authorized Android device.

The relay transport is also portable: ntfy.sh is the default only. A compatible HTTPS relay can replace it through HAKIM_RELAY_BASE_URL and the pairing contract without changing Hakim's tool semantics.

The bridge is an execution connector, not an inference provider.
