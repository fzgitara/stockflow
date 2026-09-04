/**
 * API base URL: dev uses the Angular proxy (relative /api),
 * production builds inject the real backend origin.
 */
export const environment = {
  production: false,
  apiBaseUrl: '/api',
};
