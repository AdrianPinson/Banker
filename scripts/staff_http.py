"""Cookie/CSRF client for local fictional-data demos and release checks."""
import http.cookiejar
import json
import os
import urllib.parse
import urllib.request


class StaffHttp:
    def __init__(self):
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}),
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def open(self, request, timeout=15):
        return self.opener.open(request, timeout=timeout)

    def headers(self, base):
        base = base.rstrip('/')
        with self.open(base + '/api/auth') as response:
            session = json.load(response)
        if not session['enabled']:
            return {}
        if not session['username']:
            body = urllib.parse.urlencode({
                'username': os.environ.get('BANKER_DEMO_USER', 'manager'),
                'password': os.environ.get('BANKER_DEMO_PASSWORD', 'manager-demo-only'),
            }).encode()
            login = urllib.request.Request(base + '/api/login', data=body, headers={
                'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-TOKEN': session['csrfToken']})
            with self.open(login):
                pass
            with self.open(base + '/api/auth') as response:
                session = json.load(response)
        return {'X-CSRF-TOKEN': session['csrfToken']}
