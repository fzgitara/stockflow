import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { toApiError } from '../../core/auth-interceptor';
import { TokenStore } from '../../core/token-store';
import { ErrorBanner } from '../../shared/error-banner';
import { Loading } from '../../shared/loading';
import { ApiError } from '../../core/api-error';
import { Product, ProductPayload, ProductService } from './product-service';

@Component({
  imports: [FormsModule, ErrorBanner, Loading, CurrencyPipe, DecimalPipe],
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

      <section class="mx-auto max-w-5xl px-4 py-6">
        <app-error-banner class="mb-4 block" [error]="error()" />

        <div class="mb-4 flex items-center justify-between gap-3">
          <input type="search" placeholder="Search by name or SKU…"
                 [ngModel]="search()" (ngModelChange)="onSearch($event)"
                 class="w-72 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none" />
          <button (click)="openCreate()"
                  class="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700">
            + New product
          </button>
        </div>

        @if (loading()) {
          <app-loading />
        } @else {
          <div class="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <table class="w-full text-left text-sm">
              <thead class="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th class="cursor-pointer select-none px-4 py-3 hover:text-slate-800" (click)="toggleSort('sku')">
                    SKU {{ sortArrow('sku') }}
                  </th>
                  <th class="cursor-pointer select-none px-4 py-3 hover:text-slate-800" (click)="toggleSort('name')">
                    Name {{ sortArrow('name') }}
                  </th>
                  <th class="cursor-pointer select-none px-4 py-3 text-right hover:text-slate-800" (click)="toggleSort('unitPrice')">
                    Price {{ sortArrow('unitPrice') }}
                  </th>
                  <th class="cursor-pointer select-none px-4 py-3 text-right hover:text-slate-800" (click)="toggleSort('quantityOnHand')">
                    Stock {{ sortArrow('quantityOnHand') }}
                  </th>
                  <th class="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (p of products(); track p.id) {
                  <tr class="border-b border-slate-100 last:border-0">
                    <td class="px-4 py-3 font-mono text-xs">{{ p.sku }}</td>
                    <td class="px-4 py-3">{{ p.name }}</td>
                    <td class="px-4 py-3 text-right">{{ p.unitPrice | currency:'IDR':'symbol':'1.2-2' }}</td>
                    <td class="px-4 py-3 text-right">{{ p.quantityOnHand | number }}</td>
                    <td class="px-4 py-3 text-right">
                      <button (click)="openEdit(p)" class="mr-2 text-slate-600 underline hover:text-slate-900">Edit</button>
                      <button (click)="confirmDelete(p)" class="text-red-600 underline hover:text-red-800">Delete</button>
                    </td>
                  </tr>
                } @empty {
                  <tr><td colspan="5" class="px-4 py-10 text-center text-slate-500">
                    No products found. Create your first product.
                  </td></tr>
                }
              </tbody>
            </table>
          </div>

          <nav class="mt-4 flex items-center justify-between text-sm text-slate-600">
            <span>{{ page() + 1 }} / {{ totalPages() }} ({{ totalElements() }} items)</span>
            <div class="flex gap-2">
              <button (click)="goToPage(page() - 1)" [disabled]="page() === 0"
                      class="rounded border border-slate-300 px-3 py-1 disabled:opacity-40">Prev</button>
              <button (click)="goToPage(page() + 1)" [disabled]="last()"
                      class="rounded border border-slate-300 px-3 py-1 disabled:opacity-40">Next</button>
            </div>
          </nav>
        }
      </section>

      <!-- Create / edit modal -->
      @if (formOpen()) {
        <div class="fixed inset-0 z-10 flex items-center justify-center bg-black/40 px-4">
          <div class="w-full max-w-md rounded-lg bg-white p-6 shadow-lg">
            <h2 class="mb-4 text-lg font-semibold">{{ editingId() ? 'Edit product' : 'New product' }}</h2>
            <app-error-banner class="mb-3 block" [error]="formError()" />
            <form class="space-y-3" (ngSubmit)="save()">
              <div>
                <label class="mb-1 block text-sm font-medium">SKU</label>
                <input [(ngModel)]="form.sku" name="sku" required
                       class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
              </div>
              <div>
                <label class="mb-1 block text-sm font-medium">Name</label>
                <input [(ngModel)]="form.name" name="name" required
                       class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
              </div>
              <div>
                <label class="mb-1 block text-sm font-medium">Description</label>
                <input [(ngModel)]="form.description" name="description"
                       class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="mb-1 block text-sm font-medium">Unit price</label>
                  <input type="number" min="0" step="0.01" [(ngModel)]="form.unitPrice" name="unitPrice" required
                         class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
                </div>
                <div>
                  <label class="mb-1 block text-sm font-medium">Quantity on hand</label>
                  <input type="number" min="0" step="1" [(ngModel)]="form.quantityOnHand" name="quantityOnHand" required
                         class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
                </div>
              </div>
              <div class="mt-4 flex justify-end gap-2">
                <button type="button" (click)="closeForm()"
                        class="rounded-md border border-slate-300 px-4 py-2 text-sm">Cancel</button>
                <button type="submit" [disabled]="saving()"
                        class="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50">
                  {{ saving() ? 'Saving…' : 'Save' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      }
    </main>
  `,
})
export class ProductsList {
  private readonly productService = inject(ProductService);
  private readonly tokenStore = inject(TokenStore);
  private readonly router = inject(Router);

  readonly products = signal<Product[]>([]);
  readonly search = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly totalElements = signal(0);
  readonly last = signal(true);
  readonly loading = signal(true);
  readonly error = signal<ApiError | null>(null);
  readonly sortBy = signal('createdAt');
  readonly sortDir = signal<'asc' | 'desc'>('desc');

  readonly formOpen = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly saving = signal(false);
  readonly formError = signal<ApiError | null>(null);
  form: ProductPayload = { sku: '', name: '', description: '', unitPrice: 0, quantityOnHand: 0 };

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.productService.list(this.search(), this.page(), 10, this.sortBy(), this.sortDir()).subscribe({
      next: (res) => {
        this.products.set(res.content);
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

  onSearch(value: string): void {
    this.search.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.page.set(0);
      this.load();
    }, 300);
  }

  goToPage(p: number): void {
    if (p < 0 || p >= this.totalPages()) return;
    this.page.set(p);
    this.load();
  }

  toggleSort(field: string): void {
    if (this.sortBy() === field) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortBy.set(field);
      this.sortDir.set('asc');
    }
    this.page.set(0);
    this.load();
  }

  sortArrow(field: string): string {
    if (this.sortBy() !== field) return '';
    return this.sortDir() === 'asc' ? '▲' : '▼';
  }

  openCreate(): void {
    this.editingId.set(null);
    this.form = { sku: '', name: '', description: '', unitPrice: 0, quantityOnHand: 0 };
    this.formError.set(null);
    this.formOpen.set(true);
  }

  openEdit(p: Product): void {
    this.editingId.set(p.id);
    this.form = {
      sku: p.sku,
      name: p.name,
      description: p.description ?? '',
      unitPrice: p.unitPrice,
      quantityOnHand: p.quantityOnHand,
    };
    this.formError.set(null);
    this.formOpen.set(true);
  }

  closeForm(): void {
    this.formOpen.set(false);
  }

  save(): void {
    this.saving.set(true);
    this.formError.set(null);
    const payload: ProductPayload = {
      ...this.form,
      unitPrice: Number(this.form.unitPrice),
      quantityOnHand: Number(this.form.quantityOnHand),
    };
    const call = this.editingId()
      ? this.productService.update(this.editingId()!, payload)
      : this.productService.create(payload);
    call.subscribe({
      next: () => {
        this.formOpen.set(false);
        this.saving.set(false);
        this.load();
      },
      error: (err: unknown) => {
        this.formError.set(toApiError(err));
        this.saving.set(false);
      },
    });
  }

  confirmDelete(p: Product): void {
    if (!confirm(`Delete "${p.name}" (${p.sku})?`)) return;
    this.productService.remove(p.id).subscribe({
      next: () => this.load(),
      error: (err: unknown) => this.error.set(toApiError(err)),
    });
  }

  logout(): void {
    this.tokenStore.clear();
    this.router.navigate(['/login']);
  }
}
