import crypto from "node:crypto";

const ISSUER="https://token.actions.githubusercontent.com";
const AUDIENCE="hakim-development-worker";
const REPOSITORY="smileeyes1/sovereign-android-assistant";
const WORKFLOW_REF_PREFIX=REPOSITORY+"/.github/workflows/hakim-development-worker.yml@refs/heads/main";
const JWKS_URL=ISSUER+"/.well-known/jwks";

type JwkSet={keys:Array<Record<string,unknown>>};
export type GitHubWorkerIdentity={
  repository:string;
  ref:string;
  workflow_ref:string;
  run_id:string;
  run_attempt:string;
  jti:string;
};

let cached:{expires:number;keys:JwkSet}|null=null;

function decodeJson(segment:string):Record<string,unknown>{
  try{
    return JSON.parse(Buffer.from(segment,"base64url").toString("utf8")) as Record<string,unknown>;
  }catch{
    throw new Error("worker_oidc_malformed");
  }
}

async function liveJwks():Promise<JwkSet>{
  const now=Date.now();
  if(cached&&cached.expires>now) return cached.keys;
  const response=await fetch(JWKS_URL,{headers:{accept:"application/json"}});
  if(!response.ok) throw new Error("worker_oidc_jwks_unavailable");
  const value=await response.json() as JwkSet;
  if(!value||!Array.isArray(value.keys)||value.keys.length<1) throw new Error("worker_oidc_jwks_invalid");
  cached={expires:now+10*60_000,keys:value};
  return value;
}

function audienceMatches(raw:unknown){
  return typeof raw==="string"
    ?raw===AUDIENCE
    :Array.isArray(raw)&&raw.some(x=>x===AUDIENCE);
}

function claimString(payload:Record<string,unknown>,key:string){
  const value=payload[key];
  if(typeof value!=="string"||value.length<1||value.length>512) throw new Error("worker_oidc_claim_invalid");
  return value;
}

export async function verifyGitHubWorkerOidc(
  token:string,
  jwksOverride?:JwkSet,
  nowSeconds=Math.floor(Date.now()/1000)
):Promise<GitHubWorkerIdentity>{
  const parts=token.split(".");
  if(parts.length!==3) throw new Error("worker_oidc_malformed");
  const [encodedHeader,encodedPayload,encodedSignature]=parts;
  const header=decodeJson(encodedHeader);
  const payload=decodeJson(encodedPayload);
  if(header.alg!=="RS256"||typeof header.kid!=="string") throw new Error("worker_oidc_algorithm_invalid");

  const jwks=jwksOverride??await liveJwks();
  const jwk=jwks.keys.find(x=>x.kid===header.kid);
  if(!jwk) throw new Error("worker_oidc_key_unknown");
  let key:crypto.KeyObject;
  try{
    key=crypto.createPublicKey({key:jwk as crypto.JsonWebKey,format:"jwk"});
  }catch{
    throw new Error("worker_oidc_key_invalid");
  }
  const valid=crypto.verify(
    "RSA-SHA256",
    Buffer.from(encodedHeader+"."+encodedPayload,"utf8"),
    key,
    Buffer.from(encodedSignature,"base64url")
  );
  if(!valid) throw new Error("worker_oidc_signature_invalid");

  if(payload.iss!==ISSUER||!audienceMatches(payload.aud)) throw new Error("worker_oidc_issuer_or_audience_invalid");
  const exp=Number(payload.exp);
  const nbf=payload.nbf===undefined?0:Number(payload.nbf);
  const iat=Number(payload.iat);
  if(!Number.isSafeInteger(exp)||exp<nowSeconds-30) throw new Error("worker_oidc_expired");
  if(!Number.isSafeInteger(iat)||iat>nowSeconds+60||iat<nowSeconds-15*60) throw new Error("worker_oidc_time_invalid");
  if(nbf&&!Number.isSafeInteger(nbf)) throw new Error("worker_oidc_time_invalid");
  if(nbf&&nbf>nowSeconds+30) throw new Error("worker_oidc_not_yet_valid");

  const repository=claimString(payload,"repository");
  const ref=claimString(payload,"ref");
  const workflowRef=claimString(payload,"job_workflow_ref");
  if(repository!==REPOSITORY) throw new Error("worker_oidc_repository_invalid");
  if(ref!=="refs/heads/main") throw new Error("worker_oidc_ref_invalid");
  if(workflowRef!==WORKFLOW_REF_PREFIX) throw new Error("worker_oidc_workflow_invalid");
  const eventName=claimString(payload,"event_name");
  if(!["schedule","workflow_dispatch"].includes(eventName)) throw new Error("worker_oidc_event_invalid");

  return {
    repository,
    ref,
    workflow_ref:workflowRef,
    run_id:claimString(payload,"run_id"),
    run_attempt:claimString(payload,"run_attempt"),
    jti:claimString(payload,"jti")
  };
}
