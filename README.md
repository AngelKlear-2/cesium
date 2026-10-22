# Cesium

Text-only Linux distribution (no graphics), built from a kernel.org kernel and an Ubuntu noble base, written to a USB flash drive.

## Build

```
sudo ./build-cesium.sh /dev/sdX
```

Use the whole device, not a partition. The script erases it after you type `YES`.

Options (env vars): `KVER` (default 6.12), `SUITE` (noble), `MIRROR`, `WORK`, `FORCE=1` (allow non-removable disks).

## Result

- UEFI and BIOS bootable GPT stick
- Shell login on tty, `sudo`, `apt`, `systemd-networkd` with DHCP, `sshd`
- User `cesium`, root login locked

## Status

Not yet booted or tested on hardware.
