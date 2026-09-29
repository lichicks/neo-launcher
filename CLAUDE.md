# Neo Launcher — kontext projektu

> Tenhle soubor si Claude Code načte automaticky při otevření projektu.
> Obsahuje všechno, co je potřeba vědět, aby se navázalo tam, kde se skončilo.
> Stručné shrnutí pro nový chat (i bez přístupu k repu) je v `docs/POKRACOVANI.md` –
> při větší změně stavu ho aktualizuj taky.

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

**Dialogy (nastavení, rychlé menu, menu karty, aktualizace)** mají od
2026-09-27 styl skla z visionOS podle dvou předloh od uživatele (smart-home
dashboardy): světle šedé matné sklo s bílým okrajem a světlou horní hranou,
uvnitř světlejší dlaždice, ikony v kulatém skle, pilulky (vybraná
= bílá s tmavým textem), přepínač bílý s tmavou tečkou, velké bílé nadpisy.
Barvy jsou v `Glass` (`GLASS_TOP/BOTTOM`, `TILE`, `INK`). Hlavní panel má
volbu „Styl skla“: Tmavé (preview, výchozí) / Světlé (světlejší šedé).

**Mezery a zaoblení (od 2026-09-27, uživateli vadily „náhodné“ mezery):**
všechno bere tokeny z `Glass` — vnitřní okraj panelu `PAD` 20 (ze všech
stran stejný), nadpis → obsah `SECTION` 20, mezi dlaždicemi `GAP` 12
(vodorovně i svisle), malé prvky vedle sebe `GAP_S` 8, text v dlaždici
`TILE_PAD` 16, hlavní tlačítko `BUTTON_H` 48. Rohy jsou **soustředné**
(vnitřní radius = vnější − odsazení): dlaždice `R_TILE` 20, panel
`R_PANEL` = 20 + 20 = 40, náhled v dlaždici odsazený `INSET` 8 má `R_INNER`
12, kolečko ikony v kapsli má stejný okraj ze všech stran (hledání: kapsle
52, kolečko 40, okraj 6). Ornament: položky 40 v bublině 52 (okraj 6),
oddělovač má z obou stran stejnou mezeru. Nová okna stavět jen z těchto
tokenů, žádná „od oka“ čísla. Rolovací plochy mají `Glass.fadeEdges`
(obsah se u okraje rozplyne, uživateli vadila ostrá „zakousnutá“ hrana).
Hodiny v rychlém menu mají nahoře víc místa (`HEADER_TOP` 30) — u rohu
s radiusem 40 působily namačkaně.

**Barvy, sklo a ikony (od 2026-09-27, uživatel: „dobrý design = barevná
paleta a ikonky“):**
- **Paleta** `ui/Palette.java` (vybral uživatel z dsgn.house): Void Black
  `#06070A`, Electric Cobalt `#3D5AFE` (akcent: NOVÉ, pokrok, výběr),
  Synth Magenta `#FF2FA3` (upozornění, baterie ≤ 20 %), Toxic Amber
  `#FF8A1E` (baterie ≤ 50 %, hvězda oblíbených), Titanium Fog `#AEB6C2`
  (vedlejší text, světlé sklo), Holographic Pearl `#F5F7FF` (text, vybraná
  pilulka, hlavní tlačítko – s jemným přechodem do fialova/modra). Žádné
  jiné barvy „od oka“. Vedlejší text přes `Palette.text2()/text3()` (na
  světlém skle je perlový, šedá by zanikla).
- **Sklo** = jeden recept `ui/GlassSurface.java` pro panel, ornament,
  lištu, rychlé menu, dialogy (`GlassDrawable`) i dlazdice: průhledná
  tónovaná výplň (nahoře světlejší), měkký odlesk vlevo nahoře, **okraj
  s přechodem** (nahoře jasný, po stranách slábne, dole jemný odraz –
  jednobarevný okraj působí jako plast). Za dialogem `ModalDepth`: blur
  10 dp + sytost 1,3 (barvy her prosvítají sklem, žádné šedé „zakalení“).
- **Hover = sklo, ne neon:** dlaždice se zesvětlí, zjasní okraj a pod
  laserem je měkké bílé světlo (rychlé menu). **Žádná barevná záře**
  (uživateli vadila modrá záře u Wi-Fi). Vybraná záložka = světlejší
  skleněná kapsle.
