import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { toApiError } from '../../core/auth-interceptor';
import { TokenStore } from '../../core/token-store';
import { ApiError } from '../../core/api-error';
import { ErrorBanner } from '../../shared/error-banner';
import { Loading } from '../../shared/loading';
import { Product, ProductService } from '../products/product-service';
import { InvoiceService, InvoicePayload } from './invoice-service';

interface DraftLine {
  productId: string;
  quantity: number;
}

@Component({
  imports: [FormsModule, RouterLink, ErrorBanner, Loading, CurrencyPipe],
  selector: 'app-invoice-create',
  template: `
    <main class="min-h-screen bg-slate-50">
      <header class="border-b border-slate-200 bg-white">
        <div class="mx-auto flex max-w-4xl items-center justify-between px-4 py-4">
          <nav class="flex items-center gap-2 text-sm">
            <a routerLink="/products" class="text-slate-500 hover:text-slate-900">Products</a>
            <span class="text-slate-300">/</span>
            <a routerLink="/invoices" class="text-slate-500 hover:text-slate-900">Invoices</a>
            <span class="text-slate-300">/</span>
            <span class="text-slate-900">New</span>
          </nav>
          <button (click)="logout()" class="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100">Logout</button>
        </div>
      </header>

      <section class="mx-auto max-w-4xl px-4 py-6">
        <h1 class="mb-4 text-2xl font-semibold">New invoice</h1>
        <app-error-banner class="mb-4 block" [error]="error()" />

        @if (loadingProducts()) {
          <app-loading />
        } @else {
          <form (ngSubmit)="save()" class="space-y-6">
            <div class="rounded-lg border border-slate-200 bg-white p-6">
              <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
                <div class="sm:col-span-3">
                  <label class="mb-1 block text-sm font-medium">Customer name</label>
                  <input [(ngModel)]="customerName" name="customerName" required
                         class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
                </div>
                <div>
                  <label class="mb-1 block text-sm font-medium">Issue date</label>
                  <input type="date" [(ngModel)]="issueDate" name="issueDate" required
                         class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
                </div>
                <div>
                  <label class="mb-1 block text-sm font-medium">Due date</label>
                  <input type="date" [(ngModel)]="dueDate" name="dueDate" required
                         class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
                </div>
                <div class="sm:col-span-3">
                  <label class="mb-1 block text-sm font-medium">Notes (optional)</label>
                  <textarea [(ngModel)]="notes" name="notes" rows="2"
                            class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"></textarea>
                </div>
              </div>
            </div>

            <div class="rounded-lg border border-slate-200 bg-white p-6">
              <h2 class="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Line items</h2>
              @for (line of lines(); track $index; let i = $index) {
                <div class="mb-3 flex items-end gap-3">
                  <div class="flex-1">
                    <label class="mb-1 block text-xs text-slate-500">Product</label>
                    <select [(ngModel)]="line.productId" [ngModelOptions]="{standalone: true}"
                            (ngModelChange)="lines.update(v => [...v])"
                            class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
                      <option [ngValue]="''" disabled>Select product…</option>
                      @for (p of products(); track p.id) {
                        <option [ngValue]="p.id">{{ p.sku }} — {{ p.name }} (stock {{ p.quantityOnHand }})</option>
                      }
                    </select>
                  </div>
                  <div class="w-24">
                    <label class="mb-1 block text-xs text-slate-500">Qty</label>
                    <input type="number" min="1" [(ngModel)]="line.quantity" [ngModelOptions]="{standalone: true}"
                           (ngModelChange)="lines.update(v => [...v])"
                           class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
                  </div>
                  <div class="w-32 pb-2 text-right text-sm">
                    {{ lineTotal(line) | currency:'IDR':'symbol':'1.2-2' }}
                  </div>
                  <button type="button" (click)="removeLine(i)"
                          class="pb-2 text-sm text-red-600 underline">Remove</button>
                </div>
              }
              <button type="button" (click)="addLine()"
                      class="rounded-md border border-dashed border-slate-300 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50">
                + Add line
              </button>
            </div>

            <div class="rounded-lg border border-slate-200 bg-white p-6">
              <div class="ml-auto max-w-xs space-y-1 text-sm">
                <div class="flex justify-between"><span class="text-slate-600">Subtotal</span>
                  <span>{{ subtotal() | currency:'IDR':'symbol':'1.2-2' }}</span></div>
                <div class="flex justify-between"><span class="text-slate-600">Tax (11%)</span>
                  <span>{{ taxAmount() | currency:'IDR':'symbol':'1.2-2' }}</span></div>
                <div class="flex justify-between border-t border-slate-200 pt-1 text-base font-semibold">
                  <span>Total</span><span>{{ grandTotal() | currency:'IDR':'symbol':'1.2-2' }}</span>
                </div>
                <p class="pt-1 text-xs text-slate-400">Totals are always recalculated by the server.</p>
              </div>
            </div>

            <div class="flex justify-end gap-2">
              <a routerLink="/invoices" class="rounded-md border border-slate-300 px-4 py-2 text-sm">Cancel</a>
              <button type="submit" [disabled]="!isValid() || saving()"
                      class="rounded-md bg-slate-900 px-6 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50">
                {{ saving() ? 'Saving…' : 'Save as draft' }}
              </button>
            </div>
          </form>
        }
      </section>
    </main>
  `,
})
export class InvoiceCreate {
  private readonly productService = inject(ProductService);
  private readonly invoiceService = inject(InvoiceService);
  private readonly router = inject(Router);
  private readonly tokenStore = inject(TokenStore);

