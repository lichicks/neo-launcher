# Neo Launcher — kontext projektu

> Tenhle soubor si Claude Code načte automaticky při otevření projektu.
> Obsahuje všechno, co je potřeba vědět, aby se navázalo tam, kde se skončilo.

## Co to je

Vlastní launcher pro **Meta Quest 3S**. Od verze **2.0 (2026-09-27)** je to
**samostatný projekt napsaný od nuly** ve složce `app/` — už to není patch nad
[Lightning Launcherem](https://github.com/threethan/LightningLauncher).
Z Lightning Launcheru (threethan, GPL-3.0) je převzatá jen logika, která tam
fungovala dobře: rozpoznání typu aplikací, seznam vyloučených balíčků,
spouštění přes vrshell a online zdroje bannerů.

Package: `com.neolauncher.v1` (stejné jako starý fork — addon pro Meta
tlačítko ho zná a otevírá prioritně). Uživatel má na headsetu i původní
Lightning Launcher jako zálohu, oba jdou nainstalovat vedle sebe.

Starý fork (v1.x) se dá pořád postavit ručně přes
`.github/workflows/legacy-fork-build.yml` — hlavně kvůli addonu
**RedirectServices** (Meta tlačítko), který se staví jen tam.

## Zlatý standard vzhledu

**`docs/preview_neo.html`** — interaktivní HTML prototyp. Uživatel ho
schválil větou "přesně takhle to chci v tom Questu". Když je spor o to,
jak se má něco chovat, rozhoduje tenhle soubor. Rozměry v kódu (dp) jsou
1:1 CSS pixely z preview.

`docs/HISTORIE_A_VIZE_NEO_LAUNCHER.txt` zmiňovaný v dřívějších verzích
tohohle souboru v repu není (zůstal u uživatele na PC).

## Architektura v2.0

Celý launcher je **jeden vlastní `View`** (`ui/NeoLauncherView.java`), žádný
RecyclerView. Důvod: ve forku hover závisel na `ACTION_HOVER_ENTER/EXIT`
jednotlivých karet a ty Quest při přejíždění mezi sousedními kartami
neposílá spolehlivě (bug 1b z minulé session). Teď se karta pod ukazatelem
počítá z pozice ukazatele při každém pohybu i každém snímku → jeden zdroj
pravdy (`focused`), žádné callbacky, žádná recyklace.

| Soubor | Co dělá |
|---|---|
| `NeoApp.java` | Drží jediné instance `Prefs`, `AppRepository`, `ArtworkLoader` |
| `LauncherActivity.java` | Jediná aktivita: skládá `NeoLauncherView` + `OverlayHost`, přijímače (baterie, čas, balíčky), výběr vlastního obrázku |
| `ShortcutStateProvider.java` | Odpovídá addonu Meta tlačítka (`isOpen`, `shouldBlur`, `allowShortcuts`) |
| `data/AppRepository.java` | Seznam aplikací (cache `apps.json` → okamžitý start, pak sken na pozadí), typ VR/2D/panel, filtrování a řazení záložek |
| `data/Prefs.java` | Všechna nastavení + ruční pořadí, skryté aplikace, přejmenování, naposledy spuštěné |
| `data/Platform.java` | Quest? Verze Horizon OS, podpora blend efektů / chain launch |
| `art/ArtworkLoader.java` | Bannery: vlastní → cache na disku → TV banner → náhradní (ikona na rozmazaném pozadí) + stažení online. Bitmapy jsou předem oříznuté na přesnou velikost karty |
| `launch/AppLauncher.java` | Spouštění (vrshell broadcast na v71+, panely, 2D), info o aplikaci, odinstalace |
| `ui/NeoLauncherView.java` | Sklo, horní lišta (logo, záložky, hodiny, baterie), mřížka, hover pop-out, hloubka ostrosti, gumové rolování, přesouvání |
| `ui/Eased.java` | Animovaná hodnota jako CSS transition (retarget z aktuální hodnoty, bez callbacků) |
| `ui/DepthBlur.java` | Radiální rozmazání: AGSL shader (Android 13+), jinak obyčejný blur |
| `ui/ShadowSprite.java` | Předpočítané rozmazané stíny karet (box-shadow) |
| `ui/Glass.java`, `OverlayHost.java`, `SettingsSheet.java`, `AppMenu.java` | Dialogy ve stylu skla (normální Android Views) |

### Vrstvy kreslení (odspodu)
1. Sklo panelu (výplň s nastavitelným krytím, okraj, světlá horní hrana)
2. `contentNode` (RenderNode): všechny karty kromě hovernuté, oříznuté pod
   lištou se zaoblenými spodními rohy. **Jeden** `RenderEffect` hloubky
   ostrosti na celou vrstvu.
3. `backdropNode`: kopie karet, které zajely pod lištu, rozmazaná 32 dp =
   matné sklo lišty
4. Horní lišta (tint, okraj, stín dolů, logo, záložky, stav)
5. Hovernutá karta — mimo ořez, nad lištou, může přesahovat panel
6. Tažená karta

Okno je o **24 dp (bok) / 30 dp (nahoře a dole)** větší než skleněný panel —
v tom průhledném okraji má zvětšená karta kam "vyskočit". Vypíná se
v nastavení ("Karty vyskakují z panelu").

## Klíčová omezení (naučeno tvrdě)

1. **Žádný blur na jednotlivých kartách.** Snapdragon XR2 Gen 2 to neutáhne
   (předchozí pokus shodil headset). V2.0: jeden efekt na celou vrstvu
   (`contentNode`), stíny jsou předpočítané bitmapy, žádné `saveLayer` na kartu.
2. **Nikdy nespoléhat na `withEndAction()` / konec animace pro stav.**
   V2.0: `Eased` se vždy dopočítá z času, stav se nikde nenastavuje v callbacku.
3. **Žádný per-holder stav.** V2.0: není RecyclerView; stav karty (`Card`) je
   klíčovaný balíčkem a přežije i obnovu seznamu.
4. **Fokus = jeden zdroj pravdy.** `updateFocus()` se volá při každé události
   ukazatele a každém snímku během rolování. Při ztrátě fokusu okna
   (`onWindowFocusChanged`) tvrdý reset. Pojistka: bez jakékoliv události
   ukazatele 6 s → reset (kdyby Quest nepostal HOVER_EXIT).
5. **HOVER_EXIT chodí i těsně před stiskem spouště** → reset hoveru se
   odkládá o 90 ms, jinak by karta při kliknutí blikla.

## Stav — co je hotové (v2.0, NEOTESTOVÁNO na headsetu)

Vše níže je napsané a přeložené v CI, ale **ještě neběželo na Questu**.

- Mřížka 4 sloupce (nastavitelné 3–6), karty 1.6:1, radius 16, title pill
- Hover: zvětšení 1.18 × perspektiva (translateZ 32 px v perspective 900 px),
  náklon ±6° za kurzorem, 380 ms `cubic-bezier(0.16,1,0.3,1)`, bílý okraj,
  velký stín + záře, karta nad lištou a mimo panel
- Hloubka ostrosti: radiální blur 2.2 → 4.4 dp (Jemná) / 2× (Silná) / vypnuto
  (pak jen ztmavení okolí)
- Gumové rolování: lerp 0.16, rubber band 0.38, squish max 4.5 % (konstanty
  z preview), setrvačnost po tažení, thumbstick (ACTION_SCROLL)
- Horní lišta: logo Neo (klik = nastavení), záložky Hry / Aplikace / Vše,
  hodiny + datum, baterie (barvy dle %, 1 blesk = nabíjení, 2 = rychlé > 7.5 W)
- Přesouvání: podržet kartu 450 ms → zvedne se → táhnout (auto-rolování
  u okraje) → pustit. Pořadí se ukládá per záložka. Podržet a pustit bez
  pohybu = menu karty.
- Menu karty: spustit, přejmenovat, vlastní obrázek, stáhnout obrázek znovu,
  skrýt, info, odinstalovat
- Nastavení: hloubka ostrosti, krytí skla, sloupce, přesah karet, řazení
  (vlastní/abecedně/naposledy), skryté aplikace, znovu stáhnout obrázky,
  systémové rozmazání pozadí, Meta tlačítko
- Průhledné okno + `com.oculus.vrshell.supports_blend_effects` (Quest 3/3S
  rozmaže prostředí za panelem)
- Pevný podpisový klíč `keystore/neo-debug.keystore` → nové buildy jdou
  instalovat přes staré (`adb install -r`) bez ztráty nastavení

## Co otestovat na headsetu jako první

1. Hover: najet na kartu, pak **přímo** na sousední (to byl bug 1b). Zvětšení +
   náklon musí fungovat vždy.
2. Směr náklonu: strana pod kurzorem by se měla "zamáčknout" dozadu. Když je to
   obráceně → `TILT_SIGN = -1f` v `NeoLauncherView`.
3. Rychlost rolování thumbstickem → `WHEEL_GAIN` v `NeoLauncherView`.
4. Průhlednost: je vidět prostředí za panelem? Je rozmazané (blend effects)?
   Jak vypadá průhledný okraj okolo panelu (měl by být neviditelný)?
   Pokud je okraj vidět jako matné sklo, vypnout "Karty vyskakují z panelu".
5. Hloubka ostrosti — plynulost (fps) a jestli AGSL shader funguje. V logcatu
   tag `NeoDepthBlur` hlásí, když shader selže a jede se na záložní blur.
6. Spuštění VR hry i 2D aplikace, Nastavení Questu (`systemux://settings`).
7. Meta tlačítko přes addon (musí být nainstalovaný z legacy workflow).

## Co zbývá / nápady

- "Peephole" animace spuštění z preview (zatím jen záblesk karty)
- Zvuková odezva při hoveru a spuštění
- Vlastní tapeta / pozadí
- Hledání aplikací
- Paralaxa celé mřížky podle ukazatele (víc 3D)
- Addon RedirectServices přenést do tohoto repa (teď jen v legacy workflow)
- Češtinu přesunout do `strings.xml` (teď natvrdo v kódu — Quest češtinu
  jako systémový jazyk nemá, takže `values-cs` by se stejně nepoužilo)

## Build

APK staví **GitHub Actions** (`.github/workflows/build.yml`) při každém pushi
na jakoukoliv větev. Hotové APK: záložka Actions → konkrétní běh → Artifacts →
`NeoLauncher-APK`.

Lokálně (s Android SDK):
```bash
./gradlew assembleDebug
# výsledek: app/build/outputs/apk/debug/app-debug.apk
```
Potřeba: JDK 17+, Android SDK `platforms;android-36`, `build-tools;36.0.0`.
AGP 9.2.1 + Gradle 9.6.1 (stejné jako Lightning Launcher).

**V cloudové session Claude Code** je `dl.google.com` (a tedy Google Maven
s AGP) zablokovaný síťovou politikou prostředí → plný build tam nejde. Pro
rychlou kontrolu, že se Java přeloží: `tools/compile-check.sh` (přeloží
zdrojáky proti `android-all.jar` z Maven Central). Neověří resources ani
manifest a nehlídá API level — to dělá až CI.

## Instalace na Quest

```bash
# na PC uživatele běží starý adb server HTC Sync Manageru na 5037 -> vždy -P 5038
adb -P 5038 install -r NeoLauncher-2.0-xxxxxxx.apk
```
Pokud je na headsetu ještě starý fork `com.neolauncher.v1` (jiný podpis),
nejdřív `adb -P 5038 uninstall com.neolauncher.v1`. (Na konci minulé session
byl odinstalovaný.)

## Poznámky pro práci na projektu

- **Uživatel mluví česky** — komentáře v kódu i komunikace česky.
  V kódu komentáře bez diakritiky, texty v UI a `.md` s diakritikou.
- **Testovat jde jen na reálném headsetu.** Uživatel je jediný, kdo vidí
  výsledek. Menší dávky změn → chyba jde jednoznačně přiřadit.
- Předchozí pokusy (Gemini, Antigravity) selhaly, protože přidaly moc funkcí
  naráz. V2.0 je výjimka (přepis od nuly) — proto je výše checklist.
- Ladicí konstanty jsou nahoře v `NeoLauncherView` (rozměry, časy, fyzika).

## Licence

Odvozeno z Lightning Launcher (threethan), **GPL-3.0**. Viz `LICENSE`.