- **Ikony** = sada **Lucide** (ISC, `THIRD_PARTY_NOTICES.md`):
  `tools/icons/gen_icons.py <lucide/icons>` vygeneruje `ui/IconPaths.java`
  (SVG cesty 24×24), `ui/SvgPath.java` je parsuje (včetně oblouků),
  `Icons.draw(...)` kreslí tahem 2/24 se zaoblenými konci. Novou ikonu
  přidat do seznamu `ICONS` v generátoru a znovu vygenerovat (Lucide:
  `git clone --depth 1 --filter=blob:none --sparse
  https://github.com/lucide-icons/lucide.git` + `sparse-checkout set icons`).
  Logo Neo je kreslené zvlášť.
- Velké nadpisy (Nastavení, hodiny v rychlém menu) mají nahoře
  `Glass.TITLE_TOP` 30 (u rohu R 40 jinak působí namačkaně).

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
| `update/Updater.java`, `InstallReceiver.java` | Aktualizace z GitHub Releases: najde nejnovější build, stáhne, nainstaluje přes PackageInstaller |
| `ui/NeoLauncherView.java` | Sklo, horní lišta (logo, záložky, hodiny, baterie), mřížka, hover pop-out, hloubka ostrosti, gumové rolování, přesouvání |
| `ui/CarouselView.java` | Testovací karusel (5× klepnout na logo): karty na šířku ve vějíři, hloubka ostrosti do stran, štítky se statistikami |
| `ui/QuickMenuView.java` | Rychlé menu (náhrada systémového menu Questu): jas, hlasitost, Wi-Fi, baterie, úložiště, dlaždice funkcí Questu |
| `ui/Spring.java` | Fyzikální pružina (jako SwiftUI spring) — při změně cíle drží rychlost; čistě z času |
| `ui/ModalDepth.java` | Launcher za otevřeným dialogem plynule ustoupí (rozmazání + zmenšení) |
| `ui/PeepholeAnimation.java`, `ui/LaunchLens.java` | Animace spuštění „kukátko“ (sdílí mřížka i karusel) + AGSL čočka |
| `data/UsageInfo.java` | Herní čas a poslední spuštění z UsageStats (jako plugin Playtime v LL) |
| `ui/Eased.java` | Animovaná hodnota jako CSS transition (retarget z aktuální hodnoty, bez callbacků) — už jen pro prolínání a hover lišty |
| `ui/DepthBlur.java` | Radiální rozmazání: AGSL shader (Android 13+), jinak obyčejný blur |
| `ui/ShadowSprite.java` | Předpočítané rozmazané stíny karet (box-shadow) |
| `ui/Glass.java`, `OverlayHost.java`, `SettingsSheet.java`, `AppMenu.java`, `UpdateSheet.java` | Dialogy ve stylu skla visionOS (normální Android Views). `OverlayHost` je nechá „vyrůst“ na pružině z místa, odkud se otevřely. Nastavení = dashboard s dlaždicemi |
| `ui/GlassWidgets.java`, `ui/Cascade.java` | Přepínač, posuvník a ikona v kulatém skle, postupný nástup dlaždic |
| `ui/Palette.java`, `ui/GlassSurface.java`, `ui/GlassDrawable.java` | Barevná paleta, jeden recept na sklo (Canvas i Drawable) |
| `ui/Icons.java`, `ui/IconPaths.java`, `ui/SvgPath.java` | Ikony Lucide: vygenerované SVG cesty + parser (viz „Barvy, sklo a ikony“) |

### Rozložení okna (od 2026-09-27, podle předloh z visionOS od uživatele)
- **Skleněný panel** jen s mřížkou karet (všechny rohy zaoblené).
- **Ornament nahoře** — plovoucí „bublina“ se záložkami (Hry / Aplikace /
  Vše), časem (bez data – to je v rychlém menu, přání uživatele) a baterií,
  **napůl zanořená do horní hrany panelu** (střed na `frame.top`).
  Klepnutí na čas/baterii = rychlé menu.
- **Levá lišta** (jako tab bar ve visionOS) — logo Neo a ikony Knihovna
  (klepnutí odroluje nahoru), Hledat, Karusel, Rychlé menu, Nastavení. Po najetí
  laserem se na pružině rozbalí a ukáže popisky. Uživatel chtěl viditelné
  tlačítko nastavení („průměrný uživatel neví, že má klepnout na logo“).
