import { localRendererAvailable } from "./local-cinematic-renderer.js";

export const AUTONOMY_KERNEL_VERSION="HAKIM_FREE_AUTONOMY_V1_2026-09-30";

export type AutonomyResource={
  id:string;
  kind:"local"|"self_hosted_free"|"account_free"|"public_free";
  capability:string;
  available:boolean;
  verified:boolean;
  privacy:"local"|"private"|"public";
  cost_usd:number;
  quota_remaining:boolean|"unknown";
  resumable:boolean;
  priority:number;
};

export type AutonomyRequirement={
  capability:string;
  allows_public_data?:boolean;
  contains_personal_data?:boolean;
};

function eligible(r:AutonomyResource,req:AutonomyRequirement){
  if(r.capability!==req.capability) return false;
  if(!r.available||!r.verified) return false;
  if(r.cost_usd!==0) return false;
  if(r.quota_remaining===false) return false;
  if(req.contains_personal_data===true&&r.privacy==="public") return false;
  if(req.allows_public_data===false&&r.privacy==="public") return false;
  return true;
}

function sovereigntyRank(kind:AutonomyResource["kind"]){
  switch(kind){
    case "local": return 400;
    case "self_hosted_free": return 300;
    case "account_free": return 200;
    case "public_free": return 100;
  }
}

export function chooseAutonomyRoute(req:AutonomyRequirement,resources:AutonomyResource[]){
  const ranked=resources
    .filter(r=>eligible(r,req))
    .map(r=>({resource:r,score:sovereigntyRank(r.kind)+r.priority+(r.resumable?10:0)}))
    .sort((a,b)=>b.score-a.score||a.resource.id.localeCompare(b.resource.id));

  if(ranked.length){
    return {
      ok:true,
      state:"ready" as const,
      resource:ranked[0]!.resource,
      candidates:ranked.map(x=>({id:x.resource.id,score:x.score})),
      cost_usd:0,
      paid_fallback:false
    };
  }

  const quotaBlocked=resources.some(r=>
    r.capability===req.capability&&r.available&&r.verified&&r.cost_usd===0&&r.quota_remaining===false
  );
  return {
    ok:false,
    state:quotaBlocked?"waiting_free_capacity" as const:"capability_unavailable" as const,
    resource:null,
    cost_usd:0,
    paid_fallback:false,
    retry_policy:quotaBlocked?"resume_from_checkpoint_when_free_capacity_returns":"discover_new_free_or_local_resource",
    rule:"never_pay_never_replay_completed_work"
  };
}

export function autonomyKernelSummary(env:NodeJS.ProcessEnv=process.env){
  const localVideo=localRendererAvailable(env);
  return {
    ok:true,
    autonomy_kernel_version:AUTONOMY_KERNEL_VERSION,
    policy:{
      cost_ceiling_usd:0,
      free_only:true,
      paid_fallback:false,
      purchase_or_credit_use:false,
      user_card_required:false,
      external_quota_is_not_claimed_unlimited:true
    },
    control_loop:[
      "inspect_current_state",
      "choose_zero_cost_route",
      "execute_with_least_privilege",
      "verify_effect",
      "checkpoint_progress",
      "repair_or_switch_route",
      "resume_without_replaying_verified_work"
    ],
    independence:{
      local_first:true,
      provider_neutral:true,
      one_provider_dependency_forbidden:true,
      durable_checkpoints:true,
      resumable_jobs:true,
      circuit_breakers:true,
      exponential_backoff:true,
      graceful_degradation:true,
      autonomous_recovery:true,
      human_needed_only_for_external_identity_consent_or_irreducible_authorization:true
    },
    privacy:{
      personal_data_to_public_compute:false,
      student_data_to_public_compute:false,
      public_compute_for_synthetic_or_non_sensitive_assets_only:true
    },
    currently_known_routes:{
      local_video_fallback:localVideo,
      public_zero_gpu_contracts_verified:true,
      external_free_quota_unlimited:false,
      external_free_quota_may_pause:true
    },
    continuity_semantics:{
      quota_exhaustion:"waiting_free_capacity_not_failure",
      provider_outage:"switch_provider_or_queue",
      network_loss:"checkpoint_and_resume",
      completed_work:"immutable_unless_regression_detected"
    },
    absolute_limit:
      "Unlimited zero-cost external GPU capacity cannot be guaranteed by software. Hakim guarantees zero automatic spend and continuity semantics, not infinite third-party capacity."
  };
}
