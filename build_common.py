"""Local build configuration and API17 packaging helpers. No network or device IO."""
from pathlib import Path
import hashlib
import json
import os
import secrets
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
CONFIG = ROOT / 'config.local.json'
if not CONFIG.is_file():
    raise SystemExit('Copy config.example.json to config.local.json and set dependency paths first.')
settings = json.loads(CONFIG.read_text(encoding='utf-8-sig'))


def dependency(name):
    path = Path(os.path.expandvars(settings[name])).expanduser()
    path = (ROOT / path).resolve() if not path.is_absolute() else path.resolve()
    if not path.exists():
        raise SystemExit('Missing build dependency: ' + name)
    return path


SDK = dependency('android_sdk')
TOOLS = dependency('build_tools')
JDK = dependency('jdk_bin')
API = dependency('android_jar')
SYSTEM = dependency('stock_system')
DEXLIB_CP = str(dependency('dexlib_directory') / '*')
B = ROOT / 'build'
OUT = ROOT / 'out'
SIGN = ROOT / 'signing'
EXPECTED_POLICY = '0ffc2d18f2ecad4af35bdecc7fd6ede0f4afe430733496093546383e7d0f3579'
for directory in (B, OUT, SIGN):
    directory.mkdir(parents=True, exist_ok=True)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require_stock_policy():
    if sha(SYSTEM / 'framework/android.policy.odex') != EXPECTED_POLICY:
        raise SystemExit('Unsupported android.policy.odex; refusing to patch this firmware.')


def clean_build_dir(name):
    if name not in {'classes', 'tool-classes', 'policy-classes', 'app-dex', 'policy-dex',
                    'policy-baseline', 'policy-patched', 'fixture-classes', 'fixture-dex',
                    'runtime-tests-classes', 'runtime-tests-dex', 'injector-classes', 'injector-dex'}:
        raise ValueError('Unknown build directory')
    path = B / name
    if B.is_symlink() or B.resolve() != ROOT / 'build' or path.resolve().parent != B.resolve():
        raise ValueError('Build directory resolves outside the expected workspace')
    if path.exists():
        shutil.rmtree(path)
    path.mkdir()
    return path


def run(label, *args):
    result = subprocess.run([str(arg) for arg in args], cwd=ROOT,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            encoding='utf-8', errors='replace')
    (B / (label + '.log')).write_text(result.stdout, encoding='utf-8')
    if result.returncode:
        raise RuntimeError(label + '\n' + result.stdout[-8000:])
    print(label + ': OK', flush=True)
    return result.stdout


def dex(label, classes, dest):
    dest.mkdir(parents=True, exist_ok=True)
    run(label, JDK/'java.exe', '-cp', TOOLS/'lib/d8.jar', 'com.android.tools.r8.D8',
        '--min-api', '17', '--lib', API, '--output', dest, *classes)


def signed_apk(manifest, dexfile, output, label):
    resources = B / (label + '-resources.apk')
    unsigned = B / (label + '-unsigned.apk')
    aligned = B / (label + '-aligned.apk')
    run(label+'-resources', TOOLS/'aapt.exe', 'package', '-f', '-M', manifest,
        '-I', API, '-F', resources)
    with zipfile.ZipFile(resources) as source, zipfile.ZipFile(unsigned, 'w') as target:
        for entry in source.infolist():
            target.writestr(entry, source.read(entry.filename))
        target.write(dexfile, 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
    run(label+'-align', TOOLS/'zipalign.exe', '-f', '4', unsigned, aligned)
    key = SIGN / 'local.keystore'
    password = SIGN / 'password.txt'
    if not key.exists():
        password.write_text(secrets.token_urlsafe(32), encoding='ascii')
        run('create-local-key', JDK/'keytool.exe', '-genkeypair', '-noprompt',
            '-keystore', key, '-storetype', 'JKS', '-storepass:file', password,
            '-keypass:file', password, '-alias', 'local', '-keyalg', 'RSA',
            '-keysize', '2048', '-validity', '3650',
            '-dname', 'CN=HMG Split Panel Local Development')
    if not password.exists():
        raise RuntimeError('Local signing password is missing; do not overwrite an existing key.')
    run(label+'-sign', JDK/'java.exe', '-jar', TOOLS/'lib/apksigner.jar', 'sign',
        '--ks', key, '--ks-key-alias', 'local', '--ks-pass', 'file:'+str(password),
        '--min-sdk-version', '17', '--max-sdk-version', '17',
        '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'false',
        '--v3-signing-enabled', 'false', '--v4-signing-enabled', 'false',
        '--out', output, aligned)
    run(label+'-verify', JDK/'java.exe', '-jar', TOOLS/'lib/apksigner.jar', 'verify',
        '--verbose', '--min-sdk-version', '17', '--max-sdk-version', '17', output)
    run(label+'-check-align', TOOLS/'zipalign.exe', '-c', '4', output)
