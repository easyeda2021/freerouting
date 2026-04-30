#!/bin/bash
# Register freerouting:// URL protocol handler for the current user.
# Usage: ./install-url-protocol.sh [install-dir]
#   install-dir defaults to the parent directory of this script's location.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="${1:-$(dirname "$SCRIPT_DIR")}"

DESKTOP_FILE="$HOME/.local/share/applications/freerouting-url-handler.desktop"
mkdir -p "$(dirname "$DESKTOP_FILE")"

sed "s|INSTALL_DIR|$INSTALL_DIR|g" \
    "$SCRIPT_DIR/freerouting-url-handler.desktop" > "$DESKTOP_FILE"

chmod 644 "$DESKTOP_FILE"

xdg-mime default freerouting-url-handler.desktop x-scheme-handler/freerouting 2>/dev/null
update-desktop-database "$HOME/.local/share/applications/" 2>/dev/null

echo "Registered freerouting:// URL protocol handler for $(whoami)"
