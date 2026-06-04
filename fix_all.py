import re, os

BASE = "C:/Users/luned/Documents/Projects/mimiral/app/src/main/java/com/mimiral/app/"

def fix_file(rel_path, transformations):
    full_path = BASE + rel_path
    with open(full_path, 'r') as f:
        content = f.read()
    for desc, old, new in transformations:
        if old in content:
            content = content.replace(old, new)
            print(f"  [{rel_path}] {desc}")
        else:
            print(f"  [{rel_path}] SKIP (not found): {desc[:60]}")
    with open(full_path, 'w') as f:
        f.write(content)

# 1. DjvuReaderScreen - remove problematic imports
fix_file("ui/reader/DjvuReaderScreen.kt", [
    ("Remove BottomReaderControls import", "import com.mimiral.app.ui.reader.BottomReaderControls\n", ""),
    ("Remove PageIndicator import", "import com.mimiral.app.ui.reader.PageIndicator\n", ""),
    ("Remove DjvuPageText import (same package)", "import com.mimiral.app.data.reader.DjvuPageText\n", ""),
])

# 2. ComicReaderScreen - make PageIndicator and BottomReaderControls public
fix_file("ui/reader/ComicReaderScreen.kt", [
    ("Make PageIndicator public", "private fun PageIndicator(", "fun PageIndicator("),
    ("Make BottomReaderControls public", "private fun BottomReaderControls(", "fun BottomReaderControls("),
])

# 3. DjvuRenderer - fix conflicting overloads
fix_file("data/reader/DjvuRenderer.kt", [
    ("Rename renderPage(dpi) to renderPageAtDpi", 
     "fun renderPage(pageIndex: Int, dpi: Int = 160): Bitmap?",
     "fun renderPageAtDpi(pageIndex: Int, dpi: Int = 160): Bitmap?"),
])

# 4. RtfParser - fix return type and other issues
fix_file("data/reader/RtfParser.kt", [
    ("Fix parseTokens return type Text -> RtfText",
     "private fun parseTokens(tokens: List<RtfToken>, source: String): Text {",
     "private fun parseTokens(tokens: List<RtfToken>, source: String): RtfText {"),
    ("Fix escape sequence",
     '"*\跳过*"',
     '"*\\跳过*"'),
])

print("\nDone with transformations.")
