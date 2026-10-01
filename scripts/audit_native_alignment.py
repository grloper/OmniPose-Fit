#!/usr/bin/env python3
"""Read-only APK/AAB/AAR ELF audit against Android's 16 KB static guidance.

No third-party modules or SDK are needed. Exit 1 means a static release gate
failed, not that a runtime crash has been observed. Every packaged ABI is listed;
16 KB checks apply to arm64-v8a and x86_64. ZIP alignment is a separate check.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile

PAGE = 16384
SOURCE = "https://developer.android.com/guide/practices/page-sizes"
# ELF class and e_machine expected for each Android ABI.
ABIS = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183),
        "x86": (1, 3), "x86_64": (2, 62)}


def unpack(data, offset, fmt):
    size = struct.calcsize(fmt)
    if offset < 0 or offset + size > len(data):
        raise ValueError("truncated ELF header/program-header table")
    return struct.unpack_from(fmt, data, offset)


def inspect_elf(data, abi):
    if len(data) < 16 or data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    cls, endian, version = data[4:7]
    if cls not in (1, 2) or endian not in (1, 2) or version != 1:
        raise ValueError("unsupported ELF class, endianness or version")
    if abi not in ABIS:
        raise ValueError("unrecognized Android ABI")
    order = "<" if endian == 1 else ">"
    header = unpack(data, 16, order + ("HHIQQQIHHHHHH" if cls == 2 else "HHIIIIIHHHHHH"))
    etype, machine, eversion, _, phoff, _, _, ehsize, phsize, phnum, *_ = header
    if (cls, machine) != ABIS[abi] or etype != 3 or eversion != 1:
        raise ValueError("ELF is not an ABI-matching shared object")
    expected_ehsize, expected_phsize = (64, 56) if cls == 2 else (52, 32)
    if ehsize != expected_ehsize or phsize != expected_phsize or phnum in (0, 0xffff):
        raise ValueError("invalid or unsupported ELF program-header layout")
    if phoff < ehsize or phoff + phsize * phnum > len(data):
        raise ValueError("truncated ELF program-header table")
    loads, relros = [], []
    for index in range(phnum):
        values = unpack(data, phoff + index * phsize, order + ("IIQQQQQQ" if cls == 2 else "IIIIIIII"))
        if cls == 2:
            kind, flags, off, addr, _, filesz, memsz, align = values
        else:
            kind, off, addr, _, filesz, memsz, flags, align = values
        segment = {"vaddr": addr, "offset": off, "filesz": filesz,
                   "memsz": memsz, "flags": flags, "alignment": align}
        if kind in (1, 0x6474e552) and (off + filesz > len(data) or filesz > memsz):
            raise ValueError("invalid ELF segment bounds")
        if kind == 1:
            loads.append(segment)
        elif kind == 0x6474e552:
            relros.append(segment)
    if not loads:
        raise ValueError("ELF contains no LOAD segment")
    checks = cls == 2
    failures = []
    for load in loads:
        align = load["alignment"]
        if align < 1 or align & (align - 1) or (load["vaddr"] - load["offset"]) % align:
            failures.append("invalid LOAD alignment or address/offset congruence")
        elif checks and align < PAGE:
            failures.append("LOAD alignment is below 16384")
    for relro in relros:
        end = relro["vaddr"] + relro["memsz"]
        rounded_end = (end + PAGE - 1) // PAGE * PAGE
        relro["end_mod_16384"] = end % PAGE
        # Diagnostic only: the official formula can fail where rounded protection
        # covers only padding. Lack of an overlap is not a substitute for runtime QA.
        relro["trailing_page_writable_load_overlap"] = any(
            load["flags"] & 2 and max(end, load["vaddr"]) < min(
                rounded_end, load["vaddr"] + load["memsz"])
            for load in loads
        )
        if checks and end % PAGE:
            failures.append("GNU_RELRO end is not divisible by 16384 (official static formula)")
    return {"elf_class": 32 if cls == 1 else 64, "machine": machine,
            "static_16kb_checked": checks, "loads": loads, "relro": relros,
            "failures": sorted(set(failures))}


def audit_archive(path, required_abis=()):
    rows, errors, seen = [], [], set()
    with zipfile.ZipFile(path) as archive:
        infos = [info for info in archive.infolist() if info.filename.endswith(".so")]
        names = [info.filename for info in infos]
        if len(names) != len(set(names)):
            errors.append("duplicate native library ZIP entries")
        for info in sorted(infos, key=lambda item: item.filename):
            parts = info.filename.split("/")
            abi = parts[-2] if len(parts) >= 2 else "unknown"
            row = {"path": info.filename, "abi": abi}
            try:
                data = archive.read(info)
                row["sha256"] = hashlib.sha256(data).hexdigest()
                row.update(inspect_elf(data, abi))
                seen.add(abi)
            except (ValueError, struct.error, zipfile.BadZipFile, RuntimeError) as error:
                row["failures"] = [str(error)]
            rows.append(row)
    if not rows:
        errors.append("no native libraries found; this app expects bundled native dependencies")
    missing = sorted(set(required_abis) - seen)
    if missing:
        errors.append("required ABIs missing or invalid: " + ", ".join(missing))
    return {"archive": str(path), "archive_sha256": hashlib.sha256(Path(path).read_bytes()).hexdigest(),
            "source": SOURCE, "formula": "(GNU_RELRO.p_vaddr + GNU_RELRO.p_memsz) % 16384 == 0",
            "scope": "ELF static audit only; APK ZIP alignment and device runtime are separate gates",
            "abis": sorted(seen), "errors": errors, "libraries": rows,
            "passed": not errors and all(not row["failures"] for row in rows)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--require-abi", action="append", choices=ABIS, default=[])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        report = audit_archive(args.archive, args.require_abi)
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as error:
        report = {"archive": str(args.archive), "passed": False, "errors": [str(error)]}
    output = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output)
    sys.stdout.write(output)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
