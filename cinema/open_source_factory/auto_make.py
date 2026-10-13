#!/usr/bin/env python3
"""Offline no-credits video request router: render only genuinely supported topics."""
import argparse, json, re, sys
from pathlib import Path
from no_credit_factory import create, parse_number, fmt

def process(request:str, output:str|None):
    request=request.strip()
    # Natural language, but narrow: do not treat arbitrary films as text-card videos.
    m=re.fullmatch(r'(?:اصنع(?: لي)?|أنشئ(?: لي)?|اعمل(?: لي)?|فيديو(?: عن)?|(?:اصنع|أنشئ)(?: لي)? فيديو(?: عن)?)?\s*(?:فيديو\s*)?(?:عن\s*)?(?:درس\s*)?العدد\s*([٠-٩0-9]{1,2})(?:\s*(?:للصف الأول|للصف الاول|للصف الأوّل|للأطفال))?\s*',request)
    if not m:
        raise ValueError('تعذّر التوليد المحلي الصادق لهذا الطلب: نموذج الفيديو الواقعي غير مثبت، ولا أصنع رسومًا وأسميها فيلمًا واقعيًا.')
    n=parse_number('العدد '+m.group(1))
    out=Path(output) if output else (Path(__file__).resolve().parent/'output'/f'number-{n:02}.mp4')
    return create(f'العدد {fmt(n)}',out)

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('request',help='مثال: اصنع لي فيديو عن العدد ٦')
    parser.add_argument('--output')
    args=parser.parse_args()
    try:
        print(json.dumps(process(args.request,args.output),ensure_ascii=False,indent=2))
    except (ValueError,RuntimeError) as e:
        print(str(e),file=sys.stderr)
        sys.exit(2)
