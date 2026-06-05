import zipfile, os

apk_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk')
apk = zipfile.ZipFile(apk_path)

print("=== All dex files ===")
for name in sorted(apk.namelist()):
    if name.startswith('classes') and name.endswith('.dex'):
        data = apk.read(name)
        print(f'{name}: {len(data)} bytes')

print("\n=== Searching for SentenceBoundaryDetector ===")
for name in sorted(apk.namelist()):
    if name.startswith('classes') and name.endswith('.dex'):
        data = apk.read(name)
        # Search for the full path as it would appear in dex
        if b'SentenceBoundaryDetector' in data:
            print(f'{name}: contains string "SentenceBoundaryDetector"')

print("\nDone")
