"""Turns a page saved from a real FA account (in the git-ignored local/fixtures/) into a test page
that is safe to commit: structure, classes and numbers stay; people, their words and the real
submission ids (which would show whose art the account watches) don't.

  python tools/scrub_fixture.py local/fixtures/famobile-msg-submissions.html fixtures/msg-submissions.html

- every username / display name becomes artist1, artist2, … (the logged-in account becomes testuser)
- submission titles become "Submission <id>" and every text in descriptions, comments and notes
  becomes filler of a similar length
- scripts (except FA's js-submissionData, rewritten the same way), ads and tracking are removed
- the logout form, CSRF-like keys and the banner are neutralised
- submission ids become made-up ones (10000010, 10000020, …) in the same order, also in thumbnail
  and file URLs
- so do journal and comment ids, upload and avatar timestamps and the exact times in date tooltips
- keywords become keyword1, keyword2, …; theme and species become "All" / "Unspecified / Any";
  gallery sizes, picture sizes and the server time get fixed values. Any one of these, with the day
  the page was saved, is enough to find the real submission on FA.

Re-running it on a page in fixtures/ is safe: made-up values are left as they are.

Only the Python standard library is used.
"""
import json
import pathlib
import re
import sys

# The logged-in account's names, mapped to testuser / TestUser. Kept out of the repo, in
# local/own-names.json: {"<username>": "testuser", "<display name>": "TestUser"}.
_OWN = pathlib.Path(__file__).resolve().parent.parent / "local" / "own-names.json"
OWN_NAMES = json.loads(_OWN.read_text(encoding="utf-8")) if _OWN.exists() else {}
FILLER = ("lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut "
          "labore et dolore magna aliqua ut enim ad minim veniam quis nostrud exercitation").split()


def filler(text: str) -> str:
    """Words of the same count, so layout and line breaks stay realistic."""
    words = re.findall(r"\S+", text)
    if not words:
        return text
    lead = re.match(r"\s*", text).group(0)
    trail = re.search(r"\s*$", text).group(0)
    return lead + " ".join(FILLER[i % len(FILLER)] for i in range(len(words))) + trail


