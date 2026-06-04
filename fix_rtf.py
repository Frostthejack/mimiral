path = "C:/Users/luned/Documents/Projects/mimiral/app/src/main/java/com/mimiral/app/data/reader/RtfParser.kt"
with open(path, 'r') as f:
    content = f.read()

# Fix 1: RtfToken.Text -> RtfToken.RtfText
content = content.replace("RtfToken.Text(", "RtfToken.RtfText(")

# Fix 2: is RtfToken.Text -> is RtfToken.RtfText
content = content.replace("is RtfToken.Text ->", "is RtfToken.RtfText ->")

# Fix 3: Add RtfText branch to when expression
# The when at line 328 is missing the RtfToken.RtfText branch
# Find the pattern: after ControlSymbol branch, before the closing of when
# The when ends with "is RtfToken.ControlSymbol -> { ... i++ }"
# We need to add "is RtfToken.RtfText -> { ... i++ }" before the closing
# Actually looking at the code, the when already has RtfToken.Text at line 438
# but the error says it's not exhaustive. Let me check if line 438 is the same when
# or a different one.

# The when at line 328 in parseTokens handles:
# StartGroup, EndGroup, ControlWord, ControlSymbol
# Missing: RtfText
# But line 438 has "is RtfToken.Text ->" which is in the same when block
# So the when DOES have RtfText but the compiler doesn't recognize it because
# it's named RtfToken.Text instead of RtfToken.RtfText

# After fixing #2 above, the when should now have is RtfToken.RtfText
# which matches the sealed class. Let me verify.

# Fix 4: baseName reference at line 130
# In the InputStream parse function, baseName is defined at line 117
# but line 130 uses it outside the scope... actually looking at the code,
# line 130 is `val title = extractTitle(plainText, baseName)` 
# and baseName is defined at lines 117-121 in the same function
# So this should be in scope. The error might be because the `return@withContext`
# at line 122 exits early and baseName is defined after it.
# Actually no - baseName is defined at 117-121, then line 122 has return@withContext
# which is inside the `if (bytes.isEmpty())` block. Line 130 is outside that block.
# So baseName IS in scope at line 130. The error might be a cascade from other errors.

# Fix 5: Line 440 - sb.append(token.text) where token is RtfToken type
# This is in the skip logic after Unicode. The `token` variable is from tokens[i]
# which is RtfToken type. We can't call .text on it directly.
# The fix: this code is inside a while loop that skips tokens after Unicode
# It should only append text for RtfToken.RtfText tokens
# Let me check the exact code

# Actually looking at the error more carefully:
# Line 440: "Overload resolution ambiguity" and "Unresolved reference 'text'"
# This is `sb.append(token.text)` where `token` is RtfToken
# The fix is to check the type first

# Let me find the exact code around line 440
lines = content.split('\n')
for i, line in enumerate(lines[435:450], start=436):
    print(f"{i}: {line}")

with open(path, 'w') as f:
    f.write(content)
print("\nDone fixing RtfParser")
