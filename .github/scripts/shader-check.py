#!/usr/bin/env python3
"""Compiles every program of the shader pack with glslangValidator, after resolving #include as OptiFine does.

OptiFine pastes an included file in place of its #include line, between #line directives; a path starting with / is
from the pack's shaders folder, any other from the including file's folder. Usage: shader-check.py [shaders folder]
"""

import os
import re
import subprocess
import sys
import tempfile

INCLUDE = re.compile(r'^\s*#include\s+"([A-Za-z0-9_/.]+)".*$')


def resolve(path, root, files, depth=0):
    if depth >= 10:
        raise RuntimeError("#include depth exceeded in " + path)
    index = files.index(path) if path in files else len(files)
    if path not in files:
        files.append(path)
    out = []
    with open(path, encoding="ascii") as f:
        lines = f.read().split("\n")
    for number, line in enumerate(lines, 1):
        match = INCLUDE.match(line)
        if match:
            name = match.group(1)
            target = os.path.join(root, name[1:]) if name.startswith("/") else os.path.join(os.path.dirname(path), name)
            target = os.path.normpath(target)
            if not os.path.isfile(target):
                raise RuntimeError("%s:%d: included file not found: %s" % (path, number, name))
            if target not in files:
                files.append(target)
            body = resolve(target, root, files, depth + 1)
            line = "#line 1 %d\n%s\n#line %d %d" % (files.index(target), body.rstrip("\n"), number + 1, index)
        out.append(line)
    return "\n".join(out)


def main():
    root = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else "shaderpack/shaders")
    failed = 0
    programs = sorted(n for n in os.listdir(root) if n.endswith((".vsh", ".fsh")))
    for name in programs:
        files = []
        source = resolve(os.path.join(root, name), root, files)
        stage = "vert" if name.endswith(".vsh") else "frag"
        with tempfile.NamedTemporaryFile("w", suffix="." + stage, delete=False) as tmp:
            tmp.write(source)
        result = subprocess.run(["glslangValidator", tmp.name], capture_output=True, text=True)
        os.unlink(tmp.name)
        if result.returncode != 0:
            failed += 1
            print("FAILED " + name)
            print("  files: " + ", ".join("%d=%s" % (i, os.path.relpath(f, root)) for i, f in enumerate(files)))
            print(result.stdout + result.stderr)
        else:
            print("ok     " + name)
    print("%d of %d programs compiled" % (len(programs) - failed, len(programs)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
