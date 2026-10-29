# Fun Time with the Savannah Cat

**1.0.0** targets Minecraft **26.1.2**, NeoForge **26.1.2.122**, GeckoLib **5.5.2**, and Java **25**. Install the matching Savannah Cat and GeckoLib NeoForge JARs on the server and every client. Open this folder as a Gradle project in IntelliJ with JDK 25.

The companion has one coordinated AI, persistent personality and bonding, defensive adult combat, safe follow/catch-up, a dyeable collar, downed rescue, commands, harvesting, home furniture and modest fishing assistance. Kittens retain explicit breeding, permanent inherited ownership, loaded-time growth, family behavior and fourteen contextual sound events. Owner bedtime companionship and the expanded Harvest command are retained. This release includes the pack metadata and Hunt feedback fixes and optional JEI crafting/information support. It preserves the existing responsive five-species Hunt policy. PORT_26.1.2.md and PORT_TESTS_26.1.2.md describe this port and its current verification; JEI_TESTS_2026-10-27.md preserves the earlier Minecraft 26.1 JEI evidence; RELEASE_TESTS_2026-10-27.md preserves the earlier candidate evidence; HUNT_RESPONSIVENESS.md records the earlier gameplay change. Approved adult geometry/coats/animations, the custom egg and Susan's established head tilt/chirp are preserved.

Owner bedtime companionship extends the existing sleep system: nearby owned adults and kittens may approach a sleeping owner's bed and settle in a safe reserved spot. Participation is optional, floor spots are preferred, Stay is respected by default, and safety and direct commands interrupt immediately. Vanilla night skipping remains unchanged. See BEDTIME.md for behavior, settings and compatibility limits.

Optional JEI support uses JEI 29.47.0.110 for Minecraft 26.1.2. Its browser shows the three normal crafting recipes plus care/control information for the whistle, scratching post, bowl and spawn egg. JEI is optional; for complete multiplayer crafting views, install matching JEI on both clients and the server. See JEI.md.

Minecraft 26.1.2 checks pass all 199 server tests both with and without JEI, plus actual native client recipe/information and no-JEI loading checks. See PORT_TESTS_26.1.2.md for current artifact evidence and remaining validation. JEI_TESTS_2026-10-27.md retains the earlier Minecraft 26.1 evidence.

## Playing

