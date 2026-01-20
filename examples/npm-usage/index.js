// Example: Using RIDDL JavaScript modules in a Node.js project

import * as riddlLib from '@ossuminc/riddl-lib';
import * as riddlUtils from '@ossuminc/riddl-utils';

console.log('RIDDL npm Package Example');
console.log('========================\n');

// Check that modules are loaded
console.log('riddlLib loaded:', typeof riddlLib);
console.log('riddlUtils loaded:', typeof riddlUtils);

// Explore what's available in the modules
console.log('\nriddlLib exports:', Object.keys(riddlLib).slice(0, 10), '...');
console.log('riddlUtils exports:', Object.keys(riddlUtils).slice(0, 10), '...');

// Example usage - this will depend on what the Scala.js code exports
// The actual API will be determined by the @JSExport annotations in the Scala code

console.log('\nSuccess! RIDDL JavaScript modules are working.');
