import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../products/product-service';

export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PAID' | 'CANCELLED';

export interface InvoiceItem {
  id: string;
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface Invoice {
  id: string;
  invoiceNumber: string;
  customerName: string;
  issueDate: string;
  dueDate: string;
  status: InvoiceStatus;
  notes: string | null;
  subtotal: number;
  taxAmount: number;
  total: number;
  items: InvoiceItem[];
}

export interface InvoiceItemPayload {
  productId: string;
  quantity: number;
}

export interface InvoicePayload {
  customerName: string;
  issueDate: string;
  dueDate: string;
  notes: string | null;
  items: InvoiceItemPayload[];
}

@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/invoices`;

  list(status: InvoiceStatus | '', page: number, size: number): Observable<PageResponse<Invoice>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<Invoice>>(this.baseUrl, { params });
  }

  get(id: string): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.baseUrl}/${id}`);
  }

  create(payload: InvoicePayload): Observable<Invoice> {
    return this.http.post<Invoice>(this.baseUrl, payload);
  }

  update(id: string, payload: InvoicePayload): Observable<Invoice> {
    return this.http.put<Invoice>(`${this.baseUrl}/${id}`, payload);
  }

  issue(id: string): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.baseUrl}/${id}/issue`, {});
  }

  pay(id: string): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.baseUrl}/${id}/pay`, {});
  }

  cancel(id: string): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.baseUrl}/${id}/cancel`, {});
  }
}