- Tame a wild cat with raw cod, salmon, chicken or rabbit (default one chance in three). Feeding cannot transfer an existing owner's cat.
- Heal your injured cat with raw or cooked fish/meat, in either hand. Default 5 HP, capped at its current maximum. Full health consumes nothing; Creative consumes nothing.
- To breed, **crouch-feed raw cod/salmon** to safe full-health adults. Injured owned cats heal first; owner Downed rescue retains top priority. Same-owner or owned/wild parents produce an automatically owned kitten; wild/wild parents produce a wild kitten. Different-owner parents cannot breed. Default parent cooldown is 5 minutes.
- Kittens grow over 20 minutes of loaded time, with saved progress and safe dimension checks. Crouch-feed an owned healthy kitten raw cod/salmon for modest optional acceleration. It keeps its UUID, owner, coat and collar on maturity. Kittens follow family/owner, play, groom, sleep and retreat; they cannot use adult combat, hunting or harvesting. See FAMILY.md.
- Use an empty hand on your cat to toggle Follow/Stay. Food in the other hand takes precedence. Use any standard dye to color its separate cloth collar.
- Craft a **Savannah Cat Whistle**. Crouch-use cycles Follow, Stay, Hunt, Harvest and the separate Recall action; normal use issues the selection. A visible owned cat under your crosshair receives an individual order; otherwise the nearby owned group does. Ordinary orders reach 64 blocks, capped at 12 cats by default. Feedback reports accepted and rejected cats; the default 15-tick use cooldown limits spam. Repeating a mode preserves its path and Stay anchor while waking peaceful sleep.
- Recall is immediate, not a saved command mode. It checks safe loaded destinations and cooldowns. The loaded-companion index can recall an already loaded cat from another dimension. It cannot discover, tick or rescue a cat in an unloaded chunk. A recalled Stay cat keeps Stay and receives a new safe anchor.
- An adult owned cat commanded to Hunt may catch a lone wild chicken, rabbit, pig, sheep or cow. Pets, named animals, babies, leashed animals and protected livestock near fences/walls/farmland are left alone. Natural wild-cat hunting still uses the separate small-prey list and population floor. Successful catches keep the default 120-second cooldown, now shown in individual whistle feedback. Repeating an idle Hunt refreshes its search; active pursuits keep their path/cooldowns. Owner range, hazards and Stay/Follow interruption remain bounded. See HUNT_RESPONSIVENESS.md.
- Harvest rapidly clears allowed harmless vegetation, collects eligible nearby dropped items and gathers only allowed mature wheat, carrots, potatoes, beetroot and berries. Default vegetation is short grass, fern, tall grass and large fern; flowers and saplings require explicit inclusion. Structural blocks, grass/dirt/farmland and immature crops remain protected. Owner permissions, protection hooks, resource claims and bounded scans apply. Replanting pays real planting materials; the bounded carry inventory delivers what fits and retains the remainder. See HARVEST.md for filters and limits.
- Crouch-use a **Scratching Post** or **Food and Water Bowl** to assign home to your nearby owned cat. A whistle on the furniture also assigns home. Alternatively, crouch-use a compass on a cat to set its current position as home; normal compass interaction asks it to return. Home is saved with its dimension.
- **Scratching Posts** are real optional activity points: safe relaxed cats approach a free adjacent space, scratch briefly with quiet sounds, then resume their previous activity. Shared reservations, failed-path memory and saved cooldowns prevent crowding and repeated failed approaches. Stay cats only use an already-adjacent point.
- A **Food and Water Bowl** starts empty and belongs to its placer. Add raw/cooked cat food one item at a time (one food type, maximum 16), use water bottles for one serving each, or use a water bucket to fill its eight-serving basin. Survival refills return empty bottles/buckets; Creative keeps the held item. The contents appear in the model. Normal empty-hand use shows counts; crouch-empty-hand or crouch-whistle use assigns home. Another player cannot refill or assign your bowl. Remaining food drops when the bowl breaks; water is discarded.
- Bowl visits consume exactly one food **or** water serving, play a finite eating/drinking animation, and respect a saved per-cat cooldown (default 60 seconds). No bowl, food or water is required for health, survival or loyalty. Automatic home activities grant no bond experience. Cats also use safe home carpets and window views. String interaction invites manual play without consuming the string. See HOME_LIFE.md for controls and activity rules.
- An eligible lethal hit downs a tamed cat with a glowing outline. Crouch-feed it as its owner before the timer expires to revive at 25% health. Recovery lasts 60 seconds and weakens combat. Void/admin bypass damage and lethal damage during recovery can still kill it. Downed deadlines survive save/reload and loaded-world time; ordinary further damage cannot duplicate death rewards.
- A nearby owned cat offers a single strongest fishing benefit: default 5–9% shorter waiting periods according to bond, plus a small chance to save one point of ordinary rod wear. Bonuses do not stack and normal loot/treasure rules remain intact.

Default bond statistics:

| Bond | Maximum HP | Melee | Pounce | Rescue window |
|---|---:|---:|---:|---:|
|1|40|6|8|45 sec|
|2|42|6.4|8.5|55 sec|
|3|44|6.8|9|65 sec|
|4|46|7.2|9.5|75 sec|
|5|48|7.5|10|90 sec|

Earn progress through useful feeding, manual play/interaction, shared travel, successful hunts and combat. Each reason has a persisted cooldown; standing beside a cat or automatic ambient animations do not earn progress. Courage grows gradually from real adventure/combat. Level 5 protection requires an injured owner and a genuine manageable threat, lasts 10 seconds and has a 75 second cooldown. Bosses and Creepers remain retreat targets.

## Natural sleeping

