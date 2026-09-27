from pathlib import Path
import re
import json
import subprocess

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/ps/hakim/phoneagent"
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
ORCH = (APP / "HakimSilentToolOrchestrator.kt").read_text(encoding="utf-8")
ROUTER = (APP / "HakimModelToolRouter.kt").read_text(encoding="utf-8")
CENTER = (APP / "CommandCenterActivity.kt").read_text(encoding="utf-8")
SERVICE = (APP / "HakimService.kt").read_text(encoding="utf-8")
DIRECTOR = (APP / "HakimIntentDirector.kt").read_text(encoding="utf-8")

def req(cond: bool, reason: str):
    if not cond:
        raise SystemExit("SILENT_TOOLS_20114=FAIL reason=" + reason)

m = re.search(r"versionCode\s+(\d+)", BUILD)
req(m is not None and int(m.group(1)) >= 20114, "version")

for token in [
    "BACKGROUND_BROWSER",
    "silentFirst = true",
    "terminalHandoffAllowed = false",
    "فتح المتصفح أو التطبيق وحده ليس نجاحًا",
]:
    req(token in ORCH, "orchestrator:" + token)

req("SILENT_BROWSER" in ROUTER, "silent_browser_channel_missing")
req("channel = Channel.SILENT_BROWSER" in ROUTER, "fresh_web_not_silent")
req("executeSilentBrowser(text, directed.instruction)" in CENTER, "command_center_not_silent")
req("HakimService.ACTION_BROWSER_TASK" in CENTER, "browser_service_action_missing")
req("pollSilentBrowser(" in CENTER, "browser_result_poll_missing")
req("HakimExecutiveLoop.complete" in CENTER, "completion_evidence_missing")
req("!HakimSilentToolOrchestrator.isDirectUrl(text)" in CENTER, "direct_url_privacy_guard_missing")

for token in [
    'ACTION_BROWSER_TASK = "ps.hakim.phoneagent.BROWSER_TASK"',
    'EXTRA_BROWSER_TASK_ID',
    'EXTRA_BROWSER_QUERY',
    'startBrowserTask(taskId, query)',
    'maybeCompleteBrowserTask',
    'target.evaluateJavascript',
    'putString(taskId + "_state", "COMPLETE")',
]:
    req(token in SERVICE, "service:" + token)

# Silent path must not explicitly open the visible browser Activity.
start = CENTER.index("private fun executeSilentBrowser")
end = CENTER.index("private fun executeLocalArtifact", start)
silent_block = CENTER[start:end]
req("startActivity(Intent(this, MainActivity::class.java))" not in silent_block, "silent_path_opens_visible_browser")

