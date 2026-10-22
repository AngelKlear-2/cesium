#!/usr/bin/env bash
# build-iso.sh - builds Cesium as a bootable live ISO (BIOS + UEFI), text-only.
#
# usage:  sudo ./build-iso.sh            -> produces $WORK/cesium-<KREL>.iso
# env:    KVER=6.12  SUITE=noble  MIRROR=http://archive.ubuntu.com/ubuntu  WORK=./cesium-build
#
# the ISO runs entirely from RAM (squashfs + live-boot). Login: cesium / the password you set.
set -euo pipefail

KVER="${KVER:-6.12}"
SUITE="${SUITE:-noble}"
MIRROR="${MIRROR:-http://archive.ubuntu.com/ubuntu}"
WORK="${WORK:-$PWD/cesium-build}"
ROOTFS="$WORK/rootfs"
ISODIR="$WORK/iso"
KSRC="$WORK/linux-$KVER"
JOBS="$(nproc)"

die() { echo "cesium: $*" >&2; exit 1; }
[[ $EUID -eq 0 ]] || die "run as root (sudo)"

mkdir -p "$WORK"
cd "$WORK"

# ---------------------------------------------------------------- host tools
echo "== host packages"
apt-get update -qq
apt-get install -y -qq build-essential bc bison flex libelf-dev libssl-dev \
    debootstrap squashfs-tools xorriso mtools dosfstools \
    grub-pc-bin grub-efi-amd64-bin grub-common xz-utils curl

# ---------------------------------------------------------------- kernel
echo "== kernel $KVER"
if [[ ! -d "$KSRC" ]]; then
    [[ -f "linux-$KVER.tar.xz" ]] || curl -fL -o "linux-$KVER.tar.xz" \
        "https://cdn.kernel.org/pub/linux/kernel/v${KVER%%.*}.x/linux-$KVER.tar.xz"
    tar -xf "linux-$KVER.tar.xz"
fi
cd "$KSRC"
make defconfig
scripts/config --file .config \
    --set-str LOCALVERSION "-cesium" \
    --enable EFI --enable EFI_STUB --enable EFIVAR_FS \
    --enable EXT4_FS --enable VFAT_FS --enable FAT_FS --enable ISO9660_FS \
    --enable SQUASHFS --enable SQUASHFS_XZ --enable OVERLAY_FS \
    --enable BLK_DEV_LOOP --enable BLK_DEV_SR --enable SCSI --enable SCSI_MOD --enable BLK_DEV_SD \
    --enable CDROM --enable BLK_DEV_INITRD \
    --enable USB_SUPPORT --enable USB --enable USB_XHCI_HCD --enable USB_EHCI_HCD \
    --enable USB_OHCI_HCD --enable USB_UHCI_HCD --enable USB_STORAGE --enable USB_UAS \
    --enable E1000E --enable IGB --enable R8169 --enable VIRTIO_NET --enable VIRTIO_BLK \
    --enable NET --enable INET --enable IPV6 --enable PACKET --enable UNIX \
    --enable DEVTMPFS --enable DEVTMPFS_MOUNT --enable TMPFS --enable TMPFS_POSIX_ACL \
    --enable PROC_FS --enable SYSFS --enable CGROUPS --enable AUTOFS_FS \
    --enable FHANDLE --enable INOTIFY_USER --enable NAMESPACES \
    --enable UNIX98_PTYS --enable VT --enable VT_CONSOLE --enable CONSOLE_TRANSLATION
make olddefconfig
make -j"$JOBS" bzImage modules
KREL="$(make -s kernelrelease)"
cd "$WORK"
echo "   built kernel $KREL"

# ---------------------------------------------------------------- rootfs
echo "== debootstrap $SUITE"
PKGS="systemd-sysv,dbus,sudo,locales,tzdata,ca-certificates,bash-completion,curl,wget,nano,less,man-db,iproute2,iputils-ping,openssh-server,wpasupplicant,iw,rfkill,pciutils,usbutils,kmod,apt-utils,live-boot,live-config,live-config-systemd,initramfs-tools"
if [[ ! -d "$ROOTFS/usr" ]]; then
    debootstrap --variant=minbase --include="$PKGS" "$SUITE" "$ROOTFS" "$MIRROR"
fi

cleanup() {
    umount -R "$ROOTFS/dev"  2>/dev/null || true
    umount    "$ROOTFS/proc" 2>/dev/null || true
    umount -R "$ROOTFS/sys"  2>/dev/null || true
    sync
}
trap cleanup EXIT

