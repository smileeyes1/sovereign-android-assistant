"""Integration checks with fake Tailscale: no network or privileged action."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

CONNECT = Path(__file__).resolve().parents[1] / 'connect.sh'


class TailscaleConnectionTests(unittest.TestCase):
    def simulate(self, ip='100.88.1.2', previous='', status_fails=False):
        with tempfile.TemporaryDirectory() as d:
            folder = Path(d)
            mock = folder / 'tailscale'
            mock.write_text('''#!/usr/bin/env bash
printf '%s\\n' "$*" >> "$FAKE_LOG"
if [[ "$*" == 'ip -4' ]]; then
  printf '%s\\n' "$FAKE_IP"
elif [[ "$*" == 'serve status' ]]; then
  [[ "$FAKE_STATUS_FAIL" != 1 ]] || exit 9
  printf '%s\\n' "$FAKE_PREVIOUS"
elif [[ "$*" == 'serve --bg http://127.0.0.1:3000' ]]; then
  exit 0
else
  exit 10
fi
''')
            mock.chmod(0o700)
            log = folder / 'calls'
            environment = dict(os.environ)
            environment.update(
                PATH=f'{d}:{os.environ.get("PATH", "")}',
                FAKE_LOG=str(log), FAKE_IP=ip, FAKE_PREVIOUS=previous,
                FAKE_STATUS_FAIL='1' if status_fails else '0')
            result = subprocess.run(['bash', str(CONNECT)], env=environment,
                                    capture_output=True, text=True, timeout=5)
            return result, log.read_text().splitlines() if log.exists() else []

    def test_offline_does_not_configure(self):
        r, calls = self.simulate(ip='')
        self.assertEqual(r.returncode, 2)
        self.assertNotIn('serve --bg http://127.0.0.1:3000', calls)

    def test_status_failure_does_not_overwrite(self):
        r, calls = self.simulate(status_fails=True)
        self.assertEqual(r.returncode, 3)
        self.assertNotIn('serve --bg http://127.0.0.1:3000', calls)

    def test_other_existing_service_preserved(self):
        r, calls = self.simulate(previous='https://host.ts.net -> http://127.0.0.1:5000')
        self.assertEqual(r.returncode, 3)
        self.assertNotIn('serve --bg http://127.0.0.1:3000', calls)

    def test_already_configured_does_not_reconfigure(self):
        r, calls = self.simulate(previous='https://host.ts.net -> http://127.0.0.1:3000')
        self.assertEqual(r.returncode, 0)
        self.assertNotIn('serve --bg http://127.0.0.1:3000', calls)

    def test_clean_status_configures_private_serve(self):
        r, calls = self.simulate(previous='No serve config')
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertIn('serve --bg http://127.0.0.1:3000', calls)


if __name__ == '__main__':
    unittest.main()
