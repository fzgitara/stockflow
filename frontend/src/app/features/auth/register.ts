import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { toApiError } from '../../core/auth-interceptor';
import { AuthService } from '../../core/auth-service';
import { ErrorBanner } from '../../shared/error-banner';
import { ApiError } from '../../core/api-error';

@Component({
  imports: [FormsModule, RouterLink, ErrorBanner],
  selector: 'app-register',
  template: `
    <main class="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div class="w-full max-w-sm">
        <h1 class="mb-6 text-center text-2xl font-semibold text-slate-900">StockFlow</h1>
        <app-error-banner class="mb-4 block" [error]="error()" />
        <form class="space-y-4 rounded-lg border border-slate-200 bg-white p-6 shadow-sm" (ngSubmit)="submit()">
          <div>
            <label for="email" class="mb-1 block text-sm font-medium text-slate-700">Email</label>
            <input id="email" type="email" required [(ngModel)]="email" name="email"
                   class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none" />
          </div>
          <div>
            <label for="password" class="mb-1 block text-sm font-medium text-slate-700">Password</label>
            <input id="password" type="password" required minlength="8" [(ngModel)]="password" name="password"
                   class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none" />
            <p class="mt-1 text-xs text-slate-500">At least 8 characters.</p>
          </div>
          <button type="submit" [disabled]="busy() || !email || password.length < 8"
                  class="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50">
            {{ busy() ? 'Creating account…' : 'Create account' }}
          </button>
          <p class="text-center text-sm text-slate-600">
            Already registered?
            <a routerLink="/login" class="font-medium text-slate-900 underline">Sign in</a>
          </p>
        </form>
      </div>
    </main>
  `,
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly email = signal('');
  readonly password = signal('');
  readonly busy = signal(false);
  readonly error = signal<ApiError | null>(null);

  submit(): void {
    this.busy.set(true);
    this.error.set(null);
    this.auth.register(this.email(), this.password()).subscribe({
      next: () => this.router.navigate(['/login']),
      error: (err: unknown) => {
        this.error.set(toApiError(err));
        this.busy.set(false);
      },
    });
  }
}
