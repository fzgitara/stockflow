import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { TokenStore } from './token-store';

export interface LoginResult {
  token: string;
  expiresInMinutes: number;
}

export interface RegisterResult {
  id: string;
  email: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenStore = inject(TokenStore);
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;

  register(email: string, password: string): Observable<RegisterResult> {
    return this.http.post<RegisterResult>(`${this.baseUrl}/register`, { email, password });
  }

  login(email: string, password: string): Observable<LoginResult> {
    return this.http
      .post<LoginResult>(`${this.baseUrl}/login`, { email, password })
      .pipe(tap((result) => this.tokenStore.set(result.token)));
  }

  logout(): void {
    // Fire-and-forget; the server is stateless, the real logout is local.
    this.http.post(`${this.baseUrl}/logout`, {}).subscribe({ error: () => undefined });
    this.tokenStore.clear();
  }

  isAuthenticated(): boolean {
    return this.tokenStore.isAuthenticated();
  }
}
