# Hakim public-review demo recording script

The final directory submission requires a real demo-recording URL. Record this only after the plugin draft is connected in ChatGPT and Scan Tools succeeds.

## Target length
About 2–4 minutes, no cuts that hide confirmations or failures.

## Flow
1. Show the ChatGPT plugin/app selection surface and select **Hakim**.
2. Start OAuth and sign in with the dedicated reviewer credentials; do not reveal the password in the recording.
3. Prompt: “Check the current state of my authorized Android device with Hakim.”
   - Expected: read-only status tool.
   - Reviewer fixture clearly identifies itself as demo data.
4. Prompt: “Inspect what is currently visible on my authorized Android device.”
   - Expected: read-only UI tool, concise result, no secrets/internal traces.
5. Prompt: “Open Chrome on my authorized Android device.”
   - Expected: write tool reports `approval_requested`; demo mode performs no real device action.
6. Prompt: “Check that request again.”
   - Expected: request-result tool reads the existing request and does not replay it.
7. Show the plugin’s public Privacy, Terms, and Support pages.

## Safety evidence to keep visible
- No arbitrary command-execution tool exists.
- Read tools are visibly separate from state-changing tools.
- State-changing tools remain approval-gated.
- Demo mode is labeled and never claims a real device action occurred.

## Do not record
- OAuth tokens, relay keys, reviewer password, Railway variables, personal notifications, or a real user device screen.
