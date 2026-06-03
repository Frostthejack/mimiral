import re

with open('app/src/main/java/com/mimiral/app/data/reader/TxtParser.kt', 'r') as f:
    lines = f.readlines()

output = []
i = 0
while i < len(lines):
    line = lines[i]
    stripped = line.rstrip('\n').rstrip('\r')
    
    # file.exists() check
    if 'if (!file.exists()) return@withContext TxtParseResult.Error("File not found: " + file.absolutePath)' in stripped:
        indent = '            '
        output.append(indent + 'if (!file.exists()) {\n')
        output.append(indent + '    return@withContext TxtParseResult.Error("File not found: " + file.absolutePath)\n')
        output.append(indent + '}\n')
        i += 1
        continue
    
    # file.canRead() check
    if 'if (!file.canRead()) return@withContext TxtParseResult.Error("Cannot read file: " + file.absolutePath)' in stripped:
        indent = '            '
        output.append(indent + 'if (!file.canRead()) {\n')
        output.append(indent + '    return@withContext TxtParseResult.Error("Cannot read file: " + file.absolutePath)\n')
        output.append(indent + '}\n')
        i += 1
        continue
    
    # empty bytes success
    if 'return@withContext TxtParseResult.Success("", "UTF-8", listOf(0), file.nameWithoutExtension, file.absolutePath, 0)' in stripped:
        indent = '                '
        output.append(indent + 'return@withContext TxtParseResult.Success(\n')
        output.append(indent + '    "", "UTF-8", listOf(0), file.nameWithoutExtension, file.absolutePath, 0\n')
        output.append(indent + ')\n')
        i += 1
        continue
    
    # TxtParseResult.Success(text, encoding, ...)
    if 'TxtParseResult.Success(text, encoding, chapterBreaks, title, file.absolutePath, text.length)' in stripped:
        indent = '            '
        output.append(indent + 'TxtParseResult.Success(\n')
        output.append(indent + '    text, encoding, chapterBreaks, title, file.absolutePath, text.length\n')
        output.append(indent + ')\n')
        i += 1
        continue
    
    # parse(InputStream) signature
    if 'suspend fun parse(inputStream: InputStream, fileName: String): TxtParseResult = withContext(Dispatchers.IO)' in stripped:
        output.append('    suspend fun parse(\n')
        output.append('        inputStream: InputStream, fileName: String\n')
        output.append('    ): TxtParseResult = withContext(Dispatchers.IO) {\n')
        i += 1
        continue
    
    # baseName (two occurrences)
    if 'val baseName = if (fileName.contains(".")) fileName.substringBeforeLast(".") else fileName' in stripped:
        indent = stripped[:len(stripped) - len(stripped.lstrip())]
        output.append(indent + 'val baseName = if (fileName.contains(".")) {\n')
        output.append(indent + '    fileName.substringBeforeLast(".")\n')
        output.append(indent + '} else {\n')
        output.append(indent + '    fileName\n')
        output.append(indent + '}\n')
        i += 1
        continue
    
    # 2-byte continuation
    if 'if (i + 1 < bytes.size && isContinuationByte(bytes[i + 1])) { validChars++; i += 2 } else i++' in stripped:
        indent = '                    '
        output.append(indent + 'if (i + 1 < bytes.size && isContinuationByte(bytes[i + 1])) {\n')
        output.append(indent + '    validChars++; i += 2\n')
        output.append(indent + '} else i++\n')
        i += 1
        continue
    
    # 3-byte continuation
    if 'if (i + 2 < bytes.size && isContinuationByte(bytes[i + 1]) && isContinuationByte(bytes[i + 2])) { validChars++; i += 3 } else i++' in stripped:
        indent = '                    '
        output.append(indent + 'if (i + 2 < bytes.size &&\n')
        output.append(indent + '    isContinuationByte(bytes[i + 1]) && isContinuationByte(bytes[i + 2])\n')
        output.append(indent + ') {\n')
        output.append(indent + '    validChars++; i += 3\n')
        output.append(indent + '} else i++\n')
        i += 1
        continue
    
    # 4-byte continuation
    if 'if (i + 3 < bytes.size && isContinuationByte(bytes[i + 1]) && isContinuationByte(bytes[i + 2]) && isContinuationByte(bytes[i + 3])) { validChars++; i += 4 } else i++' in stripped:
        indent = '                    '
        output.append(indent + 'if (i + 3 < bytes.size &&\n')
        output.append(indent + '    isContinuationByte(bytes[i + 1]) &&\n')
        output.append(indent + '    isContinuationByte(bytes[i + 2]) &&\n')
        output.append(indent + '    isContinuationByte(bytes[i + 3])\n')
        output.append(indent + ') {\n')
        output.append(indent + '    validChars++; i += 4\n')
        output.append(indent + '} else i++\n')
        i += 1
        continue
    
    # UTF8 confidence threshold
    if 'return if (totalChars == 0) true else validChars.toFloat() / totalChars.toFloat() >= UTF8_CONFIDENCE_THRESHOLD' in stripped:
        indent = '        '
        output.append(indent + 'return if (totalChars == 0) true\n')
        output.append(indent + 'else validChars.toFloat() / totalChars.toFloat() >= UTF8_CONFIDENCE_THRESHOLD\n')
        i += 1
        continue
    
    # UTF-16LE offset
    if 'offset = if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) 2 else 0' in stripped:
        indent = '                '
        output.append(indent + 'offset = if (bytes.size >= 2 &&\n')
        output.append(indent + '    bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()\n')
        output.append(indent + ') 2 else 0\n')
        i += 1
        continue
    
    # UTF-16BE offset
    if 'offset = if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) 2 else 0' in stripped:
        indent = '                '
        output.append(indent + 'offset = if (bytes.size >= 2 &&\n')
        output.append(indent + '    bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()\n')
        output.append(indent + ') 2 else 0\n')
        i += 1
        continue
    
    # relevant bytes
    if 'val relevant = if (offset > 0 && offset < bytes.size) bytes.copyOfRange(offset, bytes.size) else bytes' in stripped:
        indent = '        '
        output.append(indent + 'val relevant = if (offset > 0 && offset < bytes.size) {\n')
        output.append(indent + '    bytes.copyOfRange(offset, bytes.size)\n')
        output.append(indent + '} else {\n')
        output.append(indent + '    bytes\n')
        output.append(indent + '}\n')
        i += 1
        continue
    
    # breaks check
    if 'if (breaks.isNotEmpty() && breaks.last() == charOffset) breaks.removeAt(breaks.size - 1)' in stripped:
        indent = '                    '
        output.append(indent + 'if (breaks.isNotEmpty() && breaks.last() == charOffset) {\n')
        output.append(indent + '    breaks.removeAt(breaks.size - 1)\n')
        output.append(indent + '}\n')
        i += 1
        continue
    
    # extractTitle return
    if 'return if (firstLine.isNotEmpty() && firstLine.length <= 100 && !firstLine.endsWith(".")) firstLine' in stripped:
        indent = '        '
        output.append(indent + 'return if (firstLine.isNotEmpty() && firstLine.length <= 100 &&\n')
        output.append(indent + '    !firstLine.endsWith(".")\n')
        output.append(indent + ') firstLine\n')
        i += 1
        continue
    
    output.append(line)
    i += 1

with open('app/src/main/java/com/mimiral/app/data/reader/TxtParser.kt', 'w') as f:
    f.writelines(output)

print('TxtParser.kt fixed')
