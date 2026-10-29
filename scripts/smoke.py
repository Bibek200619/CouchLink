#!/usr/bin/env python3
"""Exercise the real TV companion. Credentials/screenshots stay in ignored work/.

python3 scripts/smoke.py --pair-ui work/tv-setup.xml
python3 scripts/smoke.py --exercise
"""
import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import socket
import ssl
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
WORK = ROOT / 'work'
PAIR = WORK / 'test-pairing.json'


def request(config, method, path, body=None, authenticated=True):
    # Verify the out-of-band pin before sending the code or any credential.
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    raw = socket.create_connection((config['host'], config['port']), timeout=8)
    connection = ctx.wrap_socket(raw, server_hostname=config['host'])
    connection.settimeout(30)
    actual = hashlib.sha256(connection.getpeercert(binary_form=True)).hexdigest()
    if not actual.startswith(config['fingerprint'].lower()):
        connection.close()
        raise RuntimeError('TV certificate did not match the fingerprint read from its screen')
    data = b'' if body is None else json.dumps(body, ensure_ascii=False).encode()
    headers = [f'{method} {path} HTTP/1.1', f"Host: {config['host']}",
               f'Content-Length: {len(data)}', 'Content-Type: application/json',
               'Connection: close']
    if authenticated:
        headers.append('Authorization: Bearer ' + config['token'])
    try:
        connection.sendall(('\r\n'.join(headers) + '\r\n\r\n').encode() + data)
        response = http.client.HTTPResponse(connection)
        response.begin()
        payload = response.read(4 * 1024 * 1024 + 1)
        if len(payload) > 4 * 1024 * 1024:
            raise RuntimeError('Response exceeded limit')
        return response.status, payload, actual
    finally:
        connection.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pair-ui', type=Path)
    parser.add_argument('--exercise', action='store_true')
    parser.add_argument('--frame-only', action='store_true')
    parser.add_argument('--revoke-self', action='store_true', help='Remove only this temporary test client')
    args = parser.parse_args()
    WORK.mkdir(mode=0o700, exist_ok=True)
    if args.pair_ui:
        texts = '\n'.join(node.get('text', '') for node in ET.parse(args.pair_ui).iter())
        code = re.search(r'^([0-9]{6})$', texts, re.M)
        pin = re.search(r'Fingerprint: ([0-9A-Fa-f]{12})', texts)
        address = re.search(r'(192\.168\.[0-9]+\.[0-9]+):([0-9]+)', texts)
        if not all((code, pin, address)):
            raise RuntimeError('Pairing screen is not visible, or its code has expired')
        config = dict(host=address[1], port=int(address[2]), fingerprint=pin[1])
        status, payload, certificate = request(config, 'POST', '/v1/pair', {'code': code[1]}, False)
        if status != 200:
            raise RuntimeError('TV refused pairing: ' + payload.decode())
        paired = json.loads(payload)
        if paired['fingerprint'] != certificate:
            raise RuntimeError('TV returned another identity')
        config.update(token=paired['token'], fingerprint=certificate)
        fd = os.open(PAIR, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, 'w') as stream:
            json.dump(config, stream)
        print('Paired test client; private credential saved locally.')
    else:
        config = json.loads(PAIR.read_text())
    if args.revoke_self:
        status, payload, _ = request(config, 'POST', '/v1/unpair', {})
        assert status == 200, payload.decode()
        status, _, _ = request(config, 'GET', '/v1/status')
        assert status == 401, 'Revoked credential must be refused'
        PAIR.unlink()
        print('Temporary test client revoked; its local credential removed.')
        return
    status, payload, _ = request(config, 'GET', '/v1/status', authenticated=False)
    assert status == 401, 'Unauthenticated status must be refused'
    status, payload, _ = request(config, 'GET', '/v1/status')
    assert status == 200, payload.decode()
    result = json.loads(payload)
    print('Authentication: passed. TV controls ready:', result['adbReady'])
    if 'diagnostics' in result:
        print('Diagnostics:', json.dumps(result['diagnostics']))
    if result.get('error'):
        print('TV message:', result['error'])
    if args.frame_only:
        status, payload, _ = request(config, 'GET', '/v1/frame')
        print('Frame status:', status, 'bytes:', len(payload))
        if status == 200:
            (WORK / 'phone-frame-comparison.jpg').write_bytes(payload)
        return
    if not args.exercise:
        return
    assert result['adbReady'], 'Accept TV control authorization before exercising controls'
    status, payload, _ = request(config, 'GET', '/v1/apps')
    assert status == 200
    apps = json.loads(payload)['apps']
    assert any(x['package'] == 'com.google.android.youtube.tv' for x in apps)
    print('App catalogue:', len(apps), 'launchable apps, including YouTube.')
    for key in ('home', 'right', 'left', 'volume_up', 'volume_down'):
        status, payload, _ = request(config, 'POST', '/v1/key', {'key': key})
        assert status == 200, payload.decode()
    status, _, _ = request(config, 'POST', '/v1/key', {'key': 'home; echo injected'})
    assert status == 400, 'Unknown controls must be refused'
    print('Navigation and volume requests passed; unknown command refused.')
    status, payload, _ = request(config, 'GET', '/v1/frame')
    if status == 204:
        print('TV reported that this screen cannot be captured.')
    else:
        assert status == 200, payload.decode(errors='replace')
        assert payload.startswith(b'\xff\xd8'), 'Expected JPEG preview'
        (WORK / 'tv-preview.jpg').write_bytes(payload)
        print('Live preview JPEG:', len(payload), 'bytes, saved privately.')


if __name__ == '__main__':
    main()
