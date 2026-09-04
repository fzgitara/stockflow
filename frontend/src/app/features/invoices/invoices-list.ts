import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { toApiError } from '../../core/auth-interceptor';
import { TokenStore } from '../../core/token-store';
import { ApiError } from '../../core/api-error';
import { ErrorBanner } from '../../shared/error-banner';
import { Loading } from '../../shared/loading';
import { Invoice, InvoiceService, InvoiceStatus } from './invoice-service';

const STATUS_STYLES: Record<InvoiceStatus, string> = {
  DRAFT: 'bg-slate-100 text-slate-700',
  ISSUED: 'bg-blue-100 text-blue-700',
  PAID: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-red-100 text-red-700',
};

@Component({
  imports: [RouterLink, ErrorBanner, Loading, CurrencyPipe, DatePipe],
  selector: 'app-invoices-list',
  template: `
    <main class="min-h-screen bg-slate-50">
      <header class="border-b border-slate-200 bg-white">
        <div class="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
          <nav class="flex items-center gap-4 text-sm">
            <a routerLink="/products" class="text-slate-500 hover:text-slate-900">Products</a>
            <span class="font-semibold text-slate-900">Invoices</span>
          </nav>
          <button (click)="logout()" class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100">Logout</button>
        </div>
      </header>

      <section class="mx-auto max-w-5xl px-4 py-6">
        <app-error-banner class="mb-4 block" [error]="error()" />

        <div class="mb-4 flex items-center justify-between">
          <div class="flex gap-2">
            @for (s of statusFilters; track s.value) {
              <button (click)="setStatus(s.value)"
                      class="rounded-full px-3 py-1.5 text-sm"
                      [class]="status() === s.value
                        ? 'bg-slate-900 text-white'
                        : 'border border-slate-300 text-slate-600 hover:bg-slate-100'">
                {{ s.label }}
              </button>
            }
          </div>
          <a routerLink="/invoices/new"
             class="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700">
            + New invoice
          </a>
        </div>

        @if (loading()) {
          <app-loading />
        } @else {
          <div class="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <table class="w-full text-left text-sm">
              <thead class="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th class="px-4 py-3">Number</th>
                  <th class="px-4 py-3">Customer</th>
                  <th class="px-4 py-3">Issue date</th>
                  <th class="px-4 py-3">Status</th>
                  <th class="px-4 py-3 text-right">Total</th>
                </tr>
              </thead>
              <tbody>
                @for (inv of invoices(); track inv.id) {
                  <tr class="cursor-pointer border-b border-slate-100 last:border-0 hover:bg-slate-50"
                      (click)="open(inv.id)">
                    <td class="px-4 py-3 font-mono text-xs">{{ inv.invoiceNumber }}</td>
                    <td class="px-4 py-3">{{ inv.customerName }}</td>
                    <td class="px-4 py-3">{{ inv.issueDate | date:'mediumDate' }}</td>
                    <td class="px-4 py-3">
                      <span class="rounded-full px-2.5 py-0.5 text-xs font-medium" [class]="STATUS_STYLES[inv.status]">
                        {{ inv.status }}
                      </span>
                    </td>
                    <td class="px-4 py-3 text-right">{{ inv.total | currency:'IDR':'symbol':'1.2-2' }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="5" class="px-4 py-10 text-center text-slate-500">No invoices found.</td></tr>
                }
              </tbody>
            </table>
          </div>
          <nav class="mt-4 flex items-center justify-between text-sm text-slate-600">
            <span>{{ page() + 1 }} / {{ totalPages() }} ({{ totalElements() }} invoices)</span>
            <div class="flex gap-2">
              <button (click)="goToPage(page() - 1)" [disabled]="page() === 0"
                      class="rounded border border-slate-300 px-3 py-1 disabled:opacity-40">Prev</button>
              <button (click)="goToPage(page() + 1)" [disabled]="last()"
                      class="rounded border border-slate-300 px-3 py-1 disabled:opacity-40">Next</button>
            </div>
          </nav>
        }
      </section>
    </main>
  `,
})
export class InvoicesList {
  private readonly invoiceService = inject(InvoiceService);
  private readonly router = inject(Router);
  private readonly tokenStore = inject(TokenStore);

  readonly statusFilters: { value: InvoiceStatus | ''; label: string }[] = [
    { value: '', label: 'All' },
    { value: 'DRAFT', label: 'Draft' },
    { value: 'ISSUED', label: 'Issued' },
    { value: 'PAID', label: 'Paid' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ];

  readonly invoices = signal<Invoice[]>([]);
  readonly status = signal<InvoiceStatus | ''>('');
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly totalElements = signal(0);
  readonly last = signal(true);
  readonly loading = signal(true);
  readonly error = signal<ApiError | null>(null);

  protected readonly STATUS_STYLES = STATUS_STYLES;

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.invoiceService.list(this.status(), this.page(), 10).subscribe({
      next: (res) => {
        this.invoices.set(res.content);
        this.page.set(res.page);
        this.totalPages.set(Math.max(res.totalPages, 1));
        this.totalElements.set(res.totalElements);
        this.last.set(res.last);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(toApiError(err));
        this.loading.set(false);
      },
    });
  }

  setStatus(status: InvoiceStatus | ''): void {
    this.status.set(status);
    this.page.set(0);
    this.load();
  }

  goToPage(p: number): void {
    if (p < 0 || p >= this.totalPages()) return;
    this.page.set(p);
    this.load();
  }

  open(id: string): void {
    this.router.navigate(['/invoices', id]);
  }

  logout(): void {
    this.tokenStore.clear();
    this.router.navigate(['/login']);
  }
}
