#!/usr/bin/env python3
"""Read-only backend reachability/route probe. No tokens, account data or SMS."""
import argparse
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True, help='Origin including trailing slash, e.g. http://192.168.1.73:8000/')
    args = parser.parse_args()
    origin = urlsplit(args.base_url)
    if (origin.scheme not in ('http', 'https') or not origin.hostname or
            origin.username is not None or origin.password is not None or
            origin.path != '/' or origin.query or origin.fragment):
        parser.error('Use an HTTP(S) origin ending in /, without credentials, query or API prefix.')
    opener = build_opener(NoRedirect())
    failed = False
    for path, expected in [('api/auth/interests/', 200), ('api/auth/send-otp/', 405),
                           ('api/auth/refresh/', 405), ('api/chat/rooms/', 401)]:
        try:
            response = opener.open(Request(args.base_url + path, method='GET'), timeout=5)
        except HTTPError as error:
            response = error
        except (URLError, TimeoutError):
            print(f'GET /{path}: unreachable; check the address, port publishing and network.')
            failed = True
            continue
        with response:
            content_type = response.headers.get_content_type()
            ok = response.code == expected and content_type == 'application/json'
            failed |= not ok
            print(f'GET /{path}: {response.code} {content_type}; expected {expected} JSON')
            if response.code == 400 and content_type == 'text/html':
                print(f'  Likely pre-API host rejection: add {origin.hostname} to DJANGO_ALLOWED_HOSTS on the backend and recreate web.')
            elif response.code == 404:
                print('  Check the backend branch/deployment and reverse-proxy API prefix.')
    raise SystemExit(1 if failed else 0)


if __name__ == '__main__':
    main()
