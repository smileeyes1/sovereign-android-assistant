from pathlib import Path
s=Path("app/src/main/java/ps/hakim/phoneagent/AutoUpdater.kt").read_text(encoding="utf-8")
d1="d13e7aa8271cb6d32aec2157cc5ba4fafd226957eb0c731e9ceba827bf78b0d3"
old="f42d71b0308a543e253099c02301bfdbfefa45f8755b12ab22a3c903305e442e"
assert f'FIELD_CERT_SHA256 = "{d1}"' in s
assert old not in s
assert "archiveCerts == expectedCerts && currentCerts == expectedCerts" in s
print("FIELD_D1_TRUST_ANCHOR=PASS")
