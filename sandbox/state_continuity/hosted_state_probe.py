"""Finite synthetic state handoff via retained CI logs; no repository writes.
Only `fetch` may contact fixed GitHub read endpoints; `run` has no network path.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import types
import urllib.request
from urllib.parse import urlparse

REPO = 'smileeyes1/sovereign-android-assistant'
BRANCH = 'review/hakim-state-continuity-20261012'
WORKFLOW = '.github/workflows/hakim-state-continuity.yml'
MARKER = 'HAKIM_STATE_V1 '
ORIGINAL_HASHES = {'.github/workflows/hakim-shadow-proof.yml': 'bd8d414f9aa6b18cd0f003131c390c1e0bfd762cb0ed924098182f8f2afc36f9', 'sandbox/shadow_worker/README.md': 'c76469a5224bbc25d712a267fa9fb00220a0f0db2f417d47bb8f208a6e0ed92b', 'sandbox/shadow_worker/acceptance.py': '5627901b36bdb80163528f894f0098757a0a4c4c84a49622fbc80dd2e4e5c07a', 'sandbox/shadow_worker/baseline.json': '2e43317fd97eaef11220c8e7d924ecf23c1c46747bcc44015344f9b519cd95f7', 'sandbox/shadow_worker/demo.py': 'c29c03062387a1f3caec1d015fcc44cde0f5ed1826ee70dfd0565af68192eef0', 'sandbox/shadow_worker/test_worker.py': '4bba8862a78ec60590be9cf77727d084ce1e659b89bdb726cbc83f75cf0d4520', 'sandbox/shadow_worker/worker.py': '7b5f6e349615dcd92272013a4612235a38e67040e1eb6660233bf896b60074a2'}
MAX_STATE = 65536
MAX_RESPONSE = 262144
TASK_FIELDS = ('id','payload','fingerprint','state','owner','fence','expires','attempts','result','result_hash','created','updated')
EVENT_FIELDS = ('seq','task_id','event','at','attempt')


def canonical(value):
    return json.dumps(value,sort_keys=True,separators=(',',':'),ensure_ascii=True,allow_nan=False)


def sha(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def positive(value):
    if type(value) is not int or not 1 <= value <= 10**15:
        raise ValueError('bounded_identifier')
    return value


def control(value):
    if not isinstance(value,dict) or set(value) != {'stage','previous'} or type(value['stage']) is not int or value['stage'] not in (1,2,3):
        raise ValueError('control_schema')
    prev=value['previous']
    if value['stage']==1:
        if prev is not None: raise ValueError('stage1_has_predecessor')
    else:
        if not isinstance(prev,dict) or set(prev) != {'run_id','job_id','head_sha','state_sha256'}:
            raise ValueError('predecessor_schema')
        positive(prev['run_id']);positive(prev['job_id'])
        if not isinstance(prev['head_sha'],str) or not re.fullmatch('[a-f0-9]{40}',prev['head_sha']): raise ValueError('head_sha')
        if not isinstance(prev['state_sha256'],str) or not re.fullmatch('[a-f0-9]{64}',prev['state_sha256']): raise ValueError('state_sha')
    return value


def load_worker(repository):
    repository=Path(repository).resolve()
    hashes=ORIGINAL_HASHES
    for name,expected in hashes.items():
        p=repository/name
        data=bounded_bytes(p,65536)
        if hashlib.sha256(data).hexdigest()!=expected:
            raise ValueError('original_source_changed')
        if name=='sandbox/shadow_worker/worker.py':worker_bytes=data
    module=types.ModuleType('hakim_original_worker')
    module.__file__=str(repository/'sandbox/shadow_worker/worker.py')
    exec(compile(worker_bytes,module.__file__,'exec'),module.__dict__)
    return module


def bounded_bytes(path,limit):
    path=Path(path)
    if path.is_symlink() or not path.is_file() or path.stat().st_size>limit:
        raise ValueError('file_path_or_size')
    with path.open('rb') as handle:data=handle.read(limit+1)
    if len(data)>limit:raise ValueError('file_size')
    return data


def validate_snapshot(value, expected_stage=None):
    if not isinstance(value,dict) or set(value)!={'format','stage','pr_number','tasks','events'} or value['format']!='hakim-state-v1': raise ValueError('snapshot_schema')
    positive(value['pr_number'])
    if type(value['stage']) is not int or value['stage'] not in (1,2,3): raise ValueError('snapshot_stage')
    if expected_stage is not None and value['stage']!=expected_stage: raise ValueError('snapshot_chain_stage')
    if not isinstance(value['tasks'],list) or len(value['tasks'])!=3 or not isinstance(value['events'],list) or len(value['events'])>24: raise ValueError('snapshot_bounds')
    if len(canonical(value).encode())>MAX_STATE: raise ValueError('snapshot_size')
    for row in value['tasks']:
        if not isinstance(row,dict) or set(row)!=set(TASK_FIELDS): raise ValueError('task_row_schema')
    seen=set()
    for row in value['events']:
        if not isinstance(row,dict) or set(row)!=set(EVENT_FIELDS) or type(row['seq']) is not int or not 1<=row['seq']<=24 or row['seq'] in seen: raise ValueError('event_row_schema')
        seen.add(row['seq'])
    return value


def export(queue,stage):
    with queue.connect() as con:
        con.execute('BEGIN')
        queue.validate_store(con)
        value={'format':'hakim-state-v1','stage':stage,'pr_number':positive(int(os.environ['CURRENT_PR'])),'tasks':[dict(r) for r in con.execute('SELECT * FROM tasks ORDER BY id')],'events':[dict(r) for r in con.execute('SELECT * FROM events ORDER BY seq')]}
    return validate_snapshot(value)


def restore(queue,value):
    validate_snapshot(value)
    with queue.connect() as con:
        con.execute('BEGIN IMMEDIATE')
        if con.execute('SELECT count(*) FROM tasks').fetchone()[0] or con.execute('SELECT count(*) FROM events').fetchone()[0]: raise ValueError('restore_requires_empty')
        for row in value['tasks']:
            con.execute('INSERT INTO tasks('+','.join(TASK_FIELDS)+') VALUES ('+','.join('?' for _ in TASK_FIELDS)+')',tuple(row[k] for k in TASK_FIELDS))
        for row in value['events']:
            con.execute('INSERT INTO events('+','.join(EVENT_FIELDS)+') VALUES ('+','.join('?' for _ in EVENT_FIELDS)+')',tuple(row[k] for k in EVENT_FIELDS))
        queue.validate_store(con)
        con.commit()


def execute(worker,root,cfg,previous=None):
    cfg=control(cfg); stage=cfg['stage']
    if (Path(root)/'queue.sqlite3').exists():raise ValueError('fresh_root_required')
    q=worker.Queue(root)
    tasks=[{'id':'shadow-continuity-'+str(i),'kind':kind,'numbers':nums,'synthetic':True} for i,kind,nums in ((1,'sum',[1,2]),(2,'sort',[3,1,2]),(3,'sum',[4,5]))]
    if stage==1:
        for t in tasks: q.enqueue(t)
        lease=q.claim('hosted-1',lease_seconds=1);q.complete(lease,worker.compute(lease['task']))
        q.claim('hosted-1',lease_seconds=0.05)
    else:
        validate_snapshot(previous,stage-1)
        if previous['pr_number'] != positive(int(os.environ['CURRENT_PR'])): raise ValueError('pr_chain_mismatch')
        if sha(previous)!=cfg['previous']['state_sha256']: raise ValueError('predecessor_digest')
        restore(q,previous)
        lease=q.claim('hosted-'+str(stage),lease_seconds=1)
        if lease is None: raise ValueError('missing_task')
        if stage==2:
            try: q.complete(lease,{'mode':'shadow','kind':'sort','value':['invalid']})
            except worker.Rejected: pass
            else: raise ValueError('invalid_result_accepted')
        q.complete(lease,worker.compute(lease['task']))
    result=export(q,stage)
    if stage==3:
        if q.claim('hosted-3') is not None: raise ValueError('unexpected_pending_work')
        if any(q.enqueue(t)!='existing' for t in tasks): raise ValueError('idempotency_failure')
        if len([e for e in result['events'] if e['event']=='completed_shadow'])!=3 or any(r['state']!='completed' for r in result['tasks']): raise ValueError('completion_failure')
        if [r['attempts'] for r in result['tasks']] != [1,2,1]: raise ValueError('resume_attempt_failure')
        if len([e for e in result['events'] if e['event']=='lease_expired'])!=1: raise ValueError('missing_recovery')
    return result


class FixedRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        u=urlparse(newurl)
        if u.scheme!='https' or u.username or u.password or not (u.hostname=='api.github.com' or (u.hostname or '').endswith('.blob.core.windows.net') or (u.hostname or '').endswith('.actions.githubusercontent.com')):
            raise ValueError('redirect_host_denied')
        nextreq=super().redirect_request(req,fp,code,msg,headers,newurl)
        nextreq.remove_header('Authorization')
        return nextreq


def read_api(path,json_result=True):
    if not re.fullmatch(r'(runs/[0-9]+|jobs/[0-9]+(?:/logs)?)',path): raise ValueError('api_path_denied')
    url='https://api.github.com/repos/'+REPO+'/actions/'+path
    headers={'Accept':'application/vnd.github+json','User-Agent':'hakim-shadow-finite-state-probe'}
    # Public read endpoints only: never use a token or secrets.
    opener=urllib.request.build_opener(FixedRedirect())
    with opener.open(urllib.request.Request(url,headers=headers),timeout=20) as response:
        data=response.read(MAX_RESPONSE+1)
    if len(data)>MAX_RESPONSE: raise ValueError('response_size')
    return json.loads(data) if json_result else data.decode('utf-8',errors='strict')


def recover(cfg):
    control(cfg);p=cfg['previous']
    if p is None: raise ValueError('no_predecessor')
    run=read_api('runs/'+str(p['run_id']))
    if run.get('head_sha')!=p['head_sha'] or run.get('head_branch')!=BRANCH or run.get('event')!='pull_request' or run.get('path')!=WORKFLOW or run.get('status')!='completed' or run.get('conclusion')!='success' or run.get('repository',{}).get('full_name')!=REPO or run.get('head_repository',{}).get('full_name')!=REPO:
        raise ValueError('run_identity_or_acceptance')
    if not any(pr.get('number')==positive(int(os.environ['CURRENT_PR'])) for pr in run.get('pull_requests',[])): raise ValueError('predecessor_pr_identity')
    job=read_api('jobs/'+str(p['job_id']))
    if job.get('run_id')!=p['run_id'] or job.get('head_sha')!=p['head_sha'] or job.get('name')!='finite-state-probe' or job.get('conclusion')!='success': raise ValueError('job_identity')
    logs=read_api('jobs/'+str(p['job_id'])+'/logs',False)
    matches=[]
    for line in logs.splitlines():
        # GitHub prepends a timestamp. Reject shell-echo lines by requiring a single JSON object after the marker.
        if MARKER in line:
            tail=line.split(MARKER,1)[1]
            if tail.startswith('{'):
                try: matches.append(json.loads(tail))
                except json.JSONDecodeError: raise ValueError('malformed_checkpoint') from None
    if len(matches)!=1: raise ValueError('checkpoint_missing_or_ambiguous')
    value=validate_snapshot(matches[0],cfg['stage']-1)
    if value['pr_number'] != positive(int(os.environ['CURRENT_PR'])): raise ValueError('checkpoint_pr_identity')
    if sha(value)!=p['state_sha256']: raise ValueError('checkpoint_digest')
    return value


def main():
    ap=argparse.ArgumentParser();ap.add_argument('command',choices=('fetch','run'));ap.add_argument('--control',required=True);ap.add_argument('--snapshot',required=True);ap.add_argument('--repository',default='.');ap.add_argument('--root')
    args=ap.parse_args();cfg=control(json.loads(bounded_bytes(args.control,4096)))
    if args.command=='fetch':
        Path(args.snapshot).write_text(canonical(recover(cfg))+'\n');return
    if not args.root: raise ValueError('root_required')
    worker=load_worker(args.repository)
    previous=None if cfg['stage']==1 else json.loads(bounded_bytes(args.snapshot,MAX_STATE))
    result=execute(worker,args.root,cfg,previous)
    print(MARKER+canonical(result),flush=True)
    print('HAKIM_STATE_SHA256 '+sha(result),flush=True)
    # Deliberate termination after a claimed task remains unfinished in cycle 1.
    if cfg['stage']==1: sys.exit(23)

if __name__=='__main__': main()
