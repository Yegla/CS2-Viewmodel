# CS2 Viewmodel

![image](https://cdn.modrinth.com/data/VdgHXf0L/images/497e616b71186a6c6c85f31452a569fd96d6a120.png)

This features a working skinchanger (Gloves, Knives, Sleeves, AK-47) menu alongside an extensive config tab with tons of ways to customize your experience with this mod.

## Build from your CS2 installation

On Windows, install Java 25, Python with `numpy` and `Pillow`, and the [Source 2 Viewer CLI](https://github.com/ValveResourceFormat/ValveResourceFormat/blob/master/docs/guides/command-line.md) at `.tools/source2viewer/`. The export scripts default to `E:\SteamLibrary\steamapps\common\Counter-Strike Global Offensive`; pass `-GameDirectory` to use another location.

Assets are exported with [Source 2 Viewer](https://s2v.app) ([ValveResourceFormat](https://github.com/ValveResourceFormat/ValveResourceFormat)). See [attributions](ATTRIBUTIONS.md) for asset and tool credits.

```powershell
.\tools\export-cs2.ps1
.\tools\export-cs2-skins.ps1
.\tools\export-cs2-previews.ps1
.\tools\build-local.ps1
```
