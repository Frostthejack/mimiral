import os

BASE = "C:/Users/luned/Documents/Projects/mimiral/app/src/main/java/com/mimiral/app/"

def fix(rel_path, replacements):
    path = BASE + rel_path
    with open(path, 'r') as f:
        content = f.read()
    changed = False
    for desc, old, new in replacements:
        if old in content:
            content = content.replace(old, new)
            print(f"  [{rel_path}] {desc}")
            changed = True
        else:
            print(f"  [{rel_path}] SKIP: {desc[:60]}")
    if changed:
        with open(path, 'w') as f:
            f.write(content)

# ============ RtfParser.kt remaining issues ============
print("=== RtfParser.kt ===")
fix("data/reader/RtfParser.kt", [
    # Line 130: baseName is actually in scope, should be fine now
    # Line 416, 438: RtfToken.RtfText - already fixed above
    # The append overload at line 440 - token.text should work now
    # since when is exhaustive
])

# ============ BottomNavBar.kt ============
print("\n=== BottomNavBar.kt ===")
# Screen.Discover doesn't exist. Let me check what screens exist
nav_path = BASE + "navigation/BottomNavBar.kt"
with open(nav_path, 'r') as f:
    nav = f.read()

# Check if Screen is defined somewhere
import subprocess
result = subprocess.run(
    ['grep', '-rn', 'sealed class Screen\|object Screen\|class Screen', 
     'C:/Users/luned/Documents/Projects/mimiral/app/src/main/java/'],
    capture_output=True, text=True
)
print("  Screen class search:", result.stdout[:500] if result.stdout else "not found")
print("  Screen class search err:", result.stderr[:200] if result.stderr else "")

