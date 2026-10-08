"""Sign a verified candidate with the fixed private key; never print secret material."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

config = json.loads(Path('.github/release-config.json').read_text())
key_dir = Path(os.environ['RUNNER_TEMP']) / 'cinemawatch-signing'
key_dir.mkdir(mode=0o700, exist_ok=True)
key = key_dir / 'release.p12'
password = key_dir / 'password.txt'
key.write_bytes(base64.b64decode(os.environ['ANDROID_KEYSTORE_BASE64'], validate=True))
password.write_text(os.environ['ANDROID_KEYSTORE_PASSWORD'])
key.chmod(0o600); password.chmod(0o600)
artifacts = Path('artifacts'); artifacts.mkdir(exist_ok=True)
apk = artifacts / ('CinemaWatch-' + config['versionName'] + '.apk')
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
try:
    subprocess.run([str(tools/'apksigner'), 'sign', '--ks', str(key), '--ks-key-alias', 'cinemawatch',
        '--ks-pass', 'file:' + str(password), '--key-pass', 'file:' + str(password), '--out', str(apk), 'candidate/app-debug.apk'], check=True)
    verified = subprocess.check_output([str(tools/'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
    certificate = re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', verified).group(1)
    if certificate != config['signerSha256']:
        raise RuntimeError('Signing key differs from the fixed release certificate; refusing publication')
    badging = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', str(apk)], text=True)
    if "name='" + config['packageName'] + "'" not in badging.splitlines()[0] or "versionCode='" + str(config['versionCode']) + "'" not in badging.splitlines()[0]:
        raise RuntimeError('Candidate package/version differs from release configuration')
    manifest = dict(config, schemaVersion=1, size=apk.stat().st_size, sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
        apkUrl='https://github.com/hoylturtle/CinemaWatch/releases/download/v' + config['versionName'] + '-preview/' + apk.name)
    (artifacts/'update.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n')
    (artifacts/'SHA256.txt').write_text(manifest['sha256']+'  '+apk.name+'\n')
finally:
    key.unlink(missing_ok=True); password.unlink(missing_ok=True)
