#!/usr/bin/env python3
"""Conservative source-only gate; does not certify an APK or Play Protect."""
import argparse, json, re, sys
from pathlib import Path
from xml.etree import ElementTree as ET
A='{http://schemas.android.com/apk/res/android}name'
T='{http://schemas.android.com/tools}node'
P={f'android.permission.{n}' for n in ('CAMERA','RECORD_AUDIO','ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','WRITE_EXTERNAL_STORAGE','REQUEST_INSTALL_PACKAGES','READ_SMS','RECEIVE_SMS','READ_PHONE_STATE','FOREGROUND_SERVICE_SPECIAL_USE')} | {'com.termux.permission.RUN_COMMAND'}
S={'HakimFamilyDnsVpnService','HakimAccessibilityService','HakimNotificationListener'}
R={'HakimTermuxResultReceiver'}
def audit(build, main, overlay):
    a,b=ET.fromstring(main),ET.fromstring(overlay)
    def effective(path, normalize=lambda n:n):
        base={normalize(e.get(A,'')) for e in a.findall(path) if e.get(A)}
        changes={}
        for e in b.findall(path):
            n=e.get(A)
            if n:
                n=normalize(n)
                if n in changes: raise ValueError('duplicate overlay entry: '+n)
                changes[n]=e.get(T,'merge')
        return (base | {n for n,v in changes.items() if v!='remove'}) - {n for n,v in changes.items() if v=='remove'}
    out=[]
    f=re.search(r'\bfield\s*\{([^}]+)\}',build)
    if not f or not re.search(r'HAKIM_ADVANCED"\s*,\s*"false"',f[1]): out.append('FIELD_ADVANCED_NOT_DISABLED')
    out += ['PERMISSION:'+p for p in sorted(P & effective('uses-permission'))]
    for path,denied,prefix in [('application/service',S,'SERVICE:'),('application/receiver',R,'RECEIVER:')]:
        out += [prefix+n for n in sorted(denied & effective(path,lambda n:n.rsplit('.',1)[-1]))]
    if re.search(r'^\s*implementation\s*\(?\s*[\'\"][^\'\"]*libadb-android',build,re.M): out.append('ADB_SHARED_DEPENDENCY')
    return out

def selftest():
    ns='xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools"'
    def xml(body): return '<manifest '+ns+'>'+body+'</manifest>'
    cam='<uses-permission android:name="android.permission.CAMERA"/>'
    rem='<uses-permission android:name="android.permission.CAMERA" tools:node="remove"/>'
    good='field { buildConfigField "boolean", "HAKIM_ADVANCED", "false" }'
    main=xml(cam+'<application><service android:name=".HakimFamilyDnsVpnService"/></application>')
    svc='<application><service android:name=".HakimFamilyDnsVpnService" tools:node="remove"/></application>'
    cases=[('minimal',lambda:not audit(good,main,xml(rem+svc))),('advanced',lambda:'FIELD_ADVANCED_NOT_DISABLED' in audit(good.replace('false','true'),main,xml(rem+svc))),('camera',lambda:'PERMISSION:android.permission.CAMERA' in audit(good,main,xml(svc))),('comment',lambda:'PERMISSION:android.permission.CAMERA' in audit(good,main,xml('<!--'+rem+'-->'+svc))),('vpn',lambda:'SERVICE:HakimFamilyDnsVpnService' in audit(good,main,xml(rem))),('adb',lambda:'ADB_SHARED_DEPENDENCY' in audit(good+'\n implementation("com.github.MuntashirAkon:libadb-android:3.1.1")',main,xml(rem+svc)))]
    for name,fn in cases:
        if not fn(): raise AssertionError(name)
        print('PASS',name)
    print(f'SELFTEST PASS {len(cases)}/{len(cases)}')

def main():
    p=argparse.ArgumentParser();p.add_argument('root',nargs='?');p.add_argument('--self-test',action='store_true');args=p.parse_args()
    if args.self_test: selftest();return 0
    if not args.root: p.error('repository root required')
    try:
        r=Path(args.root); issues=audit(*[(r/f).read_text() for f in ('app/build.gradle','app/src/main/AndroidManifest.xml','app/src/field/AndroidManifest.xml')]);print(json.dumps({'source_gate':'FAIL' if issues else 'PASS','issues':issues,'apk':'NOT TESTED'},ensure_ascii=False,indent=2));return 1 if issues else 0
    except (OSError,ValueError,ET.ParseError) as e: print('BLOCKED:',e,file=sys.stderr);return 2
if __name__=='__main__': sys.exit(main())
