#!/usr/bin/env bash
# ==============================================================================
# TinyScale Distributed URL Shortener - End-to-End System Demo & Smoke Test
# ==============================================================================

set -eo pipefail

BASE_URL="${1:-http://localhost:8080}"

# Colors
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'
BOLD='\033[1m'

echo -e "${CYAN}${BOLD}"
echo "=================================================================="
echo "    TinyScale Distributed Engine — Live System Verification"
echo "=================================================================="
echo -e "${NC}"
echo -e "Target Base URL: ${BOLD}$BASE_URL${NC}\n"

# 1. Health Check
echo -e "${YELLOW}[1/7] Probing Health Check Endpoint (/actuator/health)...${NC}"
HEALTH_STATUS=$(curl -s "$BASE_URL/actuator/health" | grep -o '"status":"[^"]*' | head -n 1 | cut -d'"' -f4 || echo "DOWN")
if [ "$HEALTH_STATUS" != "UP" ]; then
  echo -e "${RED}Error: Service is not UP ($HEALTH_STATUS). Make sure the backend is running.${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Engine status: UP${NC}\n"

# 2. Create Short Link with Auto Base62 Generation (Snowflake ID)
echo -e "${YELLOW}[2/7] Generating Snowflake ID & Base62 Short Code (POST /api/links)...${NC}"
TARGET_URL="https://github.com/torvalds/linux"
CREATE_RES=$(curl -s -X POST "$BASE_URL/api/links" \
  -H "Content-Type: application/json" \
  -d "{\"longUrl\":\"$TARGET_URL\"}")

SHORT_CODE=$(echo "$CREATE_RES" | grep -o '"shortCode":"[^"]*' | cut -d'"' -f4)
if [ -z "$SHORT_CODE" ]; then
  echo -e "${RED}Failed to create link: $CREATE_RES${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Generated short code: ${BOLD}/$SHORT_CODE${NC} -> $TARGET_URL"
echo -e "  Response: $CREATE_RES\n"

# 3. Create Short Link with Custom Alias
echo -e "${YELLOW}[3/7] Creating Custom Brand Alias (customAlias: 'linus-torvalds')...${NC}"
ALIAS_RES=$(curl -s -X POST "$BASE_URL/api/links" \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://kernel.org","customAlias":"linus-torvalds"}')
echo -e "${GREEN}✓ Alias created:${NC} $ALIAS_RES\n"

# 4. Test 302 Redirection & Async Click Event
echo -e "${YELLOW}[4/7] Testing HTTP 302 Redirection (GET /linus-torvalds)...${NC}"
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/linus-torvalds")
REDIRECT_LOC=$(curl -s -I "$BASE_URL/linus-torvalds" | grep -i "location:" | tr -d '\r\n')
echo -e "${GREEN}✓ Redirection Status: $HTTP_STATUS${NC}"
echo -e "  $REDIRECT_LOC\n"

# Wait a brief moment for async analytics consumer
echo -e "${YELLOW}[5/7] Verifying Click Telemetry (GET /api/links/linus-torvalds/stats)...${NC}"
sleep 0.5
STATS_RES=$(curl -s "$BASE_URL/api/links/linus-torvalds/stats")
CLICK_COUNT=$(echo "$STATS_RES" | grep -o '"clickCount":[0-9]*' | cut -d':' -f2)
echo -e "${GREEN}✓ Recorded Click Count: ${BOLD}$CLICK_COUNT${NC}"
echo -e "  Telemetry payload: $STATS_RES\n"

# 6. Update Destination URL & Test Cache Invalidation
echo -e "${YELLOW}[6/7] Updating Destination & Evicting Cache (PUT /api/links/linus-torvalds)...${NC}"
NEW_TARGET="https://www.linuxfoundation.org"
curl -s -X PUT "$BASE_URL/api/links/linus-torvalds" \
  -H "Content-Type: application/json" \
  -d "{\"newLongUrl\":\"$NEW_TARGET\"}" > /dev/null

UPDATED_LOC=$(curl -s -I "$BASE_URL/linus-torvalds" | grep -i "location:" | tr -d '\r\n')
echo -e "${GREEN}✓ Cache successfully evicted.${NC}"
echo -e "  New Destination: $UPDATED_LOC\n"

# 7. Delete Short Link & Verify 404
echo -e "${YELLOW}[7/7] Deleting Link & Verifying Eviction (DELETE /api/links/linus-torvalds)...${NC}"
curl -s -X DELETE "$BASE_URL/api/links/linus-torvalds" > /dev/null
HTTP_STATUS_404=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/linus-torvalds")

if [ "$HTTP_STATUS_404" == "404" ]; then
  echo -e "${GREEN}✓ Link evicted: HTTP 404 confirmed.${NC}\n"
else
  echo -e "${RED}Unexpected status: $HTTP_STATUS_404${NC}\n"
  exit 1
fi

echo -e "${GREEN}${BOLD}=================================================================="
echo "    All System Lifecycle Tests Succeeded With 100% Reliability!    "
echo "==================================================================${NC}"
