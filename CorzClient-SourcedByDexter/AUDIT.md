# Corz Client 2.0.1 - decompiled source audit

Generated from the extracted `CorzClient-SourcedByDexter` tree. Build toolchain used:
Temurin JDK 25 (Loom 1.18.4 refuses JDK 21), Gradle 9.7.1 wrapper, Minecraft 1.21.11 / Yarn 1.21.11+build.4.

## 1. Verified healthy (the decompile did not lose anything)

| Check | Result |
|---|---|
| `./gradlew compileJava` | PASS - 0 errors (only deprecation/unchecked notes) |
| `./gradlew build` -> `corz-client-2.0.1.jar` | PASS - 25.8 MB, 3,265 entries, 2,699 classes, 529 assets |
| `fabric.mod.json`, `corz-client.mixins.json`, `corz-client-refmap.json` in jar | all present (refmap 66 KB, fully generated) |
| Decompiler failure markers ("Couldn't be decompiled", `goto`, `var10000`, stub throws) | none anywhere |
| Modules on disk vs. registered in `ModuleRegistry` | 145 / 145, none missing, none orphaned |
| Mixin classes on disk vs. listed in `corz-client.mixins.json` | 99 / 99, exact match both directions |
| Module event entry points (`onGameTick`, `dispatchRender2D/3D`, packet send/receive, camera, mouse, keyboard, item use...) | all 12 are actually called from mixins / the entrypoint |
| Special-case modules (Fullbright, KeepSprint, NoFlame) | intentional 9-line markers, consumed by their mixins |

So: nothing is "broken" at the class/syntax level. Whatever is misbehaving is logic or the hand-written auth layer.

## 2. The whole `ai/auth` package is a hand-written stub (license bypass / "crack")

`ai/auth` contains 13 files. Only `Module.java` and `ModuleRegistry.java` contain real code (the module base class and the registry were moved into this package). The other 11 are hollow:

| Member | State | Effect |
|---|---|---|
| `LicenseRuntime.isActivated/isLicenseValid/isPremiumUnlocked/hasFeature` | always `true` | license gate off |
| `LicenseRuntime.getAuthToken()` | `null` | `ConfigSyncUtils.isSignedIn()` is always false; `JsonRetained` never sends `Authorization`; community config upload/download cannot authenticate |
| `LicenseRuntime.getClientVersion()` | literal `"XenntSquad"` | License panel shows `Version XenntSquad`; every exported config is stamped `clientVersion: XenntSquad` instead of 2.0.1 |
| `LicenseRuntime.getLicenseOwnerLabel()` | literal `"SourcedByDexter"` | shown in the License button supplier |
| `getLicenseStatus/getStatusMessage/getVersionLabel/getLicenseFilePath/loadLicenseFile/getVerifyContext/getLicenseFile` | `null` | License screen has no status/owner/expiry data; activation, refresh, sync, poll, activateLicense are empty |
| `LaunchIntegrity.isIntegrityOk()/isValidPath()` | `true` | launch/signature integrity never checked |
| `LicenseStatus.transform()` / `buildValuesArray()` | return `null` | any consumer gets null (enum is a shell) |
| `DeviceIdentity.*` (getDeviceId, readMachineId, readOsFingerprint, sanitize, ...) | `null` / empty | `CorzLicense` hardcodes the label `"Dexter Owns"` instead of using the device id |

Consequence: every cloud / license / account / config-sharing path is dead by design, while local modules are unaffected.
That is the first cluster of "modules that aren't really running" (Configs sharing, community configs, License).

## 3. ModuleIdentities can never resolve a name

`ModuleIdentities.getProperty(Class)` does `PROPERTIES.getProperty(clazz.getName(), clazz.getSimpleName())`.
`META-INF/corz-module-ids.properties` has 630 keys, all of the form `gg.corz.IIIIIIIllIIIIIIl=KnockbackDelay$Plan`.
Classes in this tree were renamed to `com.corz.client.*`, so **every one of those 630 lookups misses** and the fallback (raw class name) is used.

* GUI/config display names become `AnchorMacro` instead of `Anchor Macro`.
* All 630 original identities (inner classes/enums such as `KnockbackDelay$Plan`, `TpaFlow$Input`, `AutoBuilder$15`, `SellChestFlow$Action$Kind`) are unreachable.
* Config keys still work by luck: `AutoRiggerUtils.formatText("AnchorMacro")` and `...("Anchor Macro")` both produce `anchor_macro`.

