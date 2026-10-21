"""Use real released code, re-signed with a disposable CI key, for upgrade testing."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.request

config = json.loads(Path('.github/upgrade-baseline.json').read_text())
release = json.loads(Path('.github/release-config.json').read_text())
build = Path('build'); build.mkdir(exist_ok=True)
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
source = build / 'published-baseline.apk'
with urllib.request.urlopen(config['url'], timeout=30) as stream, source.open('wb') as output:
    total = 0
    while chunk := stream.read(65536):
        total += len(chunk)
        if total > 150 * 1024 * 1024:
            raise RuntimeError('Baseline exceeds download limit')
        output.write(chunk)
if hashlib.sha256(source.read_bytes()).hexdigest() != config['sha256']:
    raise RuntimeError('Historical release checksum mismatch')
certificate = subprocess.check_output([str(tools/'apksigner'), 'verify', '--print-certs', str(source)], text=True)
actual = re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', certificate).group(1)
if actual != config['signerSha256'] or actual != release['signerSha256']:
    raise RuntimeError('Historical release certificate does not match persistent signing identity')
badging = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', str(source)], text=True).splitlines()[0]
if ("name='" + release['packageName'] + "'") not in badging or ("versionCode='" + str(config['versionCode']) + "'") not in badging:
    raise RuntimeError('Historical package/version mismatch')
if release['versionCode'] <= config['versionCode']:
    raise RuntimeError('Replacement version must increase')
for original, destination in [(source, build/'upgrade-fixture.apk'),
        (Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'), build/'upgrade-seed-androidTest.apk')]:
    subprocess.run([str(tools/'apksigner'), 'sign', '--ks', os.environ['CW_TEST_KEYSTORE_FILE'],
        '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--out', str(destination), str(original)], check=True)
(build/'upgrade-baseline-evidence.json').write_text(json.dumps(dict(config,
    note='Original release hash and certificate verified; only CI signatures are replaced. This is a code/data upgrade test, not a production-key installation test.'), indent=2)+'\n')
