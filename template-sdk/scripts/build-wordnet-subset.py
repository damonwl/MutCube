#!/usr/bin/env python3
"""Build a small, verifiably sourced WordNet 3.0 glossary for original demo passages."""
import json
from pathlib import Path
import re
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
EXAMPLE = ROOT / "examples/english-reader"
CONTENT = EXAMPLE / "templates/english/content.js"
OUTPUT = EXAMPLE / "templates/english/wordnet.js"
NOTICE = EXAMPLE / "assets/WORDNET_LICENSE.txt"
SOURCE = "https://wordnetcode.princeton.edu/3.0/WordNet-3.0.tar.gz"


def main():
    # The content.js passages are original prose. Extract only words that occur in them.
    prose = CONTENT.read_text(encoding="utf-8")
    words = {word.lower() for word in re.findall(r"[A-Za-z]+(?:'[A-Za-z]+)?", prose)}
    # WordNet's first senses for grammatical words are often unrelated acronyms or elements.
    words -= set("a an the and or but in on at by for from of to as is are was were be been being it its this that these those with who which they them their you your we our can may will would should not than every one another each all more most some even does do did has have had when where how what why".split())
    with urllib.request.urlopen(SOURCE, timeout=60) as response:
        with tarfile.open(fileobj=response, mode="r|gz") as archive:
            files = {}
            for member in archive:
                name = Path(member.name).name
                if name in {"index.noun", "index.verb", "index.adj", "index.adv", "data.noun", "data.verb", "data.adj", "data.adv", "LICENSE"}:
                    files[name] = archive.extractfile(member).read().decode("utf-8", "replace")
    glossary = {}
    for suffix in ("noun", "verb", "adj", "adv"):
        synsets = {}
        for line in files[f"data.{suffix}"].splitlines():
            if not line or line.startswith("  ") or " | " not in line:
                continue
            offset, _, gloss = line.partition(" | ")
            synsets[offset.split()[0]] = gloss.split("; \"")[0].strip()
        for line in files[f"index.{suffix}"].splitlines():
            if not line or line.startswith("  "):
                continue
            fields = line.split()
            lemma = fields[0].replace("_", " ").lower()
            if lemma not in words:
                continue
            count = int(fields[2]); pointer_count = int(fields[3])
            for offset in fields[6 + pointer_count:6 + pointer_count + min(count, 3)]:
                gloss = synsets.get(offset)
                if gloss:
                    meanings = glossary.setdefault(lemma, [])
                    if gloss not in meanings and len(meanings) < 5:
                        meanings.append(gloss)
    OUTPUT.write_text("// Derived from WordNet 3.0; see assets/WORDNET_LICENSE.txt.\nexport const wordnet = " +
                      json.dumps(glossary, ensure_ascii=False, indent=2) + ";\n", encoding="utf-8")
    NOTICE.parent.mkdir(parents=True, exist_ok=True)
    NOTICE.write_text(files["LICENSE"], encoding="utf-8")
    print(f"WordNet 3.0: {len(glossary)} definitions from {SOURCE}")


if __name__ == "__main__":
    main()
