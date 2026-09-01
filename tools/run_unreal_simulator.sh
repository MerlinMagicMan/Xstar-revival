#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd -P -- "$(dirname -- "$0")" && pwd -P)"
REPO_DIR="$(cd -P -- "$SCRIPT_DIR/.." && pwd -P)"
ENGINE_DIR="${XSTAR_UNREAL_ENGINE_ROOT:-/Users/Shared/Epic Games/UE_5.8}"
PLUGIN_WEB_DIR="$ENGINE_DIR/Engine/Plugins/Media/PixelStreaming2/Resources/WebServers"
UNREAL_EDITOR="$ENGINE_DIR/Engine/Binaries/Mac/UnrealEditor.app/Contents/MacOS/UnrealEditor"
PROJECT="$REPO_DIR/simulator/unreal/XStarSimulator.uproject"

if [[ ! -x "$UNREAL_EDITOR" ]]; then
    echo "Unreal Editor was not found at: $UNREAL_EDITOR" >&2
    exit 1
fi

if [[ ! -f "$PLUGIN_WEB_DIR/SignallingWebServer/www/player.html" ]]; then
    echo "Installing Epic's matching Pixel Streaming frontend into Unreal Engine..."
    /bin/bash "$PLUGIN_WEB_DIR/get_ps_servers.sh"
fi

exec "$UNREAL_EDITOR" "$PROJECT" \
    -game \
    -XStarPixelStreaming \
    -PixelStreamingConnectionURL=ws://127.0.0.1:8888 \
    -PixelStreamingAutoStartStream=true \
    -windowed \
    -ResX=1920 \
    -ResY=1080 \
    -log
