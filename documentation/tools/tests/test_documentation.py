# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2020-2026 Patrick Heusser and MTL Explorer contributors.
"""Documentation output contracts using synthetic pages; no publication."""

from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import tempfile
import unittest

from documentation.tools.website import generate_documentation as docs
from documentation.tools.website.check_documentation import check


class DocumentationTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="mtl-docs-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.write("documentation/README.md", "# Documentation\n\n[User guide](user/README.md)\n\n[Features](features.md)\n")
        self.write("documentation/features.md", "# Features\n\nCurrent capabilities.\n")
        self.write("documentation/user/README.md", "# User guide\n\n[First steps](start.md)\n\n[Help](help.md)\n")
        self.write("documentation/user/start.md", "# First steps\n\nRead the guide.\n\n## Import\n\n[Help](help.md#recovery)\n")
        self.write("documentation/user/help.md", "# Help\n\n## Recovery\n\nTry the supported action.\n")
        self.write("docs/sitemap.xml", '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"></urlset>\n')
        self.write("docs/site-theme.css", ":root { --text: black; }\n")
        self.write("docs/site-analytics.js", "/* synthetic analytics asset */\n")
        for filename in ("documentation.css", "documentation.js", "guide-theme.js", "guide-navigation.js", "guide-analytics.js"):
            self.write("documentation/tools/website/" + filename, "/* synthetic asset */\n")

    def write(self, relative, content):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        return path

    def test_site_and_installed_links_breadcrumbs_and_search_share_sources(self):
        docs.generate(self.root)
        page = (self.root / "docs/docs/user/start/index.html").read_text()
        self.assertIn('href="/docs/user/help/#recovery"', page)
        self.assertIn('href="https://mtl-explorer.com/docs/user/start/"', page)
        self.assertLess(page.index('<h1 id="first-steps"'), page.index('aria-label="On this page"'))
        self.assertIn('"name": "User guide"', page)
        installed = (self.root / "mtl-client/public/guide/start/index.html").read_text()
        self.assertIn('href="/mtl/guide/help/index.html#recovery"', installed)
        self.assertIn("Guide included with this build", installed)
        self.assertNotIn('rel="canonical"', installed)
        theme_script = '<script src="/mtl/guide/guide-theme.js"></script>'
        self.assertLess(installed.index(theme_script), installed.index('<link rel="stylesheet"'))
        self.assertNotIn("guide-theme.js", page)
        self.assertIn('<a href="/mtl/" data-guide-back>Back to app</a>', installed)
        self.assertIn('<script defer src="/mtl/guide/guide-navigation.js"></script>', installed)
        self.assertNotIn("guide-navigation.js", page)
        self.assertEqual(page.count('<script defer src="/site-analytics.js"></script>'), 1)
        self.assertIn('href="/#site-analytics">Site analytics</a>', page)
        self.assertNotIn("site-analytics.js", installed)
        self.assertNotIn("Site analytics</a>", installed)
        self.assertEqual(installed.count('<script defer src="/mtl/guide/guide-analytics.js"></script>'), 1)
        self.assertNotIn("guide-analytics.js", page)
        self.assertEqual(
            (self.root / "mtl-client/public/guide/guide-analytics.js").read_bytes(),
            (self.root / "documentation/tools/website/guide-analytics.js").read_bytes(),
        )
        self.assertEqual(
            (self.root / "mtl-client/public/guide/guide-navigation.js").read_bytes(),
            (self.root / "documentation/tools/website/guide-navigation.js").read_bytes(),
        )
        self.assertEqual(
            (self.root / "mtl-client/public/guide/guide-theme.js").read_bytes(),
            (self.root / "documentation/tools/website/guide-theme.js").read_bytes(),
        )
        search = json.loads((self.root / "mtl-client/public/guide/search-index.json").read_text())
        self.assertIn("/mtl/guide/start/index.html", {item["url"] for item in search})
        self.assertEqual(docs.generate(self.root, check=True), docs.generate(self.root))

    def test_changed_source_and_obsolete_output_fail_freshness_check(self):
        docs.generate(self.root)
        stale = self.write("docs/docs/obsolete/index.html", "obsolete")
        with self.assertRaisesRegex(ValueError, "obsolete"):
            docs.generate(self.root, check=True)
        docs.generate(self.root)
        self.assertFalse(stale.exists())
        self.write("documentation/user/help.md", "# Help\n\nChanged guidance.\n")
        with self.assertRaisesRegex(ValueError, "Stale documentation"):
            docs.generate(self.root, check=True)

    def test_current_article_and_chapter_are_identified_in_navigation(self):
        self.write("documentation/user/README.md", "# User guide\n\n[Tracks](tracks/README.md)\n")
        self.write("documentation/user/tracks/README.md", "# Tracks\n\n[Details](details.md)\n")
        self.write("documentation/user/tracks/details.md", "# Track details\n\nRead an activity.\n")
        self.write("documentation/administration/README.md", "# Administration\n\n[Settings](settings.md)\n")
        self.write("documentation/administration/settings.md", "# Settings\n\nConfigure the server.\n")
        documents = docs.load_documents(self.root, installed=True)
        navigation = docs.navigation(documents, self.root, True, "/mtl/guide/tracks/details/index.html")
        self.assertIn('<details open><summary><a href="/mtl/guide/tracks/index.html">Tracks</a>', navigation)
        self.assertIn('href="/mtl/guide/tracks/details/index.html" aria-current="page"', navigation)
        self.assertEqual(navigation.count('aria-current="page"'), 1)
        navigation = docs.navigation(documents, self.root, True, "/mtl/guide/administration/settings/index.html")
        self.assertIn('<details><summary><a href="/mtl/guide/index.html">User guide</a>', navigation)
        self.assertIn('<details open><summary><a href="/mtl/guide/administration/index.html">Administration</a>', navigation)

    def test_navigation_follows_links_through_a_repository_directory_alias(self):
        alias = self.root / "repository-alias"
        alias.symlink_to(self.root, target_is_directory=True)
        documents = docs.load_documents(alias, installed=True)
        navigation = docs.navigation(documents, alias, True, "/mtl/guide/start/index.html")
        self.assertIn('href="/mtl/guide/start/index.html" aria-current="page"', navigation)
        self.assertIn('href="/mtl/guide/help/index.html"', navigation)
        self.assertEqual(navigation.count('aria-current="page"'), 1)

    def test_private_links_and_path_traversal_are_rejected(self):
        self.write("docs-my/private.md", "# Private\n")
        self.write("documentation/user/help.md", "# Help\n\n[Private](../../docs-my/private.md)\n")
        with self.assertRaisesRegex(ValueError, "internal material"):
            docs.generate(self.root)
        self.write("documentation/user/help.md", "# Help\n\n[Outside](../../../outside.md)\n")
        with self.assertRaisesRegex(ValueError, "escapes repository"):
            docs.generate(self.root)

    def test_missing_anchors_and_orphan_articles_are_reported(self):
        self.write("documentation/user/help.md", "# Help\n\nNo recovery heading.\n")
        self.write("documentation/user/orphan.md", "# Unlisted article\n")
        with redirect_stdout(io.StringIO()):
            failures = check(self.root)
        self.assertTrue(any("missing fragment" in failure for failure in failures))
        self.assertTrue(any("Orphan public page" in failure for failure in failures))


if __name__ == "__main__":
    unittest.main()
