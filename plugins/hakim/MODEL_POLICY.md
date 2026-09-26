# Hakim model-host policy

## Governing rule
ChatGPT is the conversational and reasoning host. Hakim does not select, unlock, proxy, imitate, or bill a language model.

- No OpenAI API key is required by the Hakim bridge.
- No ChatGPT session cookie, browser cookie, or private session token is copied into Hakim.
- The ChatGPT account and product surface determine which model, reasoning mode, tools, and usage limits are available to that user.
- Hakim must not claim that a specific model is available to every account.
- Hakim must not bypass plan, rate, regional, workspace, safety, or product limits.
- If ChatGPT changes the model selected for an account, Hakim continues to expose the same bounded device tools without needing a bridge update.
- The plugin directory is available across ChatGPT plans, but installation/use of an individual plugin can still depend on plan, region, workspace, surface, rollout, and included capabilities.

## Architecture
ChatGPT account → ChatGPT-hosted reasoning/model → Hakim MCP tools → encrypted relay → authorized Android device.

The bridge is an execution connector, not an inference provider.
