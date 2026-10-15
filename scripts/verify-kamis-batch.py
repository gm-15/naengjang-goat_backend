#!/usr/bin/env python3
"""Run the authenticated real KAMIS batch twice and reject zero-write/failed runs.
This checks job execution; use audit-kamis-data.sql to inspect physical DB rows.
No passwords, tokens or raw external request URLs are printed or written.
"""
import argparse
import getpass
import json
import os
import pathlib
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://localhost:8080')
    parser.add_argument('--username', default='demo')
    parser.add_argument('--output', help='Optional path for execution metrics JSON')
    args = parser.parse_args()
    password = os.getenv('KAMIS_VERIFY_PASSWORD')
    if password is None:
        password = os.getenv('DEMO_PASSWORD', 'demo1234') if args.username == 'demo' else getpass.getpass('Login password: ')
    base = args.base_url.rstrip('/')
    token = None

    def post(path, body):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request(base + path, data=json.dumps(body).encode(), headers=headers, method='POST')
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                return json.load(response)
        except urllib.error.HTTPError as failure:
            raise RuntimeError(f'{path}: HTTP {failure.code}; inspect server batch status/logs') from None
        except urllib.error.URLError:
            raise RuntimeError(f'{path}: backend connection failed') from None

    login = post('/api/users/login', {'username': args.username, 'password': password})
    token = login['accessToken']
    runs = []
    for _ in range(2):
        result = post('/admin/batch/kamis/run', {})
        metrics = {key: result.get(key) for key in ['executionId', 'status', 'readCount', 'filteredCount', 'writtenItems']}
        runs.append(metrics)
        if metrics['status'] != 'COMPLETED' or not metrics['writtenItems'] or metrics['writtenItems'] <= 0:
            raise RuntimeError('Batch did not complete with matched/written items: ' + json.dumps(metrics))
    output = {'batchRuns': runs, 'databaseAuditRequired': 'scripts/audit-kamis-data.sql'}
    if args.output:
        pathlib.Path(args.output).write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as failure:
        raise SystemExit(str(failure))
