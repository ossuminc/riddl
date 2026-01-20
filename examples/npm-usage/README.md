# RIDDL npm Package Usage Example

This example demonstrates how to use RIDDL JavaScript modules in a Node.js project.

## Prerequisites

1. Build the npm packages:
   ```bash
   cd ../..
   ./scripts/pack-npm-modules.sh
   ```

2. This will create packages in `npm-packages/` directory.

## Installation

```bash
npm install
```

This will install the RIDDL modules from the local `.tgz` files referenced in `package.json`.

## Running the Example

```bash
npm start
```

Or directly:
```bash
node index.js
```

## How It Works

1. **package.json** references local `.tgz` files:
   ```json
   {
     "dependencies": {
       "@ossuminc/riddl-lib": "file:../../npm-packages/ossuminc-riddl-lib-1.0.1-6-4aea8145.tgz"
     }
   }
   ```

2. **index.js** imports the modules:
   ```javascript
   import * as riddlLib from '@ossuminc/riddl-lib';
   ```

3. **npm install** extracts the `.tgz` files into `node_modules/`

## Updating Packages

After rebuilding RIDDL packages:

```bash
# Remove old installations
rm -rf node_modules package-lock.json

# Update package.json with new version numbers if needed
# Then reinstall
npm install
```

## Using in Your Own Project

Copy the `package.json` dependency format to your project:

```json
{
  "dependencies": {
    "@ossuminc/riddl-lib": "file:/absolute/path/to/riddl/npm-packages/ossuminc-riddl-lib-*.tgz"
  }
}
```

**Note:** You can use absolute paths or relative paths depending on where your project is located.
