import glob
import os
import re
import xml.etree.ElementTree as ET


def get_string_names(file_path):
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        return [elem.get('name') for elem in root if elem.tag == 'string']
    except:
        return []


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


def remove_unused_from_file(file_path, unused):
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    new_lines = []
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if line.startswith('<string name="'):
            # Extraer el name
            match = re.search(r'name="([^"]+)"', line)
            if match:
                name = match.group(1)
                if name in unused:
                    # Saltar esta línea y la siguiente si es </string>
                    i += 1
                    if i < len(lines) and lines[i].strip() == '</string>':
                        i += 1
                    continue
        new_lines.append(lines[i])
        i += 1

    with open(file_path, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)


def remove_unused_from_all(strings_files, unused):
    for strings_file in strings_files:
        if os.path.exists(strings_file):
            remove_unused_from_file(strings_file, unused)


if __name__ == "__main__":
    res_dir = '../../app/src/main/res'
    base_file = os.path.join(res_dir, 'values', 'strings.xml')
    strings_files = [base_file] + glob.glob(os.path.join(res_dir, 'values-*', 'strings.xml'))
    source_dirs = ['../../app/src/main/java', '../../app/src/main/kotlin']
    unused = find_unused_strings(base_file, source_dirs)
    remove_unused_from_all(strings_files, unused)
    print("Strings no usados eliminados de todos los archivos strings.xml, preservando comentarios")
