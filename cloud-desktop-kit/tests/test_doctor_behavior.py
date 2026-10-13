"""Offline smoke tests: doctor must not claim success on incomplete local setup."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

DOCTOR = Path(__file__).resolve().parents[1] / 'doctor.sh'


class DoctorTests(unittest.TestCase):
    def simulate(self, container=True, http='401', online=True, serve=True):
        with tempfile.TemporaryDirectory() as d:
            directory = Path(d)
            log = directory / 'trace'
            sudo = directory / 'sudo'
            sudo.write_text('''#!/usr/bin/env bash
printf 'sudo:%s\\n' "$*" >> "$FAKE_LOG"
if [[ "$1 $2" == 'docker ps' ]]; then
  [[ "$FAKE_CONTAINER" == 1 ]] && echo 'hakim-cloud-desktop'
elif [[ "$1 $2" == 'docker image' ]]; then
  echo 'lscr.io/linuxserver/webtop@sha256:example'
elif [[ "$1 $2" == 'tailscale ip' ]]; then
  [[ "$FAKE_ONLINE" == 1 ]] && echo '100.90.1.1'
elif [[ "$1 $2" == 'tailscale serve' ]]; then
  [[ "$FAKE_SERVE" == 1 ]] && echo 'https://example.ts.net -> http://127.0.0.1:3000'
else
  exit 19
fi
''')
            sudo.chmod(0o700)
            for cmd in ('docker', 'tailscale'):
                path = directory / cmd
                path.write_text('#!/usr/bin/env bash\nexit 7\n')
                path.chmod(0o700)
            curl = directory / 'curl'
            curl.write_text('#!/usr/bin/env bash\nprintf "%s" "$FAKE_HTTP"\n')
            curl.chmod(0o700)
            env = dict(os.environ)
            env.update(PATH=f'{directory}:{os.environ.get("PATH", "")}',
                       FAKE_LOG=str(log), FAKE_HTTP=http,
                       FAKE_CONTAINER='1' if container else '0',
                       FAKE_ONLINE='1' if online else '0',
                       FAKE_SERVE='1' if serve else '0')
            run = subprocess.run(['bash', str(DOCTOR)], env=env, text=True,
                                 capture_output=True, timeout=5)
            operations = log.read_text().splitlines() if log.exists() else []
            return run, operations

    def test_local_container_and_private_route_healthy(self):
        result, operations = self.simulate()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('الهاتف', result.stdout)
        self.assertFalse(any('serve --bg' in c or 'up -d' in c for c in operations))

    def test_container_missing(self):
        result, _ = self.simulate(container=False)
        self.assertNotEqual(result.returncode, 0)

    def test_password_bypass_or_no_auth_is_rejected(self):
        result, _ = self.simulate(http='200')
        self.assertNotEqual(result.returncode, 0)

    def test_redirect_does_not_prove_auth(self):
        result, _ = self.simulate(http='302')
        self.assertNotEqual(result.returncode, 0)

    def test_local_http_missing(self):
        result, _ = self.simulate(http='000')
        self.assertNotEqual(result.returncode, 0)

    def test_tailscale_disconnected(self):
        result, _ = self.simulate(online=False)
        self.assertNotEqual(result.returncode, 0)

    def test_serve_missing(self):
        result, _ = self.simulate(serve=False)
        self.assertNotEqual(result.returncode, 0)


if __name__ == '__main__':
    unittest.main()
