import test from "node:test";
import assert from "node:assert/strict";
import { AUTONOMY_KERNEL_VERSION,autonomyKernelSummary,chooseAutonomyRoute } from "../src/autonomy-kernel.js";

test("autonomy kernel is zero-cost and forbids paid fallback",()=>{
  const s=autonomyKernelSummary({});
  assert.equal(s.autonomy_kernel_version,AUTONOMY_KERNEL_VERSION);
  assert.equal(s.policy.cost_ceiling_usd,0);
  assert.equal(s.policy.paid_fallback,false);
  assert.equal(s.policy.purchase_or_credit_use,false);
  assert.equal(s.independence.provider_neutral,true);
});

test("local verified route wins over public free route",()=>{
  const result=chooseAutonomyRoute(
    {capability:"video",allows_public_data:true},
    [
      {id:"public",kind:"public_free",capability:"video",available:true,verified:true,privacy:"public",cost_usd:0,quota_remaining:true,resumable:true,priority:50},
      {id:"local",kind:"local",capability:"video",available:true,verified:true,privacy:"local",cost_usd:0,quota_remaining:true,resumable:true,priority:1}
    ]
  );
  assert.equal(result.ok,true);
  assert.equal(result.resource?.id,"local");
});

test("personal data can never route to public compute",()=>{
  const result=chooseAutonomyRoute(
    {capability:"video",contains_personal_data:true},
    [{id:"public",kind:"public_free",capability:"video",available:true,verified:true,privacy:"public",cost_usd:0,quota_remaining:true,resumable:true,priority:99}]
  );
  assert.equal(result.ok,false);
  assert.equal(result.state,"capability_unavailable");
});

test("free quota exhaustion becomes resumable wait, never paid fallback",()=>{
  const result=chooseAutonomyRoute(
    {capability:"video",allows_public_data:true},
    [{id:"free",kind:"account_free",capability:"video",available:true,verified:true,privacy:"private",cost_usd:0,quota_remaining:false,resumable:true,priority:10}]
  );
  assert.equal(result.ok,false);
  assert.equal(result.state,"waiting_free_capacity");
  assert.equal(result.paid_fallback,false);
  assert.equal(result.retry_policy,"resume_from_checkpoint_when_free_capacity_returns");
});

test("paid resources are ineligible even if otherwise strongest",()=>{
  const result=chooseAutonomyRoute(
    {capability:"video",allows_public_data:true},
    [
      {id:"paid",kind:"self_hosted_free",capability:"video",available:true,verified:true,privacy:"private",cost_usd:0.01,quota_remaining:true,resumable:true,priority:999},
      {id:"free",kind:"public_free",capability:"video",available:true,verified:true,privacy:"public",cost_usd:0,quota_remaining:true,resumable:true,priority:1}
    ]
  );
  assert.equal(result.ok,true);
  assert.equal(result.resource?.id,"free");
});
