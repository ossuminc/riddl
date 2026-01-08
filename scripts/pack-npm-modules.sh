#!/usr/bin/env bash
set -euo pipefail

# Script to build and pack RIDDL JavaScript modules as npm packages
# Usage: ./scripts/pack-npm-modules.sh [module...]
# Example: ./scripts/pack-npm-modules.sh riddlLib utils
# Or: ./scripts/pack-npm-modules.sh (builds all modules)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
NPM_DIST_DIR="$PROJECT_ROOT/npm-packages"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Available modules (without JS suffix)
AVAILABLE_MODULES=("riddlLib" "utils" "passes" "diagrams" "language")

# Modules to build (default: all)
MODULES_TO_BUILD=("${@:-${AVAILABLE_MODULES[@]}}")

echo -e "${BLUE}=== RIDDL npm Package Builder ===${NC}"
echo -e "${BLUE}Project root: ${PROJECT_ROOT}${NC}"
echo ""

# Create npm-packages directory
mkdir -p "$NPM_DIST_DIR"

# Function to get version from sbt
get_version() {
  local module="$1"
  sbt -Dsbt.supershell=false "print ${module}JS/version" 2>/dev/null | \
    grep -v "\[info\]" | grep -v "loading" | grep -v "resolving" | grep -v "set current" | tail -1 | tr -d ' '
}

# Function to build and pack a module
build_and_pack() {
  local module="$1"
  local module_js="${module}JS"

  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${GREEN}Building ${module}...${NC}"

  # Get version from sbt
  echo -e "${YELLOW}Getting version...${NC}"
  local version
  version=$(get_version "$module")

  if [ -z "$version" ]; then
    echo -e "${RED}ERROR: Could not determine version for $module${NC}"
    return 1
  fi

  echo -e "${GREEN}Version: ${version}${NC}"

  # Build the JavaScript (fullOptJS for production)
  echo -e "${YELLOW}Building JavaScript (fullOptJS)...${NC}"
  if ! sbt -Dsbt.supershell=false "project ${module_js}" fullOptJS > /dev/null 2>&1; then
    echo -e "${RED}ERROR: Build failed for $module${NC}"
    return 1
  fi

  # Find the output directory
  local output_dir
  output_dir=$(find "$PROJECT_ROOT/${module}/js/target" -type d -name "*-opt" | head -1)

  if [ ! -d "$output_dir" ]; then
    echo -e "${RED}ERROR: Could not find output directory for $module${NC}"
    return 1
  fi

  echo -e "${GREEN}Output directory: ${output_dir}${NC}"

  # Check if package.json.template exists
  local template_file="$PROJECT_ROOT/${module}/js/package.json.template"
  if [ ! -f "$template_file" ]; then
    echo -e "${RED}ERROR: package.json.template not found at $template_file${NC}"
    return 1
  fi

  # Create package.json from template
  echo -e "${YELLOW}Creating package.json...${NC}"
  sed "s/VERSION_PLACEHOLDER/${version}/g" "$template_file" > "$output_dir/package.json"

  # Create README
  echo -e "${YELLOW}Creating README.md...${NC}"
  local module_lower
  module_lower=$(echo "$module" | tr '[:upper:]' '[:lower:]')

  cat > "$output_dir/README.md" << EOF
# @ossuminc/${module_lower}

RIDDL ${module} module - JavaScript/TypeScript bindings

Version: ${version}

## Installation

From local tarball:
\`\`\`bash
npm install /path/to/@ossuminc-${module_lower}-${version}.tgz
\`\`\`

## Usage

\`\`\`javascript
import * as ${module} from '@ossuminc/${module_lower}';
// Use the module...
\`\`\`

## Documentation

See [RIDDL Documentation](https://github.com/ossuminc/riddl) for more information.

## License

Apache-2.0
EOF

  # Pack the module
  echo -e "${YELLOW}Creating npm package...${NC}"
  cd "$output_dir"

  # Run npm pack and capture the output filename
  local pack_output
  pack_output=$(npm pack --pack-destination="$NPM_DIST_DIR" 2>&1)

  if [ $? -ne 0 ]; then
    echo -e "${RED}ERROR: npm pack failed for $module${NC}"
    echo "$pack_output"
    return 1
  fi

  # Extract the package filename from npm pack output (last line)
  local package_name
  package_name=$(echo "$pack_output" | tail -1)
  local package_path="$NPM_DIST_DIR/$package_name"

  if [ -f "$package_path" ]; then
    local package_size
    package_size=$(du -h "$package_path" | cut -f1)
    echo -e "${GREEN}✓ Created: ${package_name} (${package_size})${NC}"
  else
    echo -e "${RED}ERROR: Package not found at $package_path${NC}"
    return 1
  fi

  cd "$PROJECT_ROOT"
}

# Build each module
failed_modules=()
successful_modules=()

for module in "${MODULES_TO_BUILD[@]}"; do
  if build_and_pack "$module"; then
    successful_modules+=("$module")
  else
    failed_modules+=("$module")
  fi
done

# Summary
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}=== Build Summary ===${NC}"
echo ""

if [ ${#successful_modules[@]} -gt 0 ]; then
  echo -e "${GREEN}Successfully built ${#successful_modules[@]} module(s):${NC}"
  for module in "${successful_modules[@]}"; do
    echo -e "  ${GREEN}✓${NC} $module"
  done
  echo ""
fi

if [ ${#failed_modules[@]} -gt 0 ]; then
  echo -e "${RED}Failed to build ${#failed_modules[@]} module(s):${NC}"
  for module in "${failed_modules[@]}"; do
    echo -e "  ${RED}✗${NC} $module"
  done
  echo ""
  exit 1
fi

echo -e "${GREEN}All packages created in: ${NPM_DIST_DIR}${NC}"
echo ""
echo -e "${BLUE}To install in another project:${NC}"
echo -e "  cd /path/to/your/project"
echo -e "  npm install ${NPM_DIST_DIR}/@ossuminc-riddlLib-*.tgz"
echo ""