# Execute the page classifier itself against representative page responses.
browser_block = SERVICE.split("private fun maybeCompleteBrowserTask", 1)[1].split("private fun failBrowserTask", 1)[0]
script_match = re.search(r'val script = """(.*?)"""\.trimIndent\(\)', browser_block, re.S)
req(script_match is not None, "browser_result_classifier_missing")
runner = r'''
const vm = require('node:vm');
const script = require('node:fs').readFileSync(0, 'utf8');
const fixtures = [
  ['This page is blocked', 'Your organization does not allow you to view this site', true],
  ['صفحة محظورة', 'تم حظر هذه الصفحة', true],
  ['Access denied', 'No permission', true],
  ['Example', 'An ordinary article about a blocked page.', false]
];
const outcomes = fixtures.map(([title, body, expected]) => {
  const document = {title, body: {innerText: body}, querySelectorAll: () => []};
  const result = JSON.parse(vm.runInNewContext(script, {document, location: {href: 'https://example.org/?token=private'}, URL}));
  return result.blocked === expected;
});
const pairLink = {getAttribute: k => k==='href'?'hakim://pair?token=private&relay_key=private':''};
const document = {title:'ربط حكيم',body:{innerText:'token=private'},querySelectorAll: () => [pairLink]};
const protectedResult = JSON.parse(vm.runInNewContext(script, {document,location:{href:'https://bridge.example/oauth?code=private'},URL}));
outcomes.push(protectedResult.privacy_gate===true && !JSON.stringify(protectedResult).includes('private'));
const publicLink = {innerText:'Report',href:'https://example.org/report?access_token=private',getAttribute: () => ''};
const publicDocument = {title:'Report',body:{innerText:'Public report'},querySelectorAll: selector => selector==='a[href]'?[publicLink]:[]};
const publicResult = JSON.parse(vm.runInNewContext(script,{document:publicDocument,location:{href:'https://example.org/report?state=private'},URL}));
outcomes.push(publicResult.url==='https://example.org/report' && publicResult.links[0].href==='https://example.org/report' && !JSON.stringify(publicResult).includes('private'));
process.stdout.write(JSON.stringify(outcomes));
'''
outcomes = json.loads(subprocess.check_output(["node", "-e", runner], input=script_match.group(1).encode()))
req(all(outcomes), "browser_blocked_page_fixture")
snapshot_block = SERVICE.split("private fun sendSnapshot", 1)[1].split("private fun decodeJsString", 1)[0]
snapshot_match = re.search(r'val script = """(.*?)"""\.trimIndent\(\)', snapshot_block, re.S)
sensitive_match = re.search(r'private fun sensitiveJs\(\): String = """(.*?)"""\.trimIndent\(\)', SERVICE, re.S)
req(snapshot_match is not None and sensitive_match is not None, "snapshot_privacy_script_missing")
snapshot_js = snapshot_match.group(1).replace("${sensitiveJs()}", sensitive_match.group(1))
snapshot_runner = r'''
const vm = require('node:vm');
const script = require('node:fs').readFileSync(0, 'utf8');
const pairLink = {getAttribute: k => k==='href'?'hakim://pair?token=private&relay_key=private':''};
const document = {body:{innerText:'relay_key=private'},querySelectorAll: () => [pairLink]};
const result = JSON.parse(vm.runInNewContext(script, {document,location:{href:'https://bridge.example/oauth?token=private'},URL}));
process.stdout.write(JSON.stringify({gated:result.privacy_gate,leaked:JSON.stringify(result).includes('private')}));
'''
snapshot_outcome = json.loads(subprocess.check_output(["node", "-e", snapshot_runner], input=snapshot_js.encode()))
req(snapshot_outcome == {"gated": True, "leaked": False}, "legacy_snapshot_pairing_secret_leak")
req('put("status", "gated")' in snapshot_block, "legacy_snapshot_privacy_gate_not_enforced")
req("onReceivedHttpError" in SERVICE and "request?.isForMainFrame == true" in SERVICE, "main_frame_http_failure_not_handled")
req(browser_block.index('failBrowserTask(taskId, "حُظر الوصول') < browser_block.index('putString(taskId + "_state", "COMPLETE")'), "blocked_page_marked_complete")

for phrase in [
    "المتصفح المدمج والأدوات والخدمات وسائل داخلية",
    "سلّم النتيجة أو الملف الجاهز نفسه",
    "فتح صفحة أو تطبيق وحده ليس نجاحًا",
]:
    req(phrase in DIRECTOR, "director:" + phrase)

# Failure injection: replacing silent browser channel with visible browser must be detected.
mutant = ROUTER.replace("channel = Channel.SILENT_BROWSER", "channel = Channel.LOCAL_BROWSER")
try:
    req("channel = Channel.SILENT_BROWSER" in mutant, "known_failure_visible_browser")
except SystemExit:
    pass
else:
    raise SystemExit("SILENT_TOOLS_20114=FAIL reason=sentinel_not_detected")

print("SILENT_TOOLS_20114_GATE=PASS browser=background result=returned privacy=direct_url_local terminal_handoff=false")
