import re, os

base = r"C:\Users\luned\Documents\Projects\mimiral\app\src\main\java"

# Known mappings for common Compose wildcard imports
# These are the most commonly used symbols from each package
LAYOUT_SYMBOLS = [
    "Box",
    "Column",
    "Row",
    "Spacer",
    "fillMaxWidth",
    "fillMaxHeight",
    "fillMaxSize",
    "width",
    "height",
    "size",
    "padding",
    "offset",
    "weight",
    "wrapContentWidth",
    "wrapContentHeight",
    "align",
    "Arrangement",
    "Alignment",
    "Modifier",  # often used via layout but from ui
    "heightIn",
    "widthIn",
    "background",
    "border",
    "clip",
    "clickable",
    "pointerInput",
    "focusable",
    "verticalScroll",
    "horizontalScroll",
    "rememberScrollState",
    "LazyColumn",
    "LazyRow",
    "LazyVerticalGrid",
    "items",
    "itemsIndexed",
    "Scaffold",
    "TopAppBar",
    "NavigationBar",
    "NavigationBarItem",
    "Divider",
    "HorizontalDivider",
    "VerticalDivider",
    "FlowRow",
    "FlowColumn",
    "IntrinsicSize",
    "SubcomposeLayout",
    "BoxWithConstraints",
    "animateContentSize",
    "clipToBounds",
    "alpha",
    "graphicsLayer",
    "drawBehind",
    "drawWithContent",
    "focusRequester",
    "focusTarget",
    "onKeyEvent",
    "LocalDensity",
    "LocalConfiguration",
    "Dialog",
    "AlertDialog",
    "Card",
    "Surface",
    "IconButton",
    "Button",
    "TextButton",
    "OutlinedButton",
    "Icon",
    "Text",
    "TextField",
    "OutlinedTextField",
    "Checkbox",
    "RadioButton",
    "Switch",
    "Slider",
    "CircularProgressIndicator",
    "LinearProgressIndicator",
    "MaterialTheme",
    "Color",
    "Typography",
    "remember",
    "mutableStateOf",
    "mutableIntStateOf",
    "mutableFloatStateOf",
    "mutableStateListOf",
    "mutableStateMapOf",
    "derivedStateOf",
    "produceState",
    "LaunchedEffect",
    "SideEffect",
    "DisposableEffect",
    "rememberCoroutineScope",
    "rememberUpdatedState",
    "collectAsState",
    "collectAsStateWithLifecycle",
    "getValue",
    "setValue",
    "by",
    "getValue",
    "ExperimentalMaterial3Api",
    "OptIn",
    "FontFamily",
    "FontWeight",
    "TextStyle",
    "stringResource",
    "pluralStringResource",
    "hiltViewModel",
    "viewModel",
    "collectAsLifecycle",
    "BackHandler",
    "LocalContext",
    "Context",
    "stringResource",
]

MATERIAL3_SYMBOLS = [
    "AlertDialog",
    "Button",
    "Card",
    "Checkbox",
    "CircularProgressIndicator",
    "Divider",
    "DropdownMenu",
    "DropdownMenuItem",
    "ExposedDropdownMenuBox",
    "FloatingActionButton",
    "IconButton",
    "Icon",
    "LinearProgressIndicator",
    "ListItem",
    "MaterialTheme",
    "ModalBottomSheet",
    "NavigationBar",
    "NavigationBarItem",
    "NavigationDrawerItem",
    "OutlinedButton",
    "OutlinedTextField",
    "RadioButton",
    "RangeSlider",
    "Scaffold",
    "Slider",
    "Snackbar",
    "Surface",
    "Switch",
    "Tab",
    "TabRow",
    "Text",
    "TextField",
    "TextButton",
    "TimePicker",
    "TopAppBar",
    "Typography",
    "ColorScheme",
    "ExperimentalMaterial3Api",
    "ModalNavigationDrawer",
    "NavigationRail",
    "NavigationRailItem",
    "Badge",
    "BadgedBox",
    "BottomAppBar",
    "CenterAlignedTopAppBar",
    "ElevatedCard",
    "ElevatedButton",
    "ElevatedSuggestionChip",
    "FilledTonalButton",
    "FilterChip",
    "InputChip",
    "SuggestionChip",
    "LargeTopAppBar",
    "MediumTopAppBar",
    "NavigationBar",
    "NavigationBarItem",
    "OutlinedCard",
    "OutlinedSuggestionChip",
    "PlainTooltipBox",
    "ProgressIndicator",
    "RichTooltipBox",
    "SearchBar",
    "SegmentedButton",
    "SmallFloatingActionButton",
    "SmallTopAppBar",
    "SuggestionChip",
    "TopAppBarDefaults",
    "TopAppBarScrollBehavior",
]

