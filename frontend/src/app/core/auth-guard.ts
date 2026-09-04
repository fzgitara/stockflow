import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStore } from './token-store';

/** F5: unauthenticated visitors are redirected to /login. */
export const authGuard: CanActivateFn = () => {
  const tokenStore = inject(TokenStore);
  const router = inject(Router);
  if (tokenStore.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login']);
};

/** Keeps authenticated users away from login/register pages. */
export const guestGuard: CanActivateFn = () => {
  const tokenStore = inject(TokenStore);
  const router = inject(Router);
  if (!tokenStore.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/products']);
};