## 4. 78 of 99 mixins inject with intermediary (obfuscated) member names

Examples: `MixinMinecraftClient` -> `method = {"method_1583()V"}`, `MixinCamera` -> `"method_19321(Lnet/minecraft/class_1937;Lnet/minecraft/class_1297;ZZF)V"`, `AccessorPendingUpdate` -> `@Accessor("field_37957")`.
This is the residue of decompiling a production jar. The refmap carries them, so **the packaged jar works in a normal (intermediary) game runtime**, but in a development runtime (`gradlew runClient`, Yarn names) those injections do not resolve.

`AccessorItemCooldownEntry` (`comp_3083/comp_3084`) and `AccessorPendingUpdate.field_37957` produced build warnings because the annotation processor could not map names that are already intermediary. I checked them against the real 1.21.11 intermediary jar: `class_1796$class_1797` is a record with `comp_3083/comp_3084`, and `class_7202$class_7203` has `field_37957`, so both accessors are valid at runtime. Not a bug.

Practical impact: 52 modules are mixin-backed. In a dev run they are inert or error out; in the built jar they are fine.

## 5. AnchorMacro - concrete defects

`com/corz/client/categories/combat/AnchorMacro.java`

1. **[FIXED]** **False "server refused the charge"** (`tryChargeAnchor` -> `trackAnchorState(pos, 0)` -> `checkChargeRefusal`).
   After a successful charge it records `lastChargeLevel = 0`. On the next tick the "unchanged" guard (`lastChargeLevel > 0 && ...`) is false, so `checkChargeRefusal` runs and `recordRefusal` increments. Two ticks before the server's block update arrives, the refusal limit (2) is hit and the macro stops clicking that anchor until you fully release right-click, printing "The server refused the anchor charge 2 times". On anything above ~2 ticks of latency this makes the macro look broken/random.
2. **[FIXED]** **`switchDelay` was shared** between the glowstone switch and the totem switch, so one counter served two different swaps.
3. **[FIXED]** **Delay counters never reset when the target anchor changes**: `clearTrackedAnchor()` left `glowstoneDelayCounter` / `explodeDelayCounter` / the switch counters untouched, so a half-elapsed delay followed you to the next anchor.
4. **[FIXED]** **`mc.options.useKey.setPressed(false)` every tick** while an anchor was under the crosshair. Vanilla's use key was force-released while the module was on; if the module was disabled mid-hold the key stayed released until the physical button was re-pressed. Now the release/re-press only happens on a *transition* (`updateVanillaUseSuppression` / `clearVanillaUseSuppression`), and the key state is handed back to vanilla (mirroring the physical button) as soon as the macro stops controlling - crosshair leaves the anchor, pearl sequence ends, module disabled, or you stop holding.
5. **[FIXED]** `isRightMouseHeld()` polled GLFW button 1 directly, ignoring rebinds. It now uses `CodeCategoryUtils.isKeyPressed(mc.options.useKey)`, which resolves the *currently bound* key through the `AccessorKeyBinding` mixin (`boundKey`, verified against the 1.21.11 yarn mappings) and polls the matching GLFW mouse button or key, so rebinding "Use Item/Place Block" to another mouse button or a keyboard key works.

None of these come from decompilation damage - the decompiled body is bytecode-faithful. They are the module's own behaviour, which is why it "works but not perfectly".

Fix as applied (still items 4 and 5 open):

- **1** - a successful charge now marks the anchor as *pending* (`isChargePending`, line 89: `lastChargeLevel == 0 && chargeLevel == 0`). While pending, `tickAnchorInteraction` (237-243) waits up to `CHARGE_CONFIRM_TICKS` (20 ticks, line 63) for the server's block update instead of re-clicking and counting a refusal; only after the grace period does the click count as a refusal. It also stops the macro from re-clicking (and double-charging) while the update is in flight.
- **2** - the totem swap uses its own `totemSwitchDelayCounter` (61, 171-176); the glowstone swap keeps `switchDelayCounter`, both driven by the same "Switch Delay" setting.
- **3** - `clearTrackedAnchor()` (201-210) resets `awaitingChargeTicks`, `glowstoneDelayCounter`, `explodeDelayCounter` and both switch counters, so releasing right-click or moving to another anchor starts every delay clean. `resetAll()` resets the new fields too.

## 6. Modules whose logic is disconnected (compiled, never called)

Private methods that are never referenced anywhere in the tree:

| Module | Dead private methods |
|---|---|
| AutoBuilder | 22 - `computeUnpackSettingKey`, `formatPlainMultiface`, `isSelectedMaterialItem`, `logLayerStats`, `formatPosChain`, `formatShortKind`, `checkPairWithDefaultTime`, `formatFacingForSlot`, `requestInternalSessionReset`, `getHitVecDefault`, `onCellConfirmed`, `breakNearbyTemporaryBlock`, `placeScaffoldingBase`, `getSquaredAxisHitDefault`, `getPositionPairKey`, `hasAdjacentUnplacedSpecialBlock`, `formatTicksAsSeconds`, `describeHitSurface`, `formatUnknownFluid`, `getPistonStickyFamily`, `openCrafterPresetsScreen`, `getStandPositionHash` |
| ChunkFinder | 6 - `renderClusterLabel`, `rebuildClusters`, `formatTopBlockCounts`, `formatTopExcavationTypes`, `renderClusterOutline`, `updateChunkScanResult` |
| AutoMine | 1 - `mineBlockAhead` |
| AutoElytraFinder | 1 - `containsElytra` |
| AutoElytraPilot | 1 - `formatCoordinates` |
| BlockOverlay | empty `onEnable()` + `onDisable()` |

ChunkFinder's cluster rendering/scan-refresh helpers being unreachable is notable: the cluster view cannot be driven. AutoBuilder has an entire unpack/cell/hit-surface layer with no caller.

## 7. Per-module table

`tick` = overrides the per-tick entry point (named `onReset` in this source), `hooks` = other overridden module events, `settings` = declared settings, `mixin` = module class is referenced from a mixin, `dead` = unreachable private methods.

