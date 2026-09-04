import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toApiError } from '../../core/auth-interceptor';
import { TokenStore } from '../../core/token-store';
import { ApiError } from '../../core/api-error';
import { ErrorBanner } from '../../shared/error-banner';
import { Loading } from '../../shared/loading';
import { Invoice, InvoiceService } from './invoice-service';

@Component({
  imports: [RouterLink, ErrorBanner, Loading, CurrencyPipe, DatePipe],
  selector: 'app-invoice-detail',
  template: `
    <main class="min-h-screen bg-slate-50">
      <header class="border-b border-slate-200 bg-white">
        <div class="mx-auto flex max-w-4xl items-center justify-between px-4 py-4">
          <nav class="flex items-center gap-2 text-sm">
            <a routerLink="/products" class="text-slate-500 hover:text-slate-900">Products</a>
            <span class="text-slate-300">/</span>
            <a routerLink="/invoices" class="text-slate-500 hover:text-slate-900">Invoices</a>
            <span class="text-slate-300">/</span>
            <span class="font-mono text-slate-900">{{ invoice()?.invoiceNumber }}</span>
          </nav>
          <button (click)="logout()" class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100">Logout</button>
        </div>
      </header>

      <section class="mx-auto max-w-4xl px-4 py-6">
        @if (loading()) {
          <app-loading />
        } @else if (invoice(); as inv) {
          <app-error-banner class="mb-4 block" [error]="error()" />

          <div class="mb-4 flex items-start justify-between">
            <div>
              <h1 class="text-2xl font-semibold">{{ inv.invoiceNumber }}</h1>
              <p class="text-sm text-slate-600">{{ inv.customerName }}</p>
            </div>
            <div class="flex items-center gap-2">
              @if (inv.status === 'DRAFT') {
                <button (click)="action('issue')" [disabled]="acting()"
                        class="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:opacity-50">Issue</button>
              }
              @if (inv.status === 'ISSUED') {
                <button (click)="action('pay')" [disabled]="acting()"
                        class="rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-500 disabled:opacity-50">Mark as paid</button>
              }
              @if (inv.status === 'DRAFT' || inv.status === 'ISSUED') {
                <button (click)="action('cancel')" [disabled]="acting()"
                        class="rounded-md border border-red-300 px-4 py-2 text-sm font-medium text-red-600 hover:bg-red-50 disabled:opacity-50">Cancel</button>
              }
            </div>
          </div>

          <div class="mb-4 flex gap-6 text-sm text-slate-600">
            <span>Status: <strong>{{ inv.status }}</strong></span>
            <span>Issued: {{ inv.issueDate | date:'mediumDate' }}</span>
            <span>Due: {{ inv.dueDate | date:'mediumDate' }}</span>
          </div>

          <div class="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <table class="w-full text-left text-sm">
              <thead class="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th class="px-4 py-3">Product</th>
                  <th class="px-4 py-3 text-right">Unit price</th>
                  <th class="px-4 py-3 text-right">Qty</th>
                  <th class="px-4 py-3 text-right">Line total</th>
                </tr>
              </thead>
              <tbody>
                @for (item of inv.items; track item.id) {
                  <tr class="border-b border-slate-100 last:border-0">
                    <td class="px-4 py-3">{{ item.productName }}</td>
                    <td class="px-4 py-3 text-right">{{ item.unitPrice | currency:'IDR':'symbol':'1.2-2' }}</td>
                    <td class="px-4 py-3 text-right">{{ item.quantity }}</td>
                    <td class="px-4 py-3 text-right">{{ item.lineTotal | currency:'IDR':'symbol':'1.2-2' }}</td>
                  </tr>
                }
              </tbody>
              <tfoot class="border-t border-slate-200 bg-slate-50 text-sm">
                <tr><td colspan="3" class="px-4 py-2 text-right text-slate-600">Subtotal</td>
                    <td class="px-4 py-2 text-right">{{ inv.subtotal | currency:'IDR':'symbol':'1.2-2' }}</td></tr>
                <tr><td colspan="3" class="px-4 py-2 text-right text-slate-600">Tax (11%)</td>
                    <td class="px-4 py-2 text-right">{{ inv.taxAmount | currency:'IDR':'symbol':'1.2-2' }}</td></tr>
                <tr class="font-semibold"><td colspan="3" class="px-4 py-2 text-right">Total</td>
                    <td class="px-4 py-2 text-right">{{ inv.total | currency:'IDR':'symbol':'1.2-2' }}</td></tr>
              </tfoot>
            </table>
          </div>
          @if (inv.notes) {
            <p class="mt-4 text-sm text-slate-600"><strong>Notes:</strong> {{ inv.notes }}</p>
          }
        }
      </section>
    </main>
  `,
})
export class InvoiceDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly invoiceService = inject(InvoiceService);
  private readonly router = inject(Router);
  private readonly tokenStore = inject(TokenStore);

  readonly invoice = signal<Invoice | null>(null);
  readonly loading = signal(true);
  readonly acting = signal(false);
  readonly error = signal<ApiError | null>(null);

  constructor() {
    this.route.paramMap.subscribe((params) => {
      const id = params.get('id');
      if (id) this.load(id);
    });
  }

  load(id: string): void {
    this.loading.set(true);
    this.invoiceService.get(id).subscribe({
      next: (inv) => {
        this.invoice.set(inv);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(toApiError(err));
        this.loading.set(false);
      },
    });
  }

  action(name: 'issue' | 'pay' | 'cancel'): void {
    const inv = this.invoice();
    if (!inv) return;
    this.acting.set(true);
    this.error.set(null);
    const call = name === 'issue'
      ? this.invoiceService.issue(inv.id)
      : name === 'pay'
        ? this.invoiceService.pay(inv.id)
        : this.invoiceService.cancel(inv.id);
    call.subscribe({
      next: (updated) => {
        this.invoice.set(updated);
        this.acting.set(false);
      },
      error: (err: unknown) => {
        this.error.set(toApiError(err));
        this.acting.set(false);
      },
    });
  }

  logout(): void {
    this.tokenStore.clear();
    this.router.navigate(['/login']);
  }
}
