"""Synthetic fixtures test the auditor, not the app's runtime behavior."""
import io
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from audit_native_alignment import audit_archive, inspect_elf


def elf(abi="arm64-v8a", align=16384, relro_end=16384, trailing=None,
        relro=True, order="<"):
    cls, machine = {"arm64-v8a": (2, 183), "x86_64": (2, 62),
                    "armeabi-v7a": (1, 40), "x86": (1, 3)}[abi]
    ehsize, phsize = (64, 56) if cls == 2 else (52, 32)
    entries = [(1, 6, 0, 0, 0, 1024, 4096, align)]
    if trailing is not None:
        entries.append((1, 6, 0, trailing, 0, 0, 4, align))
    if relro:
        entries.append((0x6474e552, 4, 0, 0, 0, 1024, relro_end, 1))
    data = bytearray(1024)
    data[:16] = b"\x7fELF" + bytes([cls, 1 if order == "<" else 2, 1]) + bytes(9)
    header = (3, machine, 1, 0, ehsize, 0, 0, ehsize, phsize, len(entries), 0, 0, 0)
    struct.pack_into(order + ("HHIQQQIHHHHHH" if cls == 2 else "HHIIIIIHHHHHH"), data, 16, *header)
    for i, entry in enumerate(entries):
        if cls == 1:
            kind, flags, off, addr, phys, filesz, memsz, alignment = entry
            entry = (kind, off, addr, phys, filesz, memsz, flags, alignment)
        struct.pack_into(order + ("IIQQQQQQ" if cls == 2 else "IIIIIIII"), data, ehsize+i*phsize, *entry)
    return data


class NativeAuditTest(unittest.TestCase):
    def archive(self, entries, required=()):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/"test.aab"
            with zipfile.ZipFile(path, "w") as archive:
                for name, data in entries:
                    archive.writestr(name, data)
            return audit_archive(path, required)

    def test_aligned_both_64_bit_abis(self):
        for abi in ("arm64-v8a", "x86_64"):
            for order in ("<", ">"):
                self.assertEqual(inspect_elf(elf(abi, order=order), abi)["failures"], [])

    def test_load_alignment_rejected(self):
        self.assertIn("LOAD alignment is below 16384", inspect_elf(elf(align=4096), "arm64-v8a")["failures"])

    def test_relro_misalignment_rejected_even_without_overlap(self):
        result = inspect_elf(elf(relro_end=8192), "arm64-v8a")
        self.assertTrue(result["failures"])
        self.assertFalse(result["relro"][0]["trailing_page_writable_load_overlap"])

    def test_relro_overlap_is_diagnostic(self):
        result = inspect_elf(elf(relro_end=2048), "arm64-v8a")
        self.assertTrue(result["relro"][0]["trailing_page_writable_load_overlap"])

    def test_no_relro_permitted_by_guide(self):
        self.assertEqual(inspect_elf(elf(relro=False), "arm64-v8a")["failures"], [])

    def test_32_bit_recorded_without_false_16kb_requirement(self):
        for abi in ("armeabi-v7a", "x86"):
            result = inspect_elf(elf(abi, align=4096, relro_end=4096), abi)
            self.assertFalse(result["static_16kb_checked"])
            self.assertEqual(result["failures"], [])

    def test_wrong_abi_or_malformed_elf_fails(self):
        for data in (b"not elf", elf()[:32], elf()[:100], elf("x86_64")):
            report = self.archive([("base/lib/arm64-v8a/libtest.so", data)])
            self.assertFalse(report["passed"])

    def test_invalid_load_alignment_fails(self):
        for align in (0, 12345):
            self.assertTrue(inspect_elf(elf(align=align), "arm64-v8a")["failures"])

    def test_empty_or_missing_required_abi_fails(self):
        self.assertFalse(self.archive([])["passed"])
        self.assertFalse(self.archive([("lib/arm64-v8a/test.so", elf())], ["x86_64"])["passed"])

    def test_unknown_abi_and_duplicate_entry_fail(self):
        self.assertFalse(self.archive([("lib/riscv64/test.so", elf())])["passed"])
        entry = ("lib/arm64-v8a/test.so", elf())
        self.assertFalse(self.archive([entry, entry])["passed"])

    def test_all_packaged_abis_and_hashes_recorded(self):
        entries = [(f"jni/{abi}/test.so", elf(abi)) for abi in ("arm64-v8a", "x86_64", "armeabi-v7a", "x86")]
        report = self.archive(entries)
        self.assertTrue(report["passed"])
        self.assertEqual(len(report["abis"]), 4)
        self.assertTrue(all(len(row["sha256"]) == 64 for row in report["libraries"]))

    def test_cli_error_returns_nonzero_json(self):
        result = subprocess.run([sys.executable, str(Path(__file__).with_name("audit_native_alignment.py")), "/missing/file.aab"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 1)
        self.assertIn('"passed": false', result.stdout)


if __name__ == "__main__":
    unittest.main()