RUNTIME_SYMBOLS = [
    "Composable",
    "remember",
    "mutableStateOf",
    "mutableIntStateOf",
    "mutableFloatStateOf",
    "mutableStateListOf",
    "mutableStateMapOf",
    "derivedStateOf",
    "produceState",
    "snapshotFlow",
    "LaunchedEffect",
    "SideEffect",
    "DisposableEffect",
    "rememberCoroutineScope",
    "rememberUpdatedState",
    "collectAsState",
    "collectAsStateWithLifecycle",
    "getValue",
    "setValue",
    "by",
    "ExperimentalComposeUiApi",
    "ExperimentalFoundationApi",
]

ROOM_SYMBOLS = [
    "Dao",
    "Database",
    "Entity",
    "PrimaryKey",
    "ColumnInfo",
    "Insert",
    "Update",
    "Delete",
    "Query",
    "Transaction",
    "TypeConverter",
    "TypeConverters",
    "Embedded",
    "ForeignKey",
    "Index",
    "Ignore",
    "OnConflictStrategy",
    "RoomDatabase",
    "Room",
]


def get_used_symbols(content, package_prefix):
    """Find symbols that appear to be used from a given package."""
    # Remove import statements and comments for analysis
    lines = content.split("\n")
    code_lines = []
    for line in lines:
        stripped = line.strip()
        if (
            stripped.startswith("import ")
            or stripped.startswith("//")
            or stripped.startswith("/*")
            or stripped.startswith("*")
        ):
            continue
        code_lines.append(line)
    code = "\n".join(code_lines)

    used = set()

    if package_prefix == "androidx.compose.foundation.layout":
        for sym in LAYOUT_SYMBOLS:
            # Look for usage patterns
            patterns = [
                rf"\b{re.escape(sym)}\b",
                rf"\.{re.escape(sym)}\b",
            ]
            for pat in patterns:
                if re.search(pat, code):
                    used.add(sym)
                    break

    elif package_prefix == "androidx.compose.material3":
        for sym in MATERIAL3_SYMBOLS:
            if re.search(rf"\b{re.escape(sym)}\b", code):
                used.add(sym)

    elif package_prefix == "androidx.compose.runtime":
        for sym in RUNTIME_SYMBOLS:
            if re.search(rf"\b{re.escape(sym)}\b", code):
                used.add(sym)

    elif package_prefix == "androidx.room":
        for sym in ROOM_SYMBOLS:
            if re.search(rf"\b{re.escape(sym)}\b", code):
                used.add(sym)

    elif package_prefix == "androidx.compose.material.icons.filled":
        # For icons, look for Icons.Filled.X pattern
        for match in re.finditer(r"Icons\.Filled\.(\w+)", code):
            used.add(match.group(1))

    elif package_prefix == "com.mimiral.app.data.local.dao":
        # Look for DAO class usage
        for match in re.finditer(r"\b(\w+Dao)\b", code):
            used.add(match.group(1))

    elif package_prefix == "com.mimiral.app.data.local.entity":
        # Look for entity class usage
        for match in re.finditer(r"\b(\w+)(?=\s*[,;)\]])", code):
            sym = match.group(1)
            if sym[0].isupper() and sym not in (
                "val",
                "var",
                "fun",
                "class",
                "object",
                "interface",
                "enum",
                "sealed",
                "data",
                "override",
                "private",
                "public",
                "internal",
                "protected",
                "abstract",
                "open",
                "final",
                "const",
                "lateinit",
                "lazy",
                "by",
                "is",
                "as",
                "in",
                "out",
                "where",
                "typealias",
                "import",
                "package",
                "return",
                "if",
                "else",
                "when",
                "for",
                "while",
                "do",
                "try",
                "catch",
                "finally",
                "throw",
                "true",
                "false",
                "null",
                "this",
                "super",
                "it",
                "Unit",
                "Int",
                "Long",
                "Float",
                "Double",
                "String",
                "Boolean",
                "Char",
                "Byte",
                "Short",
                "Any",
                "Nothing",
                "List",
                "Map",
                "Set",
                "MutableList",
                "MutableMap",
                "MutableSet",
                "ArrayList",
                "HashMap",
                "HashSet",
                "Pair",
                "Triple",
                "Result",
                "Flow",
                "StateFlow",
                "SharedFlow",
                "Channel",
                "CoroutineScope",
                "Job",
                "Deferred",
                "Context",
                "Exception",
                "RuntimeException",
                "IllegalArgumentException",
                "IllegalStateException",
                "NullPointerException",
                "IndexOutOfBoundsException",
                "UnsupportedOperationException",
                "NoSuchElementException",
                "ConcurrentModificationException",
                "TimeoutCancellationException",
                "CancellationException",
                "IOException",
                "FileNotFoundException",
                "NumberFormatException",
                "ClassCastException",
                "AssertionError",
                "Error",
                "Throwable",
                "StackTraceElement",
                "Thread",
                "Runnable",
                "Callable",
                "Future",
                "CompletableFuture",
                "Optional",
                "Stream",
                "Collector",
                "Collectors",
                "Arrays",
                "Collections",
                "Objects",
                "Comparator",
                "Comparable",
                "Iterable",
                "Iterator",
                "ListIterator",
                "Sequence",
                "SequenceScope",
                "buildSequence",
                "generateSequence",
                "emptySequence",
                "sequenceOf",
                "asSequence",
                "toList",
                "toSet",
                "toMap",
                "toMutableList",
                "toMutableSet",
                "toMutableMap",
                "first",
                "last",
                "firstOrNull",
                "lastOrNull",
                "single",
                "singleOrNull",
                "get",
                "set",
                "size",
                "isEmpty",
                "isNotEmpty",
                "contains",
                "containsAll",
                "indexOf",
                "lastIndexOf",
                "subList",
                "slice",
                "take",
                "drop",
                "filter",
                "map",
                "flatMap",
                "fold",
                "reduce",
                "forEach",
                "forEachIndexed",
                "withIndex",
                "asIterable",
                "asSequence",
                "associate",
                "associateBy",
                "associateWith",
                "groupBy",
                "partition",
                "zip",
                "unzip",
                "plus",
                "minus",
                "union",
                "intersect",
                "distinct",
                "sorted",
                "sortedBy",
                "sortedWith",
                "reversed",
                "shuffled",
                "random",
                "count",
                "sum",
                "average",
                "max",
                "min",
                "maxBy",
                "minBy",
                "maxWith",
                "minWith",
                "all",
                "any",
                "none",
                "find",
                "findLast",
                "indexOfFirst",
                "indexOfLast",
                "takeIf",
                "takeUnless",
                "also",
                "apply",
                "let",
                "run",
                "with",
                "repeat",
                "assert",
                "check",
                "require",
                "error",
                "TODO",
                "lazy",
                "lazyOf",
                "lateinit",
                "by",
                "delegate",
                "getValue",
                "setValue",
                "provideDelegate",
                "NotNullVar",
                "NotNullPropertyDelegate",
                "ReadWriteProperty",
                "PropertyDelegateProvider",
                "KProperty",
                "KCallable",
                "KClass",
                "KType",
                "KParameter",
                "KVisibility",
                "KAnnotatedElement",
                "KClassifier",
                "KTypeParameter",
                "KTypeProjection",
                "Variance",
                "KModifier",
                "KDeclarationContainer",
                "KAnnotatedElement",
            ):
                used.add(sym)

    return sorted(used)


# Process each file
for root, dirs, files in os.walk(base):
    for f in files:
        if not f.endswith(".kt"):
            continue
        fpath = os.path.join(root, f)
        with open(fpath, "r") as fh:
            content = fh.read()

        # Find wildcard imports
        wildcards = list(re.finditer(r"^import\s+([\w.]+)\.\*$", content, re.MULTILINE))
        if not wildcards:
            continue

        rel_path = fpath.replace(base + "\\", "")
        print(f"\n=== {rel_path} ===")

        new_content = content
        offset = 0

        for match in wildcards:
            pkg = match.group(1)
            used = get_used_symbols(content, pkg)
            print(f"  {pkg}.* -> {used}")

            if used:
                # Replace wildcard with explicit imports
                old_import = match.group(0)
                new_imports = "\n".join(f"import {pkg}.{sym}" for sym in used)
                new_content = new_content.replace(old_import, new_imports, 1)

        # Write back
        with open(fpath, "w", newline="\n") as fh:
            fh.write(new_content)

print("\n\nDone! All wildcard imports replaced.")
