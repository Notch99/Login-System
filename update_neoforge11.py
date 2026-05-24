import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('!loggedInPlayers.contains(uuid)', '!loggedIn.getOrDefault(uuid, false)')

# Wait, in the login command (successful login), I replaced loggedInPlayers.add(uuid) with loginTimers.remove(uuid)
# But in NeoForge, it was loggedIn.put(playerId, true). 
# Did my previous python script break the login command?
# Let's check!
