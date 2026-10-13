import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "codespaces-desktop.sh"

class CodespacesDesktopTests(unittest.TestCase):
    def run_script(self, *, codespace=True, docker=True, http='401', reused=False, altered=False):
        with tempfile.TemporaryDirectory() as tmp:
            home=Path(tmp)/'home'; home.mkdir()
            bindir=Path(tmp)/'bin'; bindir.mkdir()
            log=Path(tmp)/'docker.log'
            docker_script='''#!/bin/sh
printf '%s\\n' "$*" >> "$FAKE_DOCKER_LOG"
if [ "$1" = info ]; then exit 0; fi
if [ "$1" = compose ]; then exit 0; fi
exit 9
'''
            for name,content in [
               ('docker',docker_script),
               ('df','#!/bin/sh\nprintf "Filesystem 1024-blocks Used Available Capacity Mounted on\\n/mock 80000000 10000 70000000 1%% /\\n"\n'),
               ('curl',f'#!/bin/sh\nprintf "%s" "{http}"\n'),
               ('sleep','#!/bin/sh\nexit 0\n')]:
                f=bindir/name; f.write_text(content); f.chmod(0o700)
            if not docker:
                (bindir/'docker').write_text('#!/bin/sh\nexit 1\n')
            dest=home/'hakim-codespaces-desktop'
            if reused:
                dest.mkdir()
                (dest/'compose.yaml').write_text('different' if altered else (ROOT/'compose.yaml').read_text())
                (dest/'.env').write_text('PUID=1000\nPGID=1000\nDESKTOP_USER=hakim\nDESKTOP_PASSWORD=secret123\n')
            env=os.environ.copy();env.update(HOME=str(home),PATH=str(bindir)+':'+os.environ['PATH'],FAKE_DOCKER_LOG=str(log))
            if codespace: env['CODESPACE_NAME']='safe-test-codespace'
            else: env.pop('CODESPACE_NAME',None)
            cp=subprocess.run(['bash',str(SCRIPT)],cwd=ROOT,env=env,text=True,capture_output=True,timeout=25)
            return cp, log.read_text() if log.exists() else '', dest.exists(), (dest/'.env').exists(), (dest/'.env').read_text() if (dest/'.env').exists() else ''

    def test_away_from_codespaces_has_no_side_effect(self):
        cp, log, exists, *_=self.run_script(codespace=False)
        self.assertNotEqual(cp.returncode,0);self.assertEqual(log,'');self.assertFalse(exists)

    def test_without_docker_rejects_without_writes(self):
        cp,log,exists,*_=self.run_script(docker=False)
        self.assertNotEqual(cp.returncode,0);self.assertFalse(exists)

    def test_success_creates_local_auth_and_docker(self):
        cp,log,exists,secrets,envfile=self.run_script()
        self.assertEqual(cp.returncode,0,cp.stderr)
        self.assertTrue(exists and secrets)
        self.assertIn('DESKTOP_PASSWORD=',envfile)
        self.assertIn('up -d',log)
        self.assertNotIn('stop',log)

    def test_unauthenticated_200_must_stop(self):
        cp,log,*_=self.run_script(http='200')
        self.assertEqual(cp.returncode,3)
        self.assertIn('stop',log)
        self.assertNotIn('نجح فحص المصادقة',cp.stdout)

    def test_no_http_response_must_stop(self):
        cp,log,*_=self.run_script(http='000')
        self.assertEqual(cp.returncode,3)
        self.assertIn('stop',log)

    def test_existing_configuration_preserved(self):
        cp,log,exists,secrets,envfile=self.run_script(reused=True,altered=True)
        self.assertNotEqual(cp.returncode,0)
        self.assertNotIn('up -d',log)
        self.assertEqual(envfile,'PUID=1000\nPGID=1000\nDESKTOP_USER=hakim\nDESKTOP_PASSWORD=secret123\n')

    def test_existing_correct_configuration_preserved(self):
        cp,log,exists,secrets,envfile=self.run_script(reused=True)
        self.assertEqual(cp.returncode,0,cp.stderr)
        self.assertIn('DESKTOP_PASSWORD=secret123',envfile)
        self.assertIn('up -d',log)

if __name__=='__main__':unittest.main()
