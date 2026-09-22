from pathlib import Path
s=Path("app/src/main/java/ps/hakim/phoneagent/HakimIntentEngine.kt").read_text(encoding="utf-8")
for x in ["ALL_TOKEN","ALL_CONTRACT","all_things_active","all_things_contract_available","all_things_scope_limited","ماذا أيضًا","لا WAIT بلا شرط استئناف","لا GATE بلا دليل"]:
    assert x in s, x
g=Path("governance/ALL_THINGS_CONTRACT.md").read_text(encoding="utf-8")
for x in ["الأثر المثبت هو وحدة الإغلاق","فشل الوسيلة لا يعني فشل المقصد","لا WAIT بلا شرط استئناف","لا GATE بلا دليل"]:
    assert x in g, x
print("ALL_THINGS_CONTRACT=PASS")
