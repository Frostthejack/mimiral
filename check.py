import os
p = '/c/Users/luned/Documents/Projects/mimiral/app/src/main/java/com/mimiral/app/ui/reader/EpubReaderScreen.kt'
print("exists:", os.path.exists(p))
print("isfile:", os.path.isfile(p))
# Check if it's a path issue
import sys
print("sys.platform:", sys.platform)
print("cwd:", os.getcwd())
# Try with raw string
p2 = r'C:\Users\luned\Documents\Projects\mimiral\app\src\main\java\com\mimiral\app\ui\reader\EpubReaderScreen.kt'
print("exists p2:", os.path.exists(p2))
