#!/usr/bin/env bash
# ==============================================================================
# TinyScale Distributed URL Shortener - Zero-Docker Local Startup Script
# Boots Backend (Spring Boot + In-Memory H2/Caffeine) and Frontend (Vite + React)
# ==============================================================================

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color
BOLD='\033[1m'

echo -e "${CYAN}${BOLD}"
echo "  _____ _             ____             _      "
echo " |_   _(_)_ __  _   _/ ___|  ___ __ _| | ___ "
echo "   | | | | '_ \| | | \___ \ / __/ _\` | |/ _ \\"
echo "   | | | | | | | |_| |___) | (_| (_| | |  __/"
echo "   |_| |_|_| |_|\__, |____/ \___\__,_|_|\___|"
echo "                |___/  DISTRIBUTED ENGINE v2.0"
echo -e "${NC}"
echo -e "${BOLD}Starting TinyScale in Zero-Docker Native Mode...${NC}\n"

# 1. Prerequisite checks
command -v java >/dev/null 2>&1 || { echo -e "${RED}Error: Java 17+ is required but not installed.${NC}"; exit 1; }
command -v node >/dev/null 2>&1 || { echo -e "${RED}Error: Node.js 18+ is required but not installed.${NC}"; exit 1; }
command -v npm >/dev/null 2>&1 || { echo -e "${RED}Error: npm is required but not installed.${NC}"; exit 1; }

JAVA_VER=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
if [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
    echo -e "${YELLOW}Warning: Java 17+ recommended. Detected version: $JAVA_VER${NC}"
fi

# 2. Trap cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Shutting down TinyScale services...${NC}"
    if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
        kill "$BACKEND_PID" 2>/dev/null || true
    fi
    if [ -n "$FRONTEND_PID" ] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
        kill "$FRONTEND_PID" 2>/dev/null || true
    fi
    echo -e "${GREEN}All services stopped cleanly.${NC}"
    exit 0
}
trap cleanup SIGINT SIGTERM EXIT

# 3. Check frontend node_modules
if [ ! -d "frontend/node_modules" ]; then
    echo -e "${CYAN}Installing frontend dependencies...${NC}"
    (cd frontend && npm install)
fi

# 4. Start Spring Boot Backend in background
echo -e "${CYAN}Booting Backend Engine (Spring Boot + H2 in-memory shard & analytics)...${NC}"
mvn spring-boot:run -f backend/pom.xml \
    -Dspring-boot.run.arguments="--server.port=8080 --app.sharding.enabled=false --spring.profiles.active=default" \
    > backend.log 2>&1 &
BACKEND_PID=$!

echo -e "Backend launching (PID: $BACKEND_PID, logs: ${BOLD}backend.log${NC})"

# Wait for backend health check
echo -n "Waiting for Backend to initialize"
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health 2>/dev/null | grep -q "UP"; then
        echo -e " ${GREEN}[READY]${NC}"
        break
    fi
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
        echo -e "\n${RED}Backend failed to start. Check backend.log:${NC}"
        tail -n 25 backend.log
        exit 1
    fi
    echo -n "."
    sleep 1
done

# 5. Start Vite Frontend
echo -e "${CYAN}Booting Frontend Web Dashboard (Vite)...${NC}"
(cd frontend && npm run dev) &
FRONTEND_PID=$!

sleep 2

echo -e "\n${GREEN}${BOLD}================================================================${NC}"
echo -e "${GREEN}${BOLD}  TinyScale Distributed URL Shortener is LIVE!${NC}"
echo -e "${GREEN}${BOLD}================================================================${NC}"
echo -e "  Frontend UI:    ${CYAN}http://localhost:5173${NC}"
echo -e "  Backend API:   ${CYAN}http://localhost:8080${NC}"
echo -e "  Health Check:  ${CYAN}http://localhost:8080/actuator/health${NC}"
echo -e "  Metrics:       ${CYAN}http://localhost:8080/actuator/prometheus${NC}"
echo -e "${GREEN}${BOLD}================================================================${NC}"
echo -e "Press ${YELLOW}Ctrl+C${NC} to stop both services.\n"

# Attempt to open browser automatically
if command -v open >/dev/null 2>&1; then
    open "http://localhost:5173"
elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "http://localhost:5173"
fi

# Wait for background processes
wait
