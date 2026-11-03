"""Reassemble a locally signed APK using only its public APK signature/tail bytes.

The private signing key never leaves the local signing workspace. Both byte hashes
are pinned, so this cannot sign a different binary or accept a changed artifact.
"""
from pathlib import Path
import base64, hashlib, json, shutil, zlib

manifest = json.loads(Path('release/v019-apk-signature.json').read_text())
root = Path('build-artifact')
inputs = list(root.rglob('*.apk'))
assert len(inputs) == 1
original = inputs[0].read_bytes()
assert hashlib.sha256(original).hexdigest() == manifest['original_sha256']
assert len(original) == manifest['original_size']
recipe = json.loads(zlib.decompress(base64.b64decode(manifest['public_apk_recipe_zlib_base64'], validate=True)))
chunks = []
for operation in recipe:
    if 'copy' in operation:
        offset, size = operation['copy']
        assert 0 <= offset <= offset + size <= len(original)
        chunks.append(original[offset:offset + size])
    else:
        chunks.append(base64.b64decode(operation['bytes'], validate=True))
signed = b''.join(chunks)
assert len(signed) == manifest['signed_size']
assert hashlib.sha256(signed).hexdigest() == manifest['signed_sha256']
out = Path('distribution'); out.mkdir(exist_ok=True)
(out / 'InterFlashTV-OwnTV-v0.19-prueba.apk').write_bytes(signed)
sources = list(root.rglob('InterFlashTV-OwnTV-source-v0.19.zip'))
assert len(sources) == 1
shutil.copyfile(sources[0], out / 'InterFlashTV-OwnTV-source-v0.19.zip')
print('Reassembled APK matches the locally verified permanent signature exactly.')
