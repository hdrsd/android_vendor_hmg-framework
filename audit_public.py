"""Audit the Git index against the explicit source-only publication allowlist."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parent
PUBLIC_FILES = '''.gitattributes
.gitignore
AndroidManifest.xml
README.md
audit_public.py
build.py
build_common.py
config.example.json
framework/dev/stinger/split/policy/SplitPolicy.java
photo-src/dev/stinger/standby/PhotoMath.java
photo-src/dev/stinger/standby/PhotoProvider.java
photo-src/dev/stinger/standby/PhotoStore.java
photo-src/dev/stinger/standby/PreviewActivity.java
photo-src/dev/stinger/standby/SettingsActivity.java
photo-src/dev/stinger/standby/core/PhotoLayer.java
photo-src/dev/stinger/standby/core/StockBridge.java
src/dev/stinger/split/ControlActivity.java
src/dev/stinger/split/MediaState.java
src/dev/stinger/split/OemMediaSource.java
src/dev/stinger/split/PanelService.java
src/dev/stinger/split/PanelView.java
tests/AndroidManifest.xml
tests/SplitRuntimeTest.java
tests/fixture/AndroidManifest.xml
tests/fixture/FixtureActivity.java
tools/CommandRunner.java
tools/InjectMedia.java
tools/PatchPolicy.java
tools/UxFixture.java
tools/build_tests.py
verification/public-source-build-2026-11-14.json
verification/qemu-2026-11-14.json'''.splitlines()


def audit():
    tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode('utf-8').split('\0')
    actual = {name for name in tracked if name}
    if actual != set(PUBLIC_FILES):
        raise ValueError('Index differs from public allowlist: ' + repr(sorted(actual ^ set(PUBLIC_FILES))))
    total = 0
    for name in sorted(actual):
        data = subprocess.check_output(['git', 'show', ':' + name], cwd=ROOT)
        content = data.decode('utf-8')
        if b'\0' in data or len(data) > 65536:
            raise ValueError('Binary or unexpectedly large file: ' + name)
        patterns = (r'(?i)[A-Z]:[/\\]Users[/\\]',
                    r'(?i)[A-Z]:[/\\]hack[/\\]',
                    r'gh[pousr]_[A-Za-z0-9]{20,}',
                    r'github_pat_[A-Za-z0-9_]{20,}',
                    r'-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----')
        if any(re.search(pattern, content) for pattern in patterns):
            raise ValueError('Possible machine path or secret in: ' + name)
        total += len(data)
    print(f'Public index OK: {len(actual)} UTF-8 source/evidence files, {total} bytes.')
    print('No firmware binaries, signing files, local settings or build outputs are staged.')


if __name__ == '__main__':
    audit()