- **Rychlé menu** vyjede jako boční panel vpravo přes celou výšku panelu
  (`OverlayHost.showSide`): nahoře hodiny + celé datum, vpravo v řadě
  pilulky Wi-Fi a baterie, pak karta „Naposledy hráno“, posuvníky, dlaždice
  (vyplní zbytek výšky) a dole tlačítko „Nastavení Nea“ — mezi vším `GAP`.
- Okno 1176×664 dp: vlevo 88 dp na lištu, nahoře 36 dp (půlka ornamentu),
  vpravo 24 / dole 30 dp průhledný okraj pro „vyskočení“ karty (vypínatelné).

### Vrstvy kreslení (odspodu)
1. Sklo panelu (výplň s nastavitelným krytím, okraj, světlá horní hrana)
2. `contentNode` (RenderNode): všechny karty kromě hovernuté, oříznuté
   panelem se zaoblenými rohy. **Jeden** `RenderEffect` hloubky ostrosti na
   celou vrstvu.
3. `backdropNode`: kopie karet, které zajely pod ornament, rozmazaná 24 dp =
   matné sklo ornamentu (jen část uvnitř panelu)
4. Levá lišta a ornament (sklo, stín, obsah)
5. Hovernutá karta — mimo ořez, může přesahovat panel
6. Tažená karta

## Klíčová omezení (naučeno tvrdě)

1. **Žádný blur na jednotlivých kartách.** Snapdragon XR2 Gen 2 to neutáhne
   (předchozí pokus shodil headset). V2.0: jeden efekt na celou vrstvu
   (`contentNode`), stíny jsou předpočítané bitmapy, žádné `saveLayer` na kartu.
2. **Nikdy nespoléhat na `withEndAction()` / konec animace pro stav.**
   V2.0: `Eased` i `Spring` se vždy dopočítají z času (pružina v uzavřeném
   tvaru, žádné kroky po snímcích), stav se nikde nenastavuje v callbacku.
   Zavírání dialogu mění stav hned; animace je jen „duch“ bez dotyků.
3. **Žádný per-holder stav.** V2.0: není RecyclerView; stav karty (`Card`) je
   klíčovaný balíčkem a přežije i obnovu seznamu.
4. **Fokus = jeden zdroj pravdy.** `updateFocus()` se volá při každé události
   ukazatele a každém snímku během rolování. Při ztrátě fokusu okna
   (`onWindowFocusChanged`) tvrdý reset. Pojistka: bez jakékoliv události
   ukazatele 6 s → reset (kdyby Quest nepostal HOVER_EXIT).
5. **HOVER_EXIT chodí i těsně před stiskem spouště** → reset hoveru se
   odkládá o 90 ms, jinak by karta při kliknutí blikla.
7. **AGSL: zápis `content.eval(p)`** (Android 13+). Kompiluje se přes
   `ui/Agsl.compile()`, který při chybě zkusí starý zápis `sample(content, p)`
   — ten zná jen starší Skia v Robolectricu. Náhled tak ověří, že shader je
   jinak platný; Robolectric ale neumí `RenderEffect.createRuntimeShaderEffect`
   (UnsatisfiedLinkError), takže **vizuál shaderů (hloubka ostrosti, čočka
   kukátka) je vidět až na headsetu** — v náhledu jede záložní cesta.
6. **3D náklon karty jen přes `RenderNode.setRotationX/Y`**, ne přes vlastní
   perspektivní matici z `android.graphics.Camera` + `canvas.concat()`. Ta se
   po odrolování vůbec nevykreslila (karta pod laserem zmizela) — odhaleno
   Robolectric snímkem 2026-09-27. Nakloněná karta má vlastní `Card.node`;
   ten smí být v jednom snímku jen v jednom rodiči, proto kopie pod lištou
   (backdrop) kreslí karty vždy naplocho.

## Stav — co je hotové (v2.0)

**2026-09-29 první test na headsetu: „funguje až podivuhodně super“.**
Zpětná vazba z testu (vyřešeno ve v2.0.4x): Meta tlačítko Neo neotevíralo
(→ vestavěná služba `MetaButtonService`, viz níže), karusel ukazoval
„Spuštěno 0×“ vedle hodin hraní (počítá jen spuštění z Nea → štítek pryč,
datum instalace taky, nápověda dole = volba „Nápověda v karuselu“, výchozí
vypnuto). Přání: silnější barevné světlo hry na skle (`drawGameLight`),
nástup karet při otevření (`playIntro`). Hudba v rychlém menu: spíš ne
(jen kdyby šla ukázat jen když něco hraje). Připomínka pauzy: ne.
Detaily níže psané před testem nemusí všechny sedět – ověřuj s uživatelem.

