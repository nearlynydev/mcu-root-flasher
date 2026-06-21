# IHU717P MCU — Research History

A consolidated narrative of the reverse-engineering work on the management
microcontroller (MCU) of the ECARX IHU717P head unit: motivation, method, tools,
domains covered, milestones, build/vehicle comparison, how the car reacted to
different MCU firmware, and conclusions.

---

## 1. Motivation

The IHU717P head unit (ECARX E02 platform) is a **two-processor** design: a
MediaTek **MT6771** SoC running Android (multimedia, screen, navigation) and a
Renesas **RH850** **MCU** (power/ACC, board GPIO, CAN/steering wheel, 360 camera,
USB hub, audio mute, FOTA). They talk over the **ECP** protocol on UART
(`/dev/ecp_uart_1`).

Practical questions that drove the MCU analysis:
- why **MTK USB/preloader** (`mtkclient`) is caught on one firmware but not another;
- the **black screen** after swapping MCU firmware;
- why the **surround-view cameras** don't work;
- how to restore **vehicle configuration** (Local/Vehicle config) after tampering;
- overall — enough understanding of the MCU firmware to make small, testable changes.

Object of study — three `fsl_app.s19` builds (raw RH850, 786432 bytes, **RI850V4**
RTOS):

| Build | Model / variant | Date | fsl SHA-256 |
|---|---|---|---|
| **AAC** | Knewstar `IHU717P-00-AAC` (our car's native, MY2024) | 2024-06-04 | `45d16972…` |
| **BOH** | Geely Tugella `IHU717P-00-BOH` | 2024-01-05 | `b2ebd921…` |
| **AEH** | Knewstar `IHU717P-00-AEH` (reworked) | 2025-08-06 | `27c4548d…` |

---

## 2. Method & milestones

1. **Format ID.** `fsl_app.s19` is not an S-record but a raw RH850 image;
   RI850V4 RTOS strings; rough disassembly with `rizin -a v850`.
2. **Proper toolchain.** Installed **Ghidra 12.1.2** (from the GitHub release —
   no Homebrew package anymore) + **OpenJDK 21** (won't run on system JDK 26).
   Ghidra has no dedicated RH850 module — used base `V850:LE:32:default` (covers
   the RH850 core; a few opcodes may misdecode).
3. **Headless pipeline** (see Tools below): analysis + TSV export, decompile by
   address, data-xrefs, name application, naming map, K↔T cross-mapping.
4. **Function cross-mapping.** Both images yield **4976 functions**; string
   anchors + ordered segment alignment gave **3826 reliable pairs**, almost all
   byte-identical — this separated "build shift" noise from real differences.
5. **Annotation.** Meaningful names applied in the Ghidra databases:
   Knewstar(AEH) ~**244**, Tugella ~**79 manual + 98 auto-carried**. Databases in
   `ghidra/projects_named/` (open in the GUI).

---

## 2.5 Tools used

**Disassembly / decompilation**
- **Ghidra 12.1.2** (`~/tools/ghidra/ghidra_12.1.2_PUBLIC`), run headless via
  `support/analyzeHeadless` — primary decompiler, auto-analysis, call graph.
- **OpenJDK 21** (`/opt/homebrew/opt/openjdk@21`) — required by Ghidra.
- **rizin 0.8.2** (`-a v850 -b 32`) — fast auxiliary disassembler, RH850
  `switch`/jump tables, byte search.
- GNU/Apple **objdump** — pre-existing approximate V850/RH850 windows in
  `work/mcu_fsl_analysis/`.
- **capstone 5.0.7** — evaluated and rejected (no RH850/V850 support).

**Custom Ghidra scripts** (`ghidra/scripts/`)
- `ExportMcuInfo.java` — export functions / calls / strings / string-xrefs to TSV.
- `DecompileAt.java` — decompile the function(s) containing given addresses
  (force-creates a function in unrecognized code holes when needed).
- `DataXrefs.java` — read/write references to given data addresses, grouped by
  function.
- `ApplyLabels.java` — apply a TSV of names/comments (func / data / gpio) to a
  program; idempotent by address.

**Custom Python tooling** (`ghidra/`)
- `run_ghidra.sh` — import + analyze + export wrapper.
- `build_naming.py` — curated semantic name map → `naming_{knewstar,tugella}.tsv`.
- `build_func_mapping.py` — Knewstar↔Tugella function cross-mapping
  (`func_mapping_k2t.tsv`).
- `build_tugella_auto.py` — carry Knewstar names onto Tugella via the mapping
  (excludes Knewstar-only funcs).
- earlier helpers: `parse_command_tables.py`, `parse_gpio_table.py`,
  `scan_gpio_usage.py`, `build_callgraph.py`, `mcu_uart_proto.py` (upgrade.dat
  UART frames).

**Firmware handling**
- `unzip` / `python3 zipfile` — extract & repack `mcu_update.zip`.
- `shasum` / Python `hashlib` — image hashing and byte-diff.

**Live device & misc**
- `adb` — live head-unit access (`172.20.10.11:5555`): props, dumpsys, logcat,
  sysfs backlight, screencap, root via Magisk (`su`).
- `gh` — GitHub API (download Ghidra release, inspect external CAN-log repo).
- config cross-check against the factory table
  `2025-09-28 - new_config_tugellarest2_knewstar001.xlsx`.

---

## 3. Domains reverse-engineered

Each domain has its own map document (see `README.md`).

| # | Domain | Key findings | Doc |
|---|---|---|---|
| 1 | Format + memory map | RH850, 786432 B; vectors, code, data banks A/B, tail | `FIRMWARE_STRUCTURE.md` |
| 2 | RTOS kernel + 12 tasks | reset→`0x10994`→kernel; task table `0xc068` (entry/prio/stack); mailboxes 1..0xe | `rtos_map.md` |
| 3 | ECP protocol | 4-byte command tuples, `ecp_send_event`, ack/queue | (in `FIRMWARE_STRUCTURE`) |
| 4 | Config / NVM | records `0x69` Local, `0x5e` Network(F110), `0x56` Vehicle(F101), `0x6a` checksum, `0x57` BootVer; `nvm_read/write_record` | `config_nvm_map.md` |
| 5 | Local Config bits | byte→feature verified against factory table (radar/RVC/BSD/AVM/ambient) | `local_config_bitmap.md` |
| 6 | Power/ACC/Wake | `cmd_set_power_mode` (FullOn/AWAKE/STDBY/Reboot); PM state machine (states 0..0xd); ACC edge; heartbeat watchdog; `reset_inhibit` | `power_map.md` |
| 7 | CAN RX/TX + signals | `can_id_handler_table` (54 IDs `0x900-0x935`); speed/doors/ignition/BSD/SWRC; raw `febf69xx/74xx`→`febf5xxx` | `can_signals_map.md` |
| 8 | AVM / camera / video / I2C | I2C chip `0x07` (camera reg `0x50/51`, mute reg `0xd8`); `AvmReset` sequence | `avm_video_map.md` |
| 9 | Audio / voice / amplifier | voice mute via I2C `0x07`; Tbox/amp config; amp diag | `audio_map.md` |
| 10 | USB hub / mirror | HubReset (line `0x2e`); Knewstar-only `0x42→0x18` automaton | `usb_screen_diff_decompiled.md` |
| 11 | Screen / MIC_clear4 | blank GPIO `1..0x30` gated by line `0x49`(AEH)/`0x48`(Tug/AAC) | `usb_screen_diff_decompiled.md` |
| 12 | IPK dashboard theme/skin | skin follows drive mode; ThemeLink; NVM `0x6e` | (in diff analysis) |
| 13 | FOTA / boot | upgrade cmd→PM event `0x40`; FOTA status→`reset_inhibit`; MCUBOOT | `fota_boot_map.md` |
| 14 | 93Cxx standby ICs | standby flags of two ICs (not memory!), recovery | (in diff analysis) |

---

## 4. Differences between builds (= between vehicle trims)

Methodological point: **byte diff is misleading** (28–55%) due to build shift;
the real delta is at the function level.

### AAC (Knewstar 2024, ours) ↔ BOH (Tugella): **6 functions**
String sets are **identical**; the only differing functions:
- IPK dashboard theme — `ipk_skin_sync`, `ipk_skin_mode_correl`, `ipk_theme_field_set`;
- `cfg_read_f110_check` (F110/network);
- `publish_radar_adas_signals` and `broadcast_status_group` (AAC also emits the
  extra radar/ADAS event `0x1070400`).

Conclusion: **AAC is functionally == Tugella**; the delta is Knewstar dashboard
and radar specifics. No AVM, no 93Cxx, no usb-mirror; `MIC_clear4` reads `0x48`;
line `0x42` bit = `0x0c`.

### AEH (Knewstar 2025) ↔ BOH (Tugella): **14 functions + whole subsystems**
AEH is a reworked branch (version string at `0x8b0c` vs `0x88d8`, code ~`0x77c`
longer). On top of the "AAC 6 functions" it adds:
- **360 AVM camera** (I2C reset sequence) — Knewstar-only cluster;
- a **second standby IC `93c541`** on top of `93C104`;
- the **USB-mirror automaton `0x42→0x18`** (arm/disarm/reset);
- a **capability-flags** table for the UI;
- line `0x42` bit = `0x0e`; `MIC_clear4` reads `0x49`.

Conclusion: **AEH ≠ AAC/Tugella** — a newer trim (Knewstar 2025 with AVM etc.).

---

## 5. How the car reacted to different MCU firmware (live observations)

| MCU on the car | Observed | Code explanation |
|---|---|---|
| **AEH** (Knewstar 2025) | **Black screen** | `MIC_clear4` reads line `0x49`; on this board that reads such that the MCU mass-blanks GPIO `1..0x30` (cuts board peripherals). Tugella/AAC use line `0x48`, no blanking. |
| **Tugella (BOH)** | Screen OK; **MTK download caught** by `mtkclient`; **cameras dead** | No `0x42→0x18` automaton → USB path to SoC stays free. No AVM cluster → Android (Knewstar 2025) doesn't get the camera management it expects. Workaround: **camera libs replaced with Tugella's**. |
| **AAC via manual `upgrade.dat`/UART** | **Black screen** (`brightness=0`) + **touch dead** + empty `vehi.config` | Backlight `0` from power policy under incomplete Vehicle Config; touch blocked by `ECarXPowerManagerService` (`isEnableTouch=false`) because the MCU reports a wrong ACC/`getCarKeyState` → IHU_OFF. `local_config` was valid (`1102a085…`) but `vehi.config` (F101) was nearly empty (`11 02 00…`). |
| **Knewstar MCU via stock recovery / firmware-update** | **Everything works** (screen, touch, config) | The stock update mechanism flashes the MCU *and* initializes/writes the vehicle config correctly. The earlier fault was the **flashing method**, not the AAC firmware itself: manual `upgrade.dat`/UART left the config NVM incomplete; the stock recovery path does the full, correct sequence. |

**Incident resolution.** The black-screen/dead-touch state appeared only after a
**manual `upgrade.dat`/UART** flash of AAC (incomplete vehicle config). It was
resolved by reflashing the Knewstar MCU through the **stock recovery /
firmware-update mechanism** — after which screen, touch and config are all fine.
Lesson: **the flashing method matters** — use the stock recovery path (it writes
config correctly), not raw `upgrade.dat`/UART, unless you also restore the
vehicle config separately.

Car configuration (after resolution): **Android system/vendor = Knewstar 2025**,
**MCU = Knewstar (flashed via stock recovery)**, **camera libs = Tugella**.
Everything working. (During the incident, ADB was at `172.20.10.11:5555`;
backlight was temporarily restored via sysfs `brightness=200`/`bl_power=0`.)

---

## 6. Conclusions from current results

1. **One code base.** All three builds are RH850/RI850V4 with an identical core
   (RTOS, ECP, config/NVM, CAN, power, FOTA). Differences are **trim** (data/
   calibration + a handful of functions), not different products.
2. **AAC == Tugella** functionally; **AEH** is a separate branch with AVM/
   usb-mirror/93Cxx for the richer Knewstar 2025 trim.
3. **The "Knewstar" USB/screen problems are AEH-specific**, not AAC/Tugella.
   So EXP-001…004 (line `0x42` bit, `MIC_clear4`, usb-mirror) apply only to AEH;
   AAC/Tugella don't have those nodes.
4. **Cameras (AVM)** exist only in AEH MCU. On AAC/Tugella they are driven by
   Android libs; there is no native AVM behavior without the AEH MCU.
5. **The flashing method matters more than the build.** The black-screen/dead-
   touch state came from a **manual `upgrade.dat`/UART** flash that left the
   vehicle config NVM incomplete (`vehi.config` = `11 02 00…` / F101 mostly empty)
   → wrong ACC → power-policy IHU_OFF → touch blocked, backlight off — even though
   `local_config` (`0x69`) was valid. **Reflashing via the stock recovery /
   firmware-update mechanism fixed it** (it writes/initializes config correctly).
   If you must use raw `upgrade.dat`/UART, restore the vehicle config separately
   (e.g. import via `ecarx.debugtools/.prop.SysPropAct`).
6. **MCU flashing is recoverable:** prefer the stock recovery path; there is also
   a built-in `reset_inhibit` (armed in FOTA mode) that suppresses the
   comm-timeout watchdog during an update; rollback Tugella ↔ Knewstar is proven.

---

## 7. Open directions

See `OPEN_QUESTIONS.md` and `EXPERIMENTS_TODO.md`. In short:
- **Now (priority):** restore a working config on AAC — complete Vehicle Config
  (F101)/ACC to leave IHU_OFF and bring touch back; persist backlight (Magisk) as
  a safety net.
- Physical NVM medium; purpose of the `93Cxx` ICs; exact raw CAN IDs (needs a
  log); PDC/DTC/CSI/LIN/ESK domains; carry RAM-variable names over to Tugella.
