#!/usr/bin/env python3
"""UI driver for the dedicated CouchLink QA emulator. Never selects an arbitrary device."""
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
WORK = ROOT / 'work'
ADB = '/private/tmp/cleanyourtv-sdk/platform-tools/adb'
SERIAL = 'emulator-5580'


def adb(*args):
    result = subprocess.run([ADB, '-s', SERIAL, *args], capture_output=True, timeout=35)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace'))
    return result.stdout


def tree():
    adb('shell', 'uiautomator dump --compressed /data/local/tmp/couchlink-qa.xml')
    return ET.fromstring(adb('shell', 'cat /data/local/tmp/couchlink-qa.xml'))


def tap(node):
    bounds = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
    if len(bounds) != 4:
        raise RuntimeError('UI element has no usable bounds')
    adb('shell', f'input touchscreen tap {(bounds[0]+bounds[2])//2} {(bounds[1]+bounds[3])//2}')


def named(label):
    for attempt in range(3):
        nodes = [n for n in tree().iter() if n.get('text', '').strip().casefold() == label.casefold() or n.get('content-desc', '').strip().casefold() == label.casefold()]
        nodes = [n for n in nodes if visible(n)]
        if nodes:
            return nodes[0]
        time.sleep(.3)
    raise RuntimeError('UI element not found: ' + label)


def visible(node):
    b = list(map(int, re.findall(r'-?\d+', node.get('bounds', ''))))
    return len(b) == 4 and b[2] > b[0] and b[3] > max(0, b[1])


def snapshot(name):
    WORK.mkdir(mode=0o700, exist_ok=True)
    adb('shell', 'screencap -p /data/local/tmp/couchlink-qa.png')
    adb('pull', '/data/local/tmp/couchlink-qa.png', str(WORK / (name + '.png')))
    print('Screenshot:', name)


def pair(path):
    texts = '\n'.join(n.get('text', '') for n in ET.parse(path).iter())
    code = re.search(r'^([0-9]{6})$', texts, re.M)
    fingerprint = re.search(r'Fingerprint: ([0-9A-Fa-f]{12})', texts)
    address = re.search(r'(192\.168\.[0-9]+\.[0-9]+:[0-9]+)', texts)
    if not all((code, fingerprint, address)):
        raise RuntimeError('Fresh TV pairing screen is required')
    try:
        tap(named('Enter TV address manually'))
    except RuntimeError:
        tap(named('TVs'))
        tap(named('Enter TV address manually'))
    for index, value in enumerate((address[1], code[1], fingerprint[1])):
        fields = [n for n in tree().iter() if n.get('class') == 'android.widget.EditText' and visible(n)]
        tap(fields[index])
        adb('shell', 'input text ' + shlex.quote(value))
    nodes = [n for n in tree().iter() if n.get('resource-id') == 'android:id/button1']
    tap(nodes[0])
    time.sleep(3)
    snapshot('qa-paired-remote')
    texts = [n.get('text', '') for n in tree().iter() if n.get('class') == 'android.widget.TextView']
    print('Pair result labels:', json.dumps([t for t in texts if 'CouchLink' in t or 'TV' in t or 'could' in t or 'fingerprint' in t], ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['dump', 'shot', 'tap', 'pair', 'key', 'type', 'edit', 'swipe', 'theme', 'export-test-credential'])
    parser.add_argument('value', nargs='?', default='')
    args = parser.parse_args()
    if args.action == 'pair':
        pair(Path(args.value))
    elif args.action == 'export-test-credential':
        root = ET.fromstring(adb('shell', 'run-as dev.couchlink.phone cat shared_prefs/phone.xml'))
        values = {n.get('name'): n.text if n.tag == 'string' else n.get('value') for n in root}
        config = dict(host=values['host'], port=int(values['port']), fingerprint=values['pin'], token=values['token'])
        fd = os.open(WORK / 'test-pairing.json', os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, 'w') as stream:
            json.dump(config, stream)
        print('Dedicated emulator credential exported privately for integration QA.')
    elif args.action == 'shot':
        snapshot(args.value or 'qa-current')
    elif args.action == 'tap':
        tap(named(args.value))
    elif args.action == 'key':
        if not re.fullmatch(r'[A-Z0-9_]+', args.value):
            raise RuntimeError('Invalid key')
        adb('shell', 'input keyevent ' + args.value)
    elif args.action == 'type':
        adb('shell', 'input text ' + shlex.quote(args.value))
    elif args.action == 'edit':
        fields = [n for n in tree().iter() if n.get('class') == 'android.widget.EditText']
        if len(fields) != 1:
            raise RuntimeError('Expected exactly one editable field')
        tap(fields[0])
        adb('shell', 'input text ' + shlex.quote(args.value))
    elif args.action == 'swipe':
        gestures = {'up': '540 1800 540 700', 'down': '540 700 540 1800', 'left': '970 750 150 750', 'right': '150 750 970 750'}
        if args.value not in gestures:
            raise RuntimeError('Expected up, down, left or right')
        adb('shell', 'input touchscreen swipe ' + gestures[args.value] + ' 500')
    elif args.action == 'theme':
        root = tree()
        nodes = list(root.iter())
        titles = [n for n in nodes if n.get('text') == args.value]
        if len(titles) != 1:
            raise RuntimeError('Expected one visible theme card')
        buttons = [n for n in nodes[nodes.index(titles[0])+1:] if n.get('text') == 'Apply on TV']
        if not buttons:
            raise RuntimeError('Expected an apply button after the theme title')
        tap(buttons[0])
    else:
        for node in tree().iter():
            if node.get('class') == 'android.widget.EditText':
                print('Editable input', node.get('bounds'))
            elif node.get('text') or node.get('content-desc'):
                print(node.get('class'), node.get('text'), node.get('content-desc'), node.get('bounds'))


if __name__ == '__main__':
    main()
