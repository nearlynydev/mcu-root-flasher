# MCU Root Flasher

Root-only helper for IHU717P MCU experiments. It does not call
`RecoverySystem.verifyPackage()` and does not install an OTA package. Instead it
validates a local `mcu_update.zip`, extracts `fsl_app.s19` and `upgrade.dat`,
copies them to `/tmp` with root, releases `/dev/ecp_uart_1`, and runs
`/tmp/upgrade.dat`.

Build:

```sh
./build.sh
```

Output:

```text
build/mcu-root-flasher.apk
```

Operational notes:

- Requires Magisk/root on the HU.
- Default paths are scanned from `/storage/udisk*`, `/sdcard`, and
  `/sdcard/Download`.
- The app writes a best-effort log to `/sdcard/mcu_root_flasher.log`.
- Signature verification is intentionally not performed; this is a direct root
  flasher, not a recovery OTA installer.
- The app still checks the ZIP's JAR/v1 signature and warns when the signer is
  not ECARX_WH (`FC:B2:6D:E3:0D:03:3A:1C:16:63:F6:0A:7F:06:17:35:7B:AC:59:C1:E0:7F:75:10:C5:A7:32:87:51:C7:05:1D`).
