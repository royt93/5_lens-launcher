#!/bin/bash

# Script để chạy tất cả unit tests và verify fixes
# Author: Claude Code
# Date: 2025-10-06

echo "🧪 Running Unit Tests for Lens Launcher"
echo "========================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_green() {
    echo -e "${GREEN}$1${NC}"
}

print_red() {
    echo -e "${RED}$1${NC}"
}

print_blue() {
    echo -e "${BLUE}$1${NC}"
}

# Check if gradlew exists
if [ ! -f "./gradlew" ]; then
    print_red "❌ Error: gradlew not found in current directory"
    exit 1
fi

# Make gradlew executable
chmod +x ./gradlew

echo "📋 Test Plan:"
echo "  1. BitmapCacheTest (14 tests) - Fix 5.1"
echo "  2. AppEventManagerTest (13 tests) - Fix 1.2"
echo "  3. RAppsSingletonTest (12 tests) - Fix 2.1, 3.4"
echo "  4. ObservableWrappersTest (11 tests) - Fix 1.2"
echo "  5. TaskUpdateAppsTest (5 tests) - Fix 1.1, 4.1"
echo ""
echo "Total: 55+ tests"
echo ""

# Clean build
print_blue "🧹 Cleaning build..."
./gradlew clean > /dev/null 2>&1

# Run all tests
print_blue "🚀 Running all unit tests..."
echo ""

./gradlew test --info 2>&1 | tee test_output.log

# Check result
if [ ${PIPESTATUS[0]} -eq 0 ]; then
    print_green "✅ ALL TESTS PASSED!"
    echo ""
    echo "📊 Test Summary:"
    grep -E "tests? completed" test_output.log || echo "  Check test_output.log for details"
    echo ""
    print_green "🎉 Implementation VERIFIED - All fixes working correctly!"
    echo ""
    echo "📁 Test reports:"
    echo "  - HTML: app/build/reports/tests/testDebugUnitTest/index.html"
    echo "  - Log: test_output.log"
    rm -f test_output.log
    exit 0
else
    print_red "❌ TESTS FAILED"
    echo ""
    echo "📄 Check test_output.log for details"
    echo "🔍 Common issues:"
    echo "  1. Missing dependencies - run: ./gradlew build"
    echo "  2. Robolectric errors - check SDK version"
    echo "  3. Compilation errors - check code syntax"
    exit 1
fi
