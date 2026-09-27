from pathlib import Path
import json
import re
import subprocess

root = Path(__file__).resolve().parents[1]
service = (root / "app/src/main/java/ps/hakim/phoneagent/HakimService.kt").read_text(encoding="utf-8")
legacy = service.split("private fun maybeCompleteBrowserTask", 1)[1].split("private fun failBrowserTask", 1)[0]
snapshot = service.split("private fun snapshotScript", 1)[1].split("private fun readBrowser", 1)[0]
old_script = re.search(r'val script = """(.*?)"""\.trimIndent\(\)', legacy, re.S)
new_script = re.search(r'private fun snapshotScript\(\): String = """(.*?)"""\.trimIndent\(\)', service, re.S)
sensitive = re.search(r'private fun sensitiveJs\(\): String = """(.*?)"""\.trimIndent\(\)', service, re.S)
assert old_script and new_script and sensitive, "missing browser scripts"
scripts = [old_script.group(1), new_script.group(1).replace("${sensitiveJs()}", sensitive.group(1))]

runner = r'''
const vm = require('node:vm');
const scripts = JSON.parse(require('node:fs').readFileSync(0,'utf8'));
const cases = [
  {title:'Public',text:'A normal article about API keys.',kind:'normal'},
  {title:'Public',text:'api_key=private',kind:'secret'},
  {title:'Public',text:'token=private',kind:'secret'},
  {title:'Public',text:'Bearer ABCDEFGHIJKLMNOPQRSTUVWXYZ1234',kind:'secret'},
  {title:'Bearer ABCDEFGHIJKLMNOPQRSTUVWXYZ1234',text:'Normal article',kind:'secret'},
  {title:'This page is blocked',text:'Your organization does not allow you to view this site',kind:'blocked'},
  {title:'Public',text:'An ordinary article about a blocked page.',kind:'normal'}
];
const outcomes=[];
for(const source of scripts){
  for(const test of cases){
    const document={
      title:test.title,
      body:{innerText:test.text},
      readyState:'complete',
      querySelectorAll:()=>[]
    };
    const page=JSON.parse(vm.runInNewContext(source,{
      document,location:{href:'https://example.org/article?state=private'},URL,
      window:{scrollY:0,innerHeight:900},getComputedStyle:()=>({visibility:'visible',display:'block'})
    }));
    outcomes.push(test.kind==='secret' ? page.privacy_gate===true && !JSON.stringify(page).includes('private')
      : test.kind==='blocked' ? page.blocked===true
      : page.privacy_gate!==true && page.blocked===false);
  }
}
const input={id:'key',name:'api_key',value:'private',tagName:'INPUT',href:'',innerText:'',
  getAttribute:k=> k==='name'?'api_key':k==='type'?'text':'',
  getBoundingClientRect:()=>({width:40,height:20,x:0,y:0})};
const document={title:'Public',body:{innerText:'A normal article.'},readyState:'complete',
  querySelectorAll:q=>q.includes('input')?[input]:[]};
const page=JSON.parse(vm.runInNewContext(scripts[1],{
  document,location:{href:'https://example.org/article'},URL,
  window:{scrollY:0,innerHeight:900},getComputedStyle:()=>({visibility:'visible',display:'block'})
}));
outcomes.push(page.interactive[0].sensitive===true && !JSON.stringify(page).includes('private'));
input.name='generic'; input.id='generic';
input.getAttribute=k=> k==='name'?'generic':k==='type'?'text':'',
document.querySelectorAll=q=>q.includes('input')?[input]:[];
const generic=JSON.parse(vm.runInNewContext(scripts[1],{
  document,location:{href:'https://example.org/article'},URL,
  window:{scrollY:0,innerHeight:900},getComputedStyle:()=>({visibility:'visible',display:'block'})
}));
outcomes.push(generic.interactive[0].sensitive===false && !JSON.stringify(generic).includes('private'));
process.stdout.write(JSON.stringify(outcomes));
'''
outcomes = json.loads(subprocess.check_output(["node", "-e", runner], input=json.dumps(scripts).encode()))
assert all(outcomes), "browser safety fixture failed: " + repr(outcomes)
assert 'page.optBoolean("blocked") -> JSONObject().put("ok", false)' in service, "relay read treats blocked as success"
assert 'if (page.optBoolean("blocked")) {' in service.split("private fun sendSnapshot", 1)[1], "legacy action treats blocked as success"
print("BROWSER_READ_SAFETY=PASS secret_gated=true blocked_failed=true benign_readable=true")