Safe, tired cats can nap near a stationary owner, at home or on suitable carpets. The dedicated Gecko sleep chain lowers into a tucked loaf with breathing and occasional ear twitches, then smoothly wakes. The four supplied closed-eye textures are selected only during peaceful Sleep; waking, sitting, Downed and other actions retain the approved awake coats. All supplied PNG bytes are preserved. Commands, owner movement and danger interrupt sleep. It has no glowing outline or rescue timer; Downed remains a separate emergency state. See SLEEPING.md for texture paths, behavior, configuration and tests.

## Sounds

Fourteen namespaced events cover ambient, pain, final death, paws, purring, warnings, food/water, sleep, post contact, taming, revival and play. Current recordings are documented temporary **vanilla Minecraft mappings**, with no downloaded recordings or fake OGG placeholders. Server triggers, quiet kitten pitch/volume, per-cat cooldowns and local group budgets limit noise. Susan retains her existing cue and default pitch. See AUDIO.md for every actual mapping and setting.

## Build, datagen and tests

```powershell
.\gradlew.bat runData
.\gradlew.bat clean build
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient
```

Datagen's source is `src/main/java/net/ronm19/savannahcat/data/GameplayResources.java`; its 24 JSON outputs live under `src/generated/resources` and are included in the build. It generates language/subtitles, sounds.json, recipes, tags, loot tables, biome modifiers and block/item definitions/models, including all four bowl content states. Edit the provider, then run `runData`; do not edit generated JSON. Textures and Gecko geometry/animations remain under `src/main/resources`. Pack metadata and the narrow access transformer also remain source resources. No duplicate main/generated paths are retained.

Run data generation and the distributable build in separate invocations. The release check caught an intermediate JAR with stale processed language after a combined runData/build call; the separate build and all-resource byte check correct it. `build` runs actual Gecko parsing/baking and asset checks. `runGameTestServer` exercises production server paths. RELEASE_TESTS_2026-10-27.md records current evidence; older Hunt/audit reports remain historical. The test-only mod and visual harness are excluded from the runtime JAR.

After opening or updating the project, reload Gradle in IntelliJ once. The client run configuration prepares its launch arguments before starting, so a previous `clean` does not leave `build/moddev/clientRunVmArgs.txt` missing. A terminal launch through `gradlew.bat runClient` also prepares these files automatically.

The earlier 0.5.4-rc1 candidate passes 199 required server tests, all nine native main/Hunt/family/bedtime/render runs and the two-native-client loopback scenario. Moving Hunt, protected/cooldown HUD width, actual process restarts, 65 rendered age-specific clips and packaged class/resource equality pass within the documented fixtures. The permanent IntelliJ project passes its clean 41-second build and native Java 25 launch-argument parse; its JAR matches the tested artifact. Managed-file and source-archive readbacks also pass. RELEASE_TESTS_2026-10-27.md records exact results and limits. Human listening, human/WAN multiplayer, arbitrary modpacks and long sessions remain untested.

The earlier 0.5.1 audit and 0.5.3 Hunt release retain their historical server/native/install evidence in RELEASE_AUDIT.md, TEST_RESULTS.md and HUNT_RESPONSIVENESS.md. Their restart and installation results are not presented as fresh RC1 delivery proof. Approved artwork remains unchanged. KNOWN_ISSUES.md lists current pending acceptance and design limits.

Optional asset tools use standard Node/Python: `node tools/extend_animations.mjs`, `python tools/verify_assets.py`, and `python tools/create_test_fixture.py`. The model converter refuses to overwrite approved assets. A complete pre-gameplay source backup is retained under `source_assets`; the supplied external ZIPs were left untouched.

See BEDTIME.md, HARVEST.md, FAMILY.md, AUDIO.md, CONFIGURATION.md, HOME_LIFE.md, SLEEPING.md, AI_ARCHITECTURE.md, FEATURE_CHECKLIST.md, CHANGELOG.md and NEXT_STEPS.md. This snapshot keeps the exact 26.1 dependency pins; another Minecraft version requires a verified port.

On this Windows machine Java's 8.3 temporary socket path required an existing normal directory. If needed, set `$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir="C:\an-existing-directory"'` for the terminal before Gradle. No global launcher or existing-world settings were changed.

