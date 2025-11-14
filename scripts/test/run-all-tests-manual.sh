#!/bin/bash
# run-all-tests-manual.sh
# Execute este script manualmente para ver todos os erros

echo "Running ALL tests..."
echo "===================="
echo ""

clojure -M:dev:test 2>&1 | tee test-results/full-test-output.txt

echo ""
echo "===================="
echo "Output saved to: test-results/full-test-output.txt"
echo ""
echo "To see summary:"
echo "  grep -E 'Ran|failures|errors' test-results/full-test-output.txt"
echo ""
echo "To see errors:"
echo "  grep -A 10 'ERROR in' test-results/full-test-output.txt"
