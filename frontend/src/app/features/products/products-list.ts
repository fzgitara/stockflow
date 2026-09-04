import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TokenStore } from '../../core/token-store';

/** Placeholder products shell — real list lands in Phase 2. */
@Component({
  imports: [],
  selector: 'app-products-list',
  template: `
    <main class="min-h-screen bg-slate-50">
      <header class="border-b border-slate-200 bg-white">
        <div class="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
          <h1 class="text-xl font-semibold">StockFlow — Products</h1>
          <button (click)="logout()"
                  class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100">
            Logout
          </button>
        </div>
      </header>
      <section class="mx-auto max-w-5xl px-4 py-8">
        <p class="text-slate-600">Authenticated ✓ — product table coming in Phase 2.</p>
      </section>
    </main>
  `,
})
export class ProductsList {
  private readonly tokenStore = inject(TokenStore);
  private readonly router = inject(Router);

  readonly hasToken = signal(this.tokenStore.isAuthenticated());

  logout(): void {
    this.tokenStore.clear();
    this.router.navigate(['/login']);
  }
}
