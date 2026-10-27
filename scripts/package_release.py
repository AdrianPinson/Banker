#!/usr/bin/env python3
"""Package an already-built web JAR. No database, credentials, or runtime is bundled."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def project_version():
    element = ET.parse(ROOT / 'pom.xml').getroot().find('{http://maven.apache.org/POM/4.0.0}version')
    version = element.text
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?', version):
        raise ValueError('Unsupported project version')
    return version


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'target/releases')
    args = parser.parse_args()
    version = project_version()
    jar = args.jar or ROOT / f'target/banker-{version}.jar'
    with zipfile.ZipFile(jar) as source:
        if source.testzip() is not None:
            raise ValueError('Corrupt JAR')
        names = source.namelist()
        if 'BOOT-INF/classes/static/index.html' not in names or not any(n.endswith('.woff2') for n in names):
            raise ValueError('Build with mvn -Pweb clean verify: dashboard/fonts are missing')
        if 'org/springframework/boot/loader/launch/JarLauncher.class' not in names:
            raise ValueError('An executable Spring Boot JAR is required')
    args.output.mkdir(parents=True, exist_ok=True)
    name = f'banker-{version}'
    archive = args.output / f'{name}.zip'
    pending_archive = archive.with_suffix('.zip.tmp')
    revision = os.getenv('GITHUB_SHA', '')
    if not re.fullmatch('[0-9a-f]{40}', revision):
        revision = 'local-build'
    with tempfile.TemporaryDirectory() as temporary:
        stage = Path(temporary) / name
        stage.mkdir()
        shutil.copyfile(jar, stage / 'banker.jar')
        for file in ['README.md', 'start-banker.sh', 'start-banker.cmd']:
            shutil.copyfile(ROOT / 'packaging' / file, stage / file)
        (stage / 'start-banker.sh').chmod(0o755)
        for file in ['demo-guide.md', 'api.md', 'architecture.md', 'release-notes.md']:
            (stage / 'docs').mkdir(exist_ok=True)
            shutil.copyfile(ROOT / 'docs' / file, stage / 'docs' / file)
        (stage / 'PROVENANCE.json').write_text(json.dumps({
            'project': 'Banker', 'version': version, 'sourceCommit': revision,
            'sourceRepository': 'https://github.com/AdrianPinson/Banker',
            'requiresJava': '17+', 'bundledRuntime': False,
        }, indent=2) + '\n', encoding='utf-8')
        files = sorted(p for p in stage.rglob('*') if p.is_file())
        (stage / 'SHA256SUMS.txt').write_text(''.join(f'{digest(p)}  {p.relative_to(stage).as_posix()}\n' for p in files), encoding='utf-8')
        with zipfile.ZipFile(pending_archive, 'w', zipfile.ZIP_DEFLATED) as output:
            for path in sorted(stage.rglob('*')):
                if path.is_file():
                    output.write(path, f'{name}/{path.relative_to(stage).as_posix()}')
    with zipfile.ZipFile(pending_archive) as check:
        if check.testzip() is not None:
            raise ValueError('Generated ZIP failed integrity verification')
    with pending_archive.open('r+b') as completed:
        os.fsync(completed.fileno())
    os.replace(pending_archive, archive)
    (args.output / 'SHA256SUMS.txt').write_text(f'{digest(archive)}  {archive.name}\n', encoding='utf-8')
    print(f'Packaged {archive} ({archive.stat().st_size / 1024 / 1024:.1f} MiB)')


if __name__ == '__main__':
    main()
