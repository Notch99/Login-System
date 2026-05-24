import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('!loggedInPlayers.contains(uuid)', '!loggedIn.getOrDefault(uuid, false)')

# Make sure successful login removes the timer
content = re.sub(
    r'(loggedIn\.put\(playerId, true\);)',
    r'\1\n                                              loginTimers.remove(playerId);',
    content
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed loggedInPlayers and timer removal")
