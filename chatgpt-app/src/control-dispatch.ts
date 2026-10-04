import type { DeviceCredential } from "./protocol.js";
import { publishCommand } from "./relay.js";
import { continuityStore } from "./continuity-store.js";

export async function dispatchControlLaunch(
  credential:DeviceCredential,
  payload:{package:string;url:string}
){
  const requestId=await publishCommand(credential,"launch",payload);
  await continuityStore.recordRequested(credential,requestId,"launch");
  return requestId;
}

export async function dispatchControlNavigate(
  credential:DeviceCredential,
  kind:"home"|"back"|"recents"
){
  const requestId=await publishCommand(credential,"action",{action:kind});
  await continuityStore.recordRequested(credential,requestId,"action");
  return requestId;
}
