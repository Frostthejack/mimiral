import re, os

base = r"C:\Users\luned\Documents\Projects\mimiral\app\src\main\java"

wildcard_imports = {}

for root, dirs, files in os.walk(base):
    for f in files:
        if not f.endswith(".kt"):
            continue
        fpath = os.path.join(root, f)
        with open(fpath, "r") as fh:
            content = fh.read()

        for match in re.finditer(r"^import\s+([\w.]+)\.\*$", content, re.MULTILINE):
            pkg = match.group(1)
            if pkg not in wildcard_imports:
                wildcard_imports[pkg] = []
            wildcard_imports[pkg].append(fpath)

for pkg, files in sorted(wildcard_imports.items()):
    print(f"{pkg}.* -> {len(files)} files:")
    for f in files:
        rel = f.replace(base + "\\", "")
        print(f"  {rel}")
    print()