def scrub(html: str) -> str:
    # 1. People: URL names from links, display names from the places FA shows them.
    url_names = {m.lower() for m in re.findall(r'/user/([^/"?#]+)/', html)}
    url_names |= {m.lower() for m in re.findall(r'/(?:gallery|scraps|favorites|journals|newpm|watchlist/by|watchlist/to)/([^/"?#]+)/', html)}
    url_names |= {m.lower() for m in re.findall(r'"lower":"([^"]+)"', html)}
    display = set(re.findall(r'class="(?:js-displayName|c-usernameBlockSimple__displayName)"[^>]*>([^<]+)<', html))
    display |= set(re.findall(r'<i>by</i> <a href="/user/[^"]+" title="([^"]+)"', html))
    display |= set(re.findall(r'"username":"([^"]+)"', html))
    display |= set(re.findall(r'class="loggedin_user_avatar[^"]*"[^>]*alt="([^"]+)"', html))
    display |= set(re.findall(r'<img class="loggedin_user_avatar[^>]*alt="([^"]+)"', html))
    mapping: dict[str, str] = {}
    for name in sorted(url_names):
        mapping[name] = OWN_NAMES.get(name) or f"artist{len([v for v in mapping.values() if v.startswith('artist')]) + 1}"
    for name in sorted({d.strip() for d in display if d.strip()}):
        key = name.lower()
        if key in mapping or key in OWN_NAMES:
            continue
        squashed = re.sub(r"[^a-z0-9]", "", key)
        match = next((v for k, v in mapping.items() if re.sub(r"[^a-z0-9]", "", k) == squashed), None)
        mapping[key] = match or OWN_NAMES.get(key) or f"Artist{len(mapping) + 1}"
    for own, repl in OWN_NAMES.items():
        mapping.setdefault(own, repl)

    # 2. Scripts: keep only the submission data, rewritten.
    def keep_script(m: re.Match) -> str:
        tag = m.group(0)
        if 'id="js-submissionData"' not in tag:
            return ""
        body = re.search(r">(.*)</script>", tag, re.S).group(1)
        try:
            data = json.loads(body)
        except ValueError:
            return ""
        for sid, entry in data.items():
            entry["title"] = f"Submission {sid}"
            entry["description"] = filler(entry.get("description", ""))
        return '<script id="js-submissionData" type="application/json">' + json.dumps(data) + "</script>"

    html = re.sub(r"<script\b.*?</script>", keep_script, html, flags=re.S)
    html = re.sub(r"<noscript\b.*?</noscript>", "", html, flags=re.S)
    html = re.sub(r"<ins\b[^>]*>\s*</ins>", "", html)
    html = re.sub(r"<iframe\b.*?</iframe>", "", html, flags=re.S)
    html = re.sub(r"<link\b[^>]*>", "", html)
    html = re.sub(r'<meta\b[^>]*(?:property="og:|name="twitter:|name="description"|name="keywords")[^>]*>', "", html)
    html = re.sub(r"<site-banner\b.*?</site-banner>", "<site-banner></site-banner>", html, flags=re.S)
    html = re.sub(r'<form class="post-btn logout-link".*?</form>', "", html, flags=re.S)
    html = re.sub(r"key=[A-Za-z0-9]+", "key=0000000000", html)
    html = re.sub(r'(name="f" value=")[^"]*', r"\g<1>0", html)

    # 3. Words: titles, descriptions, comments, note texts.
    html = re.sub(r'(<a href="/view/(\d+)/" title=")[^"]*(">)[^<]*(</a>)', r"\1Submission \2\3Submission \2\4", html)
    html = re.sub(r'(<img[^>]*id="submissionImg"[^>]*\balt=")[^"]*', r"\1Submission", html)
    html = re.sub(r'(\b(?:alt|title)=")[^"]*(")(?=[^>]*src="//t\.furaffinity\.net)', r"\1Submission\2", html)
    html = re.sub(r"(<title>).*?( -- Fur Affinity)", r"\1Page\2", html, flags=re.S)

    def fill_region(m: re.Match) -> str:
        return re.sub(r">([^<]+)<", lambda t: ">" + filler(t.group(1)) + "<", m.group(0))

    for region in [
        r'<div class="submission-description-text.*?</div>\s*</div>\s*</section>',
        r"<comment-user-text\b.*?</comment-user-text>",
        r"<comment-title\b.*?</comment-title>",
        r'<div class="section-body user-submitted-links">.*?</div>',
        r'<div class="note-text.*?</div>',
        r'<div class="user-profile.*?</div>',
    ]:
        html = re.sub(region, fill_region, html, flags=re.S)

    # The page's own title, after the filler so it stays readable ("Submission <id>", like the grids).
    page_id = re.search(r'/(?:fav|unfav|full)/(\d+)/', html) or re.search(r'data-preview-src="[^"]*?/(\d+)@', html)
    title = f"Submission {page_id.group(1)}" if page_id else "Submission title"
    # The artist's own user title, next to their name.
    html = re.sub(
        r'(<div class="submission-description-artist">.*?</a></span>\s*</span>\s*<span>)([^<]*)(</span>)',
        lambda m: m.group(1) + filler(m.group(2)) + m.group(3), html, count=1, flags=re.S)
    html = re.sub(r'(<div class="submission-title">\s*<h2>\s*<p>)[^<]*', lambda m: m.group(1) + title, html)
    html = re.sub(r'(<div class="submission-title">\s*<h2>)\s*([^<]+)', lambda m: m.group(1) + title, html)

    # Art file names carry the artist's own wording: keep the timestamp, "thumbnail" and the extensions.
    def file_name(m: re.Match) -> str:
        parts = m.group(2).split(".")
        exts = []
        while len(parts) > 1 and parts[-1].lower() in {"jpg", "jpeg", "png", "gif", "webp", "docx", "doc", "pdf", "txt",
                                                       "rtf", "odt", "mp3", "wav", "ogg", "m4a", "swf", "mp4"}:
            exts.insert(0, parts.pop())
        keep = [p for p in parts if p.isdigit() or p == "thumbnail"]
        return m.group(1) + ".".join(keep + ["submission"] + exts) + '"'

    html = re.sub(r'(//d\.furaffinity\.net/[^"]*?/\d+/)([^"/]+)"', file_name, html)

    # 4. Names last, so every place they appear (links, file paths, tags, text) is covered.
    for name in sorted(mapping, key=len, reverse=True):
        repl = mapping[name]
        html = re.sub(rf"(?<![A-Za-z0-9_]){re.escape(name)}(?![A-Za-z0-9_])", repl, html, flags=re.I)
    # Display names keep FA's leading/trailing spaces in titles; normalise the obvious leftovers.
    html = re.sub(r"u_[a-z0-9_.-]+", lambda m: m.group(0) if m.group(0)[2:] in mapping.values() else "u_artist", html)
    return fake_traces(fake_ids(html))


def fake_ids(html: str) -> str:
    """Real submission ids would tell whose art the account watches: made-up ones, same order."""
    found = set(re.findall(r"(?:/view/|/full/|sid-|data-id=\"|t\.furaffinity\.net/)(\d{5,})", html))
    fake = {old: str(10000000 + 10 * (n + 1)) for n, old in enumerate(sorted(found, key=int))}
    if not fake:
        return html
    return re.sub(r"(?<!\d)(" + "|".join(map(re.escape, fake)) + r")(?!\d)", lambda m: fake[m.group(1)], html)


def _made_up(number: str) -> bool:
    """Values this script or a hand-written page chose: 1700000000-ish, or with a run of zeros."""
    return "0000" in number or number.startswith("1700000")


