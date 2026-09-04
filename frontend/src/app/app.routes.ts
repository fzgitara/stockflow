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
  { path: '**', redirectTo: 'products' },
];
