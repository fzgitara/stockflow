#!/usr/bin/env node
/**
 * Generates src/environments/environment.prod.ts at build time.
 * The API base URL comes from API_BASE_URL so CI/CD platforms (Vercel etc.)
 * can point the built SPA at the deployed backend without code edits.
 */
const { writeFileSync, mkdirSync } = require('fs');
const { dirname, join } = require('path');

const apiBaseUrl = process.env.API_BASE_URL || 'http://localhost:8080/api';

const content = `// AUTO-GENERATED at build time by scripts/write-prod-env.js — do not edit.
export const environment = {
  production: true,
  apiBaseUrl: '${apiBaseUrl}',
};
`;

const target = join(__dirname, '..', 'src', 'environments', 'environment.prod.ts');
mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, content);
console.log(`environment.prod.ts written with apiBaseUrl=${apiBaseUrl}`);
