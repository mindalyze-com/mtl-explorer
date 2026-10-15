"""Release generation checks using synthetic notes; no publication."""

from contextlib import redirect_stdout
from datetime import date, timedelta
import io
from pathlib import Path
import tempfile
import unittest

from documentation.tools.website import generate_releases as website
from documentation.tools.website.release_notes import MAX_TEASER_LENGTH, github_notes_body, load_release_note, load_releases


class WebsiteReleaseTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="mtl-website-releases-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.notes = self.root / "documentation/release-notes"
        self.notes.mkdir(parents=True)
        self.docs = self.root / "docs"
        self.docs.mkdir()
        self.home_prefix = "<main>Keep this introduction.\n"
        self.home_suffix = "\nKeep these features.</main>\n"
        (self.docs / "index.html").write_text(
            self.home_prefix + website.ANNOUNCEMENT_START + "\n" + website.ANNOUNCEMENT_END
            + "\nKeep these tours.\n" + website.NEWS_START + "\n" + website.NEWS_END + self.home_suffix
        )
        (self.docs / "sitemap.xml").write_text(
            '<urlset>\n<url><loc>https://mtl-explorer.com/</loc></url>\n'
            + website.SITEMAP_START + "\n" + website.SITEMAP_END + "\n</urlset>\n"
        )
        self.write_note("0.9.0", "null")
        self.write_note("0.8.1", "2026-08-19")

    def write_note(self, version, released_on, content=None, teaser="A short summary."):
        content = content or "## Changes\n\n- First improvement.\n"
        path = self.notes / f"v.{version}.md"
        folded = "  " + teaser.replace("\n", "\n  ")
        path.write_text(f"---\nreleased_on: {released_on}\nteaser: >-\n{folded}\n---\n\n"
                        f"# MTL Explorer Release v.{version}\n\n{content}")
        return path

    def generate(self, check=False):
        with redirect_stdout(io.StringIO()):
            return website.generate(self.root, check=check)

    def test_metadata_is_removed_from_github_notes_and_legacy_notes_are_preserved(self):
        path = self.notes / "v.0.9.0.md"
        release = load_release_note(path)
        self.assertIsNone(release.released_on)
        self.assertEqual(release.teaser, "A short summary.")
        self.assertEqual(github_notes_body(path.read_text()), release.body)
        self.assertEqual(release.body.count(release.teaser), 1)
        self.assertTrue(release.content.startswith(release.teaser + "\n\n## Changes"))
        self.assertEqual(github_notes_body("# Custom notes\n\nDetails.\n"), "# Custom notes\n\nDetails.\n")
        self.assertEqual(github_notes_body("---\nreleased_on: null\n---\n\n# Custom notes\n\nDetails.\n"),
                         "# Custom notes\n\nDetails.\n")

    def test_explicit_folded_teaser_is_independent_of_the_introduction_and_chapters(self):
        teaser = "Segment comparisons, saved places,\nand optional health data."
        path = self.write_note("0.9.0", "null", "Detailed introduction about calorie estimates.\n\n"
                               "## An unrelated first chapter\n\nDetails.\n", teaser=teaser)
        release = load_release_note(path)
        self.assertEqual(release.teaser, teaser.replace("\n", " "))
        self.generate()
        for relative in ["index.html", "releases/index.html"]:
            page = (self.docs / relative).read_text()
            self.assertIn(release.teaser, page)
            self.assertNotIn("Detailed introduction", page)
            self.assertNotIn("An unrelated first chapter", page)
        page = (self.docs / "releases/0.9.0/index.html").read_text()
        self.assertIn(f'<meta name="description" content="{release.teaser}">', page)
        self.assertIn("Detailed introduction about calorie estimates.", page)
        self.assertIn(f'<article class="article-copy release-content"><p>{release.teaser}</p>', page)
        self.assertNotIn("teaser: >-", release.body)

    def test_versions_sort_numerically_and_news_separates_upcoming_from_latest(self):
        self.write_note("0.10.0", "null")
        releases = load_releases(self.notes)
        self.assertEqual([release.number for release in releases], ["0.10.0", "0.9.0", "0.8.1"])
        news = website.news(releases, self.root)
        self.assertIn("Upcoming", news)
        self.assertIn("Latest release", news)
        self.assertIn('/releases/0.10.0/', news)
        self.assertIn('/releases/0.8.1/', news)
        self.assertNotIn('/releases/0.9.0/', news)

    def test_regeneration_updates_all_surfaces_and_check_detects_stale_output(self):
        self.assertEqual(self.generate(), 0)
        self.assertEqual(self.generate(check=True), 0)
        before = (self.docs / "index.html").read_text()
        self.assertTrue(before.startswith(self.home_prefix))
        self.assertTrue(before.endswith(self.home_suffix))
        self.write_note("0.9.0", "2026-10-03", teaser="Changed summary.")
        self.assertEqual(self.generate(check=True), 1)
        self.generate()
        for relative in ["index.html", "releases/index.html", "releases/0.9.0/index.html"]:
            self.assertIn("Changed summary.", (self.docs / relative).read_text())
        home = (self.docs / "index.html").read_text()
        self.assertNotIn("Upcoming", home)
        self.assertNotIn("/releases/0.8.1/", home)
        self.assertEqual(self.generate(check=True), 0)

    def test_homepage_release_links_use_version_and_publication_status(self):
        self.write_note("0.9.0", "null", "## Automatic **heart rate** matching\n\nDetails.\n", teaser="One summary.")
        self.generate()
        home = (self.docs / "index.html").read_text()
        announcement = home.split(website.ANNOUNCEMENT_START, 1)[1].split(website.ANNOUNCEMENT_END, 1)[0]
        self.assertIn('href="/releases/0.9.0/"', announcement)
        self.assertIn('>Upcoming release</span>', announcement)
        self.assertIn('>Version 0.9.0</strong>', announcement)
        self.assertIn('<p class="release-announcement-summary">One summary.</p>', announcement)
        self.assertIn('href="/releases/0.9.0/">MTL Explorer 0.9.0</a>', home)
        self.assertNotIn("Automatic heart rate matching", home)
        self.assertEqual(home.count("One summary."), 2)
        self.write_note("0.9.0", "2026-10-03", teaser="A fix.")
        self.generate()
        home = (self.docs / "index.html").read_text()
        self.assertIn('>Latest release</span>', home)
        self.assertIn('>Version 0.9.0</strong>', home)
        self.assertNotIn("Upcoming", home)
        self.assertNotIn("/releases/0.8.1/", home)

    def test_relative_links_and_unique_chapter_anchors_are_rendered(self):
        self.write_note("0.9.0", "null", "Summary.\n\n## Changes\n\n[Guide](../guide.md#setup).\n\n"
                        "## Changes\n\n[Earlier notes](v.0.8.1.md).\n\n- Parent\n  - Child\n")
        self.generate()
        page = (self.docs / "releases/0.9.0/index.html").read_text()
        self.assertIn('href="https://github.com/mindalyze-com/mtl-explorer/blob/main/documentation/guide.md#setup"', page)
        self.assertIn('href="/releases/0.8.1/"', page)
        self.assertIn('id="changes"', page)
        self.assertIn('id="changes-2"', page)
        self.assertIn("<li>Parent\n<ul>\n<li>Child</li>", page)

    def test_invalid_metadata_and_missing_teaser_fail_before_writing(self):
        future = (date.today() + timedelta(days=1)).isoformat()
        invalid = [
            "---\nreleased_on: null\nextra: true\n---\n\n# Title\n\nSummary.\n",
            "---\nreleased_on: 2026-02-30\n---\n\n# Title\n\nSummary.\n",
            f"---\nreleased_on: {future}\n---\n\n# Title\n\nSummary.\n",
            "---\nreleased_on: null\n---\n\n# Title\n\nA paragraph cannot replace the teaser.\n",
            "---\nreleased_on: null\nteaser: >-\n  \n---\n\n# Title\n\nDetails.\n",
            "---\nreleased_on: null\nteaser: >-\nUnindented text.\n---\n\n# Title\n\nDetails.\n",
            "---\nreleased_on: null\nteaser: >-\n  Summary.\nextra: true\n---\n\n# Title\n\nDetails.\n",
            "---\nreleased_on: null\nteaser: >-\n  Summary.\nteaser: >-\n  Duplicate.\n---\n\n# Title\n\nDetails.\n",
            "---\nreleased_on: null\nteaser: >-\n  A **formatted** teaser.\n---\n\n# Title\n\nDetails.\n",
            "---\nreleased_on: null\nteaser: >-\n  ## A heading\n---\n\n# Title\n\nDetails.\n",
            "---\nreleased_on: null\nteaser: >-\n  An _italic_ teaser.\n---\n\n# Title\n\nDetails.\n",
            "---\nreleased_on: null\nteaser: >-\n  A <b>formatted</b> teaser.\n---\n\n# Title\n\nDetails.\n",
            "---\nreleased_on: null\nteaser: >-\n  " + "x" * (MAX_TEASER_LENGTH + 1)
            + "\n---\n\n# Title\n\nDetails.\n",
        ]
        home = (self.docs / "index.html").read_text()
        for text in invalid:
            with self.subTest(text=text):
                (self.notes / "v.0.9.0.md").write_text(text)
                with self.assertRaises(ValueError):
                    self.generate()
                self.assertEqual((self.docs / "index.html").read_text(), home)

    def test_unsafe_html_is_escaped_and_links_cannot_leave_the_repository(self):
        self.write_note("0.9.0", "null", "## Changes\n\nSafe text with <script>alert(1)</script>.\n",
                        teaser='Summary with < 3 changes & "fixes".')
        self.generate()
        page = (self.docs / "releases/0.9.0/index.html").read_text()
        self.assertNotIn("<script>alert(1)</script>", page)
        home = (self.docs / "index.html").read_text()
        announcement = home.split(website.ANNOUNCEMENT_START, 1)[1].split(website.ANNOUNCEMENT_END, 1)[0]
        self.assertNotIn("<script>alert(1)</script>", announcement)
        self.assertIn("&lt; 3 changes &amp; &quot;fixes&quot;", announcement)
        archive = (self.docs / "releases/index.html").read_text()
        self.assertIn("&lt; 3 changes &amp; &quot;fixes&quot;", archive)
        self.write_note("0.9.0", "null", "Summary.\n\n[Outside](../../../secret.md).\n")
        with self.assertRaisesRegex(ValueError, "escapes the repository"):
            self.generate()

    def test_teaser_length_limit(self):
        path = self.write_note("0.9.0", "null", teaser="x" * MAX_TEASER_LENGTH)
        self.assertEqual(len(load_release_note(path).teaser), MAX_TEASER_LENGTH)
        self.write_note("0.9.0", "null", teaser="x" * (MAX_TEASER_LENGTH + 1))
        with self.assertRaisesRegex(ValueError, "160 characters"):
            load_release_note(path)

    def test_removed_release_cleans_only_generated_pages(self):
        self.generate()
        manual = self.docs / "releases/manual/index.html"
        manual.parent.mkdir()
        manual.write_text("Manually maintained page.")
        (self.notes / "v.0.8.1.md").unlink()
        self.assertEqual(self.generate(check=True), 1)
        self.generate()
        self.assertFalse((self.docs / "releases/0.8.1/index.html").exists())
        self.assertTrue(manual.exists())



if __name__ == "__main__":
    unittest.main()
