#!/usr/bin/env python3
"""Adds strings to every locale: scripts/add-strings.py batch.json, where batch = {key: {en, tr, es, fr, de, ja, zh}}.
Keys that already exist in a file are left alone."""
import json
import os
import sys

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res')
DIRS = {'en': 'values', 'tr': 'values-tr', 'es': 'values-es', 'fr': 'values-fr', 'de': 'values-de', 'ja': 'values-ja', 'zh': 'values-zh-rCN'}


def escape(text):
    text = text.replace('&', '&amp;').replace('<', '&lt;').replace("'", "\\'").replace('"', '\\"')
    return '\\' + text if text[:1] in ('@', '?') else text


batch = json.load(open(sys.argv[1], encoding='utf-8'))
missing = [(key, lang) for key, values in batch.items() for lang in DIRS if lang not in values]
if missing:
    sys.exit(f'missing translations: {missing}')
for lang, folder in DIRS.items():
    path = os.path.join(RES, folder, 'strings.xml')
    text = open(path, encoding='utf-8').read()
    added = ''.join(
        f'    <string name="{key}">{escape(values[lang])}</string>\n'
        for key, values in batch.items()
        if f'name="{key}"' not in text
    )
    if added:
        open(path, 'w', encoding='utf-8').write(text.replace('</resources>', added + '</resources>'))
print('added', len(batch), 'keys')
