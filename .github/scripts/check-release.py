"""Reject a debuggable or mismatched candidate before it can reach the signing job."""
import json
import os
from pathlib import Path
import subprocess
import sys

def check(apk):
    config = json.loads(Path('.github/release-config.json').read_text())
    aapt = Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0/aapt'
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    if 'application-debuggable' in badging:
        raise RuntimeError('Refusing a debuggable release artifact')
    package = badging.splitlines()[0]
    for expected in ["name='"+config['packageName']+"'", "versionCode='"+str(config['versionCode'])+"'", "versionName='"+config['versionName']+"'"]:
        if expected not in package:
            raise RuntimeError('Release identity differs from configuration')
    return dict(package=config['packageName'], versionCode=config['versionCode'], versionName=config['versionName'], debuggable=False)

if __name__ == '__main__':
    print(json.dumps(check(Path(sys.argv[1]))))
