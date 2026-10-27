#!/usr/bin/env python3
"""Exercise the local Banker workflow. Uses fictional DEMO-1001 and changes its balance by $15."""
import argparse
import json
import urllib.request
import uuid
from decimal import Decimal


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://127.0.0.1:8080')
    args = parser.parse_args()

    def request(path, payload=None, key=None, write=False):
        data = json.dumps(payload).encode() if payload is not None else (b'' if write else None)
        headers = {'Content-Type': 'application/json'}
        if write:
            headers['Idempotency-Key'] = key or str(uuid.uuid4())
        req = urllib.request.Request(args.url.rstrip('/') + '/api' + path, data=data, headers=headers)
        with urllib.request.urlopen(req, timeout=15) as response:
            return json.load(response, parse_float=Decimal)

    if not request('/branch')['open'] or request('/queue'):
        raise RuntimeError('Use an open branch with an empty queue for this demo.')
    before = request('/accounts/DEMO-1001')['balance']
    ticket = request('/queue/check-in', {'accountId': 'DEMO-1001', 'firstName': 'Alex', 'lastName': 'River'}, write=True)
    claimed = request('/queue/claim-next', {'teller': 'demo-teller'}, write=True)
    if claimed['id'] != ticket['id']:
        raise RuntimeError('Another client was ahead in the queue. Use a fresh demo database for this script.')
    prefix = '/tickets/' + str(ticket['id'])
    key = str(uuid.uuid4())
    deposit = request(prefix + '/deposits', {'amount': '25.00'}, key, True)
    retry = request(prefix + '/deposits', {'amount': '25.00'}, key, True)
    if deposit != retry:
        raise RuntimeError('Retry did not return the original deposit')
    request(prefix + '/withdrawals', {'amount': '10.00'}, write=True)
    request(prefix + '/complete', write=True)
    after = request('/accounts/DEMO-1001')['balance']
    if Decimal(after) - Decimal(before) != Decimal('15.00'):
        raise RuntimeError('Unexpected balance change')
    print(f'PASS: FIFO service, $25 deposit + identical retry, $10 withdrawal, session completed. Balance: ${after:.2f}')


if __name__ == '__main__':
    main()
