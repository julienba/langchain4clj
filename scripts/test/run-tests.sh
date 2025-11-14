#!/bin/bash
# run-tests.sh - Script to run tests and save results for analysis
# Usage: ./run-tests.sh [options]
#
# Options:
#   --save       Save output to test-results/ directory
#   --verbose    Show full output
#   --quiet      Only show summary
#   --help       Show this help message

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default options
SAVE_OUTPUT=false
VERBOSE=false
QUIET=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --save)
      SAVE_OUTPUT=true
      shift
      ;;
    --verbose)
      VERBOSE=true
      shift
      ;;
    --quiet)
      QUIET=true
      shift
      ;;
    --help)
      head -n 8 "$0" | tail -n 7
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

# Create test-results directory if saving
if [ "$SAVE_OUTPUT" = true ]; then
  mkdir -p test-results
  TIMESTAMP=$(date +%Y%m%d_%H%M%S)
  OUTPUT_FILE="test-results/test-run-${TIMESTAMP}.txt"
  SUMMARY_FILE="test-results/test-summary-${TIMESTAMP}.txt"
fi

# Print header
if [ "$QUIET" = false ]; then
  echo -e "${YELLOW}======================================${NC}"
  echo -e "${YELLOW}Running LangChain4Clj Test Suite${NC}"
  echo -e "${YELLOW}======================================${NC}"
  echo ""
fi

# Run tests
if [ "$SAVE_OUTPUT" = true ]; then
  # Save full output to file
  if [ "$QUIET" = false ]; then
    echo -e "${YELLOW}Running tests and saving output to: ${OUTPUT_FILE}${NC}"
    echo ""
  fi
  
  clojure -M:dev:test 2>&1 | tee "$OUTPUT_FILE"
  EXIT_CODE=${PIPESTATUS[0]}
  
  # Extract summary
  echo "" > "$SUMMARY_FILE"
  echo "Test Run Summary - $(date)" >> "$SUMMARY_FILE"
  echo "==========================================" >> "$SUMMARY_FILE"
  echo "" >> "$SUMMARY_FILE"
  
  # Extract key metrics
  grep -E "(Ran [0-9]+ tests|[0-9]+ failures|[0-9]+ errors|FAIL in|ERROR in)" "$OUTPUT_FILE" >> "$SUMMARY_FILE" 2>/dev/null || true
  
  if [ "$QUIET" = false ]; then
    echo ""
    echo -e "${YELLOW}Summary saved to: ${SUMMARY_FILE}${NC}"
    echo ""
  fi
  
else
  # Just run tests normally
  clojure -M:dev:test 2>&1
  EXIT_CODE=$?
fi

# Print final status
if [ "$QUIET" = false ]; then
  echo ""
  echo -e "${YELLOW}======================================${NC}"
  if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ Tests passed!${NC}"
  else
    echo -e "${RED}❌ Tests failed with exit code: $EXIT_CODE${NC}"
  fi
  echo -e "${YELLOW}======================================${NC}"
fi

exit $EXIT_CODE