- Mřížka 4 sloupce (nastavitelné 3–6), karty 1.6:1, radius 16, title pill
- Hover: zvětšení 1.18 × perspektiva (translateZ 32 px v perspective 900 px),
  náklon ±6° za kurzorem, 380 ms `cubic-bezier(0.16,1,0.3,1)`, bílý okraj,
  velký stín + záře, karta nad lištou a mimo panel
- Hloubka ostrosti: radiální blur 2.2 → 4.4 dp (Jemná) / 2× (Silná) / vypnuto
  (pak jen ztmavení okolí)
- Gumové rolování: lerp 0.16, rubber band 0.38, squish max 4.5 % (konstanty
  z preview), setrvačnost po tažení, thumbstick (ACTION_SCROLL)
- Ornament: záložky Hry / Aplikace / Vše, hodiny, baterie (barvy
  dle %, 1 blesk = nabíjení, 2 = rychlé > 7.5 W). Logo Neo je v levé liště
  (klik = nastavení, 5× = karusel).
- Přesouvání: podržet kartu 450 ms → zvedne se → táhnout (auto-rolování
  u okraje) → pustit. Pořadí se ukládá per záložka.
- Menu karty: držet kartu **bez pohybu 1 s** (nastavitelné 0,7 / 1 / 1,5 s,
  pod kartou běží modrý ukazatel). Pustit dřív = nic, karta jen dosedne.
- Menu karty: spustit, přejmenovat, vlastní obrázek, stáhnout obrázek znovu,
  skrýt, info, odinstalovat
- Animace spuštění "kukátko" (1,15 s), fáze: **stisk** (karta se zmenší na
  90 %, 130 ms) → **kukátko** (pružný návrat karty, tma se stáhne do kruhu,
  rámeček/název karty zmizí, rybí oko, 320 ms) → **průlet** (kruh se roztáhne,
  banner se přiblíží přes celé okno, radiální "zoom blur" do stran, 400 ms)
  → **setmění do černa** a zavření launcheru (nebo rozplynutí zpět, když je
  zavírání vypnuté). Easingy navazují bez skoků. Aplikace se spouští v 48 %.
  Kód je v `ui/PeepholeAnimation.java`. Čočka (rybí oko + měkký okraj) je
  JEDEN AGSL efekt na vrstvu animace (`ui/LaunchLens.java`); bez shaderu
  záložní kreslení (kruhový ořez). Stav odvozený jen z času, respektuje
  systémové měřítko animací (0 = bez animace), vypínatelná v nastavení.
  Omezení: 2D panel nemůže kreslit mimo své okno — "přiblížení k očím" se
  odehrává v okně (včetně průhledného okraje), panel sám se k hráči nepohne.
  Rozmazání do stran při průletu jsou vrstvené zvětšené kopie banneru (jede
  i bez shaderu, je vidět i v náhledu).
- Po spuštění se launcher zavře (`finish()` na konci animace, zapnuté
  v nastavení "Po spuštění zavřít launcher"): poslední fáze animace ztmaví do
  černa, takže pod ní launcher neprosvítá. Zavře se jen když se aplikace
  opravdu spustila; kdyby se okno nezavřelo, po 2,5 s se launcher zase ukáže.
- Prémiový vzhled: záře hovernuté karty v barvě obrázku hry (průměrná barva
  banneru vytažená do syta — `ArtworkLoader.ambientColor`), jemná modrá záře
  okolo skla (jen v průhledném okraji, `setShadowLayer` + `clipOutPath`),
  ploché ostré stíny pod kartami, pilulkami, záložkami a stavem, modrá záře
  indikátoru záložky, puls tečky loga při klepnutí.
- **Fyzika a prostorová návaznost (jako iOS/visionOS)** — hover, náklon,
  indikátor záložek, přeskládání karet, zvednutí tažené karty a karusel jedou
  na pružinách (`Spring`: response + tlumení, hover lehce překmitne, rychlé
  změny cíle navazují bez cuknutí, fling v karuselu předá rychlost laseru).
  Dialogy vyrůstají z místa, odkud se otevřely (nastavení z loga, menu karty
  z karty, rychlé menu z hodin) a launcher za nimi ustoupí do hloubky.
