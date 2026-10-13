from pathlib import Path
import unittest
import yaml
ROOT = Path(__file__).resolve().parents[1]
class SecurityTests(unittest.TestCase):
    def setUp(self):
        self.raw = (ROOT/'compose.yaml').read_text()
        self.compose = yaml.safe_load(self.raw)
        self.service = self.compose['services']['desktop']
    def test_private_binding(self):
        self.assertEqual(self.service['ports'], ['127.0.0.1:3000:3000'])
    def test_nonprivileged(self):
        self.assertNotIn('privileged', self.service)
        self.assertNotIn('network_mode', self.service)
        self.assertNotIn('docker.sock', self.raw)
    def test_persistent_data(self):
        self.assertIn('./desktop-data:/config', self.service['volumes'])
    def test_auth_required(self):
        self.assertEqual(self.service['environment']['PASSWORD'], '${DESKTOP_PASSWORD}')
        self.assertEqual(self.service['environment']['CUSTOM_USER'], '${DESKTOP_USER}')
    def test_no_secrets_in_archive(self):
        self.assertFalse((ROOT/'.env').exists())
    def test_android_is_readonly(self):
        s=(ROOT/'android-readiness.sh').read_text()
        for forbidden in ['modprobe ', 'docker run ', 'docker compose up', 'tailscale funnel']:
            self.assertNotIn(forbidden, s)
if __name__ == '__main__': unittest.main()
