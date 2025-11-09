import glob
import os
import re
import xml.etree.ElementTree as ET
from collections import Counter


def get_string_names(file_path):
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        return [elem.get('name') for elem in root if elem.tag == 'string']
    except:
        return []


def find_duplicates_in_file(file_path):
    names = get_string_names(file_path)
    counts = Counter(names)
    duplicates = [name for name, count in counts.items() if count > 1]
    return duplicates


def find_missing_translations(base_path, other_paths):
    base_names = set(get_string_names(base_path))
    missing = {}
    for path in other_paths:
        lang = os.path.basename(os.path.dirname(path))
        other_names = set(get_string_names(path))
        missing[lang] = base_names - other_names
    return missing


def find_unused_strings(base_path, source_dirs):
    all_defined = set(get_string_names(base_path))
    used = set()
    for src_dir in source_dirs:
        for root, dirs, files in os.walk(src_dir):
            for file in files:
                if file.endswith(('.java', '.kt')):
                    with open(os.path.join(root, file), 'r', encoding='utf-8',
                              errors='ignore') as f:
                        content = f.read()
                        matches = re.findall(r'R\.string\.(\w+)', content)
                        used.update(matches)
    unused = all_defined - used
    return unused


# Paths
res_dir = '../../app/src/main/res'
base_strings = os.path.join(res_dir, 'values', 'strings.xml')
other_strings = glob.glob(os.path.join(res_dir, 'values-*', 'strings.xml'))
source_dirs = ['../../app/src/main/java', '../../app/src/main/kotlin']

# Realizar análisis
duplicates = find_duplicates_in_file(base_strings)
missing = find_missing_translations(base_strings, other_strings)
unused = find_unused_strings(base_strings, source_dirs)

# 1. Duplicados en base
with open('duplicates.txt', 'w') as f:
    f.write("Strings duplicados en values/strings.xml:\n")
    if duplicates:
        for dup in duplicates:
            f.write(f"  {dup}\n")
    else:
        f.write("  Ninguno\n")

# 2. Faltan por traducir
with open('missing_translations.txt', 'w') as f:
    f.write("Strings faltantes por traducir:\n")
    for lang, strs in missing.items():
        if strs:
            f.write(f"{lang}:\n")
            for s in strs:
                f.write(f"  {s}\n")
        else:
            f.write(f"{lang}: Ninguno\n")

# 3. No se están usando
with open('unused_strings.txt', 'w') as f:
    f.write("Strings no usados:\n")
    for un in unused:
        f.write(f"  {un}\n")

print("Análisis completado. Resultados guardados en:")
print("- duplicates.txt")
print("- missing_translations.txt")
print("- unused_strings.txt")
