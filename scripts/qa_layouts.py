#!/usr/bin/env python3
"""Capture the real TV's six layouts for visual comparison with the reference video."""
import importlib.util
import json
from pathlib import Path
import time

ROOT=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('smoke',Path(__file__).with_name('smoke.py'))
smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)
config=json.loads((ROOT/'work/test-pairing.json').read_text())

def call(path,body=None):
    code,payload,_=smoke.request(config,'GET' if body is None else 'POST',path,body)
    assert code==200,(path,code,payload[:200])
    return payload

original=json.loads(call('/v1/studio/state'))['scene']
try:
    for layout,background in [('app-icons','mountain'),('spotlight','golden'),('mosaic','city'),('sidebar','coast'),('dock','dunes'),('minimal','forest')]:
        call('/v1/studio/scene',dict(design=layout,layout=layout,background=background,font='elegant' if layout=='spotlight' else 'modern',tiles='glass' if layout=='dock' else 'brand',labels='logo' if layout in ('spotlight','minimal') else 'both',clock=True))
        time.sleep(2.2)
        image=call('/v1/frame')
        assert image.startswith(b'\xff\xd8')
        (ROOT/'work'/('layout-'+layout+'.jpg')).write_bytes(image)
        print('Captured real TV:',layout,flush=True)
finally:
    call('/v1/studio/scene',original)
