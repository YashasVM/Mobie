"""Regression fixtures for release mapping verification; no Android/device required."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name('verify-litert-release.py')
PREFIX = 'com.google.ai.edge.litertlm.'
CLASSES = ['BenchmarkInfo', 'InputData$Text', 'InputData$Image', 'InputData$Audio',
           'LiteRtLmJniException', 'LiteRtLmJni$JniMessageCallback',
           'LiteRtLmJni$JniInferenceCallback']


def valid_mapping():
    lines = [f'{PREFIX}{name} -> {PREFIX}{name}:' for name in CLASSES]
    lines.extend(f'    void {name}() -> {name}' for name in ['onMessage', 'onDone', 'onError', 'onNext'])
    return '\n'.join(lines)


class ReleaseMappingTest(unittest.TestCase):
    def verify(self, mapping):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'mapping.txt'
            path.write_text(mapping)
            return subprocess.run([sys.executable, str(SCRIPT), str(path)], capture_output=True, text=True)

    def test_preserved_names_pass(self):
        self.assertEqual(0, self.verify(valid_mapping()).returncode)

    def test_renamed_class_fails(self):
        result = self.verify(valid_mapping().replace(f'-> {PREFIX}BenchmarkInfo:', '-> a.b:'))
        self.assertNotEqual(0, result.returncode)
        self.assertIn('renamed SDK class', result.stderr)

    def test_removed_class_fails(self):
        result = self.verify('\n'.join(valid_mapping().splitlines()[1:]))
        self.assertNotEqual(0, result.returncode)
        self.assertIn('missing JNI class', result.stderr)

    def test_renamed_callback_fails(self):
        result = self.verify(valid_mapping().replace('-> onMessage', '-> a'))
        self.assertNotEqual(0, result.returncode)
        self.assertIn('renamed JNI callback', result.stderr)

    def test_removed_callback_fails(self):
        result = self.verify(valid_mapping().replace('    void onMessage() -> onMessage', ''))
        self.assertNotEqual(0, result.returncode)
        self.assertIn('missing JNI callback', result.stderr)

    def test_r8_generated_lambda_does_not_fail(self):
        result = self.verify(valid_mapping() + f'\n{PREFIX}Engine$$ExternalSyntheticLambda0 -> a.b:')
        self.assertEqual(0, result.returncode)


if __name__ == '__main__':
    unittest.main()
