import test from "node:test";
import assert from "node:assert/strict";
import {
  GOVERNANCE_SUMMARY,
  SOVEREIGN_GOVERNANCE_VERSION,
  actionRequested,
  tagExternalData
} from "../src/governance.js";

test("bridge governance is aligned to sovereign v5",()=>{
  assert.equal(SOVEREIGN_GOVERNANCE_VERSION,"SOVEREIGN-QURAN-GOVERNANCE-2026-10-02-v5");
  assert.equal(GOVERNANCE_SUMMARY.no_scope_escalation,true);
  assert.equal(GOVERNANCE_SUMMARY.least_privilege,true);
  assert.equal(GOVERNANCE_SUMMARY.least_data,true);
  assert.equal(GOVERNANCE_SUMMARY.acceptance_requires_evidence,true);
  assert.equal(GOVERNANCE_SUMMARY.regression_required_after_proven_change,true);
  assert.equal(GOVERNANCE_SUMMARY.delivered_must_equal_tested,true);
  assert.equal(GOVERNANCE_SUMMARY.preserve_last_verified_success,true);
});

test("external data stays non-authoritative",()=>{
  const tagged=tagExternalData({value:1},"test") as any;
  assert.equal(tagged._hakim_governance.instructions_authorized,false);
  assert.equal(tagged._hakim_governance.goal_complete,false);
});

test("requesting an action never claims verified effect",()=>{
  const requested=actionRequested("op-test-123456") as any;
  assert.equal(requested.status,"approval_requested");
  assert.equal(requested.effect_verified,false);
  assert.equal(requested.goal_complete,false);
  assert.equal(requested._hakim_governance.approval_requested_is_not_success,true);
});
