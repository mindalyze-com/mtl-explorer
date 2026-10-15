#!/usr/bin/env python3
"""Check the MTL Explorer website locally, or compare it with an explicit HTTP base."""

import argparse
from collections import Counter, deque
from datetime import date, datetime
from html.parser import HTMLParser
import json
from pathlib import Path
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import unquote, urldefrag, urljoin, urlsplit
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET

ORIGIN = 'https://mtl-explorer.com/'
OLD_ORIGIN = 'https://mindalyze-com.github.io/mtl-explorer/'
DEFAULT_ROOT = Path(__file__).resolve().parents[3] / 'docs'
TIMEOUT_SECONDS = 15
SITEMAP_NS = {'s': 'http://www.sitemaps.org/schemas/sitemap/0.9'}


class Page(HTMLParser):
    def __init__(self, text):
        super().__init__(convert_charrefs=True)
        self.meta = {}
        self.canonicals = []
        self.references = []
        self.anchors = []
        self.ids = []
        self.images = []
        self.schemas = []
        self.json_errors = []
        self.lang = None
        self.title = ''
        self.h1_count = 0
        self.main_count = 0
        self.breadcrumb = False
        self.in_title = False
        self.json_text = None
        self.feed(text)

    def handle_starttag(self, tag, pairs):
        attrs = dict(pairs)
        if 'id' in attrs:
            self.ids.append(attrs['id'])
        if tag == 'html':
            self.lang = attrs.get('lang')
        elif tag == 'title':
            self.in_title = True
        elif tag == 'h1':
            self.h1_count += 1
        elif tag == 'main':
            self.main_count += 1
        elif tag == 'meta':
            self.meta.setdefault(attrs.get('name', attrs.get('property', '')), []).append(attrs.get('content', ''))
        elif tag == 'a' and attrs.get('href'):
            self.anchors.append(attrs['href'])
        elif tag == 'img':
            self.images.append(attrs)
        elif tag == 'script' and attrs.get('type') == 'application/ld+json':
            self.json_text = ''
        elif tag == 'nav' and attrs.get('aria-label') == 'Breadcrumb':
            self.breadcrumb = True
        if tag == 'link' and 'canonical' in attrs.get('rel', '').split():
            self.canonicals.append(attrs.get('href'))
        if tag in ['a', 'link'] and attrs.get('href'):
            self.references.append(attrs['href'])
        if attrs.get('src'):
            self.references.append(attrs['src'])
        for attr in ['srcset', 'imagesrcset']:
            if attrs.get(attr):
                self.references.extend(part.strip().split()[0] for part in attrs[attr].split(',') if part.strip())
        if tag == 'meta' and attrs.get('property', attrs.get('name')) in ['og:image', 'twitter:image']:
            self.references.append(attrs.get('content', ''))

    def handle_data(self, text):
        if self.in_title:
            self.title += text
        if self.json_text is not None:
            self.json_text += text

    def handle_endtag(self, tag):
        if tag == 'title':
            self.in_title = False
        elif tag == 'script' and self.json_text is not None:
            try:
                self.schemas.append(json.loads(self.json_text))
            except ValueError as error:
                self.json_errors.append(str(error))
            self.json_text = None


