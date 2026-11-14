"""Build isolated QEMU fixture APKs and the test-only Binder injector."""
from pathlib import Path
import zipfile, sys
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import build_common as b
if not (b.B/'classes/dev/stinger/split/PanelService.class').is_file():
    raise SystemExit('Run build.py first.')
for name,sources,manifest in [('fixture',[ROOT/'tests/fixture/FixtureActivity.java'],ROOT/'tests/fixture/AndroidManifest.xml'),('runtime-tests',[ROOT/'tests/SplitRuntimeTest.java'],ROOT/'tests/AndroidManifest.xml'),('injector',[ROOT/'tools/InjectMedia.java',ROOT/'tools/CommandRunner.java',ROOT/'tools/UxFixture.java'],None)]:
    if '--helpers-only' in sys.argv and name!='injector':continue
    classes=b.clean_build_dir(name+'-classes');b.clean_build_dir(name+'-dex')
    b.run('compile-'+name,b.JDK/'javac.exe','-encoding','UTF-8','-parameters','-source','8','-target','8','-bootclasspath',b.API,'-cp',b.B/'classes','-d',classes,*sources)
    b.dex('dex-'+name,sorted(classes.rglob('*.class')),b.B/(name+'-dex'))
    if manifest:b.signed_apk(manifest,b.B/(name+'-dex/classes.dex'),b.OUT/(name+'-QEMU-ONLY.apk'),name)
    else:
        with zipfile.ZipFile(b.OUT/'injector-QEMU-ONLY.jar','w') as z:z.write(b.B/(name+'-dex/classes.dex'),'classes.dex')
print('TEST BUILD COMPLETE',flush=True)
