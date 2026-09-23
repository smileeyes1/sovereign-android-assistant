# Hakim verified baseline

## LAST_VERIFIED_BASELINE

`c84359e422dad0aa205f59a153a1f29d7f4c4e81`

Verified on production on 2026-09-23.

## Evidence

- Railway production deployment `84e5c7d1-4bd3-4a44-add2-e60c03cff234` reports `SUCCESS`.
- Railway deployment metadata identifies commit `c84359e422dad0aa205f59a153a1f29d7f4c4e81` on branch `release/hakim-chatgpt-plugin-v1-rc1`.
- External HTTPS check: `/health` returned `ok:true`.
- External OAuth check accepted exactly `https://chatgpt.com/connector_platform_oauth_redirect`.
- Legacy ChatGPT OAuth callback `https://chatgpt.com/oauth/callback` remained accepted.
- Negative regression checks returned HTTP 400 for:
  - query mutation on the connector callback,
  - lookalike callback path,
  - wrong host,
  - userinfo injection,
  - unknown scope `hakim.admin`.
- ChatGPT Bridge CI and Android build completed successfully for the merge commit.

## Freeze rule

This baseline is not replaced by a later commit merely because it builds or deploys. A successor must pass the relevant CI gates plus production or field acceptance for the behavior it changes. Until then, this commit remains the rollback target.

## Next work

New work starts on isolated feature branches. Current sequence:
1. remaining regression coverage,
2. model/tool router,
3. unified attachment gateway,
4. Hakim conversational interface,
5. field acceptance before promoting a new baseline.
