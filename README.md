# MCU Root Flasher

## Disclaimer

This tool directly flashes MCU firmware on rooted automotive head units. Using
it can permanently break the MCU, the head unit, vehicle integration, cameras,
display, USB, CAN behavior, or other safety-relevant functions. It is provided
for research and recovery work only. You are solely responsible for verifying
the firmware image, understanding the hardware target, keeping recovery options
available, and accepting the risk of damage or data loss.

Root-only helper for IHU717P MCU experiments. It does not call
`RecoverySystem.verifyPackage()` and does not install an OTA package. Instead it
validates a local `mcu_update.zip`, extracts `fsl_app.s19` and `upgrade.dat`,
mounts `/tmp` as `tmpfs`, copies the payload there with root, releases
`/dev/ecp_uart_1`, and runs `/tmp/upgrade.dat`.

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
- The app creates `/tmp` when missing, mounts it as `tmpfs` with mode `0777`,
  then remounts `/` back to read-only before flashing.
- Signature verification is intentionally not performed; this is a direct root
  flasher, not a recovery OTA installer.
- The app still checks the ZIP's JAR/v1 signature and warns when the signer is
  not ECARX_WH (`FC:B2:6D:E3:0D:03:3A:1C:16:63:F6:0A:7F:06:17:35:7B:AC:59:C1:E0:7F:75:10:C5:A7:32:87:51:C7:05:1D`).

## License

MIT. See [LICENSE](LICENSE).
