import type { DeviceCredential } from "./protocol.js";
import { pollResult,publishCommand } from "./relay.js";

export const LIVE_PREFLIGHT_VERSION="HAKIM_LIVE_PREFLIGHT_V1";
export const MIN_FIELD_VERSION=20315;

export function livePreflightSummary(result:unknown,requestId:string){
  const root=(typeof result==="object"&&result!==null)?result as Record<string,unknown>:{};
  const inner=(typeof root.result==="object"&&root.result!==null)?root.result as Record<string,unknown>:{};
  const fabric=(typeof inner.execution_fabric==="object"&&inner.execution_fabric!==null)
    ?inner.execution_fabric as Record<string,unknown>:{};
  const containment=(typeof inner.fault_containment==="object"&&inner.fault_containment!==null)
    ?inner.fault_containment as Record<string,unknown>:{};

  const versionCode=typeof inner.version_code==="number"?inner.version_code:0;
  const packageName=typeof inner.package==="string"?inner.package:"";
  const relayState=typeof inner.secure_relay_state==="string"?inner.secure_relay_state:"unknown";
  const relayConnected=inner.secure_relay_connected===true;
  const fabricState=typeof fabric.state==="string"?fabric.state:"UNKNOWN";
  const fabricOnline=fabric.online===true;
  const browserRunning=inner.browser_service_running===true;
  const selfCheck=typeof inner.self_check==="string"?inner.self_check:"NOT_TESTED";
  const highImpactBlocked=containment.high_impact_blocked===true;
  const transportStatus=typeof root.status==="string"?root.status:"complete";
  const deviceOk=inner.ok!==false&&transportStatus!=="error"&&transportStatus!=="failed";

  const runtimeReady=
    deviceOk&&
    packageName==="ps.hakim.stable"&&
    versionCode>=MIN_FIELD_VERSION&&
    relayConnected&&
    relayState==="direct_connected"&&
    fabricOnline&&
    fabricState==="ONLINE"&&
    selfCheck!=="FAIL_CLOSED";

  return {
    preflight_version:LIVE_PREFLIGHT_VERSION,
    request_id:requestId,
    observed_at_ms:Date.now(),
    fresh:true,
    runtime_ready:runtimeReady,
    action_ready:runtimeReady&&!highImpactBlocked,
    minimum_field_version:MIN_FIELD_VERSION,
    device:{
      package:packageName,
      version_code:versionCode,
      version_name:typeof inner.version_name==="string"?inner.version_name:"",
      secure_relay_state:relayState,
      secure_relay_connected:relayConnected,
      browser_service_running:browserRunning,
      execution_fabric:{
        state:fabricState,
        online:fabricOnline
      },
      fault_containment:{
        high_impact_blocked:highImpactBlocked,
        recovery_required:containment.recovery_required===true
      },
      self_check:selfCheck
    },
    reason:
      !result?"status_timeout":
      !deviceOk?"device_status_failed":
      packageName!=="ps.hakim.stable"?"package_mismatch":
      versionCode<MIN_FIELD_VERSION?"field_version_below_minimum":
      !relayConnected||relayState!=="direct_connected"?"secure_relay_not_live":
      !fabricOnline||fabricState!=="ONLINE"?"execution_fabric_not_online":
      selfCheck==="FAIL_CLOSED"?"self_check_fail_closed":
      highImpactBlocked?"high_impact_blocked":
      "ready"
  };
}

export async function fetchLivePreflight(credential:DeviceCredential,reviewMode=false){
  if(reviewMode){
    return {
      preflight_version:LIVE_PREFLIGHT_VERSION,
      observed_at_ms:Date.now(),
      fresh:true,
      runtime_ready:true,
      action_ready:true,
      minimum_field_version:MIN_FIELD_VERSION,
      demo:true,
      device:{
        package:"ps.hakim.stable",
        version_code:MIN_FIELD_VERSION,
        version_name:"review-fixture",
        secure_relay_state:"direct_connected",
        secure_relay_connected:true,
        browser_service_running:true,
        execution_fabric:{state:"ONLINE",online:true},
        fault_containment:{high_impact_blocked:false,recovery_required:false},
        self_check:"PASS"
      },
      reason:"ready"
    };
  }
  const requestId=await publishCommand(credential,"status",{});
  const result=await pollResult(credential,requestId,8_000);
  return livePreflightSummary(result,requestId);
}
