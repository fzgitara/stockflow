import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ApiError } from './api-error';
import { TokenStore } from './token-store';

/**
 * Attaches the JWT to every request; on 401 (expired/invalid token) clears
 * the session and sends the user to login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStore = inject(TokenStore);
  const router = inject(Router);

  const token = tokenStore.get();
  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        const isAuthCall = req.url.includes('/auth/');
        if (!isAuthCall) {
          tokenStore.clear();
          router.navigate(['/login']);
        }
      }
      return throwError(() => err);
    }),
  );
};

/** Extracts the backend's ApiError payload from an HttpErrorResponse. */
export function toApiError(err: unknown): ApiError {
  if (err instanceof HttpErrorResponse && typeof err.error === 'object' && err.error !== null) {
    return err.error as ApiError;
  }
  return {
    status: 0,
    error: 'Network Error',
    message: 'Could not reach the server. Is the backend running?',
    timestamp: new Date().toISOString(),
  };
}
