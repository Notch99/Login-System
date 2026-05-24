import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

var_injection = '''
    public static java.util.Map<java.util.UUID, Long> lastLogins = new java.util.concurrent.ConcurrentHashMap<>();
'''
content = re.sub(r'public static java\.util\.Set<java\.util\.UUID> mutedPlayers.*?;', r'\g<0>\n' + var_injection, content, count=1)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed lastLogins in NeoForge")