- **Karusel (testovací režim)** — 5× rychle klepnout na logo Neo (jedno
  klepnutí otevře nastavení se zpožděním 380 ms), nebo přepínač v nastavení.
  Úplně průhledný, jen karty **na šířku** (bannery jako v mřížce, uživatel
  2026-09-27 chtěl šířku místo výšky) ve „vějíři“: prostřední velká a ostrá,
  boční menší, přes sebe a natočené 18° k hráči, **čím dál, tím rozmazanější**
  (jeden `DepthBlur` na vrstvu bočních karet; v náhledu Robolectricu jen
  rovnoměrný blur). Dokola od 5 položek, pořadí "3 / 13" v zářezu karty,
  záře v barvě hry pod prostřední kartou, pod ní název, typ a štítky: herní
  čas (UsageStats — potřebuje "Přístup k využití", štítek "Herní čas –
  povolit" otevře nastavení), naposledy spuštěno, počet spuštění z Nea
  (počítá se od v2.0.25), datum instalace. Ovládání: joystick ←/→
  (ACTION_SCROLL, drží-li se, opakuje se), klepnutí na boční kartu ji přinese
  doprostřed, klepnutí na prostřední spustí (kukátko), tažení laserem roluje
  se setrvačností, podržení otevře menu karty. Šipka vlevo nahoře = zpět.
  Velké karty chtějí větší bannery → `ArtworkLoader.requestTargetSize`
  bere největší požadavek (karusel žádá jen když je vidět).
- **Rychlé menu** (vlastní obdoba systémového menu Questu, cíl: časem plně
  nahradit výchozí launcher) — klepnutí na hodiny/baterii (mřížka i karusel)
  nebo tlačítko menu na ovladači (`KEYCODE_MENU`, pokud ho Quest do aplikace
  pošle). Hodiny + datum, baterie, Wi-Fi (signál z `NetworkCapabilities`),
  posuvník **jasu** (`Settings.System.SCREEN_BRIGHTNESS` 10–255, potřebuje
  "Úprava systémových nastavení" = `WRITE_SETTINGS`, stejně jako QuestDim;
  bez povolení posuvník nabídne povolení) a **hlasitosti** (`AudioManager`,
  bez oprávnění), joystick nad posuvníkem = ±5 %. Dlaždice: Wi-Fi, Bluetooth
  (Android nastavení, záložně Nastavení Questu), Nastavení Questu, Menu Questu
  (`systemux://quick_settings` — systémové rychlé nastavení na vše ostatní:
  průchod, sdílení…), Soubory, Prohlížeč, Fotoaparát, Neo (nastavení).
  Úložiště je v nastavení (widget). Addon Meta tlačítka zůstává beze změny.
- Nastavení jako dashboard (visionOS): nadpis, kategorie pilulkami (Vzhled,
  Aplikace, Quest, Aktualizace, O Neo), dlaždice s ikonou a přepínačem
  (klepnutí kamkoliv na dlaždici přepne = velký cíl pro laser) nebo volbami,
  vpravo widgety (datum, čas, Neo + aktualizace, počet her/aplikací
  a úložiště) a „Hotovo“. Dlaždice nastoupí kaskádou. Obsah: hloubka
  ostrosti, styl a krytí skla, sloupce, kukátko, zavření po spuštění, přesah
  karet, karusel, řazení, prodleva menu karty, obrázky z internetu, stáhnout
  obrázky znovu, herní čas (povolení), skryté aplikace, rozmazání prostředí,
  Meta tlačítko, Nastavení Questu, ovládání jasu (povolení), aktualizace
  a testovací verze, o aplikaci
- **Pohodlí (QoL, 2026-09-27 podle přání uživatele):**
  - „Co je nového“ po aktualizaci (jednou; `WhatsNewSheet`, text z
    `assets/novinky.txt`; jde otevřít i v Nastavení → Aktualizace).
  - Pamatuje si pozici rolování mřížky pro každou záložku (i po návratu ze
    hry, kdy se launcher zavřel) a vybranou kartu karuselu (`Prefs`).
  - Oblíbené: hvězdička v menu karty, oblíbené jsou vždy nahoře (i ve
    vlastním pořadí), na kartě zlatá hvězda vpravo nahoře.
  - Řazení: Vlastní / Abecedně / Naposledy / **Nejhranější** (herní čas
    z UsageStats) / **Chytré** (nové první, pak herní čas, pak naposledy).
  - Štítek **NOVÉ** (instalace < 14 dní, ještě nespuštěná – ani z knihovny
    Questu podle UsageStats; `AppEntry.installTime`).
  - V rychlém menu karta „Naposledy hráno“ s tlačítkem Hrát.
  - Slabá baterie (≤ 20 %, nenabíjí) svítí v ornamentu červeně (8 s pulzuje).
  - Hledání (lupa v levé liště, klávesnice Questu, bez ohledu na diakritiku,
    Enter spustí první výsledek) – `SearchSheet`.
  - V ornamentu ikonka posuvníků = nápověda, že čas/baterie otevírá rychlé menu.
  - Sklo dialogů se řídí „Stylem skla“ (tmavé jako launcher / světlé visionOS),
    pozadí za dialogem jen lehce rozmazané (uživateli vadilo „zakalení“).
  - Uživatel NEchce: testovací funkce (bezpečný režim, diagnostika, FPS),
    „vrátit zpět“ po skrytí, vlastní kategorie, weby jako karty, denní
    přehled herního času. Zvuky/haptika a počasí možná později.
- Aktualizace přímo v launcheru: CI po každém pushi vytvoří GitHub Release
  `v2.0.<číslo běhu>` (mimo `main` jako prerelease = testovací). Launcher při
  otevření (max 1× za 6 h) nebo tlačítkem v nastavení zkontroluje
  `api.github.com/repos/lichicks/neo-launcher/releases`, ukáže poznámky
  (zpráva commitu) a po potvrzení nainstaluje. `versionCode = 2000 + run_number`.
  Funguje jen s **veřejným** repem (GitHub bez přihlášení vrací u soukromého
  404, launcher pak hlásí "repozitář je soukromý"). Uživatel se 2026-09-27
  rozhodl repo zveřejnit (přepíná sám v GitHub Settings). Záložní varianta:
  APK do zvláštního veřejného repa `lichicks/neo-launcher-releases`
  (launcher ho zkouší jako druhý zdroj; CI by potřebovalo token v secrets).
  Změny jen v `.md`/`docs/` build ani release nespouští. Poznámky k releasu =
  první sekce `app/src/main/assets/novinky.txt`.
- Průhledné okno + `com.oculus.vrshell.supports_blend_effects` (Quest 3/3S
  rozmaže prostředí za panelem)
- Pevný podpisový klíč `keystore/neo-debug.keystore` → nové buildy jdou
  instalovat přes staré (`adb install -r`) bez ztráty nastavení

## Co otestovat na headsetu jako první

0. **Meta tlačítko (v2.0.4x):** zapnout službu „Neo – Meta tlačítko“,
   v Přístupnosti vypnout službu Lightning Launcheru. Doma Meta → Neo?
   Znovu Meta → menu Questu? Ve hře Meta → menu Questu (ne Neo)? Po
   ukončení hry se otevře Neo – a neotevře se při pouhé pauze? Při
   problému `adb -P 5038 logcat -s NeoMeta`.

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
8. Kukátko: zavře se launcher hned po spuštění hry (bez probliknutí mřížky)?
9. Karusel: 5× klepnout na logo. Směr joysticku (doprava = další karta; když
   obráceně → prohodit znaménko v `CarouselView.onGenericMotionEvent`),
   rychlost opakování (`STICK_*`). Je vidět postupné rozmazání do stran
   (AGSL, `NeoDepthBlur` v logcatu)? Natočení bočních karet (vnější hrana
   k hráči; obráceně → znaménko `sl.ry` v `drawCards`). Otevře štítek
   "Herní čas – povolit" nastavení přístupu k využití? Záložně z PC:
   `adb -P 5038 shell appops set com.neolauncher.v1 GET_USAGE_STATS allow`.
10. Záře: je vidět barevná záře hovernuté karty a modrá záře okolo skla?
    Není moc? (alfa v `drawCardBody` / `drawGlass`)
11. Rychlé menu: otevře se klepnutím na hodiny? Tlačítkem menu na levém
    ovladači? Mění posuvník jas Questu (po povolení "Úprava systémových
    nastavení"; záložně `adb -P 5038 shell appops set com.neolauncher.v1
    WRITE_SETTINGS allow`)? Hlasitost? Co otevřou dlaždice?
12. Pružiny: nepůsobí hover/záložky moc "gumově"? (`HOVER_SPRING` atd.
    nahoře v `NeoLauncherView`, tlumení 1 = bez překmitu)
13. Nastavení (dashboard) a rychlé menu ve světlém skle: je text dobře
    čitelný na Questu? Případně ztmavit `Glass.GLASS_TOP/BOTTOM`. Zkusit
    „Styl skla: visionOS“ pro hlavní panel.
14. Nové rozložení: je ornament nahoře a lišta vlevo dobře vidět i proti
    prostředí (kus leží mimo panel)? Rozbalí se lišta po najetí? Není
    průhledná část okna kolem panelu vidět jako rozmazaný obdélník
    (systémové rozmazání prostředí)? Velikost okna je 1176×664 dp.

## Meta tlačítko — vestavěná služba `MetaButtonService` (od 2026-09-29)

Meta tlačítko do aplikací nechodí a přepsat natvrdo nejde (a uživatel se
bál, že by se pak nedostal do nastavení Questu). Řešení jako v Lightning
Launcheru, ale **přímo v Neu** (žádný zvláštní addon): služba přístupnosti
`MetaButtonService` (`res/xml/meta_button_service.xml`), uživatel ji zapne
jednou v Nastavení → Quest → „Meta tlačítko otevře Neo“ → Zapnout
(otevře `Settings.ACTION_ACCESSIBILITY_SETTINGS`).

- **Jak LL pozná Meta tlačítko** (`threethan/LightningLauncher`,
  `RedirectServices/.../ShortcutAccessibilityService.java`, varianta
  *navigator*): jen `com.oculus.systemux`, `TYPE_WINDOW_STATE_CHANGED`
  s textem přesně `[Library]` nebo `[Navigator]` (lokalizace v
  `src/navigator/res/values-*/target_name.xml`). Proto uživateli fungoval,
  jen když se Navigator otevřel na záložce aplikací.
- **Neo navíc:** Navigator na jiné záložce pozná podle tlačítka Knihovna
  uvnitř okna (prohledá max. 400 uzlů, `LIBRARY_LABELS`).
- **Pravidla:** doma Meta = Neo; když je Neo vidět (`fgPkg` = Neo a
  `LauncherActivity.isVisible()`), další stisk = opravdové menu Questu;
  ve VR hře menu Questu (volba „Ve hře menu Questu“, `Prefs.metaGameMenu`);
  po skončení hry (hra byla v popředí > 4 s a objeví se
  `com.oculus.vrshell`/`com.oculus.shellenv`) se otevře Neo (volba „Po
  hře otevřít Neo“, `Prefs.metaAfterGame`). Když Neo samo otevírá systém
  Questu (rychlé menu, `AppLauncher` panely), `MetaButtonService.suppress()`
  na 4 s. Respektuje i `Prefs.allowShortcuts`.
- **Vše heuristika z událostí oken, NEOVĚŘENO na headsetu.** Loguje:
  `adb -P 5038 logcat -s NeoMeta` (popředí, Navigator, rozhodnutí). Když
  „po hře“ otevře Neo i při pauze ve hře, je to tím, že vrshell pošle
  událost i při otevření menu → vypnout volbu nebo zpřísnit `onHome`.
- Pokud má uživatel zapnutou i službu Lightning Launcheru, otevřou se po
  stisku oba launchery → v Přístupnosti nechat jen „Neo – Meta tlačítko“.
- `ShortcutStateProvider` zůstává kvůli starému addonu (kompatibilita).

## Co zbývá / nápady

- Zvuková odezva při hoveru a spuštění
- Vlastní tapeta / pozadí
- Hledání aplikací
- Paralaxa celé mřížky podle ukazatele (víc 3D)
- **Lišta běžící aplikace (rozhodnuto 2026-09-27; staví na
  `MetaButtonService`, která už ví, co je v popředí):** dole na panelu pilulka ve stylu ornamentu (napůl zanořená
  do spodní hrany, `frame.bottom`), když na pozadí běží hra/aplikace:
  náhled + skutečný název (Quest u sideloadů píše „Neznámá aplikace“, my
  máme label z Androidu), tlačítka **Pokračovat** (znovu spustit = návrat
  do běžící hry) a **Ukončit**. Obyčejná aplikace to na Androidu 14 neumí
  (`killBackgroundProcesses` smí jen vlastní procesy, běžící cizí procesy
  nejsou vidět, UsageStats řekne jen „naposledy na obrazovce“) → detekci
  a ukončení dělá addon (služba přístupnosti): čte systémové menu Questu
  (lišta s běžící aplikací) a klepne na jeho „Ukončit“; záložně Informace
  o aplikaci → „Vynutit ukončení“. Uživatel NEchtěl jednodušší verzi
  s odhadem ze statistik dřív (mohla by ukázat už zavřenou hru).
- Rozložení jako v Lightning Launcheru: hry velké karty, aplikace malá
  kolečka dole (uživatel zvažuje místo záložek)
- Karusel: podle zpětné vazby z headsetu buď vylepšit (paralaxa, zvuk,
  tapeta), nebo zahodit — je to testovací režim
- Rychlé menu: když Meta tlačítko (addon přenesený do repa) pozná, že ho
  otevřel uživatel, otevřít rovnou rychlé menu; ovladače (baterie) nejdou
  z běžné aplikace přečíst
- Češtinu přesunout do `strings.xml` (teď natvrdo v kódu — Quest češtinu
  jako systémový jazyk nemá, takže `values-cs` by se stejně nepoužilo)

## Náhled bez headsetu (skutečný snímek kódu)

`tools/screenshot/run.sh` spustí skutečný `NeoLauncherView` v Robolectricu
(emulace Androidu na JVM) s nativní grafikou a HW vykreslováním — RenderNode,
RenderEffect i AGSL shader jedou přes opravdovou Skia/HWUI. Stáhne opravdové
bannery her, vyfotí klidový stav, stav s kartou pod "laserem" po odrolování,
6 fází animace spuštění (6× zpomalené), karusel (klid + pohyb), rychlé menu,
světlé sklo, nastavení, hledání, „Co je nového“ a menu karty, a složí to s ilustračním pozadím. Potřebuje jen JDK, Maven a přístup na Maven
Central + GitHub (funguje i v cloudové session). Trvá ~3 min, poprvé stáhne
~350 MB. **Používat po každé změně kreslení** — takhle se našla chyba v bodě 6.
Pozadí a systémové rozmazání prostředí jsou jen ilustrace; skutečný vzhled
průhlednosti na Questu je potřeba ověřit na headsetu.

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

První instalace jakýmkoliv způsobem sideloadu (adb, SideQuest, Meta Quest
Developer Hub, nebo stáhnout APK v prohlížeči Questu a otevřít v Souborech —
vše vyžaduje vývojářský režim). Další verze už přes aktualizace v launcheru.

```bash
# na PC uživatele běží starý adb server HTC Sync Manageru na 5037 -> vždy -P 5038
adb -P 5038 install -r NeoLauncher-2.0-xxxxxxx.apk
```
Pokud je na headsetu ještě starý fork `com.neolauncher.v1` (jiný podpis),
nejdřív `adb -P 5038 uninstall com.neolauncher.v1`. (Na konci minulé session
byl odinstalovaný.)

## Poznámky pro práci na projektu

- **Novinky: při každé změně, kterou uživatel uvidí, přidej nahoru sekci do
  `app/src/main/assets/novinky.txt`** (`# Nadpis`, pod tím body `• …`, lidsky
  česky s diakritikou – jak píšeš uživateli, ne technicky). Launcher ji ukáže
  po aktualizaci v okně „Co je nového“ a CI ji dá do poznámek k releasu
  (okno aktualizace). Starší sekce nech pod ní.

- **Žádné cizí značky v textech pro uživatele** (UI, `novinky.txt`, poznámky
  k releasu): nepsat „visionOS“, „iOS“, „iPhone“, „Apple“ apod. Inspirace je
  jasná, ale uživatel to tam psát nechce. (Interní poznámky v kódu a tady
  v CLAUDE.md nevadí.)

- **Uživatel mluví česky** — komentáře v kódu i komunikace česky.
  V kódu komentáře bez diakritiky, texty v UI a `.md` s diakritikou.
- **Testovat jde jen na reálném headsetu.** Uživatel je jediný, kdo vidí
  výsledek. Menší dávky změn → chyba jde jednoznačně přiřadit.
- Předchozí pokusy (Gemini, Antigravity) selhaly, protože přidaly moc funkcí
  naráz. V2.0 je výjimka (přepis od nuly) — proto je výše checklist.
- Ladicí konstanty jsou nahoře v `NeoLauncherView` (rozměry, časy, fyzika).

## Licence

Odvozeno z Lightning Launcher (threethan), **GPL-3.0**. Viz `LICENSE`.
