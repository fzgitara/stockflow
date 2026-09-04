import { Injectable, signal } from '@angular/core';

const TOKEN_KEY = 'stockflow.token';

/**
 * Holds the JWT for the current session. Logout = discard the token
 * (stateless server-side; documented trade-off in the README).
 */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private readonly token = signal<string | null>(this.readInitial());

  get(): string | null {
    return this.token();
  }

  set(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
    this.token.set(token);
  }

  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.token.set(null);
  }

  isAuthenticated(): boolean {
    return this.token() !== null;
  }

  private readInitial(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }
}