| Module | Category | LOC | tick | hooks | settings | mixin | dead |
|---|---|---|---|---|---|---|---|
| AnchorHunter | bots | 2192 | yes | onCameraAngle, onDisable, onEnable, onPacketReceive, onPlayerScaffold, onRender2D, onRender3D, onReset | 11 | yes | - |
| AutoAreaMiner | bots | 573 | yes | onDisable, onEnable, onRender3D, onReset | 21 | yes | - |
| AutoAuctionFlipper | bots | 1470 | yes | onDisable, onEnable, onReset | 31 | yes | - |
| AutoBuilder | bots | 58646 | yes | onCameraAngle, onConfigLoaded, onDisable, onEnable, onMultiSessionMessage, onPacketReceive, onPacketSend, onRender2D, onRender3D, onReset, onSwordBot | 149 | yes | 22 |
| AutoElytraFinder | bots | 5179 | yes | onCameraAngle, onDisable, onEnable, onRender2D, onReset | 9 | yes | 1 |
| AutoElytraPilot | bots | 2810 | yes | onCameraAngle, onDisable, onEnable, onPacketReceive, onRender2D, onRender3D, onReset | 13 | - | 1 |
| AutoTpaHere | bots | 403 | yes | onDisable, onEnable, onPacketReceive, onRender2D, onReset | 7 | yes | - |
| CrystalPvpBot | bots | 207 | yes | onButtonClick, onCameraAngle, onDisable, onEnable, onPacketReceive, onPacketSend, onPlayerScaffold, onRender3D, onReset, onSwordBot, onZoomHud | 11 | yes | - |
| RtpBaseAlert | bots | 362 | yes | onDisable, onEnable, onReset | 10 | - | - |
| SpawnerProtect | bots | 559 | yes | onCameraAngle, onDisable, onEnable, onReset | 11 | yes | - |
| SwordPvpBot | bots | 147 | yes | onButtonClick, onCameraAngle, onDisable, onEnable, onPacketReceive, onPacketSend, onPlayerScaffold, onReset, onSwordBot, onZoomHud | 6 | yes | - |
| VillagerTradeBot | bots | 4237 | yes | onCameraAngle, onDisable, onEnable, onPacketReceive, onRender2D, onRender3D, onReset | 35 | - | - |
| AimAssist | combat | 405 | yes | onDisable, onEnable, onRender2D, onReset | 7 | - | - |
| AnchorMacro | combat | 445 | yes | onDisable, onEnable, onPacketReceive, onPacketSend, onReset | 9 | - | - |
| AutoAggroPearl | combat | 141 | yes | onEnable, onReset | 5 | - | - |
| AutoCart | combat | 270 | yes | onDisable, onEnable, onReset | 0 | - | - |
| AutoClicker | combat | 137 | yes | onDisable, onEnable, onReset | 2 | - | - |
| AutoHitCrystal | combat | 79 | yes | onReset | 3 | - | - |
| AutoHotbar | combat | 396 | yes | onDisable, onEnable, onPacketSend, onReset | 5 | - | - |
| AutoInventoryTotem | combat | 1064 | yes | onDisable, onEnable, onPacketReceive, onPacketSend, onReset | 12 | - | - |
| AutoJumpReset | combat | 171 | yes | onDisable, onEnable, onReset | 3 | - | - |
| AutoShieldBreaker | combat | 79 | yes | onDisable, onPacketSend, onReset | 3 | - | - |
| AutoTotem | combat | 74 | yes | onDisable, onEnable, onReset | 1 | - | - |
| AutoTrap | combat | 247 | yes | onDisable, onEnable, onRender3D, onReset | 6 | yes | - |
| AutoXP | combat | 86 | yes | onDisable, onEnable, onReset | 4 | - | - |
| Criticals | combat | 106 | yes | onDisable, onPacketSend, onReset | 1 | - | - |
| CrystalAura | combat | 1870 | yes | onDisable, onEnable, onPacketSend, onRender2D, onRender3D, onReset | 10 | - | - |
| CrystalOptimizer | combat | 34 | - | onPacketSend | 0 | - | - |
| HitBoxes | combat | 24 | - | - | 2 | yes | - |
| HitSelect | combat | 369 | yes | onDisable, onEnable, onPacketSend, onReset | 3 | - | - |
| HitSwap | combat | 91 | yes | onDisable, onPacketSend, onReset | 6 | - | - |
| KeyPearl | combat | 86 | yes | onEnable, onReset | 1 | - | - |
| MaceBomber | combat | 130 | yes | onDisable, onPacketSend, onReset | 4 | - | - |
| ObsidianOptimizer | combat | 56 | yes | onEnable, onReset | 0 | - | - |
| PerfectWindCharge | combat | 143 | yes | onCameraAngle, onDisable, onEnable, onReset | 1 | - | - |
| Reach | combat | 196 | yes | onDisable, onEnable, onPacketSend, onReset | 5 | yes | - |
| SafeAnchor | combat | 1469 | yes | onDisable, onEnable, onPacketReceive, onPacketSend, onReset | 10 | - | - |
| SilentAim | combat | 312 | yes | onCameraAngle, onDisable, onReset | 9 | yes | - |
| TridentAssist | combat | 234 | yes | onDisable, onEnable, onReset | 3 | - | - |
| TriggerBot | combat | 451 | yes | onButtonClick, onDisable, onEnable, onPacketSend, onReset, onSwordBot | 9 | - | - |
| WTap | combat | 144 | yes | onDisable, onEnable, onPacketSend, onReset | 4 | - | - |
| Configs | configs | 51 | yes | onReset | 2 | - | - |
| AntiVanish | donutsmp | 526 | yes | onDisable, onEnable, onPacketReceive, onReset | 0 | - | - |
| AutoMine | donutsmp | 238 | yes | onDisable, onEnable, onReset | 7 | - | 1 |
| AutoSell | donutsmp | 167 | yes | onDisable, onEnable, onReset | 5 | - | - |
| AutoSellChest | donutsmp | 433 | yes | onDisable, onEnable, onReset | 1 | - | - |
| Bloodhound | donutsmp | 628 | yes | onDisable, onEnable, onPacketReceive, onRender2D, onRender3D, onReset, onSwordBot | 11 | yes | - |
| BoneDropper | donutsmp | 846 | yes | onDisable, onEnable, onPacketSend, onReset | 3 | - | - |
| ChunkFinder | donutsmp | 2184 | yes | onDisable, onEnable, onRender2D, onRender3D, onReset | 13 | yes | 6 |
| ChunkFinderV2 | donutsmp | 1895 | yes | onDisable, onEnable, onRender3D, onReset | 13 | yes | - |
| DeepPlayerFinder | donutsmp | 242 | yes | onDisable, onEnable, onRender3D, onReset | 7 | - | - |
| FakePay | donutsmp | 222 | yes | onConfigLoaded, onDisable, onReset | 5 | yes | - |
| FakeStats | donutsmp | 216 | - | onEnable | 13 | yes | - |
| GamblingRigger | donutsmp | 304 | yes | onDisable, onReset | 3 | - | - |
| NetheriteFinder | donutsmp | 440 | yes | onDisable, onEnable, onRender3D, onReset | 5 | - | - |
| RegionMap | donutsmp | 583 | - | onRender2D | 5 | - | - |
| Relog | donutsmp | 169 | yes | onDisable, onEnable, onReset | 1 | - | - |
| SeedAnalyzer | donutsmp | 55 | - | onEnable | 2 | - | - |
| SilentHome | donutsmp | 332 | yes | onDisable, onEnable, onPacketReceive, onReset | 3 | - | - |
| SpecialItemBuyer | donutsmp | 2087 | yes | onDisable, onEnable, onPacketReceive, onReset | 24 | - | - |
| StaffList | donutsmp | 432 | yes | onDisable, onEnable, onReset | 2 | - | - |
| SusChunkFinder | donutsmp | 554 | yes | onDisable, onEnable, onRender3D, onReset | 13 | - | - |
| SusChunkFinderV2 | donutsmp | 577 | yes | onDisable, onEnable, onRender3D, onReset | 10 | - | - |
| WanderingTraderFinder | donutsmp | 329 | yes | onDisable, onEnable, onRender3D, onReset | 15 | - | - |
| ACAlert | misc | 136 | yes | onDisable, onEnable, onPacketReceive, onReset | 3 | - | - |
| AutoPot | misc | 56 | yes | onReset | 3 | - | - |
| AutoPromo | misc | 82 | yes | onDisable, onEnable, onReset | 2 | yes | - |
| AutoTool | misc | 86 | yes | onDisable, onReset | 1 | - | - |
| BoatFly | misc | 74 | yes | onDisable, onReset | 3 | yes | - |
| Capes | misc | 134 | - | - | 3 | yes | - |
| CorzRadio | misc | 20 | - | - | 1 | - | - |
| CreamyKeys | misc | 132 | yes | onDisable, onEnable, onReset, onSwordBot | 3 | yes | - |
| CustomCrosshair | misc | 314 | - | onRender2D | 10 | yes | - |
| CustomSkin | misc | 203 | yes | onDisable, onEnable, onPacketSend, onReset | 4 | - | - |
| FOVSlider | misc | 71 | - | - | 5 | yes | - |
| FakeCoordinates | misc | 352 | yes | onReset | 1 | yes | - |
| FakePlayer | misc | 37 | - | onDisable, onEnable | 0 | - | - |
| FastPlace | misc | 71 | - | onPostItemUse | 5 | - | - |
| Fastbreak | misc | 155 | yes | onDisable, onEnable, onReset | 5 | yes | - |
| InventoryCleaner | misc | 273 | yes | onDisable, onEnable, onPacketSend, onReset | 6 | - | - |
| NameProtect | misc | 292 | - | - | 3 | yes | - |
| PotionRefill | misc | 61 | yes | onReset | 2 | - | - |
| SkinProtect | misc | 158 | - | onEnable | 1 | yes | - |
| TimeChanger | misc | 33 | yes | onReset | 1 | - | - |
| WeatherChanger | misc | 36 | - | - | 1 | yes | - |
| WeatherNotifier | misc | 87 | yes | onEnable, onReset | 4 | - | - |
| Zoom | misc | 62 | - | onZoomHud | 4 | yes | - |
| AutoSpear | movement | 98 | yes | onDisable, onEnable, onReset | 3 | - | - |
| AutoWalk | movement | 23 | yes | onDisable, onReset | 0 | - | - |
| Flight | movement | 103 | yes | onDisable, onReset | 3 | yes | - |
| Freecam | movement | 482 | yes | onDisable, onEnable, onReset | 8 | yes | - |
| KeepSprint | movement | 9 | - | - | 0 | yes | - |
| PearlCatchAssist | movement | 168 | yes | onCameraAngle, onDisable, onEnable, onReset | 4 | - | - |
| Scaffold | movement | 916 | yes | onDisable, onEnable, onPlayerScaffold, onPreItemUse, onRender2D, onRender3D, onReset | 8 | - | - |
| SpeedHack | movement | 51 | - | - | 4 | yes | - |
| Sprint | movement | 24 | yes | onReset | 0 | - | - |
| BackTrack | network | 476 | yes | onDisable, onEnable, onPacketReceive, onPacketSend, onRender3D, onReset | 3 | - | - |
| Blink | network | 206 | yes | onDisable, onEnable, onPacketSend, onRender3D, onReset | 4 | - | - |
| FakeLag | network | 338 | yes | onDisable, onEnable, onPacketSend, onRender3D, onReset | 3 | - | - |
| KnockbackDelay | network | 185 | yes | onDisable, onEnable, onReset | 4 | - | - |
| ClientSounds | settings | 106 | - | - | 8 | - | - |
| GUISettings | settings | 58 | - | onDisable | 1 | - | - |
| License | settings | 25 | - | - | 1 | - | - |
| MiddleClickFriend | settings | 35 | - | onButtonClick | 0 | - | - |
| Notifications | settings | 26 | - | - | 2 | - | - |
| Panic | settings | 73 | - | - | 1 | - | - |
| SelfDestruct | settings | 100 | - | - | 3 | - | - |
| Friends | socials | 20 | - | - | 0 | - | - |
| ThemeCustomizer | theme | 137 | yes | onConfigLoaded, onDisable, onReset | 20 | - | - |
| AtmosphericParticles | visuals | 43 | - | - | 3 | - | - |
| BlockESP | visuals | 249 | yes | onDisable, onRender3D, onReset | 6 | - | - |
| BlockHighlight | visuals | 411 | - | onDisable, onRender3D | 5 | - | - |
| BlockOverlay | visuals | 202 | - | onDisable, onEnable, onRender3D | 9 | - | - |
| CustomFlame | visuals | 440 | yes | onDisable, onEnable, onRender2D, onRender3D, onReset | 14 | yes | - |
| CustomSky | visuals | 224 | - | onDisable, onEnable | 9 | yes | - |
| CustomWorld | visuals | 159 | - | onRender3D | 13 | yes | - |
| FireFly | visuals | 326 | yes | onDisable, onRender3D, onReset | 2 | - | - |
| Freelook | visuals | 46 | - | onCameraAngle, onEnable | 0 | yes | - |
| Fullbright | visuals | 9 | - | - | 0 | yes | - |
| HandCustomer | visuals | 360 | - | - | 21 | yes | - |
| HandShader | visuals | 195 | yes | onDisable, onEnable, onReset | 21 | - | - |
| HeatColor | visuals | 82 | - | onRender3D | 3 | - | - |
| HoleESP | visuals | 777 | yes | onDisable, onEnable, onRender3D, onReset | 6 | yes | - |
| JumpCircles | visuals | 396 | yes | onDisable, onRender3D, onReset | 13 | - | - |
| LootESP | visuals | 412 | - | onRender2D, onRender3D | 10 | - | - |
| MobESP | visuals | 170 | - | onRender3D | 2 | yes | - |
| MotionBlur | visuals | 152 | - | onDisable, onEnable | 4 | yes | - |
| NameTags | visuals | 1606 | yes | onDisable, onPacketReceive, onRender2D, onReset | 21 | yes | - |
| NoExplosions | visuals | 34 | - | - | 5 | yes | - |
| NoFarmLag | visuals | 190 | yes | onDisable, onEnable, onReset | 10 | yes | - |
| NoFlame | visuals | 9 | - | - | 0 | yes | - |
| Particles | visuals | 298 | yes | onDisable, onPacketReceive, onRender3D, onReset | 7 | yes | - |
| PearlCatch | visuals | 51 | yes | onEnable, onReset | 1 | - | - |
| PearlPrediction | visuals | 815 | - | onDisable, onRender2D, onRender3D | 7 | - | - |
| PlayerESP | visuals | 345 | - | onRender3D | 8 | yes | - |
| RealisticWater | visuals | 113 | yes | onDisable, onEnable, onReset | 4 | - | - |
| ShowHUD | visuals | 6667 | - | onZoomHud | 29 | yes | - |
| SpawnerFinder | visuals | 822 | yes | onDisable, onEnable, onRender2D, onRender3D, onReset | 25 | yes | - |
| SpotifyHUD | visuals | 1502 | yes | onDisable, onRender2D, onReset | 4 | - | - |
| StorageESP | visuals | 236 | yes | onDisable, onRender3D, onReset | 10 | - | - |
| SwordInspect | visuals | 955 | yes | onDisable, onEnable, onReset, onSwordBot | 11 | yes | - |
| TargetESP | visuals | 195 | - | onDisable, onRender3D | 11 | - | - |
| TotemCounter | visuals | 472 | yes | onEnable, onRender2D, onReset | 3 | - | - |
| Trails | visuals | 423 | yes | onDisable, onEnable, onRender3D, onReset | 9 | - | - |
| Waypoints | visuals | 384 | - | onDisable, onRender2D, onRender3D, onSwordBot | 11 | - | - |


## 8. Not verified

No Minecraft client can be started in this environment, so nothing above is runtime-observed. All conclusions come from the compiler, the built jar, the generated refmap, the actual 1.21.11 intermediary game jar, and static call-graph checks.
