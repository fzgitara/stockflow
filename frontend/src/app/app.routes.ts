import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth-guard';
import { Login } from './features/auth/login';
import { Register } from './features/auth/register';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'products' },
  { path: 'login', component: Login, canActivate: [guestGuard] },
  { path: 'register', component: Register, canActivate: [guestGuard] },
  {
    path: 'products',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/products/products-list').then((m) => m.ProductsList),
  },
  {
    path: 'invoices',
    canActivate: [authGuard],
    children: [
      { path: '', loadComponent: () => import('./features/invoices/invoices-list').then((m) => m.InvoicesList) },
      { path: 'new', loadComponent: () => import('./features/invoices/invoice-create').then((m) => m.InvoiceCreate) },
      { path: ':id', loadComponent: () => import('./features/invoices/invoice-detail').then((m) => m.InvoiceDetail) },
    ],
  },
  { path: '**', redirectTo: 'products' },
];
