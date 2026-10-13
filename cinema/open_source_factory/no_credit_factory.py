#!/usr/bin/env python3
"""Open Cinema: entirely offline, no-credit early-primary number lesson motion video.
This is NOT a photorealistic / actor-generative engine. MIT licensed.
"""
from __future__ import annotations
import argparse, hashlib, json, math, shutil, subprocess, tempfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, features

HERE=Path(__file__).resolve().parent
AR_TRANSLATION=str.maketrans('0123456789','٠١٢٣٤٥٦٧٨٩')
RES=(960,540); FPS=12
FONTS=[Path('/usr/share/fonts/truetype/noto/NotoSansArabic-Bold.ttf'),
       Path('/usr/share/fonts/truetype/noto/NotoSansArabic-Regular.ttf'),
       Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')]

def find_font():
    for path in FONTS:
        if path.exists(): return path
    # Android Termux fonts may be placed in project/fonts; do not fetch them.
    for path in (HERE/'fonts').glob('*.ttf'):
        return path
    raise RuntimeError('لم يُعثر على خط عربي: أضف خط NotoSansArabic إلى مجلد fonts/')

def execute(cmd):
    p=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    if p.returncode: raise RuntimeError(p.stderr[-1800:])
    return p.stdout

def fmt(n:int)->str:
    return str(n).translate(AR_TRANSLATION)


def paint(topic:str, shot:int, t:float, fontpath:Path, number:int)->Image.Image:
    w,h=RES
    im=Image.new('RGB',(w,h),'#0b1830')
    d=ImageDraw.Draw(im)
    for y in range(0,h,4):
        s=y/h
        d.rectangle((0,y,w,y+4),fill=(int(7+20*s),int(24+24*s),int(46+25*s)))
    # Use non-circular decorations: children must not mistake ornaments for objects being counted.
    for i in range(8):
        x=int((i*173 + 13*math.sin(t*.7+i))%w)
        y=int((i*83 + 7*math.cos(t*.4+i))%h)
        d.line((x,y,x+33,y+12),fill=(39,69,89),width=2)
    d.rounded_rectangle((42,44,w-42,h-44),radius=38,outline=(107,157,171),width=3)
    fbig=ImageFont.truetype(str(fontpath),62)
    fsub=ImageFont.truetype(str(fontpath),35)
    if shot==0:
        d.text((w//2,h//2-70),f'العدد {fmt(number)}',anchor='mm',font=fbig,fill='#f5efde',direction='rtl',stroke_width=1)
        d.text((w//2,h//2+18),'هيا نعدّ الكرات معًا',anchor='mm',font=fsub,fill='#c7dbc9',direction='rtl')
    else:
        # Each panel has a mathematically exact, visually countable number of orange balls.
        first=1 if number==1 else min(number-1,3)
        remaining=1 if number==1 else number-first
        count={1:first,2:remaining,3:number}[shot]
        if shot==1: title='المجموعة الأولى'
        elif shot==2: title='نعدّ مرة أخرى' if number==1 else 'المجموعة الثانية'
        else: title='العدد كاملًا'
        d.text((w//2,93),title,anchor='mm',font=fbig,fill='#f5efde',direction='rtl')
        # Arrange 1–10 balls in up to two rows to avoid clipping.
        per_row=5
        rows=(count+per_row-1)//per_row
        ballR=36
        for row in range(rows):
            howmany=min(per_row,count-row*per_row)
            spacing=116
            startx=w//2-((howmany-1)*spacing)//2
            yy=(256 if rows==1 else 225+row*108)
            for col in range(howmany):
                x=startx+col*spacing
                y=yy+int(4*math.sin(t*2+col*.3))
                d.ellipse((x-ballR,y-ballR,x+ballR,y+ballR),fill='#f28b35',outline='#ffd2a4',width=4)
                d.ellipse((x-20,y-20,x-4,y-8),fill='#ffb66a')
        fnum=ImageFont.truetype(str(fontpath),50)
        d.text((w//2,424),fmt(count),anchor='mm',font=fnum,fill='#ecf2e5',direction='rtl')
    return im


def parse_number(topic:str)->int:
    import re
    m=re.fullmatch(r'\s*(?:درس\s+)?العدد\s*([٠-٩0-9]{1,2})\s*',topic)
    if not m:
        raise ValueError('الموضوع غير مدعوم بصدق: النسخة المحلية تولّد فيديوهات العدد ١ إلى ١٠ فقط؛ ولا تختلق مشاهد واقعية')
    number=int(m.group(1).translate(str.maketrans('٠١٢٣٤٥٦٧٨٩','0123456789')))
    if not 1 <= number <= 10:
        raise ValueError('يدعم القالب الأعداد من ١ إلى ١٠ فقط')
    return number

def create(topic,dest):
    number=parse_number(topic)
    if not features.check('raqm'):
        raise RuntimeError('محرّك تشكيل العربية RAQM غير متاح، ولذلك توقف الإخراج لمنع انقلاب الحروف')
    if not shutil.which('ffmpeg') or not shutil.which('ffprobe'):
        raise RuntimeError('أداة ffmpeg أو ffprobe غير موجودة')
    font=find_font()
    dest=Path(dest).expanduser().resolve()
    dest.parent.mkdir(parents=True,exist_ok=True)
    secs=8; frames=secs*FPS
    with tempfile.TemporaryDirectory(prefix='no-credit-factory-') as d:
        td=Path(d)
        for i in range(frames):
            shot=i//(2*FPS)
            image=paint(topic,shot,i/FPS,font,number)
            image.save(td/f'{i:04}.jpg',quality=88)
        out=td/'candidate.mp4'
        execute(['ffmpeg','-hide_banner','-loglevel','error','-y','-framerate',str(FPS),
             '-i',str(td/'%04d.jpg'),'-f','lavfi','-i','anullsrc=r=48000:cl=stereo',
             '-c:v','libx264','-threads','2','-preset','veryfast','-crf','23',
             '-pix_fmt','yuv420p','-r','24','-c:a','aac','-shortest',
             '-t',str(secs),'-movflags','+faststart',str(out)])
        data=json.loads(execute(['ffprobe','-v','quiet','-print_format','json','-show_streams','-show_format',str(out)]))
        v=next((s for s in data['streams'] if s['codec_type']=='video'),None)
        a=next((s for s in data['streams'] if s['codec_type']=='audio'),None)
        dur=float(data['format']['duration'])
        if not (v and v.get('codec_name')=='h264' and v.get('width')==RES[0]
                and v.get('height')==RES[1] and a and a.get('codec_name')=='aac' and abs(dur-secs)<.5):
            raise RuntimeError('لم يجتز ملف الفيديو فحص التصدير')
        # Test pass before overwrite, with a rollback copy if target existed.
        if dest.exists(): shutil.copy2(dest,dest.with_suffix('.previous.mp4'))
        shutil.copy2(out,dest)
        result={'status':'technically_verified','topic':f'العدد {fmt(number)}','file':dest.name,
                'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),
                'duration_seconds':dur,'resolution':list(RES),'video':'h264','audio':'silent_aac',
                'credits_consumed':0,'internet_calls':0,'ball_counts':[1 if number==1 else min(3,number-1),1 if number==1 else number-min(3,number-1),number],
                'type':'original motion graphics; NOT cinematic acting or photorealistic generation',
                'limitations':['ليس تصويرًا واقعيًا','لا يحتوي على حوار أو صوت راوٍ','الحدود الطبيعية للموارد ما زالت قائمة']}
        dest.with_suffix('.verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        return result

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--topic',default='العدد ٧')
    p.add_argument('--output',default=str(HERE/'output'/'offline-number-seven.mp4'))
    a=p.parse_args()
    print(json.dumps(create(a.topic,a.output),ensure_ascii=False,indent=2))
