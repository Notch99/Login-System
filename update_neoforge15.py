import re

file_path = r'c:\Users\lenovo\OneDrive\Documents\mods\login neoforge\src\main\java\com\example\loginsystem\LoginSystem.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add name property
content = content.replace('itemObj.addProperty("count", stack.getCount());', 'itemObj.addProperty("count", stack.getCount());\n                          itemObj.addProperty("name", stack.getHoverName().getString());')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Added name property to inventory JSON")
