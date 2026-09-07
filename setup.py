"""
Setup script for building OptCorteTextil executable with PyInstaller.

Usage:
    python setup.py build

Or use PyInstaller directly:
    pyinstaller --onefile --windowed OptCorteTextil.py
"""

import sys
import os
from pathlib import Path

# PyInstaller spec file approach
spec_template = '''# -*- mode: python ; coding: utf-8 -*-
import sys
from pathlib import Path

a = Analysis(
    ['OptCorteTextil.py'],
    pathex=[],
    binaries=[],
    datas=[
        ('polygons.json', '.'),
        ('patrones.json', '.'),
    ],
    hiddenimports=[
        'shapely',
        'matplotlib',
        'numpy',
        'polymaker',
        'polyorder',
        'algorpatronsnap',
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludedimports=[
        'tkinter',
        'unittest',
        'email',
    ],
    noarchive=False,
)
pyz = PYZ(a.pure, a.zipped_data, cipher=None)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name='OptCorteTextil',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
'''

if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'build':
        # Create PyInstaller spec file
        spec_path = Path('OptCorteTextil.spec')
        with open(spec_path, 'w', encoding='utf-8') as f:
            f.write(spec_template)
        print(f"Created {spec_path}")
        print("\nTo build the executable, run:")
        print("  pyinstaller OptCorteTextil.spec")
        print("\nOr build with a single executable:")
        print("  pyinstaller --onefile OptCorteTextil.py")
    else:
        print("OptCorteTextil Setup")
        print("=" * 50)
        print("\nUsage:")
        print("  python setup.py build       - Create PyInstaller spec file")
        print("  pyinstaller OptCorteTextil.spec  - Build executable from spec")
        print("  pyinstaller --onefile OptCorteTextil.py  - Quick build")
        print("\nOr use directly:")
        print("  pyinstaller --onefile --windowed \\")
        print("    --add-data 'polygons.json:.' \\")
        print("    --add-data 'patrones.json:.' \\")
        print("    OptCorteTextil.py")

