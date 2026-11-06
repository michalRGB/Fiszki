import json
import re

INPUT_FILE = "vocab.md"
OUTPUT_FILE = "vocab.json"

data = {
    "name": "",
    "displayName": "",
    "flag": "",
    "themes": []
}

current_theme = None
current_subtheme = None

# Precompiled regex (faster)
theme_re = re.compile(r"^##\s*\*\*(.*?)\*\*")
subtheme_re = re.compile(r"^###\s*\*\*(.*?)\*\*")
pair_re = re.compile(r"^(.+?) \\\- (.+)$")  # STRICT: space \- space

def clean(text):
    return text.replace("\\", "").strip()

with open(INPUT_FILE, "r", encoding="utf-8") as f:
    for raw_line in f:
        line = raw_line.strip()

        if not line:
            continue

        # Theme
        m = theme_re.match(line)
        if m:
            theme_name = m.group(1)
            current_theme = {"name": theme_name, "words": []}
            data["themes"].append(current_theme)
            current_subtheme = theme_name
            continue

        # Subtheme
        m = subtheme_re.match(line)
        if m:
            current_subtheme = m.group(1)
            continue

        # Word pair (must be "word \- translation")
        m = pair_re.match(line)
        if m and current_theme:
            original = clean(m.group(1))
            translation = clean(m.group(2))

            current_theme["words"].append({
                "original": original,
                "translation": translation,
                "subtheme": current_subtheme
            })

# Remove themes with no words
data["themes"] = [t for t in data["themes"] if t["words"]]

with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print("JSON created:", OUTPUT_FILE)