def schema_nodes(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from schema_nodes(child)
    elif isinstance(value, list):
        for child in value:
            yield from schema_nodes(child)


def check(root, base_url=None):
    failures = []
    request_count = 0

    def require(condition, message):
        if not condition:
            failures.append(message)

    def read(path):
        nonlocal request_count
        local = path.read_bytes()
        if not base_url:
            return local.decode('utf-8')
        relative = path.relative_to(root).as_posix()
        if relative.endswith('index.html'):
            relative = relative[:-len('index.html')]
        target = urljoin(base_url.rstrip('/')+'/', relative)
        request_count += 1
        try:
            with urlopen(Request(target, headers={'User-Agent': 'MTL-Explorer-SEO-Check', 'Cache-Control': 'no-cache'}), timeout=TIMEOUT_SECONDS) as result:
                body = result.read()
                require(result.status == 200, f'{target}: HTTP {result.status}')
                require('noindex' not in result.headers.get('X-Robots-Tag', '').lower(), f'{target}: HTTP header blocks indexing')
                require(body == local, f'{target}: served content differs from local {relative or "index.html"}')
                return body.decode('utf-8')
        except (HTTPError, URLError, TimeoutError, UnicodeError) as error:
            failures.append(f'{target}: {type(error).__name__}: {error}')
            return ''

    files = sorted(root.rglob('index.html'))
    pages = {}
    checked_refs = set()
    for path in files:
        url = urljoin(ORIGIN, path.relative_to(root).as_posix()[:-len('index.html')])
        text = read(path)
        page = Page(text)
        pages[url] = page
        require(page.canonicals == [url], f'{url}: expected one self-canonical, found {page.canonicals}')
        require(OLD_ORIGIN not in text, f'{url}: old GitHub Pages origin remains')
        require(page.lang == 'en', f'{url}: missing English language declaration')
        require(bool(page.title.strip()), f'{url}: missing title')
        require(page.h1_count == 1 and page.main_count == 1, f'{url}: expected one H1 and one main element')
        require(len(page.meta.get('description', [])) == 1 and bool(page.meta.get('description', [''])[0]), f'{url}: expected one nonempty description')
        require(page.meta.get('og:url') == [url], f'{url}: Open Graph URL differs from canonical')
        require('noindex' not in ','.join(page.meta.get('robots', [])).lower(), f'{url}: robots blocks indexing')
        require(bool(page.meta.get('viewport')), f'{url}: missing viewport')
        require(not page.json_errors, f'{url}: JSON-LD errors {page.json_errors}')
        require(len(page.ids) == len(set(page.ids)), f'{url}: duplicate element IDs')
        for image in page.images:
            require('alt' in image, f'{url}: image without alt attribute')
            if image.get('src'):
                require(image.get('width') and image.get('height'), f'{url}: content image lacks dimensions: {image["src"]}')
        if page.breadcrumb:
            crumbs = [node for node in schema_nodes(page.schemas) if node.get('@type') == 'BreadcrumbList']
            require(len(crumbs) == 1, f'{url}: expected one breadcrumb schema')
            if crumbs:
                items = crumbs[0].get('itemListElement', [])
                require([i.get('position') for i in items] == list(range(1, len(items)+1)), f'{url}: breadcrumb positions are not sequential')
                require(bool(items) and items[-1].get('item') == url, f'{url}: breadcrumb does not end at this page')

    for url, page in pages.items():
        for reference in page.references:
            target, fragment = urldefrag(urljoin(url, reference))
            if urlsplit(target).netloc != urlsplit(ORIGIN).netloc:
                continue
            checked_refs.add(target)
            resource = root / unquote(urlsplit(target).path).lstrip('/')
            if target.endswith('/'):
                resource /= 'index.html'
            require(resource.resolve().is_relative_to(root.resolve()), f'{url}: reference escapes website root: {reference}')
            require(resource.is_file(), f'{url}: missing local resource {reference}')
            if fragment and target in pages:
                require(unquote(fragment) in pages[target].ids, f'{url}: missing fragment {reference}')

    for label, values in [('title', [p.title for p in pages.values()]),
                          ('description', [p.meta.get('description', [''])[0] for p in pages.values()])]:
        for value, count in Counter(values).items():
            require(count == 1, f'Duplicate {label}: {value}')

    try:
        sitemap = ET.fromstring(read(root/'sitemap.xml'))
        locations = [node.text for node in sitemap.findall('s:url/s:loc', SITEMAP_NS)]
        require(len(locations) == len(set(locations)), 'Duplicate sitemap URLs')
        require(set(locations) == set(pages), f'Sitemap/page mismatch: {set(locations) ^ set(pages)}')
        for modified in sitemap.findall('s:url/s:lastmod', SITEMAP_NS):
            require(datetime.fromisoformat(modified.text.replace('Z', '+00:00')).date() <= date.today(), 'Sitemap has a future modification date')
    except (ET.ParseError, ValueError, AttributeError) as error:
        failures.append(f'sitemap.xml: {type(error).__name__}: {error}')
    robots = read(root/'robots.txt')
    require(f'Sitemap: {ORIGIN}sitemap.xml' in robots, 'robots.txt must link directly to the canonical sitemap')
    require(OLD_ORIGIN not in robots, 'robots.txt retains old origin')

    visited, queue = set(), deque([ORIGIN])
    while queue:
        url = queue.popleft()
        if url in visited or url not in pages:
            continue
        visited.add(url)
        queue.extend(urldefrag(urljoin(url, anchor))[0] for anchor in pages[url].anchors)
    require(visited == set(pages), f'Pages unreachable from home: {sorted(set(pages)-visited)}')
    home = pages.get(ORIGIN)
    if home:
        sites = [n for n in schema_nodes(home.schemas) if n.get('@type') == 'WebSite']
        require(any(n.get('name') == 'MTL Explorer' and n.get('url') == ORIGIN for n in sites), 'Homepage lacks canonical WebSite identity')
    print(f'Checked {len(pages)} pages, {len(checked_refs)} local URL targets, sitemap, robots, schema, and reachability'+(f'; {request_count} HTTP reads' if base_url else '; no network requests'))
    for failure in failures:
        print('FAIL:', failure)
    if not failures:
        print('PASS')
    return 1 if failures else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=DEFAULT_ROOT, help='Local website directory')
    parser.add_argument('--base-url', help='Optional read-only comparison with a served website; never publishes')
    args = parser.parse_args()
    if args.base_url and urlsplit(args.base_url).scheme not in ['http', 'https']:
        parser.error('--base-url must use HTTP or HTTPS')
    if not (args.root/'index.html').is_file():
        parser.error('--root must contain the website index.html')
    return check(args.root, args.base_url)


if __name__ == '__main__':
    sys.exit(main())
