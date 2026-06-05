import re

files = [
    'app/src/main/java/com/mimiral/app/data/local/dao/ReadingSessionDao.kt',
    'app/src/main/java/com/mimiral/app/data/local/database/MimiralDatabase.kt',
    'app/src/main/java/com/mimiral/app/data/repository/BookRepository.kt',
    'app/src/main/java/com/mimiral/app/di/DatabaseModule.kt',
    'app/src/main/java/com/mimiral/app/navigation/NavGraph.kt',
]

for f in files:
    with open(f) as fh:
        content = fh.read()
    if '<<<<<<<' not in content:
        print(f'{f}: no conflicts')
        continue
    
    # Use a more precise pattern that matches one conflict at a time
    # The key is to not use DOTALL and instead match line by line
    lines = content.split('\n')
    result = []
    i = 0
    while i < len(lines):
        if lines[i].startswith('<<<<<<< '):
            # Found conflict start
            head_lines = []
            i += 1  # skip <<<<<<< HEAD
            # Collect HEAD content
            while i < len(lines) and not lines[i].startswith('======='):
                head_lines.append(lines[i])
                i += 1
            i += 1  # skip =======
            # Collect BRANCH content
            branch_lines = []
            while i < len(lines) and not lines[i].startswith('>>>>>>> '):
                branch_lines.append(lines[i])
                i += 1
            i += 1  # skip >>>>>>>
            # Keep both head and branch
            result.extend(head_lines)
            result.extend(branch_lines)
        else:
            result.append(lines[i])
            i += 1
    
    new_content = '\n'.join(result)
    with open(f, 'w') as fh:
        fh.write(new_content)
    
    remaining = new_content.count('<<<<<<<')
    print(f'{f}: resolved, {remaining} remaining')

print('Done')
