#!/usr/bin/env python3
"""Minimal v0.56 Movies category opener patch on reconstructed v0.42 code.

Does not touch live TV, series, mpv, account pairing, playlists or playback.
Existing OwnTV LiveCategories already reads real per-category movie counts.
"""
from pathlib import Path
import sys

def patch(root: Path):
    movie = root / "app/src/main/java/tv/own/owntv/features/movies/MoviesScreen.kt"
    res_en = root / "app/src/main/res/values/strings.xml"
    res_es = root / "app/src/main/res/values-es/strings.xml"
    src = movie.read_text(encoding="utf-8")
    # Cinematic toolbar and client-catalogue toolbar each have an existing category opener.
    target = "stringResource(R.string.content_category_browser_title)"
    if src.count(target) < 3:
        raise SystemExit("Base source changed. Refusing to patch.")
    if src.count("vodCategoryEntries(railItems, railCounts, playlistMarks, series = false)") != 1:
        raise SystemExit("Category counts are not present. Refusing to patch.")
    before1 = "StageTool(text = stringResource(R.string.content_category_browser_title), icon = OwnTVIcon.LIST, onClick = { categoriesOpen = true })"
    before2 = "text = stringResource(R.string.content_category_browser_title), icon = OwnTVIcon.LIST,"
    if src.count(before1) != 1 or src.count(before2) != 1:
        raise SystemExit("The two catalog buttons do not match the audited source.")
    en = res_en.read_text(encoding="utf-8")
    es = res_es.read_text(encoding="utf-8")
    for text in (en, es):
        if text.count("</resources>") != 1 or 'name="interflash_view_catalog"' in text:
            raise SystemExit("Translation file not as expected.")
    movie.write_text(
        src.replace(before1, "StageTool(text = stringResource(R.string.interflash_view_catalog), icon = OwnTVIcon.LIST, onClick = { categoriesOpen = true })")
           .replace(before2, "text = stringResource(R.string.interflash_view_catalog), icon = OwnTVIcon.LIST,"),
        encoding="utf-8",
    )
    res_en.write_text(en.replace("</resources>", '    <string name="interflash_view_catalog">View catalogue</string>\n</resources>'), encoding="utf-8")
    res_es.write_text(es.replace("</resources>", '    <string name="interflash_view_catalog">Ver catálogo</string>\n</resources>'), encoding="utf-8")
    print("Parche v0.56: dos botones Películas -> Ver catálogo. Cantidades de OwnTV conservadas.")
    assert "onClick = { categoriesOpen = true }" in movie.read_text(encoding="utf-8")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: python patch_v056_movie_catalog.py InterFlashTV-OwnTV")
    patch(Path(sys.argv[1]))
