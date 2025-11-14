#!/bin/bash
# quick-test.sh - Quick test runner with automatic analysis
# This script runs tests, saves output, and shows analysis automatically

set -e

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUTPUT_DIR="test-results"
OUTPUT_FILE="${OUTPUT_DIR}/test-run-${TIMESTAMP}.txt"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}🧪 Running tests...${NC}\n"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Run tests and capture output
if clojure -M:dev:test 2>&1 | tee "$OUTPUT_FILE"; then
  TEST_EXIT=0
else
  TEST_EXIT=$?
fi

echo ""
echo -e "${YELLOW}📊 Test output saved to: ${OUTPUT_FILE}${NC}"
echo ""

# Show quick summary from output
echo -e "${YELLOW}Summary:${NC}"
grep -E "Ran [0-9]+ tests|failures|errors" "$OUTPUT_FILE" | head -5 || echo "No summary found"

echo ""

# Try to run analysis if babashka is available
if command -v bb &> /dev/null; then
  echo -e "${YELLOW}🔍 Running detailed analysis...${NC}\n"
  bb analyze-test-results.clj "$OUTPUT_FILE"
else
  echo -e "${YELLOW}💡 Install Babashka for detailed analysis:${NC}"
  echo "   brew install borkdude/brew/babashka"
  echo ""
  echo -e "${YELLOW}Or view results manually:${NC}"
  echo "   cat $OUTPUT_FILE"
fi

exit $TEST_EXIT