mount --rbind /dev "$ROOTFS/dev";  mount --make-rslave "$ROOTFS/dev"
mount -t proc proc "$ROOTFS/proc"
mount --rbind /sys "$ROOTFS/sys";  mount --make-rslave "$ROOTFS/sys"
cp /etc/resolv.conf "$ROOTFS/etc/resolv.conf"

# ---------------------------------------------------------------- identity
echo "== identity"
echo cesium > "$ROOTFS/etc/hostname"
cat > "$ROOTFS/etc/hosts" <<'EOF'
127.0.0.1 localhost
127.0.1.1 cesium
EOF
cat > "$ROOTFS/etc/os-release" <<'EOF'
PRETTY_NAME="Cesium Linux"
NAME="Cesium"
VERSION_ID="1"
VERSION_CODENAME=cesium
ID=cesium
ID_LIKE=debian
HOME_URL="https://cesium.invalid/"
EOF
ln -sf ../etc/os-release "$ROOTFS/usr/lib/os-release"
printf 'Cesium \\r (\\l)\n\n' > "$ROOTFS/etc/issue"

# ---------------------------------------------------------------- network
mkdir -p "$ROOTFS/etc/systemd/network"
cat > "$ROOTFS/etc/systemd/network/20-wired.network" <<'EOF'
[Match]
Name=en* eth*

[Network]
DHCP=yes
EOF
cat > "$ROOTFS/etc/systemd/network/25-wireless.network" <<'EOF'
[Match]
Name=wl*

[Network]
DHCP=yes
EOF

# ---------------------------------------------------------------- kernel into rootfs
echo "== kernel modules"
make -C "$KSRC" -j"$JOBS" modules_install INSTALL_MOD_PATH="$ROOTFS"
cp "$KSRC/arch/x86/boot/bzImage" "$ROOTFS/boot/vmlinuz-$KREL"
cp "$KSRC/System.map"            "$ROOTFS/boot/System.map-$KREL"

# ---------------------------------------------------------------- finish in chroot
echo "== configure system"
read -rsp "password for user 'cesium': " PW; echo
[[ -n "$PW" ]] || die "empty password"

chroot "$ROOTFS" /bin/bash -e <<CHROOT
export DEBIAN_FRONTEND=noninteractive
locale-gen en_US.UTF-8 >/dev/null
update-locale LANG=en_US.UTF-8
ln -sf /usr/share/zoneinfo/UTC /etc/localtime
useradd -m -s /bin/bash -G sudo cesium
passwd -l root
systemctl enable systemd-networkd systemd-resolved ssh
depmod -a $KREL
update-initramfs -c -k $KREL
CHROOT
echo "cesium:$PW" | chroot "$ROOTFS" chpasswd

chroot "$ROOTFS" apt-get clean
rm -f "$ROOTFS/etc/resolv.conf"
ln -sf /run/systemd/resolve/stub-resolv.conf "$ROOTFS/etc/resolv.conf"
rm -rf "$ROOTFS"/var/lib/apt/lists/* "$ROOTFS"/var/cache/apt/archives/*.deb

# ---------------------------------------------------------------- squashfs
echo "== filesystem.squashfs"
ISO_STAGE="$WORK/isostage"
rm -rf "$ISO_STAGE"
mkdir -p "$ISO_STAGE/live" "$ISO_STAGE/boot/grub"
mksquashfs "$ROOTFS" "$ISO_STAGE/live/filesystem.squashfs" -comp xz -noappend -e boot
# kernel and initrd live outside the squashfs so grub can load them directly
cp "$ROOTFS/boot/vmlinuz-$KREL" "$ISO_STAGE/live/vmlinuz"
cp "$ROOTFS/boot/initrd.img-$KREL" "$ISO_STAGE/live/initrd.img"

# ---------------------------------------------------------------- grub menu
cat > "$ISO_STAGE/boot/grub/grub.cfg" <<'EOF'
set timeout=5
set default=0
menuentry "Cesium Linux (live)" {
    linux /live/vmlinuz boot=live quiet console=tty0 console=ttyS0,115200
    initrd /live/initrd.img
}
menuentry "Cesium Linux (live, safe)" {
    linux /live/vmlinuz boot=live nomodeset console=tty0
    initrd /live/initrd.img
}
EOF

# ---------------------------------------------------------------- iso
echo "== grub-mkrescue"
ISO="$WORK/cesium-$KREL.iso"
grub-mkrescue -o "$ISO" "$ISO_STAGE" -volid CESIUM
sync

echo
echo "== done: $ISO"
echo "   write it with: sudo dd if=$ISO of=/dev/sdX bs=4M status=progress conv=fsync"
echo "   or test in a VM: qemu-system-x86_64 -m 2G -cdrom $ISO -boot d"
