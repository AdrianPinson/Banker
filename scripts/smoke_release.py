#!/usr/bin/env python3
"""Verify and run an extracted release from a path with spaces, then test restart durability."""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
from package_release import ROOT, project_version

HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def request(base, path, body=None, key=None):
    headers = {}
    data = None
    if key:
        headers = {'Content-Type': 'application/json', 'Idempotency-Key': key}
        data = json.dumps(body).encode() if body is not None else b''
    req = urllib.request.Request(base + '/api' + path, data=data, headers=headers)
    with HTTP.open(req, timeout=5) as response:
        return json.load(response)


def stop(process, force=False):
    if process.poll() is not None:
        return
    if os.name == 'nt':
        subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], check=False,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    else:
        os.killpg(process.pid, signal.SIGKILL if force else signal.SIGTERM)
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        if os.name == 'nt':
            process.kill()
        else:
            os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=10)


def wait_closed(port):
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(('127.0.0.1', port), timeout=.2):
                pass
        except OSError:
            return
        time.sleep(.1)
    raise AssertionError('The test app is still listening after process termination')


@contextmanager
def release_workspace():
    temporary = tempfile.TemporaryDirectory(prefix='Banker release check ')
    try:
        yield temporary.name
    finally:
        # Windows can briefly retain Java/antivirus file handles after taskkill returns.
        # Retry deletion for a bounded interval; never suppress an unresolved cleanup error.
        deadline = time.monotonic() + 10
        while True:
            try:
                temporary.cleanup()
                break
            except PermissionError:
                if time.monotonic() >= deadline:
                    raise
                time.sleep(.1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, default=ROOT / f'target/releases/banker-{project_version()}.zip')
    args = parser.parse_args()
    expected = (args.archive.parent / 'SHA256SUMS.txt').read_text(encoding='utf-8').split()[0]
    check(hashlib.sha256(args.archive.read_bytes()).hexdigest() == expected, 'Release ZIP checksum mismatch')
    with release_workspace() as temporary:
        temporary = Path(temporary)
        extract = temporary / 'Extracted app with spaces'
        with zipfile.ZipFile(args.archive) as archive:
            check(archive.testzip() is None, 'Corrupt ZIP')
            for entry in archive.namelist():
                check(not entry.startswith('/') and '..' not in Path(entry).parts, 'Unsafe ZIP entry')
            archive.extractall(extract)
        roots = list(extract.iterdir())
        check(len(roots) == 1 and roots[0].is_dir(), 'Expected one application folder')
        app = roots[0]
        for line in (app / 'SHA256SUMS.txt').read_text(encoding='utf-8').splitlines():
            checksum, name = line.split('  ', 1)
            check(hashlib.sha256((app / name).read_bytes()).hexdigest() == checksum, f'Packaged file checksum mismatch: {name}')
        check(not (app / 'data').exists(), 'A demo database must never be shipped')
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            port = listener.getsockname()[1]
        base = f'http://127.0.0.1:{port}'
        env = dict(os.environ, BANKER_DB_USER='sa', BANKER_DB_PASSWORD='',
                   BANKER_BIND_ADDRESS='127.0.0.1', BANKER_NONINTERACTIVE='1')
        # Exercise the shipped file-backed H2 defaults, never an inherited external database.
        env.pop('BANKER_DB_URL', None)
        outside = temporary / 'Different working directory'
        outside.mkdir()
        launcher = app / ('start-banker.cmd' if os.name == 'nt' else 'start-banker.sh')
        command = ['cmd.exe', '/d', '/c', 'call', str(launcher), f'--server.port={port}'] if os.name == 'nt' else ['sh', str(launcher), f'--server.port={port}']
        process = None
        def start(log):
            nonlocal process
            process = subprocess.Popen(command, cwd=outside, env=env, stdout=log, stderr=subprocess.STDOUT,
                                       start_new_session=os.name != 'nt')
            deadline = time.monotonic() + 45
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise AssertionError(f'Launcher exited: {process.returncode}')
                try:
                    if request(base, '/branch')['open']:
                        return
                except (OSError, ValueError):
                    time.sleep(.2)
            raise AssertionError('Server did not become ready')
        with (temporary / 'server.log').open('w+', encoding='utf-8') as log:
            try:
                start(log)
                with HTTP.open(base, timeout=5) as response:
                    html = response.read().decode()
                check('Banker' in html and '/assets/' in html, 'Dashboard is missing')
                # Verify the Java-served frontend entry points, fonts, and licenses.
                import re
                assets = re.findall(r'(?:src|href)="(/assets/[^\"]+)"', html)
                check(any(a.endswith('.js') for a in assets) and any(a.endswith('.css') for a in assets), 'Dashboard entry points are missing')
                for asset in assets:
                    with HTTP.open(base + asset, timeout=5) as response:
                        content = response.read()
                    if asset.endswith('.css'):
                        for font in re.findall(r'url\(["\']?([^\)"\']+\.woff2)["\']?\)', content.decode()):
                            with HTTP.open(urllib.parse.urljoin(base + asset, font), timeout=5) as response:
                                check(len(response.read()) > 1000, 'Font not served')
                with HTTP.open(base + '/licenses/DM-Sans.txt', timeout=5) as response:
                    check(b'OPEN FONT LICENSE' in response.read(), 'Font license missing')
                def post(path, body=None, key=None):
                    return request(base, path, body, key or str(uuid.uuid4()))
                ticket = post('/queue/check-in', {'accountId':'DEMO-1001', 'firstName':'Alex', 'lastName':'River'})
                check(post('/queue/claim-next', {'teller':'release-teller'})['id'] == ticket['id'], 'FIFO claim mismatch')
                path = f"/tickets/{ticket['id']}"
                deposit_key = str(uuid.uuid4())
                original = post(path + '/deposits', {'amount':'25.00'}, deposit_key)
                check(post(path + '/deposits', {'amount':'25.00'}, deposit_key) == original, 'Deposit retry changed response')
                post(path + '/withdrawals', {'amount':'10.00'})
                post(path + '/complete')
                check(request(base, '/accounts/DEMO-1001')['balance'] == 1015, 'Unexpected balance before restart')
                stop(process, force=True)
                wait_closed(port)
                start(log)
                check(request(base, '/accounts/DEMO-1001')['balance'] == 1015, 'Balance was not durable')
                check(post(path + '/deposits', {'amount':'25.00'}, deposit_key) == original, 'Receipt was not durable')
                check(request(base, '/accounts/DEMO-1001')['balance'] == 1015, 'Retry applied another deposit')
                check(len(request(base, '/accounts/DEMO-1001/transactions')) == 2, 'Ledger count mismatch')
                check(request(base, '/queue') == [], 'Completed queue was not durable')
                check((app / 'data').exists() and not (outside / 'data').exists(), 'Launcher used the wrong data directory')
            except Exception:
                if process:
                    stop(process)
                    wait_closed(port)
                log.flush()
                print((temporary / 'server.log').read_text(encoding='utf-8', errors='replace'))
                raise
            finally:
                if process:
                    stop(process)
                    wait_closed(port)

    print('PASS: extracted launcher, paths with spaces, dashboard/assets/fonts, checksums, transaction retries, ledger, forced restart, and cleanup')


if __name__ == '__main__':
    main()
