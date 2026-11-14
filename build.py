"""Build the API17 panel and hash-gated policy patch locally; no device access."""
import os, json, zipfile, difflib
import build_common as b
ROOT = b.ROOT
b.require_stock_policy()
for name in ('classes', 'tool-classes', 'policy-classes', 'app-dex', 'policy-dex',
             'policy-baseline', 'policy-patched'):
    b.clean_build_dir(name)
sources = sorted((ROOT/'src').rglob('*.java')) + sorted((ROOT/'photo-src').rglob('*.java'))
b.run('compile-app',b.JDK/'javac.exe','-encoding','UTF-8','-parameters','-source','8','-target','8','-bootclasspath',b.API,'-d',b.B/'classes',*sources)
b.dex('dex-app',sorted((b.B/'classes').rglob('*.class')),b.B/'app-dex')
b.signed_apk(ROOT/'AndroidManifest.xml',b.B/'app-dex/classes.dex',b.OUT/'Stinger-Split-API17.apk','split')
b.run('compile-policy',b.JDK/'javac.exe','-encoding','UTF-8','-parameters','-source','8','-target','8','-bootclasspath',b.API,'-d',b.B/'policy-classes',*sorted((ROOT/'framework').rglob('*.java')))
b.dex('dex-policy',sorted((b.B/'policy-classes').rglob('*.class')),b.B/'policy-dex')
cp=b.DEXLIB_CP
b.run('compile-patcher',b.JDK/'javac.exe','-encoding','UTF-8','-cp',cp,'-d',b.B/'tool-classes',ROOT/'tools/PatchPolicy.java')
odex=b.SYSTEM/'framework/android.policy.odex'
assert b.sha(odex)=='0ffc2d18f2ecad4af35bdecc7fd6ede0f4afe430733496093546383e7d0f3579'
b.run('patch-policy',b.JDK/'java.exe','-cp',str(b.B/'tool-classes')+os.pathsep+cp,'PatchPolicy',odex,b.SYSTEM/'framework',b.B/'policy-baseline.dex',b.B/'policy-dex/classes.dex',b.B/'policy-patched.dex')
for kind in ('baseline','patched'):
    b.run('readback-'+kind,b.JDK/'java.exe','-cp',cp,'org.jf.baksmali.Main','disassemble','-a','17','--sequential-labels','-o',b.B/('policy-'+kind),b.B/('policy-'+kind+'.dex'))
base=b.B/'policy-baseline';patched=b.B/'policy-patched'
changed=[p.relative_to(base).as_posix() for p in base.rglob('*.smali') if p.read_bytes()!=(patched/p.relative_to(base)).read_bytes()]
assert changed==['com/android/internal/policy/impl/PhoneWindowManager.smali'],changed
old=(base/changed[0]).read_text();new=(patched/changed[0]).read_text()
hook='    invoke-static {v0, v7, v8, v9, v10}, Ldev/stinger/split/policy/SplitPolicy;->constrain(Ljava/lang/Object;Landroid/graphics/Rect;Landroid/graphics/Rect;Landroid/graphics/Rect;Landroid/graphics/Rect;)V\n\n    nop\n\n'
assert new.count(hook)==1 and new.replace(hook,'')==old
(b.OUT/'PhoneWindowManager.patch').write_text(''.join(difflib.unified_diff(old.splitlines(True),new.splitlines(True),fromfile='stock/PhoneWindowManager',tofile='split/PhoneWindowManager')),encoding='utf-8')
jar=b.OUT/'android.policy-SPLIT-QEMU-ONLY.jar'
with zipfile.ZipFile(b.SYSTEM/'framework/android.policy.jar') as src,zipfile.ZipFile(jar,'w') as dst:
    for entry in src.infolist():dst.writestr(entry,src.read(entry.filename))
    dst.write(b.B/'policy-patched.dex','classes.dex',compress_type=zipfile.ZIP_DEFLATED)
report={'api':17,'stock_policy_sha256':b.sha(odex),'modified_stock_classes':changed,'hook_count':1,'other_original_policy_code_unchanged':True,'apk_sha256':b.sha(b.OUT/'Stinger-Split-API17.apk'),'policy_sha256':b.sha(jar),'vehicle_tested':False,'bluetooth_radio_tested':False}
(b.OUT/'build-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('BUILD COMPLETE',flush=True)
