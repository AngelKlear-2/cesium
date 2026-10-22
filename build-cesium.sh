#!/usr/bin/env bash
# build-cesium.sh - builds Cesium, a text-only Linux distribution, and writes it to a USB flash drive.
#
# usage:  sudo ./build-cesium.sh /dev/sdX        (WHOLE device - its contents are erased)
# env:    KVER=6.12  SUITE=noble  MIRROR=http://archive.ubuntu.com/ubuntu  WORK=./cesium-build  FORCE=0
#
# result: UEFI + BIOS bootable stick, GPT: EFI (FAT32) | BIOS boot | root (ext4, label cesium-root)
#         shell login on tty, sudo, apt, systemd-networkd (DHCP on wired), sshd, the usual coreutils.
set -euo pipefail

DEV="${1:-}"
KVER="${KVER:-6.12}"
SUITE="${SUITE:-noble}"
MIRROR="${MIRROR:-http://archive.ubuntu.com/ubuntu}"
WORK="${WORK:-$PWD/cesium-build}"
FORCE="${FORCE:-0}"
MNT="$WORK/mnt"
KSRC="$WORK/linux-$KVER"
JOBS="$(nproc)"

die() { echo "cesium: $*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "run as root (sudo)"
[[ -b "$DEV" ]]   || die "usage: $0 /dev/sdX   (whole block device, not a partition)"

# never touch the disk the build host is running from
ROOT_SRC="$(findmnt -n -o SOURCE / || true)"
ROOT_DISK="$(lsblk -no PKNAME "$ROOT_SRC" 2>/dev/null | head -1 || true)"
[[ "$DEV" == "$ROOT_SRC" ]] && die "$DEV is the running root device. refusing."
[[ -n "$ROOT_DISK" && "$DEV" == "/dev/$ROOT_DISK" ]] && die "$DEV holds the running system. refusing."

REMOVABLE="$(cat "/sys/block/${DEV#/dev/}/removable" 2>/dev/null || echo 0)"
if [[ "$REMOVABLE" != 1 && "$FORCE" != 1 ]]; then
    die "$DEV is not marked removable (flash drive?). set FORCE=1 to override."
fi

lsblk "$DEV"
read -rp "ERASE $DEV completely? type YES: " ans
[[ "$ans" == YES ]] || die "aborted"
read -rsp "password for user 'cesium': " PW; echo
[[ -n "$PW" ]] || die "empty password"

part() { case "$DEV" in *[0-9]) echo "${DEV}p$1" ;; *) echo "${DEV}$1" ;; esac; }

cleanup() {
    umount -R "$MNT" 2>/dev/null || true
    sync
}
trap cleanup EXIT

mkdir -p "$WORK"

# ---------------------------------------------------------------- host tools
echo "== host packages"
apt-get update -qq
apt-get install -y -qq build-essential bc bison flex libelf-dev libssl-dev \
    debootstrap dosfstools e2fsprogs gdisk xz-utils rsync curl kmod cpio

# ---------------------------------------------------------------- kernel
echo "== kernel $KVER"
cd "$WORK"
if [[ ! -d "$KSRC" ]]; then
    [[ -f "linux-$KVER.tar.xz" ]] || curl -fL -o "linux-$KVER.tar.xz" \
        "https://cdn.kernel.org/pub/linux/kernel/v${KVER%%.*}.x/linux-$KVER.tar.xz"
    tar -xf "linux-$KVER.tar.xz"
fi
cd "$KSRC"
make defconfig

# built-in (=y) drivers so no initramfs is needed: the stick's root is found directly
scripts/config --file .config \
    --set-str LOCALVERSION "-cesium" \
    --enable EFI --enable EFI_STUB --enable EFIVAR_FS \
    --enable EXT4_FS --enable FAT_FS --enable VFAT_FS \
    --enable SCSI --enable SCSI_MOD --enable BLK_DEV_SD \
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

# ---------------------------------------------------------------- partition
echo "== partition $DEV"
lsblk -nrpo MOUNTPOINT "$DEV" | grep -v '^$' | xargs -r umount -l || true
wipefs -a "$DEV" >/dev/null
sgdisk --zap-all "$DEV" >/dev/null
sgdisk -n1:1MiB:+512MiB -t1:ef00 -c1:CESIUM-EFI \
       -n2:0:+1MiB      -t2:ef02 -c2:BIOS-BOOT \
       -n3:0:0          -t3:8300 -c3:CESIUM-ROOT "$DEV" >/dev/null
partprobe "$DEV" 2>/dev/null || true
sleep 2

mkfs.vfat -F32 -n CESIUMEFI "$(part 1)" >/dev/null
mkfs.ext4 -q -L cesium-root "$(part 3)"

