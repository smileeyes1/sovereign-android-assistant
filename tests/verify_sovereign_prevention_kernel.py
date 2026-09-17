from pathlib import Path

p = Path('governance/SOVEREIGN_PREVENTION_KERNEL.md')
assert p.exists(), 'missing sovereign prevention kernel'
s = p.read_text(encoding='utf-8')
required = [
    'القرآن الكريم أصل الهداية',
    'لا تُحوَّل البركة أو النصوص الشرعية إلى سببية تقنية خفية',
    'المستخدم يملك المقصد',
    'الهوية ≠ القدرة ≠ الصلاحية ≠ التفويض',
    'المجهول ≠ سليم',
    'غياب اكتشاف الخطر ≠ إثبات السلامة',
    'المولد ≠ المدقق',
    'أقل صلاحية',
    'الجديد لا يرث نجاح القديم',
    'لا يثبت الميدان',
    'اختبار انحدار دائم',
    'غير مثبت',
]
missing = [x for x in required if x not in s]
assert not missing, f'missing governance invariants: {missing}'
forbidden = ['القرآن آلية تقنية', 'البركة آلية تقنية', 'نضمن صفر خطأ', 'مضمون بلا خطأ']
found = [x for x in forbidden if x in s]
assert not found, f'forbidden claims: {found}'
print('PASS: sovereign prevention kernel invariants')
