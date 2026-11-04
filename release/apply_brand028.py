"""Use the existing Inter Flash artwork for every TV launcher alias."""
from pathlib import Path
import re, shutil, hashlib

root = Path("InterFlashTV-OwnTV/app/src/main")
art = Path("app/src/main/res/drawable-nodpi/inter_flash_logo_round.webp")
assert hashlib.sha256(art.read_bytes()).hexdigest() == "929f9734ced33c7157cfebbe09d64ada405ecffcb09dd1bf5aa3efd1d03244f9"
res = root / "res/drawable-nodpi"
res.mkdir(parents=True, exist_ok=True)
shutil.copyfile(art, res / "interflash_brand.webp")
drawable = root / "res/drawable"
drawable.mkdir(exist_ok=True)
(drawable / "interflash_tv_banner.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item><shape android:shape="rectangle"><size android:width="320dp" android:height="180dp"/><solid android:color="#101C32"/></shape></item>
    <item><inset android:insetLeft="25%" android:insetRight="25%" android:insetTop="5.5556%" android:insetBottom="5.5556%">
        <bitmap android:src="@drawable/interflash_brand" android:gravity="fill" android:filter="true"/>
    </inset></item>
</layer-list>
''')
manifest = root / "AndroidManifest.xml"
text = manifest.read_text()
text, banners = re.subn(r'android:banner="@drawable/owntv_banner_[^"]+"', 'android:banner="@drawable/interflash_tv_banner"', text)
text, icons = re.subn(r'android:(icon|roundIcon)="@mipmap/owntv_icon_[^"]+"', r'android:\1="@drawable/interflash_brand"', text)
assert banners == 10 and icons == 20, (banners, icons)
manifest.write_text(text)
print("All launcher aliases use the original Inter Flash artwork.")