mkdir -p "$MNT"
mount "$(part 3)" "$MNT"

# ---------------------------------------------------------------- base system
echo "== debootstrap $SUITE"
PKGS="systemd-sysv,dbus,sudo,locales,tzdata,ca-certificates,bash-completion,curl,wget,nano,less,man-db,iproute2,iputils-ping,openssh-server,wpasupplicant,iw,rfkill,pciutils,usbutils,kmod,apt-utils"
debootstrap --variant=minbase --include="$PKGS" "$SUITE" "$MNT" "$MIRROR"

mkdir -p "$MNT/boot/efi"
mount "$(part 1)" "$MNT/boot/efi"

mount --rbind /dev "$MNT/dev";  mount --make-rslave "$MNT/dev"
mount -t proc proc "$MNT/proc"
mount --rbind /sys "$MNT/sys";  mount --make-rslave "$MNT/sys"
cp /etc/resolv.conf "$MNT/etc/resolv.conf"

# ---------------------------------------------------------------- identity
echo "== identity"
echo cesium > "$MNT/etc/hostname"
cat > "$MNT/etc/hosts" <<'EOF'
127.0.0.1 localhost
127.0.1.1 cesium
EOF
cat > "$MNT/etc/os-release" <<'EOF'
PRETTY_NAME="Cesium Linux"
NAME="Cesium"
VERSION_ID="1"
VERSION_CODENAME=cesium
ID=cesium
ID_LIKE=debian
HOME_URL="https://cesium.invalid/"
EOF
ln -sf ../etc/os-release "$MNT/usr/lib/os-release"
printf 'Cesium \\r (\\l)\n\n' > "$MNT/etc/issue"
cat > "$MNT/etc/fstab" <<'EOF'
LABEL=cesium-root  /          ext4  defaults,noatime  0 1
LABEL=CESIUMEFI    /boot/efi  vfat  umask=0077        0 2
EOF

# ---------------------------------------------------------------- network
mkdir -p "$MNT/etc/systemd/network"
cat > "$MNT/etc/systemd/network/20-wired.network" <<'EOF'
[Match]
Name=en* eth*

[Network]
DHCP=yes
EOF
cat > "$MNT/etc/systemd/network/25-wireless.network" <<'EOF'
[Match]
Name=wl*

[Network]
DHCP=yes
EOF

# ---------------------------------------------------------------- kernel into rootfs
echo "== kernel into rootfs"
make -C "$KSRC" -j"$JOBS" modules_install INSTALL_MOD_PATH="$MNT"
cp "$KSRC/arch/x86/boot/bzImage" "$MNT/boot/vmlinuz-$KREL"
cp "$KSRC/System.map"            "$MNT/boot/System.map-$KREL"
cp "$KSRC/.config"               "$MNT/boot/config-$KREL"
ln -sf "vmlinuz-$KREL" "$MNT/boot/vmlinuz"

# ---------------------------------------------------------------- finish inside the chroot
echo "== configure system"
chroot "$MNT" /bin/bash -e <<CHROOT
export DEBIAN_FRONTEND=noninteractive
apt-get install -y -qq grub-efi-amd64-bin grub-pc-bin >/dev/null
locale-gen en_US.UTF-8 >/dev/null
update-locale LANG=en_US.UTF-8
ln -sf /usr/share/zoneinfo/UTC /etc/localtime
useradd -m -s /bin/bash -G sudo cesium
passwd -l root
systemctl enable systemd-networkd systemd-resolved ssh
depmod -a $KREL
CHROOT
echo "cesium:$PW" | chroot "$MNT" chpasswd

# ---------------------------------------------------------------- bootloader
echo "== bootloader"
mkdir -p "$MNT/boot/grub"
# --removable writes EFI/BOOT/BOOTX64.EFI (works on any PC); --no-nvram keeps this host's firmware untouched
chroot "$MNT" grub-install --target=x86_64-efi --efi-directory=/boot/efi \
    --boot-directory=/boot --removable --no-nvram
chroot "$MNT" grub-install --target=i386-pc --boot-directory=/boot "$DEV"
cat > "$MNT/boot/grub/grub.cfg" <<'EOF'
set timeout=3
set default=0
menuentry "Cesium Linux" {
    search --no-floppy --label --set=root cesium-root
    linux /boot/vmlinuz root=LABEL=cesium-root rw rootwait console=tty0 console=ttyS0,115200
}
EOF

# ---------------------------------------------------------------- finalize
chroot "$MNT" apt-get clean
ln -sf /run/systemd/resolve/stub-resolv.conf "$MNT/etc/resolv.conf"
sync

echo
echo "== done. Cesium $KREL is on $DEV."
echo "   boot it from BIOS or UEFI, log in as 'cesium', then: sudo apt update"
