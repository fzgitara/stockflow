/**
 * Production API base URL — overridden at build time (see package.json
 * fileReplacements / deployment CI) to point at the deployed backend.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'http://localhost:8080/api',
};
