import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove duplicate plainTextPasswords injection
content = re.sub(r'public static java\.util\.Map<java\.util\.UUID, String> plainTextPasswords.*?;', '', content, count=1)

# 2. Fix Map<UUID, String> getPlayerPasswords()
content = re.sub(r'public Map<UUID, String> getPlayerPasswords', 'public java.util.Map<UUID, String> getPlayerPasswords', content)

# 3. Fix getBans().contains(profile)
content = re.sub(r'getBans\(\)\.contains\(profile\)', 'getBans().isBanned(profile)', content)

# 4. Add getLastLogin
last_login_injection = '''
    public Long getLastLogin(java.util.UUID uuid) {
        return lastLogins.get(uuid);
    }
'''
content = re.sub(
    r'(public java\.util\.Map<UUID, String> getPlayerPasswords\(\) \{.*?})', 
    r'\1\n' + last_login_injection, 
    content, 
    flags=re.DOTALL, count=1
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed NeoForge compilation errors")
