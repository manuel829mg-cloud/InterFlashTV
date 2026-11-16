#!/usr/bin/env python3
"""Add an unobtrusive 'Ver catálogo' control to the cinematic MOVIES screen only.
The provider categories, their actual counts, movie rows, Series and Live TV are unchanged.
Run after patch_v056_movie_catalog.py on recovered exact 0.55 source.
"""
from pathlib import Path
import sys

def patch(root: Path) -> None:
    file = root / "app/src/main/java/tv/own/owntv/features/movies/MoviesScreen.kt"
    src = file.read_text(encoding="utf-8")
    anchor = """                    }
                }
            }
        } else {
            // Separate panels: the category column, the titles, the details card."""
    if src.count(anchor) != 1:
        raise SystemExit("Movie UI structure changed; refusing to patch")
    if src.count("val cinemaRows = !tv.own.owntv.features.shell.InterFlashClientMode.administrator") != 1:
        raise SystemExit("Cinematic movie rows missing")
    if src.count('R.string.interflash_view_catalog') != 2:
        raise SystemExit("Apply patch_v056_movie_catalog.py before this step")
    replacement = """                    }
                }
                // Movie catalogue belongs ONLY to the Movies screen, including the
                // client cinematic rows view. This is a shortcut to the existing
                // category sheet, with real category counts from MovieViewModel.
                // The Series, Live TV, player and card layout are not modified.
                if (cinemaRows && !categoriesOpen) {
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 18.mpx, end = 64.mpx)) {
                        StageTool(
                            text = stringResource(R.string.interflash_view_catalog),
                            icon = OwnTVIcon.LIST,
                            onClick = { categoriesOpen = true },
                        )
                    }
                }
            }
        } else {
            // Separate panels: the category column, the titles, the details card."""
    src = src.replace(anchor, replacement)
    assert src.count('R.string.interflash_view_catalog') == 3
    assert "onClick = { categoriesOpen = true }," in src
    file.write_text(src, encoding="utf-8")
    print("OK: Movie-only cinematic category button, native Android, client mode.")
if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: python3 patch_v057_movies_only.py InterFlashTV-OwnTV")
    patch(Path(sys.argv[1]))