def _remap(html: str, found: set[str], make) -> str:
    fake = {old: make(n) for n, old in enumerate(sorted(found, key=int))}
    if not fake:
        return html
    return re.sub(r"(?<!\d)(" + "|".join(map(re.escape, fake)) + r")(?!\d)", lambda m: fake[m.group(1)], html)


def _system_tag(tag: str) -> str:
    """Theme and species narrow a day's uploads down to a handful: generic ones instead."""
    return "t_all" if tag.startswith("t_") else "s_unspecified_any" if tag.startswith("s_") else tag


def fake_traces(html: str) -> str:
    """What still leads back to real submissions once names and ids are gone."""
    # Journal and comment ids: a real journal id opens a real journal, and so names its author.
    journals = {i for i in re.findall(r'(?:/journal/|journals\[\]" value=")(\d{5,})', html) if not _made_up(i)}
    html = _remap(html, journals, lambda n: str(11100001 + n))
    comments = {i for i in re.findall(r'(?:cid:|replyto/journal/|name="replyto" value="|data-comment-id=")(\d{5,})', html)
                if not _made_up(i)}
    html = _remap(html, comments, lambda n: str(60000101 + n))
    # Unix times (uploads in file and thumbnail URLs, avatar changes, data-time): made-up ones in the
    # same order, so a "posted" time still matches its file name.
    times = {t for t in re.findall(r"(?<!\d)(1[6-9]\d{8})(?!\d)", html) if int(t) % 1000 and not _made_up(t)}
    html = _remap(html, times, lambda n: str(1791010000 + 10000 * n))
    html = re.sub(r"(a\.furaffinity\.net/)(\d{8})/", lambda m: m.group(1) + (m.group(2) if _made_up(m.group(2)) else "1700000004") + "/", html)
    html = re.sub(r'(class="popup_date"[^>]*\btitle=")([A-Z][a-z]+ \d{1,2}, \d{4})[^"]*', r"\g<1>\2", html)
    # Keywords: the artist's own words. Kept in order, as keyword1, keyword2, …
    words: dict[str, str] = {}

    def word(w: str) -> str:
        if re.match(r"^[ucts]_", w):
            return _system_tag(w)
        if re.fullmatch(r"keyword\d+", w):
            return w
        return words.setdefault(w.lower(), f"keyword{len(words) + 1}")

    html = re.sub(r'data-tags="([^"]*)"', lambda m: 'data-tags="' + " ".join(word(w) for w in m.group(1).split()) + '"', html)
    html = re.sub(r'(/search/@keywords )([^"<]+)(")([^>]*>)([^<]*)(<)',
                  lambda m: m.group(1) + word(m.group(2)) + m.group(3) + m.group(4) + word(m.group(5)) + m.group(6), html)
    html = re.sub(r'(data-tag-name="|<span class="tag-invalid">)([^"<]+)', lambda m: m.group(1) + word(m.group(2)), html)
    # The submission's theme and species in its details box (the select lists of a form stay).
    html = re.sub(r'(<div class="info text">.*?</section>)', lambda m: re.sub(
        r"(<span[^>]*>\s*(?:Theme|Species)\s*</span>\s*<span>)[^<]*",
        lambda s: s.group(1) + ("All" if "Theme" in s.group(1) else "Unspecified / Any"), m.group(1), flags=re.S), html, flags=re.S)
    # Sizes that fit one real upload or gallery only.
    html = re.sub(r"<i>\d+ submissions", "<i>120 submissions", html)
    html = re.sub(r"<span>\d+ x \d+</span>", "<span>2000 x 1600</span>", html)
    html = re.sub(r"<span>\d+(?:\.\d+)? [kKM]B</span>", "<span>400.0 kB</span>", html)
    html = re.sub(r"(Server Time: )[^<]*", r"\1Oct 1, 2026 12:00 PM", html)
    return html


def main() -> None:
    src, dst = sys.argv[1], sys.argv[2]
    html = open(src, encoding="utf-8").read()
    if re.search(r"Test page scrubbed by tools/scrub_fixture\.py|<!-- Hand-built test page", html[:600]):
        # A page in fixtures/ already: only the later passes, so its made-up names stay as they are.
        open(dst, "w", encoding="utf-8", newline="\n").write(fake_traces(html))
        print(f"{dst}: scrubbed again")
        return
    out = scrub(html)
    note = f"<!-- Test page scrubbed by tools/scrub_fixture.py from a page saved on FA; names and texts are replaced. -->\n"
    out = re.sub(r"(<!DOCTYPE html>\s*)", lambda m: m.group(1) + note, out, count=1, flags=re.I) if "<!DOCTYPE" in out[:200].upper() else note + out
    open(dst, "w", encoding="utf-8", newline="\n").write(out)
    print(f"{dst}: {len(html)} -> {len(out)} bytes")


if __name__ == "__main__":
    main()
