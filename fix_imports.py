import re

BASE = "C:/Users/luned/Documents/Projects/mimiral/app/src/main/java/com/mimiral/app/"

FILE_IMPORTS = {
    "ui/reader/EpubReaderScreen.kt": [
        "import androidx.compose.foundation.layout.fillMaxSize",
        "import androidx.compose.foundation.layout.fillMaxWidth",
        "import androidx.compose.foundation.layout.height",
        "import androidx.compose.foundation.layout.padding",
        "import androidx.compose.foundation.layout.size",
        "import androidx.compose.foundation.layout.width",
        "import androidx.compose.material3.HorizontalDivider",
        "import androidx.compose.material3.LinearProgressIndicator",
        "import androidx.compose.material3.NavigationBar",
        "import androidx.compose.material3.NavigationBarItem",
        "import androidx.compose.material3.Switch",
        "import androidx.compose.runtime.DisposableEffect",
        "import androidx.compose.runtime.collectAsState",
        "import androidx.compose.runtime.getValue",
        "import androidx.compose.runtime.mutableStateOf",
        "import androidx.compose.runtime.remember",
        "import androidx.compose.runtime.rememberCoroutineScope",
        "import androidx.compose.runtime.setValue",
        "import com.mimiral.app.data.local.settings.TextSettings",
        "import com.mimiral.app.data.local.settings.toRenderConfig",
        "import com.mimiral.app.data.reader.PaginationEngine",
        "import com.mimiral.app.data.reader.PaginationResult",
    ],
    "ui/reader/DjvuReaderScreen.kt": [
        "import androidx.compose.foundation.layout.BoxWithConstraints",
        "import androidx.compose.foundation.layout.fillMaxSize",
        "import androidx.compose.foundation.layout.fillMaxWidth",
        "import androidx.compose.foundation.layout.padding",
        "import androidx.compose.material.icons.filled.Bookmarks",
        "import androidx.compose.material.icons.filled.Error",
        "import androidx.compose.material3.TextField",
        "import androidx.compose.material3.TopAppBarDefaults",
        "import androidx.compose.runtime.DisposableEffect",
        "import androidx.compose.runtime.collectAsState",
        "import androidx.compose.runtime.getValue",
        "import androidx.compose.runtime.mutableStateOf",
        "import androidx.compose.runtime.remember",
        "import androidx.compose.runtime.rememberCoroutineScope",
        "import androidx.compose.runtime.setValue",
        "import androidx.compose.ui.layout.ContentScale",
        "import androidx.compose.ui.unit.sp",
        "import com.mimiral.app.data.reader.DjvuPageText",
        "import com.mimiral.app.ui.reader.BottomReaderControls",
        "import com.mimiral.app.ui.reader.PageIndicator",
        "import java.io.File",
        "import kotlin.math.roundToInt",
    ],
}

def add_imports(filepath, new_imports):
    with open(filepath, "r") as f:
        content = f.read()
    existing = set(re.findall(r"import\s+[^\n]+", content))
    to_add = [imp for imp in new_imports if imp not in existing]
    if not to_add:
        print("  No new imports needed for", filepath.split("/")[-1])
        return
    lines = content.split("\n")
    last_import_idx = -1
    for i, line in enumerate(lines):
        if line.startswith("import "):
            last_import_idx = i
    if last_import_idx == -1:
        print("  WARNING: No import lines found in", filepath)
        return
    for imp in reversed(to_add):
        lines.insert(last_import_idx + 1, imp)
    with open(filepath, "w") as f:
        f.write("\n".join(lines))
    print("  Added", len(to_add), "imports to", filepath.split("/")[-1])

for rel_path, imports in FILE_IMPORTS.items():
    full_path = BASE + rel_path
    try:
        add_imports(full_path, imports)
    except FileNotFoundError:
        print("  FILE NOT FOUND:", full_path)
    except Exception as e:
        print("  ERROR:", e, "for", full_path)

print("\nDone adding imports.")
