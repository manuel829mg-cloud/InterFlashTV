from pathlib import Path
p=Path('InterFlashTV-OwnTV/app/src/main/java/tv/own/owntv/features/shell/components/StageRail.kt')
s=p.read_text()
assert s.count('            RailBrand(open)')==1
s=s.replace('            RailBrand(open)\n','')
a=s.index('            if (nowPlaying != null) {')
b=s.index('            items.forEach { section ->',a)
s=s[:a]+s[b:]
p.write_text(s)
print('Removed sidebar brand and Now Playing shortcut; channel navigation preserved.')
