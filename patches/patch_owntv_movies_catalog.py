#!/usr/bin/env python3
"""Change the existing OwnTV Movies category browser label to «Ver catálogo».

The built-in category browser already reads real movie counts from railCounts.
This patch does not touch the player, sources, channels, Series or credentials.
Apply to audited OwnTV v5.2.2; abort if the target has changed.
"""
from pathlib import Path
import sys

def patch(root: Path) -> None:
    movie = root / "app/src/main/java/tv/own/owntv/features/movies/MoviesScreen.kt"
    english = root / "app/src/main/res/values/strings.xml"
    spanish = root / "app/src/main/res/values-es/strings.xml"
    source = movie.read_text(encoding="utf-8")
    before = "StageTool(text = stringResource(R.string.content_category_browser_title), icon = OwnTVIcon.LIST, onClick = { categoriesOpen = true })"
    after = "StageTool(text = stringResource(R.string.interflash_view_catalog), icon = OwnTVIcon.LIST, onClick = { categoriesOpen = true })"
    if source.count(before) != 1:
        raise SystemExit("Versión de OwnTV diferente: no se modifica nada.")
    if "vodCategoryEntries(railItems, railCounts, playlistMarks, series = false)" not in source:
        raise SystemExit("No hay recuentos de categorías: no se modifica nada.")
    en = english.read_text(encoding="utf-8")
    if en.count("</resources>") != 1 or 'name="interflash_view_catalog"' in en:
        raise SystemExit("Recurso de idioma inesperado.")
    es = spanish.read_text(encoding="utf-8") if spanish.exists() else None
    if es is not None and (es.count("</resources>") != 1 or 'name="interflash_view_catalog"' in es):
        raise SystemExit("El archivo español no coincide.")
    movie.write_text(source.replace(before, after), encoding="utf-8")
    english.write_text(en.replace("</resources>", '    <string name="interflash_view_catalog">View catalogue</string>\n</resources>'), encoding="utf-8")
    spanish.parent.mkdir(parents=True, exist_ok=True)
    if es is None:
        spanish.write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <string name="interflash_view_catalog">Ver catálogo</string>\n</resources>\n', encoding="utf-8")
    else:
        spanish.write_text(es.replace("</resources>", '    <string name="interflash_view_catalog">Ver catálogo</string>\n</resources>'), encoding="utf-8")
    print("Películas → Ver catálogo: aplicado. Las categorías y recuentos son los originales de OwnTV.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Uso: python3 patch_owntv_movies_catalog.py /ruta/OwnTV")
    patch(Path(sys.argv[1]))