  readonly products = signal<Product[]>([]);
  readonly loadingProducts = signal(true);
  readonly saving = signal(false);
  readonly error = signal<ApiError | null>(null);

  readonly lines = signal<DraftLine[]>([{ productId: '', quantity: 1 }]);

  customerName = '';
  issueDate = new Date().toISOString().slice(0, 10);
  dueDate = new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10);
  notes = '';

  /** Live totals for display only — the server always recomputes. */
  readonly subtotal = computed(() => this.lines().reduce((sum, l) => sum + this.lineTotal(l), 0));
  readonly taxAmount = computed(() => Math.round(this.subtotal() * 0.11 * 100) / 100);
  readonly grandTotal = computed(() => this.subtotal() + this.taxAmount());

  constructor() {
    this.productService.list('', 0, 100, 'name', 'asc').subscribe({
      next: (res) => {
        this.products.set(res.content);
        this.loadingProducts.set(false);
      },
      error: (err: unknown) => {
        this.error.set(toApiError(err));
        this.loadingProducts.set(false);
      },
    });
  }

  private productById(id: string): Product | undefined {
    return this.products().find((p) => p.id === id);
  }

  lineTotal(line: DraftLine): number {
    const p = this.productById(line.productId);
    return p ? p.unitPrice * (line.quantity || 0) : 0;
  }

  isValid(): boolean {
    return this.customerName.trim().length > 0
      && this.lines().length > 0
      && this.lines().every((l) => l.productId && l.quantity >= 1)
      && this.dueDate >= this.issueDate;
  }

  addLine(): void {
    this.lines.update((v) => [...v, { productId: '', quantity: 1 }]);
  }

  removeLine(index: number): void {
    this.lines.update((v) => v.filter((_, i) => i !== index));
  }

  save(): void {
    this.saving.set(true);
    this.error.set(null);
    const payload: InvoicePayload = {
      customerName: this.customerName.trim(),
      issueDate: this.issueDate,
      dueDate: this.dueDate,
      notes: this.notes.trim() || null,
      items: this.lines().map((l) => ({ productId: l.productId, quantity: Number(l.quantity) })),
    };
    this.invoiceService.create(payload).subscribe({
      next: (inv) => this.router.navigate(['/invoices', inv.id]),
      error: (err: unknown) => {
        this.error.set(toApiError(err));
        this.saving.set(false);
      },
    });
  }

  logout(): void {
    this.tokenStore.clear();
    this.router.navigate(['/login']);
  }
}